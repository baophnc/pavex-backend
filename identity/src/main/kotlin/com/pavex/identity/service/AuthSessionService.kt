package com.pavex.identity.service

import com.pavex.identity.dto.auth.AuthResponse
import com.pavex.identity.entity.UserAccount
import com.pavex.identity.security.JwtService
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AuthSessionService(

    private val jwtService:
        JwtService,

    private val refreshTokenService:
        RefreshTokenService,
) {

    fun issue(
        account: UserAccount,
        now: Instant,
        clientMetadata:
            ClientMetadata,
    ): AuthResponse {

        val accessToken =
            jwtService
                .createAccessToken(
                    account,
                    now,
                )

        val refreshToken =
            refreshTokenService
                .create(
                    userId =
                        account.id,

                    now =
                        now,

                    clientMetadata =
                        clientMetadata,
                )

        return AuthResponse(
            accessToken =
                accessToken.token,

            refreshToken =
                refreshToken.token,

            expiresIn =
                accessToken.expiresIn,
        )
    }
}
