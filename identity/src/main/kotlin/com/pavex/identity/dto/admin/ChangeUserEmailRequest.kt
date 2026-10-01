package com.pavex.identity.dto.admin

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangeUserEmailRequest(
    @field:NotBlank(message = "Email là bắt buộc.")
    @field:Email(message = "Email không hợp lệ.")
    @field:Size(max = 255, message = "Email dài tối đa 255 ký tự.")
    val email: String,
)
