package org.rationalityfrontline.kevent

import kotlinx.coroutines.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.awt.GridBagLayout
import java.awt.event.WindowEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue


object ThreadingFeature : Spek({
    Feature("KEvent supports four event dispatch modes (POSTING, SEQUENTIAL, CONCURRENT, ORDERED_CONCURRENT) and three subscriber thread modes (POSTING, BACKGROUND, UI)") {

        val counter by memoized { AtomicInteger(0) }
        val calledThreadModesMap by memoized {
            ConcurrentHashMap<SubscriberThreadMode, Boolean>().apply {
                putAll(
                    mapOf(
                        SubscriberThreadMode.POSTING to false,
                        SubscriberThreadMode.BACKGROUND to false,
                        SubscriberThreadMode.UI to false,
                    )
                )
            }
        }
        lateinit var jframe: JFrame
        lateinit var jlabel: JLabel

        fun resetCounters() {
            counter.set(0)
            calledThreadModesMap.replaceAll { _, _ -> false }
        }

        fun addAllKindsOfSubscribers() {
            resetCounters()
            runBlocking(Dispatchers.Main) {
                jlabel.text = "0"
            }
            SubscriberThreadMode.values().forEach { threadMode ->
                KEVENT.subscribe<Unit>(TestEventType.A, threadMode) { event ->
                    when (threadMode) {
                        SubscriberThreadMode.UI -> {
                            if (SwingUtilities.isEventDispatchThread()) {
                                jlabel.text = "Received event: $event"
                                calledThreadModesMap[threadMode] = true
                            }
                        }
                        else -> {
                            calledThreadModesMap[threadMode] = true
                        }
                    }
                    counter.getAndIncrement()
                }
            }
        }

        beforeFeature {
            SwingUtilities.invokeAndWait {
                jframe = JFrame("Testing subscriber thread mode UI").apply {
                    defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
                    setSize(1000, 300)
                    jlabel = JLabel("waiting for tests").apply { font = font.deriveFont(20f) }
                    contentPane.apply {
                        layout = GridBagLayout()
                        add(jlabel)
                    }
                    isVisible = true
                }
                GlobalScope.launch(Dispatchers.Main) {
                    while (jlabel.isShowing) {
                        delay(1)
                        jlabel.text.toIntOrNull()?.run {
                            jlabel.text = (this + 1).toString()
                        }
                    }
                }
            }
        }
        beforeEachScenario {
            KEVENT.clear()
            addAllKindsOfSubscribers()
        }
        afterFeature {
            jframe.dispatchEvent(WindowEvent(jframe, WindowEvent.WINDOW_CLOSING))
            KEVENT.clear()
        }

        Scenario("POSTING: only compatible with POSTING") {

            When("an event is posted") {
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.POSTING)
            }

            Then("only subscribers whose thread mode is POSTING will be notified") {
                assertTrue { calledThreadModesMap[SubscriberThreadMode.POSTING]!! }
                assertFalse { calledThreadModesMap[SubscriberThreadMode.BACKGROUND]!! }
                assertFalse { calledThreadModesMap[SubscriberThreadMode.UI]!! }
            }
        }

        Scenario("SEQUENTIAL: compatible with BACKGROUND and UI") {
            When("an event is posted") {
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.SEQUENTIAL)
            }

            Then("only subscribers whose thread mode is BACKGROUND or UI will be notified") {
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(3, counter, 30)
                }
                assertFalse { calledThreadModesMap[SubscriberThreadMode.POSTING]!! }
                assertTrue { calledThreadModesMap[SubscriberThreadMode.BACKGROUND]!! }
                assertTrue { calledThreadModesMap[SubscriberThreadMode.UI]!! }
            }
        }

        Scenario("CONCURRENT: only compatible with BACKGROUND") {
            When("an event is posted") {
                KEVENT.post(TestEventType.A, Unit, EventDispatchMode.CONCURRENT)
            }

            Then("only subscribers whose thread mode is BACKGROUND will be notified") {
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(3, counter, 30)
                }
                assertFalse { calledThreadModesMap[SubscriberThreadMode.POSTING]!! }
                assertTrue { calledThreadModesMap[SubscriberThreadMode.BACKGROUND]!! }
                assertFalse { calledThreadModesMap[SubscriberThreadMode.UI]!! }
            }
        }
    }
})