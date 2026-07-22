package de.oliveroehme.campaignfield.ui.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionViewModelTest {
    @Test
    fun `validates empty login fields`() {
        val result = SessionViewModel.validateLogin("", "")

        assertEquals("E-Mail-Adresse eingeben.", result.emailError)
        assertEquals("Passwort eingeben.", result.passwordError)
    }

    @Test
    fun `accepts valid login fields`() {
        val result = SessionViewModel.validateLogin("field@example.test", "secret")

        assertNull(result.emailError)
        assertNull(result.passwordError)
    }
}
