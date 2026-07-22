package de.oliveroehme.campaignfield.data.auth

import android.content.Context
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface UserProfileStore {
    fun load(): UserProfile?
    fun save(profile: UserProfile): Boolean
    fun clear(): Boolean
}

class AndroidUserProfileStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : UserProfileStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): UserProfile? {
        val stored = preferences.getString(PROFILE_KEY, null) ?: return null
        return runCatching { json.decodeFromString<UserProfile>(stored) }
            .getOrElse {
                clear()
                null
            }
    }

    override fun save(profile: UserProfile): Boolean =
        preferences.edit().putString(PROFILE_KEY, json.encodeToString(profile)).commit()

    override fun clear(): Boolean = preferences.edit().remove(PROFILE_KEY).commit()

    private companion object {
        const val PREFERENCES_NAME = "campaign_field_profile"
        const val PROFILE_KEY = "authenticated_user"
    }
}
