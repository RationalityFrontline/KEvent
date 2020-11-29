package org.rationalityfrontline.kevent

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

object EventBlockingFeature : Spek({
    Feature("You can block an event type and then all events of this type will be blocked") {

        val counterA by memoized { AtomicInteger(0) }
        val counterB by memoized { AtomicInteger(0) }

        fun resetCounters() {
            counterA.set(0)
            counterB.set(0)
        }

        beforeEachScenario { KEvent.clear() }
        afterFeature { KEvent.clear() }

        Scenario("blocking event") {

            Given("some subscribers of event type A and event type B, with event type A blocked") {
                resetCounters()
                for (i in 1..10) {
                    val type = if (i <= 5) TestEventType.A else TestEventType.B
                    KEvent.subscribe<Unit>(type, KEvent.ThreadMode.POSTING) { event ->
                        when (event.type) {
                            TestEventType.A -> counterA.getAndIncrement()
                            TestEventType.B -> counterB.getAndIncrement()
                        }
                    }
                }
                KEvent.blockEventType(TestEventType.A)
            }

            When("both type of event are posted") {
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.INSTANTLY)
                KEvent.post(TestEventType.B, Unit, KEvent.DispatchMode.INSTANTLY)
            }

            Then("only event of type B will be dispatched") {
                assertEquals(0, counterA.get())
                assertEquals(5, counterB.get())
            }

            And("you can also unblock the blocked event type") {
                KEvent.unblockEventType(TestEventType.A)
            }

            Then("the original blocked event type can now get dispatched") {
                resetCounters()
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.INSTANTLY)
                assertEquals(5, counterA.get())
            }
        }
    }
})