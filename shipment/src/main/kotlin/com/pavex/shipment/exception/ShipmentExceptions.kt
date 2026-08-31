package com.pavex.shipment.exception

import org.springframework.http.HttpStatus

open class ShipmentApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)

class ShipmentNotFoundException(code: String, message: String) :
    ShipmentApiException(HttpStatus.NOT_FOUND, code, message)

class ShipmentConflictException(code: String, message: String) :
    ShipmentApiException(HttpStatus.CONFLICT, code, message)

class InvalidShipmentRequestException(code: String, message: String) :
    ShipmentApiException(HttpStatus.BAD_REQUEST, code, message)

class ShipmentForbiddenException(code: String, message: String) :
    ShipmentApiException(HttpStatus.FORBIDDEN, code, message)

class ShipmentGoneException(code: String, message: String) :
    ShipmentApiException(HttpStatus.GONE, code, message)

class UnprocessableShipmentException(code: String, message: String) :
    ShipmentApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message)

class ShipmentUnavailableException(code: String, message: String) :
    ShipmentApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message)
