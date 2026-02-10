package dev.brahmkshatriya.echo.extension.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Interceptor that enforces a rate limit on requests.
 *
 * @param maxRequests Maximum number of requests allowed in the time window
 * @param timeWindowMs Time window in milliseconds
 */
class RateLimitInterceptor(
    private val maxRequests: Int,
    private val timeWindowMs: Long
) : Interceptor {

    private val requestTimestamps = ConcurrentLinkedQueue<Long>()

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(this) {
            val now = System.currentTimeMillis()

            while (requestTimestamps.isNotEmpty() &&
                now - requestTimestamps.peek() >= timeWindowMs) {
                requestTimestamps.poll()
            }

            if (requestTimestamps.size >= maxRequests) {
                val oldestTimestamp = requestTimestamps.peek()
                val waitTime = timeWindowMs - (now - oldestTimestamp)

                if (waitTime > 0) {
                    Thread.sleep(waitTime)

                    requestTimestamps.poll()
                }
            }

            requestTimestamps.offer(System.currentTimeMillis())
        }

        return chain.proceed(chain.request())
    }
}