package com.pavex.identity.service

import com.pavex.identity.dto.auth.RegisterRequest
import com.pavex.identity.dto.auth.RegisterResponse
import com.pavex.identity.entity.UserAccount
import com.pavex.identity.entity.UserProfile
import com.pavex.identity.exception.EmailAlreadyExistsException
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthService(
    private val userAccountRepository: UserAccountRepository,
    private val userProfileRepository: UserProfileRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun register(request: RegisterRequest): RegisterResponse {

        // Chuẩn hóa email
        val email = request.email
            .trim()
            .lowercase()

        // Kiểm tra email đã tồn tại chưa
        if (userAccountRepository.existsByEmail(email)) {
            throw EmailAlreadyExistsException(email)
        }

        // Thời gian hiện tại
        val now = Instant.now()

        // Hash password
        val encodedPassword = requireNotNull(
            passwordEncoder.encode(request.password)
        ) {
            "Password encoder returned null"
        }

        // Tạo account
        val account = UserAccount(
            email = email,
            passwordHash = encodedPassword,
            createdAt = now,
            updatedAt = now,
        )

        // Lưu account
        val savedAccount = userAccountRepository.save(account)

        // Tạo profile rỗng cho user
        val profile = UserProfile(
            userId = savedAccount.id,
            createdAt = now,
            updatedAt = now,
        )

        // Lưu profile
        userProfileRepository.save(profile)

        // Trả response
        return RegisterResponse(
            id = savedAccount.id,
            email = savedAccount.email,
            status = savedAccount.status.name,
            emailVerified = savedAccount.emailVerified,
            createdAt = savedAccount.createdAt,
        )
    }
}