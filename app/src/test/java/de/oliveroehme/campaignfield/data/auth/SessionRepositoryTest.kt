package de.oliveroehme.campaignfield.data.auth

import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.network.auth.SessionErrorNormalizer
import de.oliveroehme.campaignfield.network.auth.SessionRemoteDataSource
import de.oliveroehme.campaignfield.network.auth.SessionResult
import de.oliveroehme.campaignfield.network.auth.SessionStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {
    private val profile = UserProfile("1", "Erika Feld", "erika@example.test")

    @Test
    fun `restores session through user endpoint and stores profile`() = runBlocking {
        val remote = FakeRemote(checkResult = SessionResult.Authenticated(profile))
        val store = FakeProfileStore(profile)
        val repository = SessionRepository(remote, store, FakeCleaner())

        assertEquals(AuthState.Restoring(profile), repository.authState.value)
        repository.restoreSession()

        assertEquals(AuthState.SignedIn(profile), repository.authState.value)
        assertEquals(profile, store.profile)
        assertEquals(1, remote.checkCalls)
    }

    @Test
    fun `login clears old local data before authenticating`() = runBlocking {
        val remote = FakeRemote(signInResult = SessionResult.Authenticated(profile))
        val store = FakeProfileStore()
        val cleaner = FakeCleaner()
        val repository = SessionRepository(remote, store, cleaner)

        repository.signIn(" erika@example.test ", "secret")

        assertEquals(1, cleaner.calls)
        assertEquals("erika@example.test", remote.lastEmail)
        assertEquals(AuthState.SignedIn(profile), repository.authState.value)
    }

    @Test
    fun `central unauthorized handler cleans data and signs out`() {
        val store = FakeProfileStore(profile)
        val cleaner = FakeCleaner()
        val handler = UnauthorizedSessionHandler(cleaner)
        val repository = SessionRepository(FakeRemote(), store, cleaner, handler)

        handler.handle()

        assertEquals(1, cleaner.calls)
        assertTrue(repository.authState.value is AuthState.SignedOut)
    }

    @Test
    fun `offline logout always clears local state and explains server failure`() = runBlocking {
        val remote = FakeRemote(
            checkResult = SessionResult.Authenticated(profile),
            logoutResult = SessionResult.Failure(SessionErrorNormalizer.from(SessionStage.LOGOUT, null)),
        )
        val store = FakeProfileStore(profile)
        val cleaner = FakeCleaner(onClear = { store.clear() })
        val repository = SessionRepository(remote, store, cleaner)
        repository.restoreSession()

        repository.logout()

        val state = repository.authState.value as AuthState.SignedOut
        assertEquals(1, cleaner.calls)
        assertNull(store.profile)
        assertTrue(state.message.orEmpty().contains("Lokale Daten wurden gelöscht"))
    }

    private class FakeRemote(
        private val signInResult: SessionResult = SessionResult.LoggedOut,
        private val checkResult: SessionResult = SessionResult.LoggedOut,
        private val logoutResult: SessionResult = SessionResult.LoggedOut,
    ) : SessionRemoteDataSource {
        var checkCalls = 0
        var lastEmail: String? = null

        override suspend fun signIn(email: String, password: String): SessionResult {
            lastEmail = email
            return signInResult
        }

        override suspend fun checkSession(): SessionResult {
            checkCalls += 1
            return checkResult
        }

        override suspend fun logout(): SessionResult = logoutResult
    }

    private class FakeProfileStore(
        var profile: UserProfile? = null,
    ) : UserProfileStore {
        override fun load(): UserProfile? = profile
        override fun save(profile: UserProfile): Boolean {
            this.profile = profile
            return true
        }

        override fun clear(): Boolean {
            profile = null
            return true
        }
    }

    private class FakeCleaner(
        private val succeeds: Boolean = true,
        private val onClear: () -> Unit = {},
    ) : LocalSessionCleaner {
        var calls = 0

        override fun clear(): Boolean {
            calls += 1
            onClear()
            return succeeds
        }
    }
}
