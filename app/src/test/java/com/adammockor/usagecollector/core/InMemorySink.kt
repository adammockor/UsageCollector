package com.adammockor.usagecollector.core

import java.time.LocalDate

class InMemorySink : OutputSink {
    val durations = mutableListOf<Triple<LocalDate, String, Long>>()
    val intervals = mutableListOf<Interval>()

    override fun addDuration(day: LocalDate, pkg: String, deltaMs: Long) {
        durations += Triple(day, pkg, deltaMs)
    }

    override fun addInterval(interval: Interval) {
        intervals += interval
    }
}