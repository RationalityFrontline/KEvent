package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

object EventCancellingFeature : Spek({
    Feature("You can cancel an event") {

        val counter by memoized { AtomicInteger(0) }

        beforeEachScenario { KEVENT.clear() }
        afterFeature { KEVENT.clear() }

        Scenario("cancelling event") {

            Given("some subscribers with different priorities") {
                counter.set(0)
                for (i in 1..10) {
                    KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.POSTING, priority = i) { event ->
                        if (i == 6) {
                            KEVENT.cancelEvent(event)
                        }
                        counter.getAndIncrement()
                    }
                }
            }

            When("an event is posted in dispatch mode POSTING") {
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.POSTING)
            }

            Then("the event won't be further dispatched after it get cancelled") {
                assertEquals(5, counter.get())
            }

            And("it's all the same when the event is posted in dispatch mode SEQUENTIAL") {
                counter.set(0)
                KEVENT.clear()
                for (i in 1..100) {
                    KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.BACKGROUND, priority = i) { event ->
                        if (i == 51) {
                            KEVENT.cancelEvent(event)
                        }
                        counter.getAndIncrement()
                    }
                }
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.SEQUENTIAL)
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(100, counter, timeouts = 30)
                }
                assertEquals(50, counter.get())
            }

            And("it doesn't work with event dispatch mode CONCURRENT") {
                counter.set(0)
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.CONCURRENT)
                sleep(30)
                assertNotEquals(50, counter.get())
            }

            And("it works with event dispatch mode ORDERED_CONCURRENT when cancellation is called instantly on subscriber invocation") {
                counter.set(0)
                KEVENT.clear()
                for (i in 1..100) {
                    KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.BACKGROUND, priority = i) { event ->
                        if (i == 51) {
                            KEVENT.cancelEvent(event)
                        }
                        sleep(10)
                        counter.getAndIncrement()
                    }
                }
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.ORDERED_CONCURRENT)
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(100, counter, timeouts = 1500)
                }
                assertEquals(50, counter.get())
            }

            And("it doesn't work with event dispatch mode ORDERED_CONCURRENT when cancellation is not called instantly on subscriber invocation") {
                counter.set(0)
                KEVENT.clear()
                for (i in 1..100) {
                    KEVENT.subscribe<Unit>(TestEventType.A, SubscriberThreadMode.BACKGROUND, priority = i) { event ->
                        sleep(10)
                        if (i == 51) {
                            KEVENT.cancelEvent(event)
                        }
                        counter.getAndIncrement()
                    }
                }
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.ORDERED_CONCURRENT)
                sleep(1500)
                assertNotEquals(50, counter.get())
            }
        }
    }
})