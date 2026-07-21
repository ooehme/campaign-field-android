package de.oliveroehme.campaignfield.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiConfigurationTest {
    @Test
    fun `normalizes whitespace and trailing slashes`() {
        assertEquals(
            "https://example.invalid/api",
            normalizeApiBaseUrl("  https://example.invalid/api///  "),
        )
    }
}
