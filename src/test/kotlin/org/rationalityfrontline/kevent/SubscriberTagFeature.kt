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

        beforeEachScenario { KEvent.clear() }
        afterFeature { KEvent.clear() }

        Scenario("tagging subscribers") {

            Given("some subscribers of event type A and event type B, with two kinds of tag ('odd' and 'even')") {
                resetCounters()
                for (i in 1..10) {
                    val tag = if (i % 2 == 1) "odd" else "even"
                    val type = if (i <= 5) TestEventType.A else TestEventType.B
                    KEvent.subscribe<Unit>(type, KEvent.ThreadMode.POSTING, tag = tag) {
                        when (tag) {
                            "odd" -> counterOdd.getAndIncrement()
                            "even" -> counterEven.getAndIncrement()
                        }
                    }
                }
            }

            When("both type of event are posted") {
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.INSTANTLY)
                KEvent.post(TestEventType.B, Unit, KEvent.DispatchMode.INSTANTLY)
            }

            Then("all subscribers will be notified") {
                assertEquals(5, counterOdd.get())
                assertEquals(5, counterEven.get())
            }

            Then("if you remove all subscribers of tag 'odd'") {
                KEvent.removeSubscribersByTag("odd")
            }

            Then("only subscribers of tag 'even' will get notified") {
                resetCounters()
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.INSTANTLY)
                KEvent.post(TestEventType.B, Unit, KEvent.DispatchMode.INSTANTLY)
                assertEquals(0, counterOdd.get())
                assertEquals(5, counterEven.get())
            }
        }
    }
})