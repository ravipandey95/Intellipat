package com.example.learningapp.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit service. Adjust paths and DTO fields to match real backend contract.
 */
interface AuthService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
)

/**
 * Response fields are nullable on purpose: Gson ignores Kotlin's non-null types and
 * would silently put null into a `String` if the server omits the field.
 * RemoteAuthApi validates them before they reach the rest of the app.
 * (@SerializedName also keeps the names intact when R8 minifies the app.)
 */
data class LoginResponse(
    @SerializedName("email") val email: String?,
    @SerializedName("token") val token: String?,
)