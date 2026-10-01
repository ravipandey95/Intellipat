package com.example.learningapp.utils

import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Transport-level errors, independent of any feature.
 * Each repository maps these to its own domain errors (see RemoteAuthApi).
 */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connectivity, DNS failure, timeout, connection reset... */
    class Network(cause: Throwable) : ApiException("Network error", cause)

    /** Server answered with a non-2xx status. */
    class Http(val code: Int, detail: String?) : ApiException("HTTP $code ${detail.orEmpty()}".trim())

    /** Anything else (e.g. malformed JSON). */
    class Unknown(cause: Throwable) : ApiException("Unexpected error", cause)
}

/**
 * Wrap every Retrofit call with this so callers only deal with [ApiException].
 * Coroutine cancellation is always re-thrown untouched.
 */
suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw ApiException.Http(e.code(), e.message())
    } catch (e: IOException) {
        throw ApiException.Network(e)
    } catch (e: Exception) {
        throw ApiException.Unknown(e)
    }