package com.adammockor.usagecollector

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class DailyUsageExportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private fun writeCsvToDownloads(context: Context, fileName: String, csvContent: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/UsageCollector"
            )
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create MediaStore record")

        resolver.openOutputStream(uri)!!.use { out ->
            out.write(csvContent.toByteArray())
        }
    }

    override suspend fun doWork(): Result {
        if (!UsageAccess.hasUsageAccess(applicationContext)) return Result.retry()

        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone).minusDays(1)

        val store = Store(applicationContext)
        val json = store.readDay(date) ?: return Result.success()

        // Export totals CSV
        val totalsCsv = buildString {
            append("date,package,interactive_foreground_ms\n")
            val keys = json.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val ms = json.getLong(pkg)
                if (ms > 0) append("$date,$pkg,$ms\n")
            }
        }

        writeCsvToDownloads(
            applicationContext,
            "usage_${date}_interactive.csv",
            totalsCsv
        )

        // Export intervals CSV (if present)
        val intervalsIn = File(File(applicationContext.filesDir, "daily_intervals"), "$date.csv")
        if (intervalsIn.exists()) {
            writeCsvToDownloads(
                applicationContext,
                "intervals_${date}_interactive.csv",
                intervalsIn.readText()
            )
        }

        store.setLastExportTs(System.currentTimeMillis())

        return Result.success()
    }
}
