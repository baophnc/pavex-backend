package com.pavex.partners.service

import com.pavex.contracts.partners.PartnerLocationSnapshot
import com.pavex.partners.dto.ProvinceResponse
import com.pavex.partners.dto.WardResponse
import com.pavex.partners.exception.ApiException
import com.pavex.partners.repository.ProvinceRepository
import com.pavex.partners.repository.WardRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LocationService(
    private val provinces: ProvinceRepository,
    private val wards: WardRepository,
) {
    @Transactional(readOnly = true)
    fun getProvinces(): List<ProvinceResponse> =
        provinces.findAllByOrderByNameAsc().map {
            ProvinceResponse(it.code, it.name, it.nameEn, it.fullName, it.fullNameEn, it.codeName)
        }

    @Transactional(readOnly = true)
    fun getWards(provinceCode: String): List<WardResponse> {
        if (!provinces.existsById(provinceCode)) {
            throw ApiException(HttpStatus.NOT_FOUND, "province_not_found", "Province was not found.")
        }
        return wards.findAllByProvinceCodeOrderByNameAsc(provinceCode).map(::toResponse)
    }

    @Transactional(readOnly = true)
    fun getWard(wardCode: String): WardResponse =
        wards.findById(wardCode)
            .map(::toResponse)
            .orElseThrow {
                ApiException(HttpStatus.NOT_FOUND, "ward_not_found", "Ward was not found.")
            }

    fun isValid(provinceCode: String, wardCode: String): Boolean =
        wards.existsByCodeAndProvinceCode(wardCode, provinceCode)

    @Transactional(readOnly = true)
    fun resolveForShipment(provinceCode: String, wardCode: String): PartnerLocationSnapshot {
        val province = provinces.findById(provinceCode.trim()).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "province_not_found", "Province was not found.")
        }
        val ward = wards.findById(wardCode.trim()).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "ward_not_found", "Ward was not found.")
        }
        if (ward.provinceCode != province.code) {
            throw ApiException(HttpStatus.BAD_REQUEST, "invalid_location", "Ward does not belong to the selected province.")
        }
        return PartnerLocationSnapshot(
            provinceCode = province.code,
            provinceName = province.fullName.ifBlank { province.name },
            wardCode = ward.code,
            wardName = ward.fullName.ifBlank { ward.name },
        )
    }

    private fun toResponse(ward: com.pavex.partners.entity.Ward) = WardResponse(
        ward.code,
        ward.name,
        ward.nameEn,
        ward.fullName,
        ward.fullNameEn,
        ward.codeName,
        ward.provinceCode,
    )
}
