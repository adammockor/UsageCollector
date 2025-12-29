package com.adammockor.usagecollector.core

import java.time.LocalDate

data class UE(
    val ts: Long,
    val type: Int,
    val pkg: String? = null
)

data class CollectorState(
    val lastTs: Long,
    val screenInteractive: Boolean,
    val currentPkg: String?,
    val currentStart: Long
)

data class Interval(
    val day: LocalDate,
    val pkg: String,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = endMs - startMs
}