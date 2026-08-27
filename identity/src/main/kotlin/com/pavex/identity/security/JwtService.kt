package com.pavex.identity.security

import com.pavex.identity.entity.UserAccount
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

    private val jwtEncoder: JwtEncoder,

    @Value("\${security.jwt.issuer}")
    private val issuer: String,

    @Value("\${security.jwt.access-token-ttl}")
    private val accessTokenTtl: Duration,
) {

    fun createAccessToken(
        account: UserAccount,
        now: Instant,
    ): AccessToken {

        val expiresAt =
            now.plus(accessTokenTtl)

        val claims = JwtClaimsSet
            .builder()
            .issuer(issuer)
            .subject(account.id.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(
                "email",
                account.email,
            )
            .claim(
                "roles",
                emptyList<String>(),
            )
            .claim(
                "permissions",
                emptyList<String>(),
            )
            .build()

        val headers = JwsHeader
            .with(SignatureAlgorithm.RS256)
            .build()

        val jwt = jwtEncoder.encode(
            JwtEncoderParameters.from(
                headers,
                claims,
            )
        )

        return AccessToken(
            token = jwt.tokenValue,
            expiresAt = expiresAt,
            expiresIn = accessTokenTtl.seconds,
        )
    }
}

data class AccessToken(
    val token: String,
    val expiresAt: Instant,
    val expiresIn: Long,
)