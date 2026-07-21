package de.oliveroehme.campaignfield.network.auth

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentCookieJarTest {
    private val origin = "https://api.example.test/".toHttpUrl()

    @Test
    fun `survives process recreation and preserves cookie attributes`() {
        val persistence = TestCookiePersistence()
        val initialJar = PersistentCookieJar(origin, persistence)
        val cookie = Cookie.Builder()
            .name("laravel_session")
            .value("secret")
            .hostOnlyDomain("api.example.test")
            .path("/api")
            .secure()
            .httpOnly()
            .sameSite("Lax")
            .build()

        initialJar.saveFromResponse("https://api.example.test/api/login".toHttpUrl(), listOf(cookie))
        val restored = PersistentCookieJar(origin, persistence)
            .loadForRequest("https://api.example.test/api/user".toHttpUrl())
            .single()

        assertEquals(cookie, restored)
    }

    @Test
    fun `applies origin path secure and expiry boundaries`() {
        var now = 1_000L
        val persistence = TestCookiePersistence()
        val jar = PersistentCookieJar(origin, persistence) { now }
        val cookie = Cookie.Builder()
            .name("XSRF-TOKEN")
            .value("token")
            .hostOnlyDomain("api.example.test")
            .path("/api")
            .secure()
            .expiresAt(2_000L)
            .build()
        jar.saveFromResponse("https://api.example.test/api/login".toHttpUrl(), listOf(cookie))

        assertEquals(1, jar.loadForRequest("https://api.example.test/api/user".toHttpUrl()).size)
        assertTrue(jar.loadForRequest("https://api.example.test/other".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://api.example.test/api/user".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://other.example.test/api/user".toHttpUrl()).isEmpty())

        now = 2_000L
        assertTrue(jar.loadForRequest("https://api.example.test/api/user".toHttpUrl()).isEmpty())
        assertEquals(null, persistence.value)
    }

    @Test
    fun `ignores cookies received from another origin`() {
        val jar = PersistentCookieJar(origin, TestCookiePersistence())
        val cookie = Cookie.Builder()
            .name("laravel_session")
            .value("secret")
            .hostOnlyDomain("other.example.test")
            .build()

        jar.saveFromResponse("https://other.example.test/login".toHttpUrl(), listOf(cookie))

        assertTrue(jar.loadForRequest(origin).isEmpty())
    }
}
