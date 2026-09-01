package com.pavex.identity.service

import com.pavex.identity.dto.admin.CreateUserRequest
import com.pavex.identity.dto.admin.CreateUserResponse
import com.pavex.identity.dto.admin.UpdateUserProfileRequest
import com.pavex.identity.dto.admin.UserDetailResponse
import com.pavex.identity.dto.admin.UserListItemResponse
import com.pavex.common.api.PageResponse
import com.pavex.common.api.OneBasedPagination
import com.pavex.identity.entity.AccountStatus
import com.pavex.identity.entity.Gender
import com.pavex.identity.entity.ProfileStatus
import com.pavex.identity.entity.UserAccount
import com.pavex.identity.entity.UserProfile
import com.pavex.identity.entity.UserRole
import com.pavex.identity.exception.InvalidUserProfileException
import com.pavex.identity.exception.LastActiveAdminException
import com.pavex.identity.exception.RoleNotFoundException
import com.pavex.identity.exception.UserNotFoundException
import com.pavex.identity.repository.RoleRepository
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import com.pavex.identity.repository.UserRoleRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class AdminUserService(
    private val accounts: UserAccountRepository,
    private val profiles: UserProfileRepository,
    private val userRoles: UserRoleRepository,
    private val roles: RoleRepository,
    private val roleService: RoleService,
    private val authCredentialClient: AuthCredentialClient,
) {
    @Transactional
    fun createUser(actorUserId: UUID, request: CreateUserRequest): CreateUserResponse {
        val requestedRole = request.roleCode.trim().uppercase()
        if (requestedRole in RoleService.WORKFORCE_MANAGED_ROLES) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Operational workforce roles must be provisioned from Operations > Workforce so Identity profile and workforce state stay consistent.",
            )
        }
        return createUserInternal(actorUserId, request)
    }

    @Transactional
    fun createWorkforceUser(actorUserId: UUID, request: CreateUserRequest): CreateUserResponse {
        val requestedRole = request.roleCode.trim().uppercase()
        if (requestedRole !in RoleService.WORKFORCE_MANAGED_ROLES) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Role is not managed by workforce onboarding")
        }
        return createUserInternal(actorUserId, request)
    }

    private fun createUserInternal(actorUserId: UUID, request: CreateUserRequest): CreateUserResponse {
        val email = request.email.trim().lowercase()
        if (accounts.existsByEmail(email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")
        }
        val role = roles.findByCode(request.roleCode.trim().uppercase()) ?: throw RoleNotFoundException()
        validateAvatarUrl(request.avatarUrl)
        val now = Instant.now()
        val account = accounts.save(UserAccount(email = email, createdAt = now, updatedAt = now))
        val firstName = normalize(request.firstName)
        val lastName = normalize(request.lastName)
        val displayName = normalize(request.displayName)
        val avatarUrl = normalize(request.avatarUrl)
        if (listOf(firstName, lastName, displayName, avatarUrl).any { it != null } ||
            request.gender != Gender.UNSPECIFIED || request.dateOfBirth != null
        ) {
            profiles.save(
                UserProfile(
                    userId = account.id,
                    firstName = firstName,
                    lastName = lastName,
                    displayName = displayName,
                    gender = request.gender,
                    dateOfBirth = request.dateOfBirth,
                    avatarUrl = avatarUrl,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            if (hasRequiredDetails(firstName, lastName, request.gender, request.dateOfBirth)) {
                account.profileStatus = ProfileStatus.COMPLETED
            }
        }
        userRoles.save(
            UserRole(
                userId = account.id,
                roleId = role.id,
                assignedAt = now,
                assignedBy = actorUserId,
            )
        )
        account.authorizationVersion += 1
        account.updatedAt = now
        accounts.save(account)
        authCredentialClient.provisionInvitation(account.id, account.email)
        return CreateUserResponse(account.id)
    }

    @Transactional(readOnly = true)
    fun searchUsers(
        pageInput: Int,
        pageSizeInput: Int,
        searchInput: String?,
        roleInput: String?,
        status: AccountStatus?,
        profileStatus: ProfileStatus?,
    ): PageResponse<UserListItemResponse> {
        val pagination = OneBasedPagination.normalize(pageInput, pageSizeInput)
        val result = accounts.searchAdminUsers(
            normalize(searchInput),
            normalize(roleInput)?.uppercase(),
            status?.name,
            profileStatus?.name,
            PageRequest.of(pagination.zeroBasedPage, pagination.pageSize),
        )
        val ids = result.content.map { it.id }
        val profilesByUser = profiles.findAllByUserIdIn(ids).associateBy { it.userId }
        val rolesByUser = loadRolesByUser(ids)
        return PageResponse(
            items = result.content.map { account ->
                toListItem(account, profilesByUser[account.id], rolesByUser[account.id].orEmpty())
            },
            page = pagination.page,
            pageSize = pagination.pageSize,
            totalCount = result.totalElements,
        )
    }

    @Transactional(readOnly = true)
    fun getUser(userId: UUID): UserDetailResponse {
        val account = findAccount(userId)
        val profile = profiles.findById(userId).orElse(null)
        val authorization = roleService.getAuthorization(userId)
        return UserDetailResponse(
            userId = account.id,
            email = account.email,
            emailVerified = account.emailVerified,
            status = account.status.name,
            profileStatus = account.profileStatus.name,
            firstName = profile?.firstName,
            lastName = profile?.lastName,
            displayName = profile?.displayName,
            gender = profile?.gender?.name,
            dateOfBirth = profile?.dateOfBirth,
            avatarUrl = profile?.avatarUrl,
            roles = authorization.roles.sorted(),
            permissions = authorization.permissions.sorted(),
            createdAt = account.createdAt,
            updatedAt = account.updatedAt,
            version = account.version,
            profileCreatedAt = profile?.createdAt,
            profileUpdatedAt = profile?.updatedAt,
        )
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UpdateUserProfileRequest) {
        val account = findAccount(userId)
        validateAvatarUrl(request.avatarUrl)
        val firstName = normalize(request.firstName)
        val lastName = normalize(request.lastName)
        if (account.profileStatus == ProfileStatus.COMPLETED &&
            !hasRequiredDetails(firstName, lastName, request.gender, request.dateOfBirth)
        ) throw InvalidUserProfileException()
        val now = Instant.now()
        val profile = profiles.findById(userId).orElseGet {
            UserProfile(userId = userId, createdAt = now, updatedAt = now)
        }
        profile.firstName = firstName
        profile.lastName = lastName
        profile.displayName = normalize(request.displayName)
        profile.gender = request.gender
        profile.dateOfBirth = request.dateOfBirth
        profile.avatarUrl = normalize(request.avatarUrl)
        profile.updatedAt = now
        profiles.save(profile)
        if (account.profileStatus == ProfileStatus.INCOMPLETE &&
            hasRequiredDetails(firstName, lastName, request.gender, request.dateOfBirth)
        ) {
            account.profileStatus = ProfileStatus.COMPLETED
            account.updatedAt = now
            accounts.save(account)
        }
    }

    @Transactional
    fun changeStatus(userId: UUID, newStatus: AccountStatus) {
        val account = findAccount(userId)
        if (account.status == newStatus) return
        if (account.status == AccountStatus.ACTIVE && newStatus != AccountStatus.ACTIVE) {
            val adminRole = roles.findByCodeForUpdate(RoleService.ADMIN_ROLE) ?: throw RoleNotFoundException()
            if (userRoles.existsByUserIdAndRoleId(account.id, adminRole.id) &&
                userRoles.countActiveUsersWithRoleCode(RoleService.ADMIN_ROLE) <= 1
            ) throw LastActiveAdminException()
        }
        account.status = newStatus
        account.authorizationVersion += 1
        account.updatedAt = Instant.now()
        accounts.save(account)
        if (newStatus != AccountStatus.ACTIVE) authCredentialClient.revokeSessions(account.id)
    }

    fun inviteUser(userId: UUID) {
        val account = findAccount(userId)
        authCredentialClient.sendInvitation(account.id, account.email)
    }

    private fun findAccount(userId: UUID): UserAccount =
        accounts.findById(userId).orElseThrow { UserNotFoundException() }

    private fun loadRolesByUser(ids: Collection<UUID>): Map<UUID, List<String>> {
        val mappings = userRoles.findAllByUserIdIn(ids)
        val rolesById = roles.findAllByIdIn(mappings.map { it.roleId }.toSet()).associateBy { it.id }
        return mappings.groupBy { it.userId }.mapValues { (_, values) ->
            values.mapNotNull { rolesById[it.roleId]?.code }.distinct().sorted()
        }
    }

    private fun toListItem(account: UserAccount, profile: UserProfile?, roleCodes: List<String>) =
        UserListItemResponse(
            userId = account.id,
            email = account.email,
            emailVerified = account.emailVerified,
            status = account.status.name,
            profileStatus = account.profileStatus.name,
            firstName = profile?.firstName,
            lastName = profile?.lastName,
            displayName = profile?.displayName,
            gender = profile?.gender?.name,
            dateOfBirth = profile?.dateOfBirth,
            avatarUrl = profile?.avatarUrl,
            roles = roleCodes,
            createdAt = account.createdAt,
            updatedAt = account.updatedAt,
            version = account.version,
            profileCreatedAt = profile?.createdAt,
            profileUpdatedAt = profile?.updatedAt,
        )

    private fun normalize(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun hasRequiredDetails(firstName: String?, lastName: String?, gender: Gender, dateOfBirth: LocalDate?) =
        !firstName.isNullOrBlank() && !lastName.isNullOrBlank() && gender != Gender.UNSPECIFIED && dateOfBirth != null

    private fun validateAvatarUrl(value: String?) {
        if (value.isNullOrBlank()) return
        val uri = runCatching { URI(value.trim()) }.getOrElse { throw InvalidUserProfileException() }
        if (!uri.isAbsolute || uri.scheme.lowercase() !in setOf("http", "https")) throw InvalidUserProfileException()
    }

}
