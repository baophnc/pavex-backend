package com.pavex.shipment.config

import com.pavex.storage.MinioObjectStorage
import com.pavex.storage.ObjectStorage
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class StorageConfig {
    @Bean
    fun objectStorage(
        @Value("\${pavex.storage.endpoint}") endpoint: String,
        @Value("\${pavex.storage.access-key}") accessKey: String,
        @Value("\${pavex.storage.secret-key}") secretKey: String,
    ): ObjectStorage = MinioObjectStorage(endpoint, accessKey, secretKey)
}
