package com.kglabs28.yapnplay.backend.application.weblog

import kotlinx.serialization.Serializable

/**
 * The issues page: crash-free numbers for the period ([summary]), the versions seen (for the
 * filter) and the issues, most events first.
 */
@Serializable
data class WebIssuesPage(val summary: WebCrashSummary, val versions: List<String>, val issues: List<WebIssueView>)

/**
 * Sessions are page loads, users are browsers (a random id each). Crash-free: without a CRASH or
 * FAILED event. [days] buckets the period's crashing sessions a day (India time), oldest first.
 */
@Serializable
data class WebCrashSummary(
    val sessions: Int,
    val crashSessions: Int,
    val users: Int,
    val crashUsers: Int,
    val crashFreeSessionsPct: Double,
    val crashFreeUsersPct: Double,
    val days: List<WebDayCount>,
)

@Serializable
data class WebDayCount(val day: String, val count: Int)

/**
 * One issue: its events (one a page load), how often it happened in all ([occurrences]), how many
 * browsers it hit ([users]), when, in which [versions], where it broke ([where], from a decoded
 * stack) and its [state] (OPEN, CLOSED or REGRESSED: it happened again after it was closed).
 */
@Serializable
data class WebIssueView(
    val id: String,
    val kind: String,
    val title: String,
    val where: String,
    val events: Int,
    val occurrences: Int,
    val users: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val versions: List<String>,
    val days: List<WebDayCount>,
    val state: String,
    val closedAt: Long = 0L,
    val closedBy: String = "",
    val note: String = "",
)

/** One issue's page: the issue, how its events split ([breakdowns]) and its events, newest first. */
@Serializable
data class WebIssueDetail(val issue: WebIssueView, val breakdowns: List<WebBreakdown>, val events: List<WebLogView>)

/** How an issue's events split by one thing ([name]: OS, browser, device…), most first. */
@Serializable
data class WebBreakdown(val name: String, val values: List<WebShare>)

@Serializable
data class WebShare(val value: String, val events: Int, val pct: Double)

/** Closing an issue, with an optional note (the fix, the release). */
@Serializable
data class WebIssueCloseBody(val note: String = "")
