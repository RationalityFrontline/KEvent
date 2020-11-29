package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

object EventCancellingFeature : Spek({
    Feature("You can cancel further event dispatching when the event is posted in dispatch mode INSTANTLY or SEQUENTIAL") {

        val counter by memoized { AtomicInteger(0) }

        beforeEachScenario { KEvent.clear() }
        afterFeature { KEvent.clear() }

        Scenario("cancelling event") {

            Given("some subscribers with different priorities") {
                counter.set(0)
                for (i in 1..10) {
                    KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.POSTING, priority = i) { event ->
                        if (i == 6) {
                            sleep(10)
                            KEvent.cancelEvent(event)
                        }
                        counter.getAndIncrement()
                    }
                }
            }

            When("an event is posted in dispatch mode INSTANTLY") {
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.INSTANTLY)
            }

            Then("the event won't be further dispatched after it get cancelled") {
                assertEquals(5, counter.get())
            }

            And("it's all the same when the event is posted in dispatch mode SEQUENTIAL") {
                counter.set(0)
                KEvent.clear()
                for (i in 1..10) {
                    KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.BACKGROUND, priority = i) { event ->
                        if (i == 6) {
                            sleep(10)
                            KEvent.cancelEvent(event)
                        }
                        counter.getAndIncrement()
                    }
                }
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.SEQUENTIAL)
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(10, counter, timeouts = 30)
                }
                assertEquals(5, counter.get())
            }

            And("it doesn't work with event dispatch mode CONCURRENT or ORDERED_CONCURRENT") {
                counter.set(0)
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.CONCURRENT)
                sleep(30)
                assertNotEquals(5, counter.get())
                counter.set(0)
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.ORDERED_CONCURRENT)
                sleep(30)
                assertNotEquals(5, counter.get())
            }
        }
    }
})