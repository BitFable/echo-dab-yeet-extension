package dev.brahmkshatriya.echo.extension.network

import dev.brahmkshatriya.echo.extension.models.ErrorResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

abstract class BaseHttpClient(@PublishedApi internal val client: OkHttpClient, @PublishedApi internal val json: Json) {
    @PublishedApi
    internal val jsonMediaType = "application/json".toMediaType()

    /**
     * Performs a GET request.
     */
    protected suspend inline fun <reified T> get(
        url: String,
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null
    ): T {
        val request = buildRequest(url, params, headers).get().build()
        val response = execute(request)
        val responseBody = response.body.string()
        return json.decodeFromString(responseBody)
    }

    /**
     * Performs a POST request with a JSON body. Directly returns the response.
     */
    protected suspend inline fun postResponse(
        url: String,
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null,
        jsonBody: String,
    ): Response {
        val request = buildRequest(url, params, headers).post(jsonBody.toRequestBody(jsonMediaType)).build()
        return execute(request)
    }

    /**
     * Performs a POST request with a JSON body.
     */
    protected suspend inline fun <reified T> post(
        url: String,
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null,
        jsonBody: String,
    ): T {
        val request = buildRequest(url, params, headers).post(jsonBody.toRequestBody(jsonMediaType)).build()
        val response = execute(request)
        val responseBody = response.body.string()
        return json.decodeFromString(responseBody)
    }

    /**
     * Performs a PATCH request with a JSON body.
     */
    protected suspend inline fun <reified T> patch(
        url: String,
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null,
        jsonBody: String,
    ): T {
        val request = buildRequest(url, params, headers).patch(jsonBody.toRequestBody(jsonMediaType)).build()
        val response = execute(request)
        val responseBody = response.body.string()
        return json.decodeFromString(responseBody)
    }

    /**
     * Performs a PUT request with a JSON body.
     */
    protected suspend inline fun <reified T> put(
        url: String,
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null,
        jsonBody: String,
    ): T {
        val request =
            buildRequest(url, params, headers).put(jsonBody.toRequestBody(jsonMediaType)).build()
        val response = execute(request)
        val responseBody = response.body.string()
        return json.decodeFromString(responseBody)
    }

    /**
     * Performs a DELETE request.
     */
    protected suspend inline fun <reified T> delete(
        url: String,
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null,
        jsonBody: String? = null,
    ): T {
        val request = buildRequest(url, params, headers).delete(jsonBody?.toRequestBody(jsonMediaType)).build()
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
                "Error ${response.code}: ${errorResponse.message ?:  errorResponse.error ?: "Unknown error"}"
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
     * Builds an [okhttp3.Request.Builder] from the given parameters without specifying an HTTP method.
     *
     * - Appends [params] as query parameters if provided and non-null.
     * - Adds [headers] as request headers if provided and non-null.
     * - Skips entries with null or empty values.
     *
     * @param url The absolute or relative URL for the request.
     * @param params Optional map of query parameters to be appended to the URL.
     * @param headers Optional map of request headers to be added to the request.
     *
     * @return A [Request.Builder] with the URL and headers set. The caller is responsible
     *         for setting the HTTP method and body before building the final [Request].
     *
     * @throws IllegalArgumentException If the provided [url] is invalid or cannot be parsed.
     */
    @PublishedApi
    internal fun buildRequest(
        url: String,
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null
    ): Request.Builder {
        val httpUrl = url.let {
            val builder = url.toHttpUrlOrNull()?.newBuilder() ?: throw IllegalArgumentException("Invalid URL: $it")
            params?.forEach { (key, value) ->
                if (value.isNotEmpty()) builder.addQueryParameter(key, value)
            }
            builder.build()
        }


        return Request.Builder().url(httpUrl).apply {
            headers?.forEach { (key, value) ->
                if (value.isNotEmpty()) addHeader(key, value)
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