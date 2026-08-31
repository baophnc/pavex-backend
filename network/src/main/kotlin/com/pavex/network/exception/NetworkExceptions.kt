package com.pavex.network.exception

import org.springframework.http.HttpStatus

open class NetworkApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)

class NotFoundException(code: String, message: String) :
    NetworkApiException(HttpStatus.NOT_FOUND, code, message)

class ConflictException(code: String, message: String) :
    NetworkApiException(HttpStatus.CONFLICT, code, message)

class InvalidNetworkRequestException(code: String, message: String) :
    NetworkApiException(HttpStatus.BAD_REQUEST, code, message)

class LocationCatalogUnavailableException :
    NetworkApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "location_catalog_unavailable",
        "Location catalog is temporarily unavailable.",
    )
