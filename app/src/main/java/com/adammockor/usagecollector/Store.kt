package com.adammockor.usagecollector

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

import com.adammockor.usagecollector.core.Interval
import com.adammockor.usagecollector.core.OutputSink

class Store(private val context: Context) : OutputSink {

    private val prefs = context.getSharedPreferences("collector_state", Context.MODE_PRIVATE)

    fun getLastTs(defaultValue: Long): Long = prefs.getLong("last_ts", defaultValue)
    fun setLastTs(v: Long) = prefs.edit().putLong("last_ts", v).apply()

    fun getLastExportTs(defaultValue: Long): Long = prefs.getLong("last_export_ts", defaultValue)
    fun setLastExportTs(v: Long) = prefs.edit().putLong("last_export_ts", v).apply()

    fun getScreenInteractive(defaultValue: Boolean): Boolean =
        prefs.getBoolean("screen_interactive", defaultValue)

    fun setScreenInteractive(v: Boolean) = prefs.edit().putBoolean("screen_interactive", v).apply()

    fun getCurrentPkg(): String? = prefs.getString("current_pkg", null)
    fun setCurrentPkg(v: String?) = prefs.edit().putString("current_pkg", v).apply()
    fun getCurrentStart(): Long = prefs.getLong("current_start", -1L)
    fun setCurrentStart(v: Long) = prefs.edit().putLong("current_start", v).apply()

    fun addToDay(day: LocalDate, pkg: String, deltaMs: Long) {
        if (deltaMs <= 0) return

        val dir = File(context.filesDir, "daily_totals")
        dir.mkdirs()
        val file = File(dir, "$day.json")

        val json = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        val current = json.optLong(pkg, 0L)
        json.put(pkg, current + deltaMs)
        file.writeText(json.toString())
    }

    fun readDay(day: LocalDate): JSONObject? {
        val file = File(File(context.filesDir, "daily_totals"), "$day.json")
        return if (file.exists()) JSONObject(file.readText()) else null
    }

    /**
     * Append a single interval row to a per-day CSV in internal storage.
     * File: filesDir/daily_intervals/YYYY-MM-DD.csv
     */
    fun addInterval(day: LocalDate, pkg: String, startMs: Long, endMs: Long) {
        if (endMs <= startMs) return

        val dir = File(context.filesDir, "daily_intervals")
        dir.mkdirs()
        val file = File(dir, "$day.csv")

        val writeHeader = !file.exists()
        file.appendText(
            buildString {
                if (writeHeader) {
                    append("date,package,start_ms,end_ms,duration_ms\n")
                }
                val dur = endMs - startMs
                append("$day,$pkg,$startMs,$endMs,$dur\n")
            }
        )
    }
    override fun addDuration(day: LocalDate, pkg: String, deltaMs: Long) {
        addToDay(day, pkg, deltaMs)
    }

    override fun addInterval(interval: Interval) {
        // Overload: delegates to the CSV append function below
        addInterval(interval.day, interval.pkg, interval.startMs, interval.endMs)
    }
}
