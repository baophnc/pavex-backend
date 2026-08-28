package com.pavex.common.api

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
) {
    init {
        require(page >= 0) {
            "page must be greater than or equal to 0"
        }

        require(pageSize > 0) {
            "pageSize must be greater than 0"
        }

        require(totalCount >= 0) {
            "totalCount must be greater than or equal to 0"
        }
    }

    val totalPages: Int
        get() =
            if (totalCount == 0L) {
                0
            } else {
                Math.toIntExact(((totalCount - 1) / pageSize) + 1)
            }
}
