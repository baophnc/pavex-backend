package com.pavex.partners.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "provinces", schema = "partners")
class Province(
    @Id
    @Column(length = 20)
    var code: String = "",
    @Column(nullable = false, length = 255)
    var name: String = "",
    @Column(name = "name_en", nullable = false, length = 255)
    var nameEn: String = "",
    @Column(name = "full_name", nullable = false, length = 255)
    var fullName: String = "",
    @Column(name = "full_name_en", nullable = false, length = 255)
    var fullNameEn: String = "",
    @Column(name = "code_name", nullable = false, length = 255)
    var codeName: String = "",
)

@Entity
@Table(name = "wards", schema = "partners")
class Ward(
    @Id
    @Column(length = 20)
    var code: String = "",
    @Column(nullable = false, length = 255)
    var name: String = "",
    @Column(name = "name_en", nullable = false, length = 255)
    var nameEn: String = "",
    @Column(name = "full_name", nullable = false, length = 255)
    var fullName: String = "",
    @Column(name = "full_name_en", nullable = false, length = 255)
    var fullNameEn: String = "",
    @Column(name = "code_name", nullable = false, length = 255)
    var codeName: String = "",
    @Column(name = "province_code", nullable = false, length = 20)
    var provinceCode: String = "",
)
