package com.example.learningapp.data.network

import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/* ---------- Errors ---------- */

sealed class AuthException(message: String) : Exception(message) {
    class InvalidCredentials : AuthException("Invalid email or password")
    class Network : AuthException("Network error")
    class Server(val code: Int) : AuthException("Server error ($code)")
}

/* ---------- Remote API contract ---------- */

data class AuthResponse(val email: String, val token: String)

interface AuthApi {
    /** @throws AuthException.InvalidCredentials, AuthException.Network */
    suspend fun login(email: String, password: String): AuthResponse
}

/**
 * Fake backend.
 *  - test@example.com / password123 -> success (unless [isOffline] is true)
 *  - network@example.com            -> always a network failure
 *  - anything else                  -> invalid credentials
 *
 * Flip [isOffline] to demo the "API down -> fall back to saved credentials" path:
 * log in once while online, log out, set isOffline = true, then log in again.
 */
@Singleton
class FakeAuthApi @Inject constructor(val networkChecker: NetworkChecker) : AuthApi {


    override suspend fun login(email: String, password: String): AuthResponse {
        delay(1500) // simulate latency
        if (!networkChecker.isOnline()) throw IOException("No internet connection")

        if (email == "test@example.com" && password == "password123") {
            return AuthResponse(email = email, token = "fake-jwt-${System.currentTimeMillis()}")
        }
        throw AuthException.InvalidCredentials()
    }
}