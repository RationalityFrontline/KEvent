package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object StickyEventFeature : Spek({
    Feature("Events can be sticky") {
        val existingSubscriberCalled by memoized { AtomicBoolean(false) }
        val laterAddedSubscriberCalled by memoized { AtomicBoolean(false) }
        val subscriberAssertionFailed by memoized { AtomicBoolean(false) }
        val counter by memoized { AtomicInteger(0) }

        fun resetCounters() {
            existingSubscriberCalled.set(false)
            laterAddedSubscriberCalled.set(false)
            subscriberAssertionFailed.set(false)
            counter.set(0)
        }

        val existingSubscriber: EventConsumer<Unit> = { event ->
            try {
                existingSubscriberCalled.set(true)
                assertFalse { event.isSticky }
                assertTrue { event.isPostedSticky }
            } catch (e: AssertionError) {
                subscriberAssertionFailed.set(true)
            } finally {
                counter.getAndIncrement()
            }
        }

        beforeEachScenario {
            KEvent.clear()
            resetCounters()
        }

        beforeEachTest {
            counter.set(0)
        }

        Scenario("basic sticky event usage") {

            Given("an existing subscriber") {
                KEvent.subscribe(TestEventType.A, existingSubscriber, KEvent.ThreadMode.BACKGROUND)
            }

            When("a sticky event is posted") {
                KEvent.post(Event(TestEventType.A, Unit, KEvent.DispatchMode.CONCURRENT, isSticky = true))
                waitForEventDispatch(1, counter)
            }

            Then("existing subscribers are called normally (with event object whose isSticky is false and isPostedSticky is true)") {
                assertTrue { existingSubscriberCalled.get() }
                assertFalse { subscriberAssertionFailed.get() }
                assertFalse { laterAddedSubscriberCalled.get() }
            }

            Then("later added subscribers are called on their subscription (with event object whose isSticky is true and isPostedSticky is true)") {
                KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.BACKGROUND) { event ->
                    try {
                        laterAddedSubscriberCalled.set(true)
                        assertTrue { event.isSticky }
                        assertTrue { event.isPostedSticky }
                    } catch (e: AssertionError) {
                        subscriberAssertionFailed.set(true)
                    } finally {
                        counter.getAndIncrement()
                    }
                }
                waitForEventDispatch(1, counter)
                assertTrue { laterAddedSubscriberCalled.get() }
                assertFalse { subscriberAssertionFailed.get() }
            }

            Then("sticky event can be removed (only once)") {
                KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.BACKGROUND) { event ->
                    try {
                        assertTrue { event.isSticky }
                        assertTrue { event.isPostedSticky }
                        assertTrue { KEvent.containsStickyEvent(event) }
                        assertTrue { KEvent.removeStickyEvent(event) }
                        assertFalse { KEvent.removeStickyEvent(event) }
                        assertFalse { KEvent.containsStickyEvent(event) }
                        assertTrue { event.isSticky }
                        assertFalse { event.isPostedSticky }
                    } catch (e: AssertionError) {
                        subscriberAssertionFailed.set(true)
                    } finally {
                        counter.getAndIncrement()
                    }
                }
                waitForEventDispatch(1, counter)
                assertFalse { subscriberAssertionFailed.get() }
            }

            Then("later added subscriber won't receive the removed sticky event") {
                KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.BACKGROUND) {
                    counter.getAndIncrement()
                }
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(1, counter, 30)
                }
            }
        }

        Scenario("sticky event can't be used together with event dispatch mode INSTANTLY") {

            Given("an existing subscriber") {
                KEvent.subscribe(TestEventType.A, existingSubscriber, KEvent.ThreadMode.POSTING)
            }

            When("a sticky event is posted with dispatch mode INSTANTLY") {
                assertFalse {
                    KEvent.post(
                        Event(
                            TestEventType.A,
                            Unit,
                            KEvent.DispatchMode.INSTANTLY,
                            isSticky = true
                        )
                    )
                }
            }

            Then("the event won't get dispatched to any subscribers") {
                assertFalse { existingSubscriberCalled.get() }
                KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.BACKGROUND) {
                    counter.getAndIncrement()
                }
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(1, counter, 30)
                }
            }
        }

        Scenario("sticky events are dispatched to later added subscribers concurrently and asynchronously") {

            When("a sticky event is posted") {
                KEvent.post(Event(TestEventType.A, Unit, KEvent.DispatchMode.CONCURRENT, isSticky = true))
            }

            Then(
                "if multiple subscribers are added at the same time, " +
                        "they are called concurrently and asynchronously, " +
                        "their execution orders are non-deterministic"
            ) {
                val unordered = AtomicBoolean(false)
                val currentOrder = AtomicInteger(0)
                val dispatchTime = measureTimeMillis {
                    for (i in 1..1000) {
                        KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.BACKGROUND) {
                            if (currentOrder.get() > i) {
                                unordered.set(true)
                            }
                            currentOrder.set(i)
                            sleep(1)
                            counter.getAndIncrement()
                        }
                    }
                }
                performanceLogger.info { "1000 sticky subscriber added in $dispatchTime milliseconds" }
                assertTrue { dispatchTime < 200 }
                val finishingTime = waitForEventDispatch(1000, counter)
                assertTrue { unordered.get() }
                assertTrue { finishingTime < 800 }
            }
        }
    }
})