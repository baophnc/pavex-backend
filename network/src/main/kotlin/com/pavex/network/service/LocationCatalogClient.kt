package com.pavex.network.service

import com.pavex.network.exception.InvalidNetworkRequestException
import com.pavex.network.exception.LocationCatalogUnavailableException
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class LocationCatalogClient(
    @LoadBalanced builder: RestClient.Builder,
    @Value("\${network.partners.service-url:http://partners-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    data class ProvinceResponse(val code: String)
    data class WardResponse(val code: String, val provinceCode: String)

    fun validate(provinceCode: String, wardCode: String) {
        val province = provinceCode.trim()
        val ward = wardCode.trim()
        val response = try {
            client.get()
                .uri("/api/v1/locations/wards/{wardCode}", ward)
                .retrieve()
                .body(WardResponse::class.java)
        } catch (_: HttpClientErrorException.NotFound) {
            throw InvalidNetworkRequestException("invalid_location", "Ward was not found in the location catalog.")
        } catch (_: RestClientException) {
            throw LocationCatalogUnavailableException()
        }
        if (response == null || response.provinceCode != province) {
            throw InvalidNetworkRequestException(
                "invalid_location",
                "Ward does not belong to the selected province.",
            )
        }
    }

    fun validateProvince(provinceCode: String) {
        val province = provinceCode.trim()
        val exists = try {
            client.get()
                .uri("/api/v1/locations/provinces")
                .retrieve()
                .body(Array<ProvinceResponse>::class.java)
                .orEmpty()
                .any { it.code == province }
        } catch (_: RestClientException) {
            throw LocationCatalogUnavailableException()
        }
        if (!exists) {
            throw InvalidNetworkRequestException("invalid_location", "Province was not found in the location catalog.")
        }
    }
}
