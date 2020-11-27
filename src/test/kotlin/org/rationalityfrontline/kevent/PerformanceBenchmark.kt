package org.rationalityfrontline.kevent

import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import mu.KotlinLogging
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureNanoTime


fun main() {
    PerformanceBenchmark.runAllTests(10000)
//    PerformanceBenchmark.testMassiveEventsWithMassiveSubscribers(1000, 2000)
}

object PerformanceBenchmark {
    private val logger = KotlinLogging.logger("PerformanceBenchmark")
    private var csvEnabled = false
    private val csvRows = mutableListOf<List<String>>()

    fun enableCsv() {
        csvRows.clear()
        val headerRow = mutableListOf<String>()
        arrayOf("AvgSubsTime", "AvgPostTime", "AvgWaitTime", "AvgCallTime").forEach {
            headerRow.add(it)
            headerRow.addAll(arrayOf("INSTANTLY", "SEQUENTIAL", "CONCURRENT", "ORDERED_CONCURRENT", ""))
        }
        csvRows.add(headerRow)
        csvEnabled = true
    }

    fun disableCsv() {
        csvRows.clear()
        csvEnabled = false
    }

    fun clearCsvRecords() {
        enableCsv()
    }

    fun writeCsv(path: String) {
        csvWriter().writeAll(csvRows, path)
    }

    fun writeCsv(file: File) {
        csvWriter().writeAll(csvRows, file)
    }

    fun measureEventDispatchTime(eventNum: Int, subscriberNum: Int, dispatchMode: KEvent.DispatchMode, threadMode: KEvent.ThreadMode, isTimeConsuming: Boolean = false, isSticky: Boolean = false, enableLog: Boolean = true): Array<Float> {
        KEvent.clear()
        val counter = AtomicInteger(0)

        fun measureSubsTime(): Float {
            return if (isTimeConsuming) {
                measureNanoTime {
                    repeat(subscriberNum) {
                        KEvent.subscribe<Unit>(TestEventType.A, threadMode) {
                            sleep(10)
                            counter.getAndIncrement()
                        }
                    }
                } / 1_000_000f
            } else {
                measureNanoTime {
                    repeat(subscriberNum) {
                        KEvent.subscribe<Unit>(TestEventType.A, threadMode) {
                            counter.getAndIncrement()
                        }
                    }
                } / 1_000_000f
            }
        }

        fun measurePostTime(): Float = measureNanoTime {
            repeat(eventNum) {
                KEvent.post(Event(TestEventType.A, Unit, dispatchMode, isSticky))
            }
        } / 1_000_000f

        val subsTime: Float
        val postTime: Float
        if (isSticky) {
            postTime = measurePostTime()
            subsTime = measureSubsTime()
        } else {
            subsTime = measureSubsTime()
            postTime = measurePostTime()
        }
        val subscriberCalledNum = eventNum * subscriberNum
        val waitTime = waitForEventDispatch(subscriberCalledNum, counter, Int.MAX_VALUE)
        KEvent.clear()
        val avgSubsTime = subsTime / subscriberNum
        val avgPostTime = postTime / eventNum
        val avgWaitTime = waitTime / subscriberCalledNum
        val avgCallTime = (postTime + waitTime) / subscriberCalledNum
        if (enableLog) logger.info { """
        
                --------------------------------------------
                eventNum       = $eventNum
                subscriberNum  = $subscriberNum
                dispatchMode   = $dispatchMode
                threadMode     = $threadMode
                isSticky       = $isSticky
                timeConsuming  = $isTimeConsuming
                subsTime       = $subsTime (avg = $avgSubsTime)
                postTime       = $postTime (avg = $avgPostTime)
                waitTime       = $waitTime (avg = $avgWaitTime)
                avgCallTime    = $avgCallTime
                --------------------------------------------
            """.trimIndent()
        }
        return arrayOf(avgSubsTime, avgPostTime, avgWaitTime, avgCallTime)
    }

    fun testWithAllDispatchModes(eventNum: Int, subscriberNum: Int, isTimeConsuming: Boolean, enableLog: Boolean = true) {
        if (enableLog) logger.info { "${"".padEnd(100, '#')}\n【Testing $eventNum events with $subscriberNum ${if (isTimeConsuming) "time consuming " else ""}subscribers】" }
        val results = mutableListOf<Array<Float>>()
        results.add(measureEventDispatchTime(eventNum, subscriberNum, KEvent.DispatchMode.INSTANTLY, KEvent.ThreadMode.POSTING, isTimeConsuming, enableLog = enableLog))
        results.add(measureEventDispatchTime(eventNum, subscriberNum, KEvent.DispatchMode.SEQUENTIAL, KEvent.ThreadMode.BACKGROUND, isTimeConsuming, enableLog = enableLog))
        results.add(measureEventDispatchTime(eventNum, subscriberNum, KEvent.DispatchMode.CONCURRENT, KEvent.ThreadMode.BACKGROUND, isTimeConsuming, enableLog = enableLog))
        results.add(measureEventDispatchTime(eventNum, subscriberNum, KEvent.DispatchMode.ORDERED_CONCURRENT, KEvent.ThreadMode.BACKGROUND, isTimeConsuming, enableLog = enableLog))
        if (csvEnabled) {
            val conditions = "event-$eventNum; subs-$subscriberNum; tc-$isTimeConsuming; st-false"
            val row = mutableListOf<String>()
            for (i in 0..3) {
                row.add(conditions)
                results.forEach {
                    row.add(it[i].toString())
                }
                row.add("")
            }
            csvRows.add(row)
        }
    }

    fun testWithStickyEvents(eventNum: Int, subscriberNum: Int, isTimeConsuming: Boolean, enableLog: Boolean = true) {
        if (enableLog) logger.info { "${"".padEnd(100, '#')}\n【Testing $eventNum sticky events with $subscriberNum ${if (isTimeConsuming) "time consuming " else ""}later added subscribers】" }
        val results = measureEventDispatchTime(eventNum, subscriberNum, KEvent.DispatchMode.CONCURRENT, KEvent.ThreadMode.BACKGROUND, isTimeConsuming, enableLog = enableLog)
        if (csvEnabled) {
            val conditions = "event-$eventNum; subs-$subscriberNum; tc-$isTimeConsuming; st-true"
            val row = mutableListOf<String>()
            for (i in 0..3) {
                row.addAll(arrayOf(conditions, "", "", results[i].toString(), "", ""))
            }
            csvRows.add(row)
        }
    }


    fun testSingleEventWithMassiveSubscribers(num: Int) {
        testWithAllDispatchModes(1, num, false)
    }

    fun testSingleEventWithMassiveTimeConsumingSubscribers(num: Int) {
        testWithAllDispatchModes(1, num, true)
    }

    fun testMassiveEventsWithSingleSubscriber(num: Int) {
        testWithAllDispatchModes(num, 1, false)
    }

    fun testMassiveEventsWithSingleTimeConsumingSubscriber(num: Int) {
        testWithAllDispatchModes(num, 1, true)
    }

    fun testMassiveEventsWithMassiveSubscribers(eventNum: Int, subscriberNum: Int) {
        testWithAllDispatchModes(eventNum, subscriberNum, false)
    }

    fun testSingleStickyEventWithMassiveLaterAddedSubscribers(num: Int) {
        testWithStickyEvents(1, num, false)
    }

    fun testSingleStickyEventWithMassiveTimeConsumingLaterAddedSubscribers(num: Int) {
        testWithStickyEvents(1, num, true)
    }

    fun testMassiveStickyEventsWithSingleLaterAddedSubscriber(num: Int) {
        testWithStickyEvents(num, 1, false)
    }

    fun testMassiveStickyEventsWithSingleTimeConsumingLaterAddedSubscriber(num: Int) {
        testWithStickyEvents(num, 1, true)
    }

    fun testMassiveStickyEventsWithMassiveLaterAddedSubscribers(eventNum: Int, subscriberNum: Int) {
        testWithStickyEvents(eventNum, subscriberNum, false)
    }

    fun runAllTests(num: Int, massiveEventLimit: Int = 1000) {
        doWarmup()
        enableCsv()
        val massiveEventNum = if (num < massiveEventLimit) num else massiveEventLimit
        testSingleEventWithMassiveSubscribers(num)
        testSingleEventWithMassiveTimeConsumingSubscribers(num)
        testMassiveEventsWithSingleSubscriber(num)
        testMassiveEventsWithSingleTimeConsumingSubscriber(num)
        testMassiveEventsWithMassiveSubscribers(massiveEventNum, num)
        testSingleStickyEventWithMassiveLaterAddedSubscribers(num)
        testSingleStickyEventWithMassiveTimeConsumingLaterAddedSubscribers(num)
        testMassiveStickyEventsWithSingleLaterAddedSubscriber(num)
        testMassiveStickyEventsWithSingleTimeConsumingLaterAddedSubscriber(num)
        testMassiveStickyEventsWithMassiveLaterAddedSubscribers(massiveEventNum, num)
        File("./benchmark").mkdirs()
        writeCsv("./benchmark/$num-$massiveEventLimit-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd.HH_mm_ss"))}.csv")
    }

    fun doWarmup() {
        repeat(10) {
            testWithAllDispatchModes(10, 1000, false, enableLog = false)
        }
    }
}