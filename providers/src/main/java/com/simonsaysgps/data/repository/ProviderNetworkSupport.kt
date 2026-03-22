package com.simonsaysgps.data.repository

import com.simonsaysgps.domain.model.Coordinate
import com.simonsaysgps.domain.model.NetworkFailure
import com.simonsaysgps.domain.model.NetworkFailureType
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.roundToInt
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.HttpException

internal object NetworkFailureClassifier {
    fun classify(error: Throwable): NetworkFailure = when (error) {
        is UnknownHostException, is ConnectException -> NetworkFailure(NetworkFailureType.NO_NETWORK, error.message)
        is SocketTimeoutException, is InterruptedIOException -> NetworkFailure(NetworkFailureType.TIMEOUT, error.message)
        is HttpException -> NetworkFailure(NetworkFailureType.SERVER, "HTTP ${error.code()}")
        is IllegalStateException, is IllegalArgumentException -> NetworkFailure(NetworkFailureType.SERVER, error.message)
        else -> NetworkFailure(NetworkFailureType.UNKNOWN, error.message)
    }
}

class RetryOnFailureInterceptor(
    private val maxAttempts: Int = 3,
    private val baseDelayMillis: Long = 200L
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < maxAttempts) {
            attempt += 1
            try {
                val response = chain.proceed(request)
                if (!shouldRetry(request, response, attempt)) {
                    return response
                }
                response.close()
            } catch (error: Exception) {
                if (!request.isGet() || attempt >= maxAttempts || !isTransient(error)) {
                    throw error
                }
                lastError = error
            }
            Thread.sleep(baseDelayMillis * attempt)
        }
        throw lastError ?: IllegalStateException("Request retry exhausted")
    }

    private fun shouldRetry(request: Request, response: Response, attempt: Int): Boolean {
        return request.isGet() && response.code in 500..599 && attempt < maxAttempts
    }

    private fun isTransient(error: Exception): Boolean {
        return error is UnknownHostException || error is ConnectException || error is SocketTimeoutException || error is InterruptedIOException
    }

    private fun Request.isGet(): Boolean = method.equals("GET", ignoreCase = true)
}

internal object RouteCacheKeyFactory {
    fun fromCoordinate(coordinate: Coordinate): String {
        return listOf(coordinate.latitude.roundedTo4(), coordinate.longitude.roundedTo4()).joinToString(",")
    }

    private fun Double.roundedTo4(): String = ((this * 10_000.0).roundToInt() / 10_000.0).toString()
}
