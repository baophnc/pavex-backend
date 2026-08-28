package com.pavex.network.dto

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int,
)

fun <T, R> Page<T>.toResponse(mapper: (T) -> R): PageResponse<R> = PageResponse(
    items = content.map(mapper),
    page = number + 1,
    pageSize = size,
    totalItems = totalElements,
    totalPages = totalPages,
)
