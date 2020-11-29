package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

object ExceptionHandlingFeature : Spek({
    Feature("If a subscriber throws an exception when invoked, the exception will be logged, and other subscribers won't be affected") {
        val normalSubscriberInvokedTimes by memoized { AtomicInteger(0) }
        val exceptionThrowingSubscriberInvokedTimes by memoized { AtomicInteger(0) }
        val counter by memoized { AtomicInteger(0) }

        fun resetCounters() {
            normalSubscriberInvokedTimes.set(0)
            exceptionThrowingSubscriberInvokedTimes.set(0)
            counter.set(0)
        }

        beforeEachScenario {
            KEvent.clear()
            resetCounters()
        }
        afterFeature { KEvent.clear() }

        Scenario("a subscriber throws an exception") {

            Given("multiple normal subscribers and one exception throwing subscriber") {
                for (i in 1..10) {
                    KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.BACKGROUND) {
                        counter.getAndIncrement()
                        if (i == 5) {
                            exceptionThrowingSubscriberInvokedTimes.getAndIncrement()
                            throw Exception("I am the trouble maker")
                        } else {
                            normalSubscriberInvokedTimes.getAndIncrement()
                        }
                    }
                }
            }

            fun assertSubscribersCalledCorrectly() {
                assertEquals(9, normalSubscriberInvokedTimes.get())
                assertEquals(1, exceptionThrowingSubscriberInvokedTimes.get())
            }

            When("an event is posted in dispatch mode CONCURRENT") {
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.CONCURRENT)
                waitForEventDispatch(10, counter)
            }

            Then("all subscribers will get invoked and you should see an error message logged to the console") {
                assertSubscribersCalledCorrectly()
            }

            And("it's all the same when the event is posted in dispatch mode ORDERED_CONCURRENT") {
                resetCounters()
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.ORDERED_CONCURRENT)
                waitForEventDispatch(10, counter)
                assertSubscribersCalledCorrectly()
            }

            And("it's all the same when the event is posted in dispatch mode SEQUENTIAL") {
                resetCounters()
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.SEQUENTIAL)
                waitForEventDispatch(10, counter)
                assertSubscribersCalledCorrectly()
            }

            And("it's all the same when the event is posted in dispatch mode INSTANTLY") {
                KEvent.clear()
                resetCounters()
                for (i in 1..10) {
                    KEvent.subscribe<Unit>(TestEventType.A, KEvent.ThreadMode.POSTING) {
                        if (i == 5) {
                            exceptionThrowingSubscriberInvokedTimes.getAndIncrement()
                            throw Exception("I am the trouble maker")
                        } else {
                            normalSubscriberInvokedTimes.getAndIncrement()
                        }
                    }
                }
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.INSTANTLY)
                assertSubscribersCalledCorrectly()
            }
        }
    }
})