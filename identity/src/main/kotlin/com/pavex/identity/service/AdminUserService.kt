package com.pavex.identity.service

import com.pavex.identity.dto.admin.UpdateUserProfileRequest
import com.pavex.identity.dto.admin.UserDetailResponse
import com.pavex.identity.dto.admin.UserListItemResponse
import com.pavex.identity.dto.common.PageResponse
import com.pavex.identity.entity.AccountStatus
import com.pavex.identity.entity.Gender
import com.pavex.identity.entity.ProfileStatus
import com.pavex.identity.entity.UserAccount
import com.pavex.identity.entity.UserProfile
import com.pavex.identity.exception.InvalidUserProfileException
import com.pavex.identity.exception.LastActiveAdminException
import com.pavex.identity.exception.RoleNotFoundException
import com.pavex.identity.exception.UserNotFoundException
import com.pavex.identity.repository.RefreshSessionRepository
import com.pavex.identity.repository.RoleRepository
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import com.pavex.identity.repository.UserRoleRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import com.pavex.identity.dto.admin.CreateUserRequest
import com.pavex.identity.dto.admin.CreateUserResponse
import com.pavex.identity.entity.UserRole
import com.pavex.identity.exception.EmailAlreadyExistsException
import com.pavex.identity.security.OpaqueTokenService
import org.springframework.security.crypto.password.PasswordEncoder
@Service
class AdminUserService(

    private val userAccountRepository:
        UserAccountRepository,

    private val userProfileRepository:
        UserProfileRepository,

    private val userRoleRepository:
        UserRoleRepository,

    private val roleRepository:
        RoleRepository,

    private val refreshSessionRepository:
        RefreshSessionRepository,

    private val roleService:
        RoleService,
    private val passwordEncoder:
    PasswordEncoder,

    private val opaqueTokenService:
    OpaqueTokenService,

    private val advisoryLockService:
        AdvisoryLockService,
) {

    @Transactional
    fun createUser(

        actorUserId: UUID,

        request: CreateUserRequest,

        ): CreateUserResponse {

        val email =
            request.email
                .trim()
                .lowercase()

        advisoryLockService
            .lockEmail(
                email
            )

        if (
            userAccountRepository
                .existsByEmail(
                    email
                )
        ) {
            throw EmailAlreadyExistsException(
                email
            )
        }

        val roleCode =
            request.roleCode
                .trim()
                .uppercase()

        val role =
            roleRepository
                .findByCode(
                    roleCode
                )
                ?: throw RoleNotFoundException()

        validateAvatarUrl(
            request.avatarUrl
        )

        val now =
            Instant.now()

        /*
         * Account được tạo nhưng admin
         * KHÔNG biết password.
         *
         * User chỉ đặt password thật
         * qua invitation.
         */
        val internalSecret =
            opaqueTokenService
                .generateRawToken()

        val internalPasswordHash =
            requireNotNull(
                passwordEncoder.encode(
                    internalSecret
                )
            ) {
                "Password encoder returned null"
            }

        val account =
            UserAccount(
                email =
                    email,

                passwordHash =
                    internalPasswordHash,

                createdAt =
                    now,

                updatedAt =
                    now,
            )

        val savedAccount =
            userAccountRepository.save(
                account
            )

        val firstName =
            normalize(
                request.firstName
            )

        val lastName =
            normalize(
                request.lastName
            )

        val displayName =
            normalize(
                request.displayName
            )

        val avatarUrl =
            normalize(
                request.avatarUrl
            )

        val shouldCreateProfile =
            firstName != null ||
                    lastName != null ||
                    displayName != null ||
                    request.gender !=
                    Gender.UNSPECIFIED ||
                    request.dateOfBirth != null ||
                    avatarUrl != null

        if (shouldCreateProfile) {

            val profile =
                UserProfile(
                    userId =
                        savedAccount.id,

                    firstName =
                        firstName,

                    lastName =
                        lastName,

                    displayName =
                        displayName,

                    gender =
                        request.gender,

                    dateOfBirth =
                        request.dateOfBirth,

                    avatarUrl =
                        avatarUrl,

                    createdAt =
                        now,

                    updatedAt =
                        now,
                )

            userProfileRepository.save(
                profile
            )

            if (
                hasRequiredDetails(
                    firstName =
                        firstName,

                    lastName =
                        lastName,

                    gender =
                        request.gender,

                    dateOfBirth =
                        request.dateOfBirth,
                )
            ) {
                savedAccount.profileStatus =
                    ProfileStatus.COMPLETED

                savedAccount.updatedAt =
                    now

                userAccountRepository.save(
                    savedAccount
                )
            }
        }

        userRoleRepository.save(
            UserRole(
                userId =
                    savedAccount.id,

                roleId =
                    role.id,

                assignedAt =
                    now,

                assignedBy =
                    actorUserId,
            )
        )

        return CreateUserResponse(
            userId =
                savedAccount.id,
        )
    }
    @Transactional(
        readOnly = true,
    )
    fun searchUsers(

        pageInput: Int,

        pageSizeInput: Int,

        searchInput: String?,

        roleInput: String?,

        status: AccountStatus?,

        profileStatus: ProfileStatus?,

    ): PageResponse<UserListItemResponse> {

        val page =
            pageInput
                .coerceAtLeast(1)

        val pageSize =
            pageSizeInput
                .coerceIn(
                    1,
                    100,
                )

        val search =
            normalize(
                searchInput
            )

        val role =
            normalize(
                roleInput
            )
                ?.uppercase()

        val result =
            userAccountRepository
                .searchAdminUsers(
                    search =
                        search,

                    role =
                        role,

                    status =
                        status
                            ?.name,

                    profileStatus =
                        profileStatus
                            ?.name,

                    pageable =
                        PageRequest.of(
                            page - 1,
                            pageSize,
                        ),
                )

        val accounts =
            result.content

        if (accounts.isEmpty()) {

            return PageResponse(
                items =
                    emptyList(),

                page =
                    page,

                pageSize =
                    pageSize,

                totalCount =
                    result.totalElements,
            )
        }

        val userIds =
            accounts
                .map {
                    it.id
                }

        val profiles =
            userProfileRepository
                .findAllByUserIdIn(
                    userIds
                )
                .associateBy {
                    it.userId
                }

        val rolesByUser =
            loadRolesByUser(
                userIds
            )

        val items =
            accounts
                .map { account ->

                    toListItem(
                        account =
                            account,

                        profile =
                            profiles[
                                account.id
                            ],

                        roles =
                            rolesByUser[
                                account.id
                            ]
                                .orEmpty(),
                    )
                }

        return PageResponse(
            items =
                items,

            page =
                page,

            pageSize =
                pageSize,

            totalCount =
                result.totalElements,
        )
    }


    @Transactional(
        readOnly = true,
    )
    fun getUser(
        userId: UUID,
    ): UserDetailResponse {

        val account =
            userAccountRepository
                .findById(
                    userId
                )
                .orElseThrow {
                    UserNotFoundException()
                }

        val profile =
            userProfileRepository
                .findById(
                    userId
                )
                .orElse(null)

        val authorization =
            roleService
                .getAuthorization(
                    userId
                )

        return UserDetailResponse(

            userId =
                account.id,

            email =
                account.email,

            emailVerified =
                account.emailVerified,

            status =
                account.status.name,

            profileStatus =
                account.profileStatus.name,

            firstName =
                profile?.firstName,

            lastName =
                profile?.lastName,

            displayName =
                profile?.displayName,

            gender =
                profile
                    ?.gender
                    ?.name,

            dateOfBirth =
                profile?.dateOfBirth,

            avatarUrl =
                profile?.avatarUrl,

            roles =
                authorization
                    .roles
                    .sorted(),

            permissions =
                authorization
                    .permissions
                    .sorted(),

            lastLoginAt =
                account.lastLoginAt,

            failedLoginAttempts =
                account.failedLoginAttempts,

            lockoutEndAt =
                account.lockoutEndAt,

            createdAt =
                account.createdAt,

            updatedAt =
                account.updatedAt,

            version =
                account.version,

            profileCreatedAt =
                profile?.createdAt,

            profileUpdatedAt =
                profile?.updatedAt,
        )
    }


    @Transactional
    fun updateProfile(

        userId: UUID,

        request:
            UpdateUserProfileRequest,

    ) {

        val account =
            userAccountRepository
                .findById(
                    userId
                )
                .orElseThrow {
                    UserNotFoundException()
                }

        validateAvatarUrl(
            request.avatarUrl
        )

        val now =
            Instant.now()

        val firstName =
            normalize(
                request.firstName
            )

        val lastName =
            normalize(
                request.lastName
            )

        val displayName =
            normalize(
                request.displayName
            )

        val avatarUrl =
            normalize(
                request.avatarUrl
            )

        /*
         * Một profile đã COMPLETED
         * không được quay về thiếu
         * required information.
         */
        if (
            account.profileStatus ==
                ProfileStatus.COMPLETED &&
            !hasRequiredDetails(
                firstName =
                    firstName,

                lastName =
                    lastName,

                gender =
                    request.gender,

                dateOfBirth =
                    request.dateOfBirth,
            )
        ) {
            throw InvalidUserProfileException()
        }

        val profile =
            userProfileRepository
                .findById(
                    userId
                )
                .orElseGet {

                    UserProfile(
                        userId =
                            userId,

                        createdAt =
                            now,

                        updatedAt =
                            now,
                    )
                }

        profile.firstName =
            firstName

        profile.lastName =
            lastName

        profile.displayName =
            displayName

        profile.gender =
            request.gender

        profile.dateOfBirth =
            request.dateOfBirth

        profile.avatarUrl =
            avatarUrl

        profile.updatedAt =
            now

        userProfileRepository.save(
            profile
        )

        /*
         * Domain rule:
         *
         * INCOMPLETE -> COMPLETED
         *
         * Không tự chuyển:
         *
         * PENDING_VERIFICATION
         * REJECTED
         */
        if (
            account.profileStatus ==
                ProfileStatus.INCOMPLETE &&
            hasRequiredDetails(
                firstName =
                    firstName,

                lastName =
                    lastName,

                gender =
                    request.gender,

                dateOfBirth =
                    request.dateOfBirth,
            )
        ) {

            account.profileStatus =
                ProfileStatus.COMPLETED

            account.updatedAt =
                now

            userAccountRepository.save(
                account
            )
        }
    }


    @Transactional
    fun changeStatus(

        userId: UUID,

        newStatus: AccountStatus,

    ) {

        advisoryLockService
            .lockUserSessions(
                userId
            )

        val account =
            userAccountRepository
                .findById(
                    userId
                )
                .orElseThrow {
                    UserNotFoundException()
                }

        val previousStatus =
            account.status

        /*
         * Idempotent:
         *
         * ACTIVE -> ACTIVE
         * không cần làm gì.
         */
        if (
            previousStatus ==
                newStatus
        ) {
            return
        }

        /*
         * ACTIVE ADMIN
         * sắp bị suspend/disable
         *
         * => phải còn ADMIN active khác.
         *
         * Lock chung role ADMIN với
         * remove-role checkpoint 10.
         */
        if (
            previousStatus ==
                AccountStatus.ACTIVE &&
            newStatus !=
                AccountStatus.ACTIVE
        ) {

            val adminRole =
                roleRepository
                    .findByCodeForUpdate(
                        RoleService.ADMIN_ROLE
                    )
                    ?: throw RoleNotFoundException()

            val isAdmin =
                userRoleRepository
                    .existsByUserIdAndRoleId(
                        userId =
                            account.id,

                        roleId =
                            adminRole.id,
                    )

            if (isAdmin) {

                val activeAdmins =
                    userRoleRepository
                        .countActiveUsersWithRoleCode(
                            RoleService.ADMIN_ROLE
                        )

                if (activeAdmins <= 1) {
                    throw LastActiveAdminException()
                }
            }
        }

        val now =
            Instant.now()

        account.status =
            newStatus

        account.updatedAt =
            now

        userAccountRepository.save(
            account
        )

        /*
         * Suspend / disable
         * => revoke toàn bộ refresh session.
         */
        if (
            newStatus !=
                AccountStatus.ACTIVE
        ) {

            refreshSessionRepository
                .revokeAllActiveByUserId(
                    userId =
                        account.id,

                    now =
                        now,
                )
        }
    }


    private fun loadRolesByUser(
        userIds: Collection<UUID>,
    ): Map<UUID, List<String>> {

        if (userIds.isEmpty()) {
            return emptyMap()
        }

        val userRoles =
            userRoleRepository
                .findAllByUserIdIn(
                    userIds
                )

        if (userRoles.isEmpty()) {
            return emptyMap()
        }

        val roleIds =
            userRoles
                .map {
                    it.roleId
                }
                .toSet()

        val roles =
            roleRepository
                .findAllByIdIn(
                    roleIds
                )
                .associateBy {
                    it.id
                }

        return userRoles
            .groupBy {
                it.userId
            }
            .mapValues {
                (_, mappings) ->

                mappings
                    .mapNotNull {
                        roles[
                            it.roleId
                        ]?.code
                    }
                    .distinct()
                    .sorted()
            }
    }


    private fun toListItem(

        account: UserAccount,

        profile: UserProfile?,

        roles: List<String>,

    ): UserListItemResponse {

        return UserListItemResponse(

            userId =
                account.id,

            email =
                account.email,

            emailVerified =
                account.emailVerified,

            status =
                account.status.name,

            profileStatus =
                account.profileStatus.name,

            firstName =
                profile?.firstName,

            lastName =
                profile?.lastName,

            displayName =
                profile?.displayName,

            gender =
                profile
                    ?.gender
                    ?.name,

            dateOfBirth =
                profile?.dateOfBirth,

            avatarUrl =
                profile?.avatarUrl,

            roles =
                roles,

            lastLoginAt =
                account.lastLoginAt,

            failedLoginAttempts =
                account.failedLoginAttempts,

            lockoutEndAt =
                account.lockoutEndAt,

            createdAt =
                account.createdAt,

            updatedAt =
                account.updatedAt,

            version =
                account.version,

            profileCreatedAt =
                profile?.createdAt,

            profileUpdatedAt =
                profile?.updatedAt,
        )
    }


    private fun hasRequiredDetails(

        firstName: String?,

        lastName: String?,

        gender: Gender,

        dateOfBirth: LocalDate?,

    ): Boolean {

        return !firstName
            .isNullOrBlank() &&
            !lastName
                .isNullOrBlank() &&
            gender !=
                Gender.UNSPECIFIED &&
            dateOfBirth != null
    }


    private fun validateAvatarUrl(
        value: String?,
    ) {

        if (value.isNullOrBlank()) {
            return
        }

        val uri =
            try {
                URI(
                    value.trim()
                )
            } catch (
                exception:
                    URISyntaxException
            ) {
                throw InvalidUserProfileException()
            }

        val scheme =
            uri.scheme
                ?.lowercase()

        if (
            !uri.isAbsolute ||
            (
                scheme != "http" &&
                scheme != "https"
            )
        ) {
            throw InvalidUserProfileException()
        }
    }


    private fun normalize(
        value: String?,
    ): String? {

        return value
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }
    }
}
