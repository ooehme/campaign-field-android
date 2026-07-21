package de.oliveroehme.campaignfield.network.auth

import de.oliveroehme.campaignfield.network.hasSameOriginAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal class PersistentCookieJar(
    private val allowedOrigin: HttpUrl,
    private val persistence: CookiePersistence,
    private val now: () -> Long = System::currentTimeMillis,
) : CookieJar {
    private val json = Json { ignoreUnknownKeys = true }
    private var cookies: MutableList<Cookie>

    init {
        val restoration = restore()
        cookies = restoration.cookies
        if (restoration.removedExpiredCookies) persist()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.hasSameOriginAs(allowedOrigin)) return

        cookies.forEach { incoming ->
            if (!incoming.matches(url)) return@forEach
            this.cookies.removeAll { it.hasSameIdentityAs(incoming) }
            if (incoming.expiresAt > now()) this.cookies += incoming
        }
        removeExpired()
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.hasSameOriginAs(allowedOrigin)) return emptyList()
        if (removeExpired()) persist()
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        persistence.clear()
    }

    private fun restore(): Restoration {
        val stored = persistence.read() ?: return Restoration(mutableListOf(), false)
        return try {
            val decoded = json.decodeFromString(
                ListSerializer(StoredCookie.serializer()),
                stored.decodeToString(),
            ).map(StoredCookie::toCookie)
            val active = decoded.filter { it.expiresAt > now() }.toMutableList()
            Restoration(active, active.size != decoded.size)
        } catch (_: Exception) {
            persistence.clear()
            Restoration(mutableListOf(), false)
        }
    }

    private fun removeExpired(): Boolean = cookies.removeAll { it.expiresAt <= now() }

    private fun persist() {
        if (cookies.isEmpty()) {
            persistence.clear()
        } else {
            val serialized = json.encodeToString(
                ListSerializer(StoredCookie.serializer()),
                cookies.map(StoredCookie::fromCookie),
            )
            persistence.write(serialized.encodeToByteArray())
        }
    }

    private fun Cookie.hasSameIdentityAs(other: Cookie): Boolean =
        name == other.name && domain == other.domain && path == other.path

    private data class Restoration(
        val cookies: MutableList<Cookie>,
        val removedExpiredCookies: Boolean,
    )
}

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val persistent: Boolean,
    val hostOnly: Boolean,
    val sameSite: String?,
) {
    fun toCookie(): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .apply {
            if (persistent) expiresAt(expiresAt)
            if (hostOnly) hostOnlyDomain(domain) else domain(domain)
            path(path)
            if (secure) secure()
            if (httpOnly) httpOnly()
            sameSite?.let(::sameSite)
        }
        .build()

    companion object {
        fun fromCookie(cookie: Cookie): StoredCookie = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            expiresAt = cookie.expiresAt,
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            persistent = cookie.persistent,
            hostOnly = cookie.hostOnly,
            sameSite = cookie.sameSite,
        )
    }
}
