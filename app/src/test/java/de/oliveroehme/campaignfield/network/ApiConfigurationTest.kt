package de.oliveroehme.campaignfield.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiConfigurationTest {
    @Test
    fun `normalizes whitespace and trailing slashes`() {
        assertEquals(
            "https://example.invalid/api",
            normalizeApiBaseUrl("  https://example.invalid/api///  "),
        )
    }

    @Test
    fun `derives api and origin endpoints without duplicate api segment`() {
        val configuration = ApiConfiguration.from(
            "https://example.invalid/api/",
            "https://app.example.invalid",
        )

        assertEquals("https://example.invalid/api/login", configuration.apiEndpoint("/login").toString())
        assertEquals(
            "https://example.invalid/sanctum/csrf-cookie",
            configuration.originEndpoint("sanctum/csrf-cookie").toString(),
        )
    }

    @Test
    fun `rejects cleartext base url`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiConfiguration.from("http://example.invalid/api", "https://app.example.invalid")
        }
    }

    @Test
    fun `rejects credentials embedded in network urls`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiConfiguration.from(
                "https://user:secret@example.invalid/api",
                "https://app.example.invalid",
            )
        }
    }
}
