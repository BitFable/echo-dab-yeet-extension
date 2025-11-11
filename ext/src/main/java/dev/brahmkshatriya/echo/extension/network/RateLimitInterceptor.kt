package dev.brahmkshatriya.echo.extension.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.LinkedList
import java.util.concurrent.TimeUnit

/**
 * An OkHttp interceptor that enforces a rate limit on outgoing requests.
 *
 * This implementation uses a sliding window algorithm based on request timestamps.
 * It is thread-safe and suitable for use in a shared OkHttpClient instance.
 *
 * @param permits The maximum number of requests allowed within the specified time period.
 * @param period The duration of the time period.
 * @param unit The time unit for the period.
 */
class RateLimitInterceptor(
    private val permits: Int,
    private val period: Long,
    private val unit: TimeUnit
) : Interceptor {

    private val requestTimestamps = LinkedList<Long>()
    private val timePeriodMillis = unit.toMillis(period)

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(requestTimestamps) {
            val now = System.currentTimeMillis()

            // Remove old timestamps that are outside the current sliding window
            while (requestTimestamps.isNotEmpty() && now - requestTimestamps.peek() > timePeriodMillis) {
                requestTimestamps.removeFirst()
            }

            // If we have reached the permit limit, calculate wait time and sleep
            if (requestTimestamps.size >= permits) {
                val oldestTimestamp = requestTimestamps.peek()
                val waitTime = timePeriodMillis - (now - oldestTimestamp)
                if (waitTime > 0) {
                    try {
                        Thread.sleep(waitTime)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("Thread interrupted while waiting for rate limit", e)
                    }
                }
            }

            // Add the timestamp for the current request
            requestTimestamps.addLast(System.currentTimeMillis())
        }

        return chain.proceed(chain.request())
    }
}