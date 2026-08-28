package com.pavex.identity.service

import com.pavex.identity.dto.user.CurrentUserResponse
import com.pavex.identity.dto.user.UpdateProfileRequest
import com.pavex.identity.entity.Gender
import com.pavex.identity.entity.ProfileStatus
import com.pavex.identity.entity.UserProfile
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class UserService(

    private val userAccountRepository:
    UserAccountRepository,

    private val userProfileRepository:
    UserProfileRepository,

    private val roleService:
    RoleService,
) {

    @Transactional(readOnly = true)
    fun getCurrentUser(
        userId: UUID,
    ): CurrentUserResponse {

        val account =
            userAccountRepository
                .findById(userId)
                .orElseThrow {
                    IllegalArgumentException(
                        "User does not exist"
                    )
                }

        val profile =
            userProfileRepository
                .findById(userId)
                .orElse(null)

        val authorization =
            roleService
                .getAuthorization(userId)

        return CurrentUserResponse(
            userId = account.id,
            email = account.email,
            status = account.status.name,
            profileStatus =
                account.profileStatus.name,
            emailVerified =
                account.emailVerified,

            firstName =
                profile?.firstName,

            lastName =
                profile?.lastName,

            displayName =
                profile?.displayName,

            gender =
                profile?.gender?.name
                    ?: Gender.UNSPECIFIED.name,

            dateOfBirth =
                profile?.dateOfBirth,

            avatarUrl =
                profile?.avatarUrl,

            roles =
                authorization.roles,

            permissions =
                authorization.permissions,
        )
    }


    @Transactional
    fun updateProfile(
        userId: UUID,
        request: UpdateProfileRequest,
    ): CurrentUserResponse {

        val account =
            userAccountRepository
                .findById(userId)
                .orElseThrow {
                    IllegalArgumentException(
                        "User does not exist"
                    )
                }

        validateProfile(request)

        val now =
            Instant.now()

        val profile =
            userProfileRepository
                .findById(userId)
                .orElseGet {
                    UserProfile(
                        userId = userId,
                        createdAt = now,
                        updatedAt = now,
                    )
                }

        val firstName =
            normalize(request.firstName)

        val lastName =
            normalize(request.lastName)

        val displayName =
            normalize(request.displayName)

        val avatarUrl =
            normalize(request.avatarUrl)

        /*
         * C# rule:
         *
         * Nếu profile đã COMPLETED thì không được
         * update thành một profile thiếu thông tin.
         */
        if (
            account.profileStatus ==
            ProfileStatus.COMPLETED
        ) {

            val stillComplete =
                hasRequiredProfileDetails(
                    firstName = firstName,
                    lastName = lastName,
                    gender = request.gender,
                    dateOfBirth =
                        request.dateOfBirth,
                )

            require(stillComplete) {
                "Completed profile cannot become incomplete"
            }
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
         * C# chỉ cho phép:
         *
         * INCOMPLETE -> COMPLETED
         *
         * Không tự:
         * COMPLETED -> INCOMPLETE
         */
        if (
            account.profileStatus !=
            ProfileStatus.COMPLETED &&
            hasRequiredProfileDetails(
                firstName = firstName,
                lastName = lastName,
                gender = request.gender,
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

        return getCurrentUser(
            userId
        )
    }


    private fun validateProfile(
        request: UpdateProfileRequest,
    ) {

        val dateOfBirth =
            request.dateOfBirth

        require(
            dateOfBirth == null ||
                    !dateOfBirth.isAfter(
                        LocalDate.now()
                    )
        ) {
            "Date of birth cannot be in the future"
        }

        val avatarUrl =
            normalize(request.avatarUrl)

        if (avatarUrl != null) {

            val uri =
                try {
                    URI(avatarUrl)
                } catch (
                    exception:
                    IllegalArgumentException
                ) {
                    throw IllegalArgumentException(
                        "Avatar URL must be a valid HTTP or HTTPS URL"
                    )
                }

            require(
                uri.scheme.equals(
                    "http",
                    ignoreCase = true,
                ) ||
                        uri.scheme.equals(
                            "https",
                            ignoreCase = true,
                        )
            ) {
                "Avatar URL must be a valid HTTP or HTTPS URL"
            }
        }
    }


    private fun hasRequiredProfileDetails(
        firstName: String?,
        lastName: String?,
        gender: Gender,
        dateOfBirth: LocalDate?,
    ): Boolean {

        return !firstName.isNullOrBlank() &&
                !lastName.isNullOrBlank() &&
                gender != Gender.UNSPECIFIED &&
                dateOfBirth != null
    }


    private fun normalize(
        value: String?,
    ): String? {

        return value
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
    }
}