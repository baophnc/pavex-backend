package com.pavex.identity.repository

import com.pavex.identity.entity.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserAccountRepository :
    JpaRepository<UserAccount, UUID> {

    fun findByEmail(email: String): UserAccount?

    fun existsByEmail(email: String): Boolean
}