package com.adammockor.usagecollector

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.graphics.Color
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.adammockor.usagecollector.ui.theme.UsageCollectorTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        scheduleCollectors()

        setContent {
            UsageCollectorTheme {
                val view = LocalView.current

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
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

private data class StatusState(
    val accessLabel: String,
    val accessValue: String,
    val lastCollection: String,
    val lastExport: String
)

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val zone = remember { ZoneId.systemDefault() }
    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    val statusState = remember {
        mutableStateOf(readStatus(context, store, zone, formatter))
    }

    fun refresh() {
        statusState.value = readStatus(context, store, zone, formatter)
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                StatusRow(statusState.value.accessLabel, statusState.value.accessValue)
                StatusRow("Last collection", statusState.value.lastCollection)
                StatusRow("Last export", statusState.value.lastExport)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Usage Access")
            }

            Button(
                onClick = {
                    WorkManager.getInstance(context)
                        .enqueue(OneTimeWorkRequestBuilder<UsageEventCollectorWorker>().build())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run Collector Now")
            }

            Button(
                onClick = {
                    WorkManager.getInstance(context)
                        .enqueue(OneTimeWorkRequestBuilder<DailyUsageExportWorker>().build())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Yesterday Now")
            }

            Button(
                onClick = { refresh() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh Status")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun readStatus(
    context: android.content.Context,
    store: Store,
    zone: ZoneId,
    formatter: DateTimeFormatter
): StatusState {
    val accessGranted = UsageAccess.hasUsageAccess(context)
    val accessLabel = "Usage access"
    val accessValue = if (accessGranted) "GRANTED" else "NOT GRANTED"

    val lastTs = store.getLastTs(0L)
    val lastCollection = formatTimestamp(lastTs, zone, formatter)

    val lastExportTs = store.getLastExportTs(0L)
    val lastExport = formatTimestamp(lastExportTs, zone, formatter)

    return StatusState(
        accessLabel = accessLabel,
        accessValue = accessValue,
        lastCollection = lastCollection,
        lastExport = lastExport
    )
}

private fun formatTimestamp(
    ts: Long,
    zone: ZoneId,
    formatter: DateTimeFormatter
): String {
    return if (ts <= 0L) {
        "Never"
    } else {
        Instant.ofEpochMilli(ts).atZone(zone).format(formatter)
    }
}
