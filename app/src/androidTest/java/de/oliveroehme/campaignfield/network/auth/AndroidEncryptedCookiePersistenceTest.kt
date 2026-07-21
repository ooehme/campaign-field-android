package de.oliveroehme.campaignfield.network.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidEncryptedCookiePersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val origin = "https://api.example.test/".toHttpUrl()

    @Before
    fun setUp() {
        AndroidEncryptedCookiePersistence(context).clear()
    }

    @After
    fun tearDown() {
        AndroidEncryptedCookiePersistence(context).clear()
    }

    @Test
    fun encryptedCookieSurvivesJarRecreationAndIsFullyDeleted() {
        val persistence = AndroidEncryptedCookiePersistence(context)
        val cookie = Cookie.Builder()
            .name("laravel_session")
            .value("not-plaintext-session")
            .hostOnlyDomain(origin.host)
            .path("/")
            .secure()
            .httpOnly()
            .sameSite("Lax")
            .build()
        PersistentCookieJar(origin, persistence).saveFromResponse(origin, listOf(cookie))

        val storedValues = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .all
            .values
            .joinToString()
        assertFalse(storedValues.contains(cookie.name))
        assertFalse(storedValues.contains(cookie.value))

        val restored = PersistentCookieJar(
            origin,
            AndroidEncryptedCookiePersistence(context),
        ).loadForRequest(origin)
        assertEquals(listOf(cookie), restored)

        AndroidEncryptedCookiePersistence(context).clear()
        assertTrue(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all.isEmpty())
        assertFalse(androidKeyStore().containsAlias(KEY_ALIAS))
        assertNull(AndroidEncryptedCookiePersistence(context).read())
    }

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private companion object {
        const val PREFERENCES_NAME = "campaign_field_secure_session"
        const val KEY_ALIAS = "campaign_field_session_cookies_v1"
    }
}
