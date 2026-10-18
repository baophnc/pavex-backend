package com.pavex.test

import java.nio.file.Files
import java.nio.file.Path

/**
 * A keys directory laid out like `~/.pavex/keys` (`<service>/private.pem`, `<service>/public.pem`),
 * where every service has the same test key pair, so tokens one service signs are trusted by the others.
 */
object TestKeys {
    private val SERVICES = listOf(
        "auth", "identity", "partners", "network", "pricing",
        "shipment", "operations", "billing", "fleet", "notifications",
    )

    val directory: Path by lazy {
        val root = Files.createTempDirectory("pavex-test-keys")
        Runtime.getRuntime().addShutdownHook(Thread { root.toFile().deleteRecursively() })
        for (service in SERVICES) {
            val dir = Files.createDirectories(root.resolve(service))
            for (file in listOf("private.pem", "public.pem")) {
                val key = requireNotNull(TestKeys::class.java.getResourceAsStream("/pavex-test-keys/$file")) { "Missing test key $file" }
                key.use { Files.copy(it, dir.resolve(file)) }
            }
        }
        root
    }
}
