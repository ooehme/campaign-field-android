package de.oliveroehme.campaignfield.network.auth

import android.content.Context
import de.oliveroehme.campaignfield.network.ApiConfiguration
import de.oliveroehme.campaignfield.network.hasSameOriginAs
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

internal object SanctumHttpClient {
    fun create(
        configuration: ApiConfiguration,
        cookieJar: PersistentCookieJar,
        allowCleartextForTests: Boolean = false,
        onUnauthorized: () -> Unit = cookieJar::clear,
    ): OkHttpClient {
        check(allowCleartextForTests || configuration.apiBaseUrl.isHttps) {
            "Sanctum darf nur über HTTPS verwendet werden."
        }
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(OriginAndJsonInterceptor(configuration))
            .addInterceptor(CsrfInterceptor(cookieJar))
            .addInterceptor(UnauthorizedInterceptor(onUnauthorized))
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

internal class OriginAndJsonInterceptor(
    private val configuration: ApiConfiguration,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.hasSameOriginAs(configuration.originUrl)) {
            throw IOException("Request außerhalb der konfigurierten API-Origin blockiert.")
        }
        val safeRequest = request.newBuilder()
            .removeHeader("Authorization")
            .header("Accept", "application/json")
            .header("Origin", configuration.sanctumClientOrigin.toString().trimEnd('/'))
            .header("Referer", configuration.sanctumClientOrigin.toString())
            .build()
        return chain.proceed(safeRequest)
    }
}

internal class CsrfInterceptor(
    private val cookieJar: PersistentCookieJar,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method !in MUTATING_METHODS) return chain.proceed(request)

        val token = cookieJar.loadForRequest(request.url)
            .lastOrNull { it.name == XSRF_COOKIE_NAME }
            ?.value
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }

        val requestWithCsrf = request.newBuilder()
            .removeHeader(XSRF_HEADER_NAME)
            .apply { if (token != null) header(XSRF_HEADER_NAME, token) }
            .build()
        return chain.proceed(requestWithCsrf)
    }

    private companion object {
        val MUTATING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        const val XSRF_COOKIE_NAME = "XSRF-TOKEN"
        const val XSRF_HEADER_NAME = "X-XSRF-TOKEN"
    }
}

internal class UnauthorizedInterceptor(
    private val onUnauthorized: () -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request()).also { response ->
            if (response.code == 401) onUnauthorized()
        }
}

object SanctumSessionClientFactory {
    fun create(
        context: Context,
        configuration: ApiConfiguration = ApiConfiguration.fromBuildConfig(),
    ): SanctumSessionClient {
        val persistence = AndroidEncryptedCookiePersistence(context.applicationContext)
        val cookieJar = PersistentCookieJar(configuration.originUrl, persistence)
        val httpClient = SanctumHttpClient.create(configuration, cookieJar)
        return SanctumSessionClient(configuration, httpClient, cookieJar)
    }
}
