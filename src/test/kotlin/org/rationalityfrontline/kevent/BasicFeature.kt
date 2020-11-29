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

        beforeEachScenario { KEvent.clear() }
        afterFeature { KEvent.clear() }

        Scenario("basic usage") {

            fun postEvents() {
                counter.set(0)
                KEvent.post(TestEventType.A, Unit)
                val eventB = Event(TestEventType.B, "Hello!")
                KEvent.post(eventB)
            }

            Given("KEvent") {
                KEvent.clear()
            }

            Then("you can add subscribers in many ways") {
                KEvent.subscribe<Unit>(TestEventType.A, tag = "lambda") {
                    counter.getAndIncrement()
                }
                assertTrue { KEvent.subscribe(TestEventType.B, ::onStringEvent) }
                assertFalse { KEvent.subscribe(TestEventType.B, ::onStringEvent) }
                KEvent.subscribe(TestEventType.B, BasicFeature::onStringEvent)
                KEvent.subscribe(TestEventType.B, Temp()::onStringEvent)
                KEvent.subscribeMultiple<Any>(setOf(TestEventType.A, TestEventType.B)) { event ->
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
                KEvent.subscribeMultiple(setOf(TestEventType.A, TestEventType.B), BasicFeature::onMultipleEvent)
            }

            Then("now it's time to post events") {
                postEvents()
            }

            Then("all subscribers will be notified") {
                assertEquals(8, counter.get())
            }

            Then("now let's remove these subscribers") {
                assertTrue { KEvent.removeSubscribersByTag("lambda") }
                assertTrue { KEvent.unsubscribe(TestEventType.B, ::onStringEvent) }
                assertTrue { KEvent.unsubscribe(TestEventType.B, BasicFeature::onStringEvent) }
                assertTrue { KEvent.unsubscribeMultiple(setOf(TestEventType.A, TestEventType.B), BasicFeature::onMultipleEvent) }
                postEvents()
                assertEquals(3, counter.get())
                assertTrue { KEvent.removeSubscribersByEventType(TestEventType.B) }
                postEvents()
                assertEquals(1, counter.get())
                KEvent.removeAllSubscribers()
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