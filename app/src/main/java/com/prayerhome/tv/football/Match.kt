package com.prayerhome.tv.football

import java.time.Instant

data class Side(val id: String, val name: String, val short: String, val img: String) {
    /**
     * livescore.com's own badge host. It serves both "enet/…" and the newer "teambadge/…" images
     * (Chelsea, Liverpool, Spurs…); the older lsm-static host only has the enet ones.
     */
    val logoUrl get() = if (img.isEmpty()) "" else "https://storage.lsmedia1.com/images/team/medium/$img"
    val logoUrlOld get() = if (img.isEmpty()) "" else "https://lsm-static-prod.livescore.com/medium/$img"
}

enum class MatchState { UPCOMING, LIVE, FINISHED, OFF }

/** LiveScore's status text and numeric status code → the state shown on screen. */
fun stateOf(status: String, statusCode: Int): MatchState = when {
    status.endsWith("'") || status == "HT" || statusCode in LIVE_CODES -> MatchState.LIVE
    status == "NS" || statusCode == 1 -> MatchState.UPCOMING
    status == "FT" || status == "AET" || status == "AP" || statusCode in DONE_CODES -> MatchState.FINISHED
    else -> MatchState.OFF
}

/** "67'", "HT", "FT", "Postponed" ... */
fun statusText(status: String): String = when (status) {
    "Postp." -> "Postponed"
    "Canc." -> "Cancelled"
    "Aband." -> "Abandoned"
    "AP" -> "FT (pens)"
    "Int." -> "Interrupted"
    else -> status
}

// 2 = 1st half, 3 = 2nd half, 4/7/8/9 = extra time & penalties, 10 = half time, 12 = interrupted
private val LIVE_CODES = setOf(2, 3, 4, 7, 8, 9, 10, 12)
private val DONE_CODES = setOf(6, 11, 13, 91)

data class Match(
    val id: String,
    val kickoff: Instant,
    val home: Side,
    val away: Side,
    val homeScore: String,
    val awayScore: String,
    /** LiveScore's status text: "NS", "67'", "45+2'", "HT", "FT", "AET", "AP", "Postp." ... */
    val status: String,
    val statusCode: Int,
    /** Ready-to-show competition name, e.g. "LaLiga", "Egypt Premier League", "CAF Champions League". */
    val league: String,
    /** LiveScore competition id ("65" for the Premier League), for the standings panel. Empty on old caches. */
    val comp: String = "",
    /** The stage's LiveScore id (a league's group or a cup's round), so the panel opens on the right one. */
    val stage: String = "",
) {
    val state: MatchState get() = stateOf(status, statusCode)

    val isLive get() = state == MatchState.LIVE
    val hasScore get() = homeScore.isNotEmpty() && (state == MatchState.LIVE || state == MatchState.FINISHED)

    /** "67'", "HT", "FT", "Postponed" ... */
    val statusLabel: String get() = statusText(status)
}
