package com.pavex.shipment.security

import com.pavex.common.security.PavexActor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication

fun Authentication.requirePavexActor(): PavexActor =
    PavexActor.parse(name, authorities.map { it.authority })
        ?: throw AccessDeniedException("Authenticated user id is invalid.")
