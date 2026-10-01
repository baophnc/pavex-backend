package com.pavex.partners.controller

import com.pavex.partners.dto.CounterContactResponse
import com.pavex.partners.dto.SaveCounterContactRequest
import com.pavex.partners.service.CounterContactService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Walk-in customers the hub counters served, for staff booking counter shipments. */
@RestController
@RequestMapping("/api/v1/partners/counter/contacts")
@PreAuthorize("hasAuthority('shipments.counter.create')")
class CounterContactController(
    private val contacts: CounterContactService,
) {
    @GetMapping
    fun find(@RequestParam phone: String): List<CounterContactResponse> = contacts.find(phone)

    @PutMapping
    fun save(@Valid @RequestBody request: SaveCounterContactRequest): CounterContactResponse = contacts.save(request)
}
