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
    Feature("KEvent supports four event dispatch modes (INSTANTLY, SEQUENTIAL, CONCURRENT, ORDERED_CONCURRENT) and three subscriber thread modes (POSTING, BACKGROUND, UI)") {

        val counter by memoized { AtomicInteger(0) }
        val calledThreadModesMap by memoized {
            ConcurrentHashMap<KEvent.ThreadMode, Boolean>().apply {
                putAll(
                    mapOf(
                        KEvent.ThreadMode.POSTING to false,
                        KEvent.ThreadMode.BACKGROUND to false,
                        KEvent.ThreadMode.UI to false,
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
            KEvent.ThreadMode.values().forEach { threadMode ->
                KEvent.subscribe<Unit>(TestEventType.A, threadMode) { event ->
                    when (threadMode) {
                        KEvent.ThreadMode.UI -> {
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
            KEvent.clear()
            addAllKindsOfSubscribers()
        }
        afterFeature {
            jframe.dispatchEvent(WindowEvent(jframe, WindowEvent.WINDOW_CLOSING))
            KEvent.clear()
        }

        Scenario("INSTANTLY: only compatible with POSTING") {

            When("an event is posted") {
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.INSTANTLY)
            }

            Then("only subscribers whose thread mode is POSTING will be notified") {
                assertTrue { calledThreadModesMap[KEvent.ThreadMode.POSTING]!! }
                assertFalse { calledThreadModesMap[KEvent.ThreadMode.BACKGROUND]!! }
                assertFalse { calledThreadModesMap[KEvent.ThreadMode.UI]!! }
            }
        }

        Scenario("SEQUENTIAL: compatible with BACKGROUND and UI") {
            When("an event is posted") {
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.SEQUENTIAL)
            }

            Then("only subscribers whose thread mode is BACKGROUND or UI will be notified") {
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(3, counter, 30)
                }
                assertFalse { calledThreadModesMap[KEvent.ThreadMode.POSTING]!! }
                assertTrue { calledThreadModesMap[KEvent.ThreadMode.BACKGROUND]!! }
                assertTrue { calledThreadModesMap[KEvent.ThreadMode.UI]!! }
            }
        }

        Scenario("CONCURRENT: only compatible with BACKGROUND") {
            When("an event is posted") {
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.CONCURRENT)
            }

            Then("only subscribers whose thread mode is BACKGROUND will be notified") {
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(3, counter, 30)
                }
                assertFalse { calledThreadModesMap[KEvent.ThreadMode.POSTING]!! }
                assertTrue { calledThreadModesMap[KEvent.ThreadMode.BACKGROUND]!! }
                assertFalse { calledThreadModesMap[KEvent.ThreadMode.UI]!! }
            }
        }

        Scenario("ORDERED_CONCURRENT: only compatible with BACKGROUND") {
            When("an event is posted") {
                KEvent.post(TestEventType.A, Unit, KEvent.DispatchMode.ORDERED_CONCURRENT)
            }

            Then("only subscribers whose thread mode is BACKGROUND will be notified") {
                assertFailsWith(TimeoutException::class) {
                    waitForEventDispatch(3, counter, 30)
                }
                assertFalse { calledThreadModesMap[KEvent.ThreadMode.POSTING]!! }
                assertTrue { calledThreadModesMap[KEvent.ThreadMode.BACKGROUND]!! }
                assertFalse { calledThreadModesMap[KEvent.ThreadMode.UI]!! }
            }
        }
    }
})