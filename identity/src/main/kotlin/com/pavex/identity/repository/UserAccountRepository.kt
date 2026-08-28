package com.pavex.identity.repository

import com.pavex.identity.entity.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
interface UserAccountRepository : JpaRepository<UserAccount, UUID> {

    fun findByEmail(email: String): UserAccount?

    fun existsByEmail(email: String): Boolean
    @Query(
        value = """
        SELECT u.*
        FROM identity.user_account u

        WHERE (
            CAST(:search AS TEXT) IS NULL

            OR LOWER(u.email)
                LIKE LOWER(
                    CONCAT(
                        '%',
                        CAST(:search AS TEXT),
                        '%'
                    )
                )

            OR EXISTS (
                SELECT 1
                FROM identity.user_profile p

                WHERE p.user_id = u.id
                  AND (
                    LOWER(
                        COALESCE(
                            p.first_name,
                            ''
                        )
                    )
                    LIKE LOWER(
                        CONCAT(
                            '%',
                            CAST(:search AS TEXT),
                            '%'
                        )
                    )

                    OR LOWER(
                        COALESCE(
                            p.last_name,
                            ''
                        )
                    )
                    LIKE LOWER(
                        CONCAT(
                            '%',
                            CAST(:search AS TEXT),
                            '%'
                        )
                    )

                    OR LOWER(
                        COALESCE(
                            p.display_name,
                            ''
                        )
                    )
                    LIKE LOWER(
                        CONCAT(
                            '%',
                            CAST(:search AS TEXT),
                            '%'
                        )
                    )
                  )
            )
        )

        AND (
            CAST(:role AS TEXT) IS NULL

            OR EXISTS (
                SELECT 1
                FROM identity.user_role ur

                JOIN identity.role r
                  ON r.id = ur.role_id

                WHERE ur.user_id = u.id
                  AND r.code =
                      CAST(:role AS TEXT)
            )
        )

        AND (
            CAST(:status AS TEXT) IS NULL
            OR u.status =
                CAST(:status AS TEXT)
        )

        AND (
            CAST(:profileStatus AS TEXT) IS NULL
            OR u.profile_status =
                CAST(:profileStatus AS TEXT)
        )

        ORDER BY
            u.created_at DESC,
            u.id ASC
    """,

        countQuery = """
        SELECT COUNT(*)
        FROM identity.user_account u

        WHERE (
            CAST(:search AS TEXT) IS NULL

            OR LOWER(u.email)
                LIKE LOWER(
                    CONCAT(
                        '%',
                        CAST(:search AS TEXT),
                        '%'
                    )
                )

            OR EXISTS (
                SELECT 1
                FROM identity.user_profile p

                WHERE p.user_id = u.id
                  AND (
                    LOWER(
                        COALESCE(
                            p.first_name,
                            ''
                        )
                    )
                    LIKE LOWER(
                        CONCAT(
                            '%',
                            CAST(:search AS TEXT),
                            '%'
                        )
                    )

                    OR LOWER(
                        COALESCE(
                            p.last_name,
                            ''
                        )
                    )
                    LIKE LOWER(
                        CONCAT(
                            '%',
                            CAST(:search AS TEXT),
                            '%'
                        )
                    )

                    OR LOWER(
                        COALESCE(
                            p.display_name,
                            ''
                        )
                    )
                    LIKE LOWER(
                        CONCAT(
                            '%',
                            CAST(:search AS TEXT),
                            '%'
                        )
                    )
                  )
            )
        )

        AND (
            CAST(:role AS TEXT) IS NULL

            OR EXISTS (
                SELECT 1
                FROM identity.user_role ur

                JOIN identity.role r
                  ON r.id = ur.role_id

                WHERE ur.user_id = u.id
                  AND r.code =
                      CAST(:role AS TEXT)
            )
        )

        AND (
            CAST(:status AS TEXT) IS NULL
            OR u.status =
                CAST(:status AS TEXT)
        )

        AND (
            CAST(:profileStatus AS TEXT) IS NULL
            OR u.profile_status =
                CAST(:profileStatus AS TEXT)
        )
    """,

        nativeQuery = true,
    )
    fun searchAdminUsers(

        @Param("search")
        search: String?,

        @Param("role")
        role: String?,

        @Param("status")
        status: String?,

        @Param("profileStatus")
        profileStatus: String?,

        pageable: Pageable,
    ): Page<UserAccount>
}