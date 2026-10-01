package com.example.learningapp.repository

import com.example.learningapp.data.dao.LoginDao
import com.example.learningapp.data.entity.LoginEntity
import com.example.learningapp.data.network.AuthApi
import com.example.learningapp.data.network.AuthException
import com.example.learningapp.data.network.AuthResponse
import com.example.learningapp.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.spec.PBEKeySpec
import kotlin.coroutines.cancellation.CancellationException

/** What the rest of the app knows about a logged-in user. */
data class Session(
    val email: String,
    val token: String,
    /** True when the API was unreachable and we signed in from locally saved credentials. */
    val isFromCache: Boolean = false,
)

interface LoginRepository {
    /** API first; falls back to saved credentials only if the API itself fails. */
    suspend fun login(email: String, password: String): Result<Session>

    /** The persisted session, if the user is still logged in (used for auto-login on app start). */
    suspend fun getSession(): Session?

    /** Ends the session. Saved credentials stay on-device so offline fallback still works. */
    suspend fun logout()
}

@Singleton
class LoginRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val dao: LoginDao,
    @DefaultDispatcher private val cpuDispatcher: CoroutineDispatcher,
) : LoginRepository {

    override suspend fun login(email: String, password: String): Result<Session> {
        val normalizedEmail = email.trim().lowercase()

        return try {
            // 1. Try the API
            val response = api.login(normalizedEmail, password)
            saveCredentials(response, password)
            Result.success(Session(response.email, response.token))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthException.InvalidCredentials) {
            // The server answered: wrong credentials. Never fall back to the cache here,
            // and drop any stale cached copy (e.g. the password was changed elsewhere).
            dao.delete(normalizedEmail)
            Result.failure(e)
        } catch (e: Exception) {
            // 2. API failed (offline / timeout / 5xx...): try saved credentials
            loginFromCache(normalizedEmail, password) ?: Result.failure(e)
        }
    }

    override suspend fun getSession(): Session? =
        dao.getActive()?.let { Session(it.email, it.token) }

    override suspend fun logout() = dao.deactivateAll()

    /* ---------- Local persistence ---------- */

    private suspend fun saveCredentials(response: AuthResponse, password: String) {
        val salt = PasswordHasher.newSalt()
        val hash = withContext(cpuDispatcher) { PasswordHasher.hash(password, salt) }
        dao.activate(
            LoginEntity(
                email = response.email,
                token = response.token,
                passwordHash = hash,
                salt = salt,
                isActive = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Returns null when there is nothing saved for this email or the password doesn't match. */
    private suspend fun loginFromCache(email: String, password: String): Result<Session>? {
        val saved = dao.getByEmail(email) ?: return null
        val matches = withContext(cpuDispatcher) {
            PasswordHasher.matches(password, saved.salt, saved.passwordHash)
        }
        if (!matches) return null

        dao.activate(saved)
        return Result.success(Session(saved.email, saved.token, isFromCache = true))
    }
}

/** Salted PBKDF2 so the raw password is never written to disk. (PBKDF2WithHmacSHA256 needs API 26+.) */
internal object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun matches(password: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(password, salt), expected) // constant-time compare
}