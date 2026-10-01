package com.pavex.partners.service

import com.pavex.partners.dto.SaveCounterContactRequest
import com.pavex.partners.entity.CounterContact
import com.pavex.partners.entity.Province
import com.pavex.partners.entity.Ward
import com.pavex.partners.exception.ApiException
import com.pavex.partners.repository.CounterContactRepository
import com.pavex.partners.repository.ProvinceRepository
import com.pavex.partners.repository.WardRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock

/** The counter address book: typed once, found by phone on the customer's next visit. */
class CounterContactServiceTest {
    private val saved = mutableListOf<CounterContact>()
    private val province = Province(code = "01", name = "Hà Nội", fullName = "Thành phố Hà Nội")
    private val ward = Ward(code = "00004", name = "Ba Đình", fullName = "Phường Ba Đình", provinceCode = "01")
    private val provinces = mock(ProvinceRepository::class.java) { invocation ->
        when (invocation.method.name) {
            "findAllById" -> listOf(province)
            else -> RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val wards = mock(WardRepository::class.java) { invocation ->
        when (invocation.method.name) {
            "findAllById" -> listOf(ward)
            "existsByCodeAndProvinceCode" -> invocation.getArgument<String>(0) == ward.code && invocation.getArgument<String>(1) == ward.provinceCode
            else -> RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val service = CounterContactService(
        mock(CounterContactRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findByPhoneOrderByLastUsedAtDesc" -> saved.filter { it.phone == invocation.getArgument(0) }
                "findFirstByPhoneAndWardCodeAndAddressLineIgnoreCase" -> saved.firstOrNull {
                    it.phone == invocation.getArgument(0) && it.wardCode == invocation.getArgument(1) &&
                        it.addressLine.equals(invocation.getArgument(2), ignoreCase = true)
                }
                "save" -> invocation.getArgument<CounterContact>(0).also { c -> if (saved.none { it.id == c.id }) saved += c }
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        },
        LocationService(provinces, wards),
        provinces,
        wards,
    )

    @Test
    fun `the same customer and address is kept once and marked as used again`() {
        service.save(request(phone = "0912 345 678", name = "Lan", address = "Số 1 Đội Cấn"))
        val again = service.save(request(phone = "+84912345678", name = "Nguyễn Thị Lan", address = "số 1 đội cấn"))

        assertEquals(1, saved.size)
        assertEquals(2, again.useCount)
        assertEquals("Nguyễn Thị Lan", again.name)
        val found = service.find("0912345678").single()
        assertEquals("Phường Ba Đình", found.wardName)
        assertEquals("Thành phố Hà Nội", found.provinceName)
    }

    @Test
    fun `a phone that is not Vietnamese is refused`() {
        val refused = assertThrows(ApiException::class.java) { service.find("12345") }
        assertEquals("invalid_phone", refused.code)
    }

    private fun request(phone: String, name: String, address: String) =
        SaveCounterContactRequest(phone = phone, name = name, addressLine = address, provinceCode = "01", wardCode = "00004")
}
