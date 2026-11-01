package com.prayerhome.tv.football

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

private const val TAG = "Goals"

/** One goal: the minute it went in, who scored it and for which side. */
data class Goal(
    val minute: Int,
    val added: Int,
    /** The feed's own label, e.g. "8'" or "45+1'". */
    val label: String,
    val player: String,
    val home: Boolean,
    /** "pen" for a penalty, "og" for an own goal. */
    val note: String = "",
) {
    val order: Int get() = minute * 100 + added
}

/**
 * The goals of one match, read from LiveScore's own match page.
 *
 * The score, the minute and the state are not here: they come from [MatchRepo]'s live Match — the
 * same values the matches list, the home screen and the banners show — so the card can never
 * disagree with them. This only answers "who scored", and only when that could have changed:
 *
 * - before kick-off, and for a postponed or cancelled tie, nothing is read;
 * - while the match is on, the page is read as soon as the score changes (so a new goal shows its
 *   scorer within seconds) and at most once every 30 seconds otherwise;
 * - once it has finished it is read once, then once more a couple of minutes later for late
 *   corrections and own-goal reassignments, and that copy is kept for a week.
 *
 * The copy on disk means the card opens with its goals already on screen, and it remembers the
 * score it was read at, so the "score changed" test works across a restart too.
 */
class GoalsRepo(private val context: Context) {
    /** A stored copy: the goals, the total score they were read at, and whether the after-FT read is done. */
    private class Entry(val at: Long, val score: Int, val settled: Boolean, val goals: List<Goal>?)

    private val dir = File(context.filesDir, "goals").apply { mkdirs() }
    private val mutex = Mutex()

    init {
        cleanLegacy(context)
        // A goal list is only worth keeping for a while.
        runCatching {
            val stale = System.currentTimeMillis() - 10L * 24 * 3600 * 1000
            dir.listFiles()?.forEach { if (it.lastModified() < stale) it.delete() }
        }
    }

    /** The stored goals, whatever their age: the card opens on them instead of an empty box. */
    fun cached(match: Match): List<Goal>? = read(match.id)?.goals

    /**
     * How often the card should ask: every 15 seconds while the match is in play or within five
     * minutes of kick-off, every five minutes otherwise. The page itself is only read when it could
     * have changed (see [load]) — this is how often the card looks, not how often we fetch.
     */
    fun interval(match: Match): Long {
        val entry = read(match.id)
        // Finished but not yet checked again: come back exactly when the corrections are due.
        if (match.state == MatchState.FINISHED && entry != null && !entry.settled) {
            return (SETTLE_MS - (System.currentTimeMillis() - entry.at)).coerceIn(POLL_MS, SETTLE_MS)
        }
        if (match.isLive) return POLL_MS
        val now = Instant.now()
        val near = !now.isBefore(match.kickoff.minus(MatchRepo.LEAD)) && !now.isAfter(match.kickoff.plus(MatchRepo.LEAD))
        return if (near) POLL_MS else 5 * 60_000L
    }

    /** The match's goals as they are now; null when the page couldn't be read. */
    suspend fun load(match: Match): List<Goal>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val entry = read(match.id)
            val score = totalScore(match)
            when (match.state) {
                // Nobody has scored before kick-off, and a postponed or cancelled tie never will.
                MatchState.UPCOMING, MatchState.OFF -> entry?.goals ?: emptyList()
                MatchState.FINISHED -> when {
                    entry?.settled == true -> entry.goals
                    entry == null -> fetch(match, score, settled = false)
                    now - entry.at >= SETTLE_MS -> fetch(match, score, settled = true)
                    else -> entry.goals
                }
                MatchState.LIVE -> {
                    val changed = entry != null && score >= 0 && entry.score != score
                    if (entry == null || changed || now - entry.at >= LIVE_MS) {
                        fetch(match, score, settled = false)
                    } else {
                        entry.goals
                    }
                }
            }
        }
    }

    private fun fetch(match: Match, score: Int, settled: Boolean): List<Goal>? {
        val goals = LiveScoreGoals.read(match) ?: return read(match.id)?.goals
        save(match.id, Entry(System.currentTimeMillis(), score, settled, goals))
        Log.i(TAG, "${match.id}: ${goals.size} goals at ${match.homeScore}-${match.awayScore} ${match.statusLabel}")
        return goals
    }

    private fun totalScore(match: Match): Int {
        val home = match.homeScore.toIntOrNull() ?: return -1
        val away = match.awayScore.toIntOrNull() ?: return -1
        return home + away
    }

    // ------------------------------------------------------------------ disk

    private fun file(id: String) = File(dir, "$id.json")

    private fun read(id: String): Entry? = runCatching {
        val o = JSONObject(file(id).readText())
        val goals = o.optJSONArray("g")?.let { a ->
            List(a.length()) { i ->
                val g = a.getJSONObject(i)
                Goal(g.optInt("m"), g.optInt("a"), g.optString("l"), g.optString("p"), g.optBoolean("h"), g.optString("n"))
            }
        }
        Entry(o.optLong("at"), o.optInt("s", -1), o.optBoolean("x"), goals)
    }.getOrNull()

    private fun save(id: String, entry: Entry) {
        runCatching {
            val a = JSONArray()
            entry.goals.orEmpty().forEach { g ->
                a.put(JSONObject().put("m", g.minute).put("a", g.added).put("l", g.label).put("p", g.player).put("h", g.home).put("n", g.note))
            }
            val tmp = File(dir, "$id.part")
            tmp.writeText(JSONObject().put("at", entry.at).put("s", entry.score).put("x", entry.settled).put("g", a).toString())
            tmp.renameTo(file(id))
        }
    }

    companion object {
        /**
         * Drops the copies older builds kept beside the goals: v1.7 and v1.8 stored one file per
         * source, a day of schedules and their match ids. Called at start-up and by [init]; both are
         * no-ops once the folders are gone.
         */
        fun cleanLegacy(context: Context) {
            runCatching { File(context.filesDir, "live").deleteRecursively() }
            runCatching { File(context.filesDir, "scorers").deleteRecursively() }
        }

        /** How often the card looks while the match is on (or about to start). */
        private const val POLL_MS = 15_000L

        /** A live page is read at most this often, and always when the score changes. */
        private const val LIVE_MS = 30_000L

        /** The late-correction read of a finished match comes this long after the first one. */
        private const val SETTLE_MS = 150_000L
    }
}
