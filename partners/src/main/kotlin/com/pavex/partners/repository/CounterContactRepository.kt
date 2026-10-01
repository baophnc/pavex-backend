package com.pavex.partners.repository

import com.pavex.partners.entity.CounterContact
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CounterContactRepository : JpaRepository<CounterContact, UUID> {
    fun findByPhoneOrderByLastUsedAtDesc(phone: String, pageable: Pageable): List<CounterContact>

    fun findFirstByPhoneAndWardCodeAndAddressLineIgnoreCase(phone: String, wardCode: String, addressLine: String): CounterContact?
}
