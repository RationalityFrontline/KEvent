package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

object SubscriberTagFeature : Spek({
    Feature("You can tag subscribers, and then remove subscribers by tag (this feature is used in the KEventSubscriber interface)") {

        val counterOdd by memoized { AtomicInteger(0) }
        val counterEven by memoized { AtomicInteger(0) }

        fun resetCounters() {
            counterOdd.set(0)
            counterEven.set(0)
        }

        beforeEachScenario { KEVENT.clear() }
        afterFeature { KEVENT.clear() }

        Scenario("tagging subscribers") {

            Given("some subscribers of event type A and event type B, with two kinds of tag ('odd' and 'even')") {
                resetCounters()
                for (i in 1..10) {
                    val tag = if (i % 2 == 1) "odd" else "even"
                    val type = if (i <= 5) TestEventType.A else TestEventType.B
                    KEVENT.subscribe<Unit>(type, SubscriberThreadMode.POSTING, tag = tag) {
                        when (tag) {
                            "odd" -> counterOdd.getAndIncrement()
                            "even" -> counterEven.getAndIncrement()
                        }
                    }
                }
            }

            When("both type of event are posted") {
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.POSTING)
                KEVENT.post(TestEventType.B, Unit, EventDispatchMode.POSTING)
            }

            Then("all subscribers will be notified") {
                assertEquals(5, counterOdd.get())
                assertEquals(5, counterEven.get())
            }

            Then("if you remove all subscribers of tag 'odd'") {
                KEVENT.removeSubscribersByTag("odd")
            }

            Then("only subscribers of tag 'even' will get notified") {
                resetCounters()
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.POSTING)
                KEVENT.post(TestEventType.B, Unit, EventDispatchMode.POSTING)
                assertEquals(0, counterOdd.get())
                assertEquals(5, counterEven.get())
            }
        }
    }
})