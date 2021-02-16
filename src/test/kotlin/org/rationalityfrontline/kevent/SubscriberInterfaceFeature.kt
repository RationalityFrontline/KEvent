package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class SubscriberExample(
    override val KEVENT_INSTANCE: KEvent
) : KEventSubscriber {

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
        val subscriber1 = SubscriberExample(KEVENT)
        val subscriber2 = SubscriberExample(KEvent("Test Instance"))

        beforeFeature {
            KEVENT.clear()
        }

        afterFeature {
            KEVENT.clear()
            subscriber2.KEVENT_INSTANCE.release()
        }

        Scenario("KEventSubscriber usage") {

            fun postEvents() {
                subscriber1.counter.set(0)
                subscriber2.counter.set(0)
                KEVENT.post(TestEventType.A, Unit)
                subscriber2.KEVENT_INSTANCE.post(TestEventType.B, "Hello!")
            }

            Given("two example subscriber instances that implement the KEventSubscriber interface") {
                subscriber1.registerSubscribers()
                subscriber2.registerSubscribers()
            }

            When("all kinds of events are posted") {
                postEvents()
            }

            Then("subscribers should be notified") {
                assertEquals(3, subscriber1.counter.get())
                assertEquals(1, subscriber2.counter.get())
            }

            Then("now test unsubscription") {
                subscriber1.unregisterSubscribers()
                postEvents()
                assertEquals(0, subscriber1.counter.get())
                assertEquals(1, subscriber2.counter.get())
                subscriber2.KEVENT_INSTANCE.removeSubscribersByEventType(TestEventType.B)
                postEvents()
                assertEquals(0, subscriber1.counter.get())
                assertEquals(0, subscriber2.counter.get())
            }
        }
    }
})