@file:Suppress("NOTHING_TO_INLINE")

package org.rationalityfrontline.kevent

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureNanoTime

inline fun sleep(millis: Long) = Thread.sleep(millis)

/**
 * Wait for all subscribers to finish.
 * @param count subscriber count.
 * @param counter counter.
 * @param timeouts in milliseconds, default to 30,000.
 * @return waited time in milliseconds.
 */
inline fun waitForEventDispatch(count: Int, counter: AtomicInteger, timeouts: Int = 30_000): Float {
    return measureNanoTime {
        var time = 0
        while (counter.get() < count) {
            sleep(1)
            time += 1
            if (time >= timeouts) {
                throw TimeoutException("Subscribers($count in total) didn't finish after waiting for $time milliseconds")
            }
        }
    } / 1_000_000f
}