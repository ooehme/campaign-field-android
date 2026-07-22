package de.oliveroehme.campaignfield.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    @Test
    fun `shell destinations are fully initialized`() {
        AppDestination.Assignments.route

        assertEquals(
            listOf("assignments", "map", "sync", "profile"),
            AppDestination.shellItems.map(AppDestination::route),
        )
    }
}
