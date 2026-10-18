package com.pavex.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder

/**
 * The beans every service configures the same way, from the same settings:
 *
 * - `userJwtDecoder`: Auth's access tokens, from `security.jwt.public-key` and `security.jwt.issuer`.
 * - `serviceJwtEncoder`: signs this service's own service tokens with `security.service-jwt.private-key`
 *   and `public-key` (services that call no other service have no such key and get no encoder).
 * - [TrustedServiceTokens]: builds a decoder per trusted caller; which callers and scopes a service accepts
 *   stays in that service.
 */
@AutoConfiguration
class PavexSecurityAutoConfiguration {
    @Bean(USER_JWT_DECODER)
    @ConditionalOnProperty(prefix = "security.jwt", name = ["public-key"])
    @ConditionalOnMissingBean(name = [USER_JWT_DECODER])
    fun userJwtDecoder(
        @Value("\${security.jwt.public-key}") publicKey: Resource,
        @Value("\${security.jwt.issuer}") issuer: String,
    ): JwtDecoder = PavexJwtDecoders.userTokens(RsaKeys.publicKey(publicKey), issuer)

    @Bean(SERVICE_JWT_ENCODER)
    @ConditionalOnProperty(prefix = "security.service-jwt", name = ["private-key"])
    @ConditionalOnMissingBean(name = [SERVICE_JWT_ENCODER])
    fun serviceJwtEncoder(
        @Value("\${security.service-jwt.private-key}") privateKey: Resource,
        @Value("\${security.service-jwt.public-key}") publicKey: Resource,
    ): JwtEncoder = RsaKeys.encoder(RsaKeys.publicKey(publicKey), RsaKeys.privateKey(privateKey))

    @Bean
    @ConditionalOnMissingBean
    fun trustedServiceTokens(environment: Environment, resourceLoader: ResourceLoader): TrustedServiceTokens =
        TrustedServiceTokens(environment, resourceLoader)

    companion object {
        const val USER_JWT_DECODER = "userJwtDecoder"
        const val SERVICE_JWT_ENCODER = "serviceJwtEncoder"
    }
}

/**
 * Decoders for the service tokens of trusted callers. Each caller's key and issuer are read from
 * `<prefix>.public-key` and `<prefix>.issuer`, normally `security.trusted-services.<caller>`.
 */
class TrustedServiceTokens(
    private val environment: Environment,
    private val resourceLoader: ResourceLoader,
) {
    /** Tokens [caller] signs for [audience] (this service), optionally limited to [requiredScope]. */
    fun decoder(
        prefix: String,
        caller: String,
        audience: String,
        requiredScope: String? = null,
    ): JwtDecoder {
        val publicKey = RsaKeys.publicKey(resourceLoader.getResource(environment.getRequiredProperty("$prefix.public-key")))
        return PavexJwtDecoders.serviceTokens(
            publicKey = publicKey,
            issuer = environment.getRequiredProperty("$prefix.issuer"),
            caller = caller,
            audience = audience,
            requiredScope = requiredScope,
        )
    }

    /** Shorthand for a caller configured under `security.trusted-services.<name>`. */
    fun trustedService(name: String, caller: String, audience: String, requiredScope: String? = null): JwtDecoder =
        decoder("security.trusted-services.$name", caller, audience, requiredScope)
}
