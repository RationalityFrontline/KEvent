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
        val existingSubscriber: EventConsumer<Unit> = { event ->
            try {
                existingSubscriberCalled.set(true)
                assertFalse { event.isSticky }
                assertTrue { event.isPostedSticky(KEVENT) }
            } catch (e: AssertionError) {
                subscriberAssertionFailed.set(true)
            } finally {
                counter.getAndIncrement()
            }
        }

        fun resetCounters() {
            existingSubscriberCalled.set(false)
            laterAddedSubscriberCalled.set(false)
            subscriberAssertionFailed.set(false)
            counter.set(0)
        }

        beforeEachScenario {
            KEVENT.clear()
            resetCounters()
        }
        beforeEachTest { counter.set(0) }
        afterFeature { KEVENT.clear() }

        Scenario("basic sticky event usage") {

            Given("an existing subscriber") {
                KEVENT.subscribe(TestEventType.A, existingSubscriber, SubscriberThreadMode.BACKGROUND)
            }

            When("a sticky event is posted") {
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.CONCURRENT, isSticky = true)
                waitForEventDispatch(1, counter)
            }

            Then("existing subscribers are called normally (with event object whose isSticky is false and isPostedSticky is true)") {
                assertTrue { existingSubscriberCalled.get() }
                assertFalse { subscriberAssertionFailed.get() }
                assertFalse { laterAddedSubscriberCalled.get() }
            }

            Then("later added subscribers are called on their subscription (with event object whose isSticky is true and isPostedSticky is true)") {
                KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.BACKGROUND) { event ->
                    try {
                        laterAddedSubscriberCalled.set(true)
                        assertTrue { event.isSticky }
                        assertTrue { event.isPostedSticky(KEVENT) }
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
                KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.BACKGROUND) { event ->
                    try {
                        assertTrue { event.isSticky }
                        assertTrue { event.isPostedSticky(KEVENT) }
                        assertTrue { KEVENT.containsStickyEvent(event) }
                        assertTrue { KEVENT.removeStickyEvent(event) }
                        assertFalse { KEVENT.removeStickyEvent(event) }
                        assertFalse { KEVENT.containsStickyEvent(event) }
                        assertTrue { event.isSticky }
                        assertFalse { event.isPostedSticky(KEVENT) }
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
                KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.BACKGROUND) {
                    counter.getAndIncrement()
                }
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(1, counter, 30)
                }
            }
        }

        Scenario("sticky event can't be used together with event dispatch mode POSTING") {

            Given("an existing subscriber") {
                KEVENT.subscribe(TestEventType.A, existingSubscriber, SubscriberThreadMode.POSTING)
            }

            When("a sticky event is posted with dispatch mode POSTING") {
                assertFalse { KEVENT.post(TestEventType.A, Unit, EventDispatchMode.POSTING, isSticky = true) }
            }

            Then("the event won't get dispatched to any subscribers") {
                assertFalse { existingSubscriberCalled.get() }
                KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.BACKGROUND) {
                    counter.getAndIncrement()
                }
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(1, counter, 30)
                }
            }
        }

        Scenario("sticky events are dispatched to later added subscribers concurrently and asynchronously") {

            When("a sticky event is posted") {
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.CONCURRENT, isSticky = true)
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
                        KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.BACKGROUND) {
                            if (currentOrder.get() > i) {
                                unordered.set(true)
                            }
                            currentOrder.set(i)
                            sleep(1)
                            counter.getAndIncrement()
                        }
                    }
                }
                assertTrue { dispatchTime < 200 }
                val finishingTime = waitForEventDispatch(1000, counter)
                assertTrue { unordered.get() }
                assertTrue { finishingTime < 800 }
            }
        }
    }
})