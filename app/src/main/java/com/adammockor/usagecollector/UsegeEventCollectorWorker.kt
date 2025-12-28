package com.adammockor.usagecollector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class UsageEventCollectorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!UsageAccess.hasUsageAccess(applicationContext)) return Result.retry()

        val store = Store(applicationContext)
        val now = System.currentTimeMillis()
        val fallbackLast = now - 20 * 60 * 1000L // first run safety window
        val last = store.getLastTs(fallbackLast)

        val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(last, now)

        val zone = ZoneId.systemDefault()

        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        var screenInteractive = store.getScreenInteractive(pm.isInteractive)
        var currentPkg: String? = store.getCurrentPkg()
        var currentStart = store.getCurrentStart() // valid only if interactive + pkg != null

        // helper: add duration, split by local midnight, and ignore micro-segments
        fun addSegment(startMs: Long, endMs: Long, pkg: String) {
            val minSegmentMs = 1_000L // ignore <1s noise (tweak if needed)
            if (endMs - startMs < minSegmentMs) return
            if (endMs <= startMs) return

            var s = startMs
            while (s < endMs) {
                val day = Instant.ofEpochMilli(s).atZone(zone).toLocalDate()
                val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val segEnd = minOf(endMs, dayEnd)
                store.addToDay(day, pkg, segEnd - s)
                store.addInterval(day, pkg, s, segEnd)
                s = segEnd
            }
        }

        var lastEventTs = last
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            lastEventTs = e.timeStamp

            when (e.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    screenInteractive = true
                    // resume counting for current foreground app from this moment
                    if (currentPkg != null) currentStart = e.timeStamp
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // close current segment at screen-off
                    if (screenInteractive && currentPkg != null && currentStart >= 0) {
                        addSegment(currentStart, e.timeStamp, currentPkg!!)
                    }
                    screenInteractive = false
                    currentStart = -1L
                }

                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // close previous app segment
                    if (screenInteractive && currentPkg != null && currentStart >= 0) {
                        addSegment(currentStart, e.timeStamp, currentPkg!!)
                    }
                    currentPkg = e.packageName
                    currentStart = if (screenInteractive) e.timeStamp else -1L
                }

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    // close segment for that app if it is current
                    if (screenInteractive && currentPkg == e.packageName && currentStart >= 0) {
                        addSegment(currentStart, e.timeStamp, currentPkg!!)
                    }
                    if (currentPkg == e.packageName) {
                        currentPkg = null
                        currentStart = -1L
                    }
                }
            }
        }

        // Close any open segment up to "now" to avoid undercounting when there are no closing events
        if (screenInteractive && currentPkg != null && currentStart >= 0 && now > currentStart) {
            addSegment(currentStart, now, currentPkg!!)
            // keep the segment open for the next run
            currentStart = now
        }

        // If no events were returned, keep lastEventTs as the previous `last`
        // and advance lastTs to now to avoid reprocessing the same empty window.

        // Persist state for the next run
        store.setLastTs(now)
        store.setScreenInteractive(screenInteractive)
        store.setCurrentPkg(currentPkg)
        store.setCurrentStart(currentStart)

        Log.d(
            "USAGE",
            "Collected events window last=$last now=$now lastEventTs=$lastEventTs interactive=$screenInteractive currentPkg=$currentPkg currentStart=$currentStart"
        )
        return Result.success()
    }
}