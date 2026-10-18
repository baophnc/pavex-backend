package com.pavex.test

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * A keys directory laid out like `~/.pavex/keys` (`<service>/private.pem`, `<service>/public.pem`),
 * where every service has the same test key pair, so tokens one service signs are trusted by the others.
 * The pair is generated per test run: key files are never committed (.gitignore drops every *.pem).
 */
object TestKeys {
    private val SERVICES = listOf(
        "auth", "identity", "partners", "network", "pricing",
        "shipment", "operations", "billing", "fleet", "notifications",
    )

    val directory: Path by lazy {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val privatePem = pem("PRIVATE KEY", pair.private.encoded)
        val publicPem = pem("PUBLIC KEY", pair.public.encoded)
        val root = Files.createTempDirectory("pavex-test-keys")
        Runtime.getRuntime().addShutdownHook(Thread { root.toFile().deleteRecursively() })
        for (service in SERVICES) {
            val dir = Files.createDirectories(root.resolve(service))
            Files.writeString(dir.resolve("private.pem"), privatePem)
            Files.writeString(dir.resolve("public.pem"), publicPem)
        }
        root
    }

    /** PKCS#8 private and X.509 public keys, as RsaKeyConverters reads them. */
    private fun pem(type: String, der: ByteArray): String =
        "-----BEGIN $type-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) +
            "\n-----END $type-----\n"
}
