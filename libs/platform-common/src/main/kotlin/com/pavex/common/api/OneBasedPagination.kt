package com.pavex.common.api

/**
 * Normalized one-based pagination values for public/admin HTTP APIs.
 *
 * Spring Data uses zero-based page indexes while Pavex APIs expose one-based
 * page numbers. Keeping the conversion here prevents every service from
 * reimplementing the same clamping rules.
 */
data class OneBasedPagination private constructor(
    val page: Int,
    val pageSize: Int,
) {
    val zeroBasedPage: Int
        get() = page - 1

    companion object {
        const val DEFAULT_MAX_PAGE_SIZE: Int = 100

        fun normalize(
            page: Int,
            pageSize: Int,
            maxPageSize: Int = DEFAULT_MAX_PAGE_SIZE,
        ): OneBasedPagination {
            require(maxPageSize > 0) {
                "maxPageSize must be greater than 0"
            }

            return OneBasedPagination(
                page = page.coerceAtLeast(1),
                pageSize = pageSize.coerceIn(1, maxPageSize),
            )
        }
    }
}
