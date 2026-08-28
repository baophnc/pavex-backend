package com.pavex.auth.security

import com.pavex.auth.entity.UserAccount
import com.pavex.auth.exception.AccountDisabledException
import com.pavex.auth.exception.AccountSuspendedException
import com.pavex.auth.service.IdentityClient
import com.pavex.auth.service.RoleService
import com.pavex.contracts.identity.IdentityAuthContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class JwtService(

    private val jwtEncoder:
        JwtEncoder,

    private val identityClient:
        IdentityClient,

    /*
     * Transitional read-only fallback only.
     *
     * Existing users may not have been copied to the Identity DB yet. If
     * Identity returns 404, read the old Auth RBAC assignment once, provision
     * Identity, and use Identity for all authorization decisions afterwards.
     */
    private val roleService:
        RoleService,

    @Value("\${security.jwt.issuer}")
    private val issuer: String,

    @Value("\${security.jwt.access-token-ttl}")
    private val accessTokenTtl:
        Duration,
) {

    fun resolveAuthorization(
        account: UserAccount,
    ): IdentityAuthContext {
        var authorization =
            identityClient.findAuthContext(account.id)
                ?: bootstrapIdentity(account)

        /*
         * During the extraction Auth still owns the verification token and
         * writes the credential-side mirror. If Identity missed an after-commit
         * synchronization because it was temporarily unavailable, repair the
         * state before issuing a new token.
         */
        if (account.emailVerified && !authorization.emailVerified) {
            identityClient.updateEmailVerification(
                userId = account.id,
                emailVerified = true,
            )
            authorization = identityClient.getAuthContext(account.id)
        }

        requireActive(authorization)
        return authorization
    }

    fun createAccessToken(
        account: UserAccount,
        now: Instant,
        authorization: IdentityAuthContext = resolveAuthorization(account),
    ): AccessToken {
        requireActive(authorization)

        val expiresAt =
            now.plus(
                accessTokenTtl
            )

        val claims =
            JwtClaimsSet
                .builder()
                .issuer(issuer)
                .subject(
                    account.id.toString()
                )
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(
                    "typ",
                    USER_TOKEN_TYPE,
                )
                .claim(
                    "email",
                    account.email,
                )
                .claim(
                    "email_verified",
                    authorization.emailVerified,
                )
                .claim(
                    "roles",
                    authorization.roles,
                )
                .claim(
                    "permissions",
                    authorization.permissions,
                )
                .claim(
                    "authz_version",
                    authorization.authorizationVersion,
                )
                .build()

        val header =
            JwsHeader
                .with(
                    SignatureAlgorithm.RS256
                )
                .build()

        val jwt =
            jwtEncoder.encode(
                JwtEncoderParameters.from(
                    header,
                    claims,
                )
            )

        return AccessToken(
            token = jwt.tokenValue,
            expiresAt = expiresAt,
            expiresIn =
                accessTokenTtl.seconds,
        )
    }

    private fun bootstrapIdentity(
        account: UserAccount,
    ): IdentityAuthContext {
        val legacyAuthorization =
            roleService.getAuthorization(
                account.id
            )

        val bootstrapRoles =
            legacyAuthorization.roles
                .ifEmpty {
                    setOf(DEFAULT_CUSTOMER_ROLE)
                }

        identityClient.provision(
            account = account,
            roles = bootstrapRoles,
        )

        return identityClient.getAuthContext(
            account.id
        )
    }

    private fun requireActive(
        authorization: IdentityAuthContext,
    ) {
        when (authorization.status.trim().uppercase()) {
            ACTIVE_STATUS -> Unit
            SUSPENDED_STATUS -> throw AccountSuspendedException()
            else -> throw AccountDisabledException()
        }
    }

    companion object {
        private const val USER_TOKEN_TYPE = "user"
        private const val DEFAULT_CUSTOMER_ROLE = "CUSTOMER"
        private const val ACTIVE_STATUS = "ACTIVE"
        private const val SUSPENDED_STATUS = "SUSPENDED"
    }
}


data class AccessToken(
    val token: String,
    val expiresAt: Instant,
    val expiresIn: Long,
)
