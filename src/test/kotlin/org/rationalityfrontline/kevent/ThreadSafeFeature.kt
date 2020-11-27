package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object ThreadSafeFeature : Spek({
    Feature("KEvent is thread safe") {
        val dispatching by memoized { AtomicBoolean(false) }
        val laterAddedSubscriberCounter by memoized { AtomicInteger(0) }
        val counter by memoized { AtomicInteger(0) }

        beforeEachScenario {
            KEvent.clear()
        }

        afterFeature { KEvent.clear() }

        Scenario("adding a new subscription while dispatching event of the same event type") {

            Given("some time consuming subscribers") {
                counter.set(0)
                for (i in 1..5) {
                    KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.BACKGROUND) {
                        if (i == 1) dispatching.set(true)
                        sleep(10)
                        if (i == 5) dispatching.set(false)
                        counter.getAndIncrement()
                    }
                }
            }

            When("an event is posted in dispatch mode SEQUENTIAL") {
                KEvent.post(Event(TestEventType.A, Unit, KEvent.DispatchMode.SEQUENTIAL))
                sleep(5)
            }

            Then("adding a new subscription on the same event type should be fine (won't throw ConcurrentModificationException)") {
                assertTrue { dispatching.get() }
                KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.BACKGROUND) {
                    try {
                        throw IllegalStateException("This subscriber should not be called")
                    } finally {
                        laterAddedSubscriberCounter.getAndIncrement()
                    }
                }
                assertTrue { dispatching.get() }
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(1, laterAddedSubscriberCounter, 10)
                }
                waitForEventDispatch(5, counter)
                assertFalse { dispatching.get() }
            }
        }
    }
})