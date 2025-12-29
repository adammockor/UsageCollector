package com.adammockor.usagecollector.core

import android.app.usage.UsageEvents
import java.time.Instant
import java.time.ZoneId

class UsageSessionProcessor(
    private val zone: ZoneId,
    private val minSegmentMs: Long = 1_000L
) {
    fun process(prev: CollectorState, events: List<UE>, nowMs: Long, sink: OutputSink): CollectorState {
        var screenInteractive = prev.screenInteractive
        var currentPkg = prev.currentPkg
        var currentStart = prev.currentStart

        fun addSegment(startMs: Long, endMs: Long, pkg: String) {
            if (endMs <= startMs) return
            if (endMs - startMs < minSegmentMs) return

            var s = startMs
            while (s < endMs) {
                val day = Instant.ofEpochMilli(s).atZone(zone).toLocalDate()
                val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val segEnd = minOf(endMs, dayEnd)
                val delta = segEnd - s

                sink.addDuration(day, pkg, delta)
                sink.addInterval(Interval(day, pkg, s, segEnd))

                s = segEnd
            }
        }

        for (e in events) {
            when (e.type) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    screenInteractive = true
                    if (currentPkg != null) currentStart = e.ts
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (screenInteractive && currentPkg != null && currentStart >= 0) {
                        addSegment(currentStart, e.ts, currentPkg!!)
                    }
                    screenInteractive = false
                    currentStart = -1L
                }

                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (screenInteractive && currentPkg != null && currentStart >= 0) {
                        addSegment(currentStart, e.ts, currentPkg!!)
                    }
                    currentPkg = e.pkg
                    currentStart = if (screenInteractive && currentPkg != null) e.ts else -1L
                }

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (screenInteractive && currentPkg != null && currentPkg == e.pkg && currentStart >= 0) {
                        addSegment(currentStart, e.ts, currentPkg!!)
                    }
                    if (currentPkg == e.pkg) {
                        currentPkg = null
                        currentStart = -1L
                    }
                }
            }
        }

        // tail close
        if (screenInteractive && currentPkg != null && currentStart >= 0 && nowMs > currentStart) {
            addSegment(currentStart, nowMs, currentPkg!!)
            currentStart = nowMs
        }

        return CollectorState(
            lastTs = nowMs,
            screenInteractive = screenInteractive,
            currentPkg = currentPkg,
            currentStart = currentStart
        )
    }
}