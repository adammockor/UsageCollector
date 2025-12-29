package com.adammockor.usagecollector

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.work.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scheduleCollectors()

        val store = Store(this)
        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val status = TextView(this)

        fun refresh() {
            val access = if (UsageAccess.hasUsageAccess(this))
                "Usage access: GRANTED"
            else
                "Usage access: NOT GRANTED"

            val lastTs = store.getLastTs(0L)
            val lastText = if (lastTs <= 0L) {
                "Never"
            } else {
                Instant.ofEpochMilli(lastTs).atZone(zone).format(formatter)
            }

            val lastExportTs = store.getLastExportTs(0L)
            val lastExportText = if (lastExportTs <= 0L) {
                "Never"
            } else {
                Instant.ofEpochMilli(lastExportTs).atZone(zone).format(formatter)
            }

            status.text = "$access\nLast collection: $lastText\nLast export: $lastExportText"
        }

        val grant = Button(this).apply {
            text = "Grant Usage Access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }

        val runCollectorNow = Button(this).apply {
            text = "Run Collector Now"
            setOnClickListener {
                WorkManager.getInstance(this@MainActivity)
                    .enqueue(OneTimeWorkRequestBuilder<UsageEventCollectorWorker>().build())
            }
        }

        val exportYesterdayNow = Button(this).apply {
            text = "Export Yesterday Now"
            setOnClickListener {
                WorkManager.getInstance(this@MainActivity)
                    .enqueue(OneTimeWorkRequestBuilder<DailyUsageExportWorker>().build())
            }
        }

        val refreshBtn = Button(this).apply {
            text = "Refresh Status"
            setOnClickListener { refresh() }
        }

        layout.addView(status)
        layout.addView(grant)
        layout.addView(refreshBtn)
        layout.addView(runCollectorNow)
        layout.addView(exportYesterdayNow)
        setContentView(layout)

        refresh()
    }

    private fun scheduleCollectors() {
        // 15-min collector (minimum periodic interval)
        val collectorReq = PeriodicWorkRequestBuilder<UsageEventCollectorWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage_event_collector",
            ExistingPeriodicWorkPolicy.KEEP,
            collectorReq
        )

        // Daily export (best effort; WorkManager doesn't guarantee exact time)
        val exportReq = PeriodicWorkRequestBuilder<DailyUsageExportWorker>(24, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_usage_export",
            ExistingPeriodicWorkPolicy.KEEP,
            exportReq
        )
    }
}
