package com.adammockor.usagecollector.core

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class UsageSessionProcessorTest {

    private val zone = ZoneId.of("Europe/Bratislava")

    @Test
    fun tailClose_countsUntilNow_whenNoClosingEvents() {
        val proc = UsageSessionProcessor(zone, minSegmentMs = 0)
        val sink = InMemorySink()

        val prev = CollectorState(
            lastTs = 0L,
            screenInteractive = true,
            currentPkg = "com.instagram.android",
            currentStart = 1_000L
        )

        val next = proc.process(prev, events = emptyList(), nowMs = 5_000L, sink = sink)

        assertEquals(1, sink.intervals.size)
        assertEquals("com.instagram.android", sink.intervals[0].pkg)
        assertEquals(1_000L, sink.intervals[0].startMs)
        assertEquals(5_000L, sink.intervals[0].endMs)
        assertEquals(4_000L, sink.intervals[0].durationMs)

        // kept open for next run
        assertEquals("com.instagram.android", next.currentPkg)
        assertEquals(5_000L, next.currentStart)
        assertEquals(true, next.screenInteractive)
    }

    @Test
    fun screenOff_closesSegment_andStopsCounting() {
        val proc = UsageSessionProcessor(zone, minSegmentMs = 0)
        val sink = InMemorySink()

        val prev = CollectorState(
            lastTs = 0L,
            screenInteractive = true,
            currentPkg = null,
            currentStart = -1L
        )

        val events = listOf(
            UE(1_000L, UsageEvents.Event.ACTIVITY_RESUMED, "com.instagram.android"),
            UE(3_000L, UsageEvents.Event.SCREEN_NON_INTERACTIVE, null)
        )

        val next = proc.process(prev, events, nowMs = 10_000L, sink = sink)

        assertEquals(1, sink.intervals.size)
        assertEquals(2_000L, sink.intervals[0].durationMs) // 1000..3000

        // no tail close because screen is non-interactive now
        assertEquals(false, next.screenInteractive)
        assertEquals(-1L, next.currentStart)
    }

    @Test
    fun appSwitch_closesPrevious_andStartsNew() {
        val proc = UsageSessionProcessor(zone, minSegmentMs = 0)
        val sink = InMemorySink()

        val prev = CollectorState(0L, true, null, -1L)

        val events = listOf(
            UE(1_000L, UsageEvents.Event.ACTIVITY_RESUMED, "com.instagram.android"),
            UE(2_500L, UsageEvents.Event.ACTIVITY_RESUMED, "com.reddit.frontpage"),
            UE(4_000L, UsageEvents.Event.ACTIVITY_PAUSED, "com.reddit.frontpage")
        )

        proc.process(prev, events, nowMs = 4_000L, sink = sink)

        assertEquals(2, sink.intervals.size)
        assertEquals("com.instagram.android", sink.intervals[0].pkg)
        assertEquals(1_000L, sink.intervals[0].startMs)
        assertEquals(2_500L, sink.intervals[0].endMs)

        assertEquals("com.reddit.frontpage", sink.intervals[1].pkg)
        assertEquals(2_500L, sink.intervals[1].startMs)
        assertEquals(4_000L, sink.intervals[1].endMs)
    }

    @Test
    fun splitsAtMidnight_intoTwoIntervals() {
        val proc = UsageSessionProcessor(zone, minSegmentMs = 0)
        val sink = InMemorySink()

        // Start an app at 23:59:30 local time and end at 00:00:30 next day.
        val d1 = java.time.LocalDate.of(2025, 12, 28)
        val start = d1.atTime(23, 59, 30).atZone(zone).toInstant().toEpochMilli()
        val end = d1.plusDays(1).atTime(0, 0, 30).atZone(zone).toInstant().toEpochMilli()

        val prev = CollectorState(0L, true, null, -1L)
        val events = listOf(
            UE(start, UsageEvents.Event.ACTIVITY_RESUMED, "com.instagram.android"),
            UE(end, UsageEvents.Event.ACTIVITY_PAUSED, "com.instagram.android")
        )

        proc.process(prev, events, nowMs = end, sink = sink)

        assertEquals(2, sink.intervals.size)

        val i1 = sink.intervals[0]
        val i2 = sink.intervals[1]

        assertEquals(d1, i1.day)
        assertEquals(d1.plusDays(1), i2.day)

        // each is 30 seconds
        assertEquals(30_000L, i1.durationMs)
        assertEquals(30_000L, i2.durationMs)
    }

    @Test
    fun ignoresShortSegments() {
        val proc = UsageSessionProcessor(zone, minSegmentMs = 1_000)
        val sink = InMemorySink()

        val prev = CollectorState(0L, true, null, -1L)
        val events = listOf(
            UE(1_000L, UsageEvents.Event.ACTIVITY_RESUMED, "com.test"),
            UE(1_500L, UsageEvents.Event.ACTIVITY_PAUSED, "com.test")
        )

        proc.process(prev, events, nowMs = 2_000L, sink = sink)

        assertEquals(0, sink.intervals.size)
    }
}