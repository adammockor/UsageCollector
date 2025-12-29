package com.adammockor.usagecollector.core

import java.time.LocalDate

interface OutputSink {
    fun addDuration(day: LocalDate, pkg: String, deltaMs: Long)
    fun addInterval(interval: Interval)
}