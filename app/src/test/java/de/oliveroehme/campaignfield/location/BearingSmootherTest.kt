package de.oliveroehme.campaignfield.location

import org.junit.Assert.assertTrue
import org.junit.Test

class BearingSmootherTest {
    @Test
    fun `smooths across north without rotating through south`() {
        val smoother = BearingSmoother(alpha = 0.5)

        smoother.update(359.0)
        val result = smoother.update(1.0)

        assertTrue(result < 5.0 || result > 355.0)
    }

    @Test
    fun `normalizes negative bearing`() {
        assertTrue(kotlin.math.abs(normalizeBearing(-10.0) - 350.0) < 0.001)
    }
}
