package de.oliveroehme.campaignfield.network.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionErrorNormalizerTest {
    @Test
    fun `normalizes supported failures without raw server data`() {
        val failures = listOf(
            SessionErrorNormalizer.from(SessionStage.LOGIN, null) to SessionErrorKind.NETWORK,
            SessionErrorNormalizer.from(SessionStage.LOGIN, 401) to SessionErrorKind.UNAUTHORIZED,
            SessionErrorNormalizer.from(SessionStage.LOGIN, 403) to SessionErrorKind.FORBIDDEN,
            SessionErrorNormalizer.from(SessionStage.LOGIN, 422) to SessionErrorKind.VALIDATION,
            SessionErrorNormalizer.from(SessionStage.LOGIN, 503) to SessionErrorKind.SERVER,
        )

        failures.forEach { (failure, expectedKind) ->
            assertEquals(expectedKind, failure.kind)
            assertFalse(failure.userMessage.contains("503"))
            assertFalse(failure.userMessage.contains("payload", ignoreCase = true))
        }
    }
}
