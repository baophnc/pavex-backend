package com.pavex.partners.service

import com.pavex.common.contact.VietnamesePhone
import com.pavex.partners.dto.CounterContactResponse
import com.pavex.partners.dto.SaveCounterContactRequest
import com.pavex.partners.entity.CounterContact
import com.pavex.partners.exception.ApiException
import com.pavex.partners.repository.CounterContactRepository
import com.pavex.partners.repository.ProvinceRepository
import com.pavex.partners.repository.WardRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * The counter's address book: staff type a phone number and pick the sender or recipient they
 * served before. Saving the same phone and address again only marks it as used.
 */
@Service
class CounterContactService(
    private val contacts: CounterContactRepository,
    private val locations: LocationService,
    private val provinces: ProvinceRepository,
    private val wards: WardRepository,
) {
    @Transactional(readOnly = true)
    fun find(rawPhone: String): List<CounterContactResponse> {
        val phone = phone(rawPhone)
        return describe(contacts.findByPhoneOrderByLastUsedAtDesc(phone, PageRequest.of(0, LOOKUP_LIMIT)))
    }

    @Transactional
    fun save(request: SaveCounterContactRequest): CounterContactResponse {
        val phone = phone(request.phone)
        val provinceCode = request.provinceCode.trim()
        val wardCode = request.wardCode.trim()
        if (!locations.isValid(provinceCode, wardCode)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "invalid_location", "Ward does not belong to the selected province.")
        }
        val name = request.name.trim()
        val addressLine = request.addressLine.trim()
        val now = Instant.now()
        val contact = contacts.findFirstByPhoneAndWardCodeAndAddressLineIgnoreCase(phone, wardCode, addressLine)
            ?.apply {
                this.name = name
                useCount += 1
                lastUsedAt = now
                updatedAt = now
            }
            ?: CounterContact(
                phone = phone,
                name = name,
                addressLine = addressLine,
                provinceCode = provinceCode,
                wardCode = wardCode,
                lastUsedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        return describe(listOf(contacts.save(contact))).single()
    }

    private fun describe(items: List<CounterContact>): List<CounterContactResponse> {
        val provinceNames = provinces.findAllById(items.map { it.provinceCode }.toSet())
            .associate { it.code to it.fullName.ifBlank { it.name } }
        val wardNames = wards.findAllById(items.map { it.wardCode }.toSet())
            .associate { it.code to it.fullName.ifBlank { it.name } }
        return items.map {
            CounterContactResponse(
                id = it.id,
                phone = it.phone,
                name = it.name,
                addressLine = it.addressLine,
                provinceCode = it.provinceCode,
                provinceName = provinceNames[it.provinceCode],
                wardCode = it.wardCode,
                wardName = wardNames[it.wardCode],
                useCount = it.useCount,
                lastUsedAt = it.lastUsedAt,
            )
        }
    }

    private fun phone(raw: String): String =
        VietnamesePhone.normalize(raw)
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "invalid_phone", "The phone number is not a Vietnamese phone number.")

    companion object {
        private const val LOOKUP_LIMIT = 10
    }
}
