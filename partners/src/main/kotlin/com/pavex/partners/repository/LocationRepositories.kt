package com.pavex.partners.repository

import com.pavex.partners.entity.Province
import com.pavex.partners.entity.Ward
import org.springframework.data.jpa.repository.JpaRepository

interface ProvinceRepository : JpaRepository<Province, String> {
    fun findAllByOrderByNameAsc(): List<Province>
}

interface WardRepository : JpaRepository<Ward, String> {
    fun findAllByProvinceCodeOrderByNameAsc(provinceCode: String): List<Ward>
    fun existsByCodeAndProvinceCode(code: String, provinceCode: String): Boolean
}
