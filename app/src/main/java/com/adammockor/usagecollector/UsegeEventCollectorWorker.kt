package com.adammockor.usagecollector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.ZoneId

import com.adammockor.usagecollector.core.CollectorState
import com.adammockor.usagecollector.core.UE
import com.adammockor.usagecollector.core.UsageSessionProcessor

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

        // Convert Android UsageEvents -> List<UE> (pure model)
        val list = ArrayList<UE>()
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            list.add(UE(ts = ev.timeStamp, type = ev.eventType, pkg = ev.packageName))
        }

        // Restore previous state (what your old worker stored)
        val screenInteractive = store.getScreenInteractive(pm.isInteractive)
        val currentPkg = store.getCurrentPkg()
        val currentStart = store.getCurrentStart()

        val prev = CollectorState(
            lastTs = last,
            screenInteractive = screenInteractive,
            currentPkg = currentPkg,
            currentStart = currentStart
        )

        // Process events into intervals + totals (store must implement OutputSink for this to compile)
        val processor = UsageSessionProcessor(zone)
        val next = processor.process(prev, list, now, store)

        // Persist next state
        store.setLastTs(next.lastTs)
        store.setScreenInteractive(next.screenInteractive)
        store.setCurrentPkg(next.currentPkg)
        store.setCurrentStart(next.currentStart)

        val lastEventTs = list.lastOrNull()?.ts ?: last

        Log.d(
            "USAGE",
            "Collected events window last=$last now=$now lastEventTs=$lastEventTs interactive=${next.screenInteractive} currentPkg=${next.currentPkg} currentStart=${next.currentStart}"
        )

        return Result.success()
    }
}