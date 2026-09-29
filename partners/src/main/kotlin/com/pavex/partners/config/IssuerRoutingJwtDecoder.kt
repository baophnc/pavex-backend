package com.pavex.partners.config

import com.nimbusds.jwt.JWTParser
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.text.ParseException

/**
 * Picks the decoder for the token's issuer; the chosen decoder then verifies the signature and
 * every claim. An unknown issuer is rejected before any verification is attempted.
 */
class IssuerRoutingJwtDecoder(
    private val decoders: Map<String, JwtDecoder>,
) : JwtDecoder {
    override fun decode(token: String): Jwt {
        val issuer = try {
            JWTParser.parse(token).jwtClaimsSet.issuer
        } catch (exception: ParseException) {
            throw BadJwtException("Malformed service token.", exception)
        }
        val decoder = decoders[issuer] ?: throw BadJwtException("Service token issuer is not trusted.")
        return decoder.decode(token)
    }
}
