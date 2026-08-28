package com.pavex.partners.repository

import com.pavex.partners.entity.UserAddress
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserAddressRepository : JpaRepository<UserAddress, UUID> {
    fun findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(userId: UUID): List<UserAddress>
    fun findByIdAndUserId(id: UUID, userId: UUID): UserAddress?
    fun findFirstByUserIdAndIsDefaultTrue(userId: UUID): UserAddress?
    fun findFirstByUserIdOrderByCreatedAtAsc(userId: UUID): UserAddress?
}
