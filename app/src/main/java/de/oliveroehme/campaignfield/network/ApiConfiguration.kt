package de.oliveroehme.campaignfield.network

import de.oliveroehme.campaignfield.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ApiConfiguration private constructor(
    val apiBaseUrl: HttpUrl,
    val sanctumClientOrigin: HttpUrl,
) {
    val baseUrl: String = apiBaseUrl.toString().trimEnd('/')
    val originUrl: HttpUrl = apiBaseUrl.newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .build()

    fun apiEndpoint(path: String): HttpUrl = apiBaseUrl.newBuilder()
        .addPathSegments(path.trim('/'))
        .build()

    fun originEndpoint(path: String): HttpUrl = originUrl.newBuilder()
        .addPathSegments(path.trim('/'))
        .build()

    companion object {
        fun fromBuildConfig(): ApiConfiguration = from(
            BuildConfig.API_BASE_URL,
            BuildConfig.SANCTUM_CLIENT_ORIGIN,
        )

        fun from(value: String, sanctumClientOrigin: String): ApiConfiguration =
            create(value, sanctumClientOrigin, requireHttps = true)

        internal fun forHttpTest(value: String): ApiConfiguration =
            create(value, originOf(value), requireHttps = false)

        private fun create(
            value: String,
            sanctumClientOrigin: String,
            requireHttps: Boolean,
        ): ApiConfiguration {
            val normalized = normalizeApiBaseUrl(value)
            val url = normalized.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Die API-Basis-URL ist ungültig.")
            val clientOriginUrl = normalizeApiBaseUrl(sanctumClientOrigin).toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Die Sanctum-Client-Origin ist ungültig.")
            require(url.query == null && url.fragment == null) {
                "Die API-Basis-URL darf weder Query noch Fragment enthalten."
            }
            require(
                url.username.isEmpty() &&
                    url.password.isEmpty() &&
                    clientOriginUrl.username.isEmpty() &&
                    clientOriginUrl.password.isEmpty()
            ) { "Netzwerk-URLs dürfen keine Zugangsdaten enthalten." }
            require(
                clientOriginUrl.encodedPath == "/" &&
                    clientOriginUrl.query == null &&
                    clientOriginUrl.fragment == null
            ) { "Die Sanctum-Client-Origin darf nur Schema, Host und Port enthalten." }
            require(!requireHttps || (url.isHttps && clientOriginUrl.isHttps)) {
                "API und Sanctum-Client-Origin müssen HTTPS verwenden."
            }
            return ApiConfiguration(url, clientOriginUrl)
        }

        private fun originOf(value: String): String = normalizeApiBaseUrl(value)
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.encodedPath("/")
            ?.query(null)
            ?.fragment(null)
            ?.build()
            ?.toString()
            ?: value
    }
}

fun normalizeApiBaseUrl(value: String): String = value.trim().trimEnd('/')

internal fun HttpUrl.hasSameOriginAs(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port
