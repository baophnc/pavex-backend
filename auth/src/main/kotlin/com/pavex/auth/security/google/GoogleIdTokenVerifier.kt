package com.pavex.auth.security.google

import com.pavex.auth.exception.GoogleAuthenticationNotConfiguredException
import com.pavex.auth.exception.InvalidGoogleTokenException
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component

@Component
class GoogleIdTokenVerifier(

    @Value(
        "\${auth.google.client-ids:}"
    )
    rawClientIds: String,
) {

    private val clientIds =
        rawClientIds
            .split(",")
            .map {
                it.trim()
            }
            .filter {
                it.isNotEmpty()
            }
            .toSet()


    private val decoder by lazy {

        val jwtDecoder =
            NimbusJwtDecoder
                .withJwkSetUri(
                    GOOGLE_JWK_SET_URI
                )
                .jwsAlgorithm(
                    SignatureAlgorithm.RS256
                )
                .build()

        val issuerValidator =
            OAuth2TokenValidator<Jwt> { jwt ->

                val issuer =
                    jwt.issuer
                        ?.toString()

                if (
                    issuer in
                    GOOGLE_ISSUERS
                ) {

                    OAuth2TokenValidatorResult
                        .success()

                } else {

                    OAuth2TokenValidatorResult
                        .failure(
                            OAuth2Error(
                                "invalid_token",
                                "Invalid Google token issuer.",
                                null,
                            )
                        )
                }
            }

        val audienceValidator =
            OAuth2TokenValidator<Jwt> { jwt ->

                val audiences =
                    jwt.audience
                        .orEmpty()

                val validAudience =
                    audiences.any {
                        it in clientIds
                    }

                if (validAudience) {

                    OAuth2TokenValidatorResult
                        .success()

                } else {

                    OAuth2TokenValidatorResult
                        .failure(
                            OAuth2Error(
                                "invalid_token",
                                "Invalid Google token audience.",
                                null,
                            )
                        )
                }
            }

        jwtDecoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators
                    .createDefault(),

                issuerValidator,

                audienceValidator,
            )
        )

        jwtDecoder
    }


    fun verify(
        rawIdToken: String,
    ): GoogleUserInfo {

        if (clientIds.isEmpty()) {
            throw GoogleAuthenticationNotConfiguredException()
        }

        val jwt =
            try {

                decoder.decode(
                    rawIdToken.trim()
                )

            } catch (
                exception: JwtException
            ) {

                throw InvalidGoogleTokenException()
            }

        val subject =
            jwt.subject
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: throw InvalidGoogleTokenException()

        val email =
            jwt
                .getClaimAsString(
                    "email"
                )
                ?.trim()
                ?.lowercase()
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: throw InvalidGoogleTokenException()

        val emailVerified =
            jwt
                .getClaimAsBoolean(
                    "email_verified"
                )
                ?: false

        if (!emailVerified) {
            throw InvalidGoogleTokenException()
        }

        val hostedDomain =
            jwt
                .getClaimAsString(
                    "hd"
                )
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }

        val authoritative =
            email.endsWith(
                "@gmail.com",
                ignoreCase = true,
            ) ||
                (
                    emailVerified &&
                    hostedDomain != null
                )

        return GoogleUserInfo(
            subject =
                subject,

            email =
                email,

            emailVerified =
                emailVerified,

            displayName =
                jwt.getClaimAsString(
                    "name"
                ),

            avatarUrl =
                jwt.getClaimAsString(
                    "picture"
                ),

            hostedDomain =
                hostedDomain,

            emailAuthoritative =
                authoritative,
        )
    }


    companion object {

        private const val
            GOOGLE_JWK_SET_URI =
                "https://www.googleapis.com/oauth2/v3/certs"

        private val
            GOOGLE_ISSUERS =
                setOf(
                    "accounts.google.com",
                    "https://accounts.google.com",
                )
    }
}
