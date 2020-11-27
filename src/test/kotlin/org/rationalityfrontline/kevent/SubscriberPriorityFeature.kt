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

        beforeEachScenario {
            KEvent.clear()
        }

        beforeEachTest {
            counter.set(0)
        }

        afterFeature { KEvent.clear() }

        Scenario("priority works with all event dispatch mode except for CONCURRENT") {

            Given("some subscribers with different priorities") {
                for (i in range) {
                    KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.BACKGROUND, priority = i) {
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
                KEvent.post(Event(TestEventType.A, Unit, KEvent.DispatchMode.CONCURRENT))
                waitForEventDispatch(range.count(), counter)
            }

            Then("subscribers are be called unordered") {
                assertTrue { subscriberAssertionFailed.get() }
            }

            When("an event is posted in dispatch mode ORDERED_CONCURRENT") {
                resetCounters()
                KEvent.post(Event(TestEventType.A, Unit, KEvent.DispatchMode.ORDERED_CONCURRENT))
                waitForEventDispatch(range.count(), counter)
            }

            Then("subscribers are be called ordered") {
                assertFalse { subscriberAssertionFailed.get() }
            }

            And("it's all the same when the event is posted in dispatch mode SEQUENTIAL") {
                resetCounters()
                KEvent.post(Event(TestEventType.A, Unit, KEvent.DispatchMode.SEQUENTIAL))
                waitForEventDispatch(range.count(), counter)
                assertFalse { subscriberAssertionFailed.get() }
            }

            And("it's all the same when the event is posted in dispatch mode INSTANTLY") {
                resetCounters()
                KEvent.clear()
                for (i in range) {
                    KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.POSTING, priority = i) {
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
                KEvent.post(Event(TestEventType.A, Unit, KEvent.DispatchMode.INSTANTLY))
                assertFalse { subscriberAssertionFailed.get() }
            }
        }
    }
})