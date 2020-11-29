package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class SubscriberExample : KEventSubscriber {
    val counter = AtomicInteger(0)

    fun registerSubscribers() {
        subscribe<Unit>(TestEventType.A) {
            counter.getAndIncrement()
        }
        assertTrue { subscribe(TestEventType.A, ::onUnitEvent) }
        assertFalse { subscribe(TestEventType.A, ::onUnitEvent) }
        assertTrue { subscribeMultiple(TestEventType.values().asList(), ::onAnyEvent) }
        assertFalse { subscribeMultiple(TestEventType.values().asList(), ::onAnyEvent) }
    }

    fun unregisterSubscribers() {
        unsubscribeAll()
    }

    private fun onUnitEvent(event: Event<Unit>) {
        counter.getAndIncrement()
    }

    fun onAnyEvent(event: Event<Any>) {
        when (event.type) {
            TestEventType.A -> event.data as Unit
            TestEventType.B -> event.data as String
        }
        counter.getAndIncrement()
    }
}

object SubscriberInterfaceFeature : Spek({
    Feature("KEvent comes with a helpful util interface: KEventSubscriber") {

        afterFeature { KEvent.clear() }

        Scenario("KEventSubscriber usage") {
            val subscriber1 = SubscriberExample()
            val subscriber2 = SubscriberExample()

            fun postEvents() {
                subscriber1.counter.set(0)
                subscriber2.counter.set(0)
                KEvent.post(TestEventType.A, Unit)
                KEvent.post(TestEventType.B, "Hello!")
            }

            Given("two example subscriber instances that implement the KEventSubscriber interface") {
                subscriber1.registerSubscribers()
                subscriber2.registerSubscribers()
            }

            When("all kinds of events are posted") {
                postEvents()
            }

            Then("subscribers should be notified") {
                assertEquals(4, subscriber1.counter.get())
                assertEquals(4, subscriber2.counter.get())
            }

            Then("now test unsubscription") {
                subscriber1.unregisterSubscribers()
                postEvents()
                assertEquals(0, subscriber1.counter.get())
                assertEquals(4, subscriber2.counter.get())
                KEvent.removeSubscribersByEventType(TestEventType.A)
                postEvents()
                assertEquals(0, subscriber1.counter.get())
                assertEquals(1, subscriber2.counter.get())
                subscriber2.unsubscribe(TestEventType.B, subscriber2::onAnyEvent)
                postEvents()
                assertEquals(0, subscriber1.counter.get())
                assertEquals(0, subscriber2.counter.get())
            }
        }
    }
})