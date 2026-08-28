package com.pavex.auth.security

import com.pavex.auth.entity.UserAccount
import com.pavex.auth.exception.AccountDisabledException
import com.pavex.auth.service.IdentityClient
import com.pavex.auth.service.RoleService
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
     * Transitional fallback only.
     *
     * Existing users may not have been copied to the Identity DB yet.
     * If Identity returns 404, bootstrap that one account with the legacy
     * Auth roles, then all normal authorization reads come from Identity.
     * This dependency is removed after the Auth RBAC tables are deleted.
     */
    private val roleService:
        RoleService,

    @Value("\${security.jwt.issuer}")
    private val issuer: String,

    @Value("\${security.jwt.access-token-ttl}")
    private val accessTokenTtl:
        Duration,
) {

    fun createAccessToken(
        account: UserAccount,
        now: Instant,
    ): AccessToken {

        val authorization =
            identityClient
                .findAuthContext(account.id)
                ?: bootstrapIdentity(account)

        if (!authorization.active) {
            throw AccountDisabledException()
        }

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
    ): com.pavex.contracts.identity.IdentityAuthContext {

        val legacyAuthorization =
            roleService.getAuthorization(
                account.id
            )

        identityClient.provision(
            account = account,
            roles = legacyAuthorization.roles,
        )

        return identityClient.getAuthContext(
            account.id
        )
    }

    companion object {
        private const val USER_TOKEN_TYPE = "user"
    }
}


data class AccessToken(
    val token: String,
    val expiresAt: Instant,
    val expiresIn: Long,
)
