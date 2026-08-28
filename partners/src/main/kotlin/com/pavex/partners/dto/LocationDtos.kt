package com.pavex.partners.dto

data class ProvinceResponse(
    val code: String,
    val name: String,
    val nameEn: String,
    val fullName: String,
    val fullNameEn: String,
    val codeName: String,
)

data class WardResponse(
    val code: String,
    val name: String,
    val nameEn: String,
    val fullName: String,
    val fullNameEn: String,
    val codeName: String,
    val provinceCode: String,
)
