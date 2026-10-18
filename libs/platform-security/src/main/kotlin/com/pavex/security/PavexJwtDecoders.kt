package com.pavex.security

import com.pavex.common.security.PavexJwtClaims
import com.pavex.common.security.PavexTokenTypes
import org.springframework.core.io.Resource
import org.springframework.security.converter.RsaKeyConverters
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/** PEM key files as configured in `application.yaml` (`file:${pavex.keys-dir}/<service>/public.pem`). */
object RsaKeys {
    fun publicKey(resource: Resource): RSAPublicKey =
        resource.inputStream.use { requireNotNull(RsaKeyConverters.x509().convert(it)) }

    fun privateKey(resource: Resource): RSAPrivateKey =
        resource.inputStream.use { requireNotNull(RsaKeyConverters.pkcs8().convert(it)) }

    /** RS256 signer for the tokens a service issues with its own key pair. */
    fun encoder(publicKey: RSAPublicKey, privateKey: RSAPrivateKey): JwtEncoder =
        NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build()
}

object PavexJwtDecoders {
    /** Access tokens Auth signs for people: signature, expiry, Auth's issuer and `typ=user`. */
    fun userTokens(publicKey: RSAPublicKey, issuer: String): JwtDecoder =
        decoder(
            publicKey,
            JwtValidators.createDefaultWithIssuer(issuer),
            PavexTokenValidators.tokenType(PavexTokenTypes.USER),
        )

    /**
     * Service tokens one trusted caller signs with its own key: signature, expiry, the caller's issuer,
     * `sub` = the caller, `typ=service`, this service in `aud` and, when given, [requiredScope] in `scope`.
     */
    fun serviceTokens(
        publicKey: RSAPublicKey,
        issuer: String,
        caller: String,
        audience: String,
        requiredScope: String? = null,
    ): JwtDecoder =
        decoder(
            publicKey,
            *listOfNotNull(
                JwtValidators.createDefaultWithIssuer(issuer),
                PavexTokenValidators.exactClaim("sub", caller),
                PavexTokenValidators.exactClaim(PavexJwtClaims.TOKEN_TYPE, PavexTokenTypes.SERVICE),
                PavexTokenValidators.audience(audience),
                requiredScope?.let(PavexTokenValidators::scope),
            ).toTypedArray(),
        )

    private fun decoder(publicKey: RSAPublicKey, vararg validators: OAuth2TokenValidator<Jwt>): JwtDecoder {
        val decoder = NimbusJwtDecoder.withPublicKey(publicKey).build()
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators.toList()))
        return decoder
    }
}

object PavexTokenValidators {
    fun tokenType(expected: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            result(jwt.getClaimAsString(PavexJwtClaims.TOKEN_TYPE) == expected, "JWT token type is invalid")
        }

    fun exactClaim(name: String, expected: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt -> result(jwt.getClaimAsString(name) == expected, "Invalid $name claim") }

    fun audience(expected: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt -> result(expected in jwt.audience.orEmpty(), "Invalid audience") }

    /** The `scope` claim is one space-separated string, as [ServiceTokenIssuer] writes it. */
    fun scope(expected: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            val scopes = jwt.getClaimAsString(PavexJwtClaims.SCOPE)?.split(' ').orEmpty()
            result(expected in scopes, "Invalid scope")
        }

    private fun result(valid: Boolean, description: String): OAuth2TokenValidatorResult =
        if (valid) OAuth2TokenValidatorResult.success()
        else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", description, null))
}
