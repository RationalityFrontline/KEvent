package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object SubscriberPriorityFeature : Spek({
    Feature("Subscribers can define their priorities") {

        val currentPriority by memoized { AtomicInteger(Int.MAX_VALUE) }
        val subscriberAssertionFailed by memoized { AtomicBoolean(false) }
        val counter by memoized { AtomicInteger(0) }
        val range = 1..1000

        fun resetCounters() {
            currentPriority.set(Int.MAX_VALUE)
            subscriberAssertionFailed.set(false)
            counter.set(0)
        }

        beforeEachScenario { KEVENT.clear() }
        beforeEachTest { counter.set(0) }
        afterFeature { KEVENT.clear() }

        Scenario("priority works with all event dispatch mode except for CONCURRENT") {

            Given("some subscribers with different priorities") {
                for (i in range) {
                    KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.BACKGROUND, priority = i) {
                        try {
                            assertTrue { currentPriority.get() > i }
                            currentPriority.set(i)
                        } catch (e: AssertionError) {
                            subscriberAssertionFailed.set(true)
                        } finally {
                            counter.getAndIncrement()
                        }
                    }
                }
            }

            When("an event is posted in dispatch mode CONCURRENT") {
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.CONCURRENT)
                waitForEventDispatch(range.count(), counter)
            }

            Then("subscribers are be called unordered") {
                assertTrue { subscriberAssertionFailed.get() }
            }

            And("it's all the same when the event is posted in dispatch mode SEQUENTIAL") {
                resetCounters()
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.SEQUENTIAL)
                waitForEventDispatch(range.count(), counter)
                assertFalse { subscriberAssertionFailed.get() }
            }

            And("it's all the same when the event is posted in dispatch mode POSTING") {
                resetCounters()
                KEVENT.clear()
                for (i in range) {
                    KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.POSTING, priority = i) {
                        try {
                            assertTrue { currentPriority.get() > i }
                            currentPriority.set(i)
                        } catch (e: AssertionError) {
                            subscriberAssertionFailed.set(true)
                        } finally {
                            counter.getAndIncrement()
                        }
                    }
                }
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.POSTING)
                assertFalse { subscriberAssertionFailed.get() }
            }
        }
    }
})