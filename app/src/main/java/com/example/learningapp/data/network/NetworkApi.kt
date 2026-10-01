package com.example.learningapp.data.network

import com.example.learningapp.utils.ApiException
import com.example.learningapp.utils.apiCall
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [AuthApi] backed by Retrofit. Translates transport errors into the
 * auth errors the repository understands:
 *  - 400/401/403 -> InvalidCredentials (repository will NOT fall back to the cache)
 *  - timeouts / no connection -> Network   (repository falls back to saved credentials)
 *  - other HTTP errors (5xx...) -> Server  (repository falls back to saved credentials)
 */
@Singleton
class RemoteAuthApi @Inject constructor(
    private val service: AuthService,
) : AuthApi {

    override suspend fun login(email: String, password: String): AuthResponse =
        try {
            val dto = apiCall { service.login(LoginRequest(email, password)) }
            // Gson doesn't enforce Kotlin nullability, so validate the payload here.
            val token = dto.token?.takeIf { it.isNotBlank() }
                ?: throw ApiException.Unknown(IllegalStateException("Login response missing token"))
            AuthResponse(email = dto.email ?: email, token = token)
        } catch (e: ApiException.Http) {
            throw if (e.code in INVALID_CREDENTIAL_CODES) {
                AuthException.InvalidCredentials()
            } else {
                AuthException.Server(e.code)
            }
        } catch (e: ApiException.Network) {
            throw AuthException.Network()
        }
    // ApiException.Unknown (e.g. bad JSON) propagates; the repository treats it as an API failure.

    private companion object {
        val INVALID_CREDENTIAL_CODES = setOf(400, 401, 403)
    }
}