package com.pavex.identity.dto.common

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
) {

    val totalPages: Int
        get() =
            if (totalCount == 0L) {
                0
            } else {
                (
                    (totalCount + pageSize - 1) /
                        pageSize
                ).toInt()
            }
}
