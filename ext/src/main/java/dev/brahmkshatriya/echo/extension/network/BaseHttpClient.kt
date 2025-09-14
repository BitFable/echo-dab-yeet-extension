package dev.brahmkshatriya.echo.extension.network

import dev.brahmkshatriya.echo.extension.models.ErrorResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

abstract class BaseHttpClient(@PublishedApi internal val client: OkHttpClient) {

    protected abstract val baseUrl: String

    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @PublishedApi
    internal val jsonMediaType = "application/json".toMediaType()

    /**
     * Performs a GET request.
     */
    protected suspend inline fun <reified T> get(
        endpoint: String,
        params: Map<String, Any> = emptyMap(),
        sessionCookie: String? = null
    ): T {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder().addPathSegments(endpoint)
        params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value.toString()) }
        val requestBuilder = Request.Builder().url(urlBuilder.build()).get()
        sessionCookie?.let { requestBuilder.header("Cookie", it) }
        val request = requestBuilder.build()
        val response = execute(request)
        val responseBody = response.body.string()
        return json.decodeFromString(responseBody)
    }

    /**
     * Performs a POST request with a JSON body.
     */
    protected suspend fun post(
        endpoint: String,
        jsonBody: String,
        sessionCookie: String? = null
    ): Response {
        val requestBody = jsonBody.toRequestBody(jsonMediaType)
        val requestBuilder = Request.Builder().url(baseUrl.toHttpUrl().newBuilder().addPathSegments(endpoint).build()).post(requestBody)
        sessionCookie?.let { requestBuilder.header("Cookie", it) }
        val request = requestBuilder.build()
        return execute(request)
    }

    /**
     * Performs a PATCH request with a JSON body.
     */
    protected suspend inline fun <reified T> patch(
        endpoint: String,
        jsonBody: String,
        sessionCookie: String? = null
    ): T {
        val requestBody = jsonBody.toRequestBody(jsonMediaType)
        val requestBuilder = Request.Builder().url(baseUrl.toHttpUrl().newBuilder().addPathSegments(endpoint).build()).patch(requestBody)
        sessionCookie?.let { requestBuilder.header("Cookie", it) }
        val request = requestBuilder.build()
        val response = execute(request)
        val responseBody = response.body.string()
        return json.decodeFromString(responseBody)
    }

    /**
     * Performs a DELETE request.
     */
    protected suspend inline fun <reified T> delete(
        endpoint: String,
        params: Map<String, Any> = emptyMap(),
        sessionCookie: String? = null
    ): T {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder().addPathSegments(endpoint)
        params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value.toString()) }
        val requestBuilder = Request.Builder().url(urlBuilder.build()).delete()
        sessionCookie?.let { requestBuilder.header("Cookie", it) }
        val request = requestBuilder.build()
        val response = execute(request)
        val responseBody = response.body.string()
        return json.decodeFromString(responseBody)
    }

    /**
     * Executes the request and returns the Response object.
     */
    @PublishedApi
    internal suspend fun execute(request: Request): Response {
        val response = client.newCall(request).await()
        if (!response.isSuccessful) {
            val errorResponse = json.decodeFromString<ErrorResponse>(response.body.string())
            throw Exception(
                "Error ${response.code}: ${errorResponse.error ?:  errorResponse.message ?: "Unknown error"}"
            )
        }
        return response
    }

    /**
     * Awaits the response of a call in a suspending manner.
     */
    @PublishedApi
    internal suspend fun Call.await(): Response {
        return suspendCancellableCoroutine { continuation ->
            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            })
            continuation.invokeOnCancellation {
                cancel()
            }
        }
    }

    /**
     * Helper extension function to serialize an object to a JSON string.
     */
    @PublishedApi
    internal inline fun <reified T> T.toJsonString(): String {
        return json.encodeToString(this)
    }
}
