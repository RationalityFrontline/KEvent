package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val counter = AtomicInteger(0)

private fun onStringEvent(event: Event<String>) {
    counter.getAndIncrement()
}

private class Temp {
    fun onStringEvent(event: Event<String>) {
        counter.getAndIncrement()
    }
}

object BasicFeature : Spek({
    Feature("Basic subscription, unsubscription and posting events") {

        beforeEachScenario { KEVENT.clear() }
        afterFeature { KEVENT.clear() }

        Scenario("basic usage") {

            fun postEvents() {
                counter.set(0)
                KEVENT.post(TestEventType.A, Unit)
                val eventB = Event(TestEventType.B, "Hello!", EventDispatchMode.POSTING)
                KEVENT.post(eventB)
            }

            Given("KEvent") {
                KEVENT.clear()
            }

            Then("you can add subscribers in many ways") {
                KEVENT.subscribe<Unit>(TestEventType.A, tag = "lambda") {
                    counter.getAndIncrement()
                }
                assertTrue { KEVENT.subscribe(TestEventType.B, ::onStringEvent) }
                assertFalse { KEVENT.subscribe(TestEventType.B, ::onStringEvent) }
                KEVENT.subscribe(TestEventType.B, BasicFeature::onStringEvent)
                KEVENT.subscribe(TestEventType.B, Temp()::onStringEvent)
                KEVENT.subscribeMultiple<Any>(setOf(TestEventType.A, TestEventType.B)) { event ->
                    when (event.type) {
                        TestEventType.A -> {
                            event.data as Unit
                        }
                        TestEventType.B -> {
                            event.data as String
                        }
                    }
                    counter.getAndIncrement()
                }
                KEVENT.subscribeMultiple(setOf(TestEventType.A, TestEventType.B), BasicFeature::onMultipleEvent)
                assertEquals(3, KEVENT.getSubscribersByEventType(TestEventType.A).size)
                assertEquals(5, KEVENT.getSubscribersByEventType(TestEventType.B).size)
                assertEquals(1, KEVENT.getSubscribersByTag("lambda").size)
                assertEquals(8, KEVENT.getAllSubscribers().size)
            }

            Then("now it's time to post events") {
                postEvents()
            }

            Then("all subscribers will be notified") {
                assertEquals(8, counter.get())
            }

            Then("now let's remove these subscribers") {
                assertTrue { KEVENT.removeSubscribersByTag("lambda") }
                assertTrue { KEVENT.unsubscribe(TestEventType.B, ::onStringEvent) }
                assertTrue { KEVENT.unsubscribe(TestEventType.B, BasicFeature::onStringEvent) }
                assertTrue { KEVENT.unsubscribeMultiple(setOf(TestEventType.A, TestEventType.B), BasicFeature::onMultipleEvent) }
                postEvents()
                assertEquals(3, counter.get())
                assertTrue { KEVENT.removeSubscribersByEventType(TestEventType.B) }
                postEvents()
                assertEquals(1, counter.get())
                KEVENT.removeAllSubscribers()
            }

            Then("no subscriber will be notified now") {
                postEvents()
                assertEquals(0, counter.get())
            }
        }
    }
}) {
    private fun onStringEvent(event: Event<String>) {
        counter.getAndIncrement()
    }

    private fun onMultipleEvent(event: Event<Any>) {
        when (event.type) {
            TestEventType.A -> {
                event.data as Unit
            }
            TestEventType.B -> {
                event.data as String
            }
        }
        counter.getAndIncrement()
    }
}