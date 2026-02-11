package dev.brahmkshatriya.echo.extension.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight token bucket rate limiter with request pooling.
 *
 * Uses a semaphore-based approach for efficient thread management without busy-waiting.
 * Permits are refilled over time, allowing burst requests up to maxBurst while
 * maintaining the average rate limit.
 *
 * @param permitsPerSecond Number of requests allowed per second
 * @param maxBurst Maximum pooled permits (allows bursting during idle periods)
 * @param timeoutSeconds Maximum time to wait for a permit before failing
 */
class RateLimitInterceptor(
    private val permitsPerSecond: Double = 5.0,
    private val maxBurst: Int = permitsPerSecond.toInt().coerceAtLeast(1),
    private val timeoutSeconds: Long = 30L
) : Interceptor {

    private val semaphore = Semaphore(maxBurst, true)
    private val lastRefillTime = AtomicLong(System.nanoTime())
    private val refillIntervalNanos = (1_000_000_000.0 / permitsPerSecond).toLong()

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        acquirePermit()
        return chain.proceed(chain.request())
    }

    private fun acquirePermit() {
        refillPermits()

        if (!semaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
            throw IOException("Rate limit timeout: could not acquire permit within ${timeoutSeconds}s")
        }
    }

    private fun refillPermits() {
        val now = System.nanoTime()
        var last = lastRefillTime.get()
        var elapsed = now - last

        while (elapsed >= refillIntervalNanos) {
            val permitsToAdd = (elapsed / refillIntervalNanos).toInt()
            if (permitsToAdd <= 0) break

            if (lastRefillTime.compareAndSet(last, now)) {
                val currentAvailable = semaphore.availablePermits()
                val spaceAvailable = maxBurst - currentAvailable
                val actualPermits = permitsToAdd.coerceAtMost(spaceAvailable)

                if (actualPermits > 0) {
                    semaphore.release(actualPermits)
                }
                break
            }

            last = lastRefillTime.get()
            elapsed = now - last
        }
    }
}