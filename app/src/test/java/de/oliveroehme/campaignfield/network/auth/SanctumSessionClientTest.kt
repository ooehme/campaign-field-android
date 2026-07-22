package de.oliveroehme.campaignfield.network.auth

import de.oliveroehme.campaignfield.network.ApiConfiguration
import de.oliveroehme.campaignfield.domain.auth.UserPermissions
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import okhttp3.Cookie
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SanctumSessionClientTest {
    private lateinit var server: MockWebServer
    private lateinit var persistence: TestCookiePersistence
    private lateinit var cookieJar: PersistentCookieJar
    private lateinit var configuration: ApiConfiguration

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        configuration = ApiConfiguration.forHttpTest(server.url("/api").toString())
        persistence = TestCookiePersistence()
        cookieJar = PersistentCookieJar(configuration.originUrl, persistence)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login loads csrf rotates token and confirms user`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(204)
                .addHeader("Set-Cookie", "XSRF-TOKEN=first%3D; Path=/; SameSite=Lax"),
        )
        server.enqueue(
            MockResponse().setResponseCode(204)
                .addHeader("Set-Cookie", "XSRF-TOKEN=rotated%3D; Path=/; SameSite=Lax")
                .addHeader("Set-Cookie", "laravel_session=session; Path=/; HttpOnly; SameSite=Lax"),
        )
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("{\"id\":1,\"name\":\"Field User\",\"email\":\"field@example.test\"}"),
        )
        val client = client()

        assertEquals(
            SessionResult.Authenticated(
                UserProfile(
                    id = "1",
                    name = "Field User",
                    email = "field@example.test",
                    permissions = UserPermissions(),
                ),
            ),
            client.signIn("field@example.test", "not-logged"),
        )

        val csrfRequest = server.takeRequest()
        val loginRequest = server.takeRequest()
        val userRequest = server.takeRequest()
        assertEquals("/sanctum/csrf-cookie", csrfRequest.path)
        assertEquals("/api/login", loginRequest.path)
        assertEquals("first=", loginRequest.getHeader("X-XSRF-TOKEN"))
        assertTrue(loginRequest.body.readUtf8().contains("field@example.test"))
        assertEquals(
            "rotated=",
            cookieJar.loadForRequest(configuration.apiEndpoint("user"))
                .single { it.name == "XSRF-TOKEN" }
                .value
                .let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) },
        )
        assertTrue(userRequest.getHeader("Cookie").orEmpty().contains("laravel_session=session"))
        assertEquals("application/json", userRequest.getHeader("Accept"))
        assertEquals(configuration.sanctumClientOrigin.toString().trimEnd('/'), userRequest.getHeader("Origin"))
        assertEquals(configuration.sanctumClientOrigin.toString(), userRequest.getHeader("Referer"))
    }

    @Test
    fun `authorization is stripped and redirects are not followed`() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://foreign.test/"))
        val httpClient = SanctumHttpClient.create(configuration, cookieJar, allowCleartextForTests = true)
        val response = httpClient.newCall(
            Request.Builder()
                .url(configuration.apiEndpoint("user"))
                .header("Authorization", "Bearer forbidden")
                .build(),
        ).execute()

        response.use { assertEquals(302, it.code) }
        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `401 and logout clear all cookie data`() = runBlocking {
        seedCookie("laravel_session", "session")
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(
            SessionResult.Failure(SessionErrorNormalizer.from(SessionStage.USER, 401)),
            client().checkSession(),
        )
        assertEquals(null, persistence.value)
        server.takeRequest()

        seedCookie("XSRF-TOKEN", "logout-token")
        seedCookie("laravel_session", "session")
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(SessionResult.LoggedOut, client().logout())
        assertEquals(null, persistence.value)
        assertEquals("logout-token", server.takeRequest().getHeader("X-XSRF-TOKEN"))
    }

    @Test
    fun `foreign origin is blocked before network`() {
        val httpClient = SanctumHttpClient.create(configuration, cookieJar, allowCleartextForTests = true)
        val result = runCatching {
            httpClient.newCall(
                Request.Builder()
                    .url("http://127.0.0.1:${server.port + 1}/api/user")
                    .post(ByteArray(0).toRequestBody(null))
                    .build(),
            ).execute()
        }

        assertTrue(result.isFailure)
        assertFalse(cookieJar.loadForRequest(configuration.originUrl).any())
        assertEquals(0, server.requestCount)
    }

    private fun client(): SanctumSessionClient = SanctumSessionClient(
        configuration,
        SanctumHttpClient.create(configuration, cookieJar, allowCleartextForTests = true),
        cookieJar,
    )

    private fun seedCookie(name: String, value: String) {
        cookieJar.saveFromResponse(
            configuration.originUrl,
            listOf(
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .hostOnlyDomain(configuration.originUrl.host)
                    .path("/")
                    .build(),
            ),
        )
    }
}
