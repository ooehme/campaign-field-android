package de.oliveroehme.campaignfield.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MapConfigurationTest {
    @Test
    fun `uses documented camera defaults`() {
        val configuration = MapConfiguration("https://tiles.openfreemap.org/styles/dark")

        assertEquals(16.5, configuration.initialZoom, 0.0)
        assertEquals(20.0, configuration.initialPitch, 0.0)
        assertEquals(0.0, configuration.fallbackBearing, 0.0)
    }

    @Test
    fun `rejects insecure style url`() {
        assertThrows(IllegalArgumentException::class.java) {
            MapConfiguration("http://tiles.example.test/style")
        }
    }
}
