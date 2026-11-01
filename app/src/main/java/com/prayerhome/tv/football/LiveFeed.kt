package com.prayerhome.tv.football

import android.content.Context
import android.util.Log
import com.prayerhome.tv.BuildConfig
import com.prayerhome.tv.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.Instant

private const val TAG = "Live"

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

/** The service an opened match's score, minute and goals are read from. */
enum class LiveSrc(val id: String, val label: String) {
    SOFA("sofa", "SofaScore"),
    LIVESCORE("livescore", "LiveScore"),
}

/**
 * Which service is used, in the order they are tried. SofaScore is the one relied on; LiveScore is
 * still here, kept whole and pickable from Settings, for the day SofaScore needs replacing.
 */
enum class LiveMode(val id: String, val label: String) {
    SOFA("sofa", "SofaScore"),
    SOFA_ONLY("sofa-only", "SofaScore only"),
    LIVESCORE("livescore", "LiveScore");

    val sources: List<LiveSrc>
        get() = when (this) {
            SOFA -> listOf(LiveSrc.SOFA, LiveSrc.LIVESCORE)
            SOFA_ONLY -> listOf(LiveSrc.SOFA)
            LIVESCORE -> listOf(LiveSrc.LIVESCORE)
        }

    companion object {
        fun of(id: String?): LiveMode = entries.firstOrNull { it.id == id } ?: SOFA
    }
}

/** The opened match exactly as one source tells it: result, minute and goals, all from the same place. */
data class MatchLive(
    val src: LiveSrc,
    val state: MatchState,
    val homeScore: String,
    val awayScore: String,
    /** "85'", "HT", "FT", "AET" — ready to show. */
    val label: String,
    /** null when they couldn't be read; empty when there are none (yet). */
    val goals: List<Goal>?,
) {
    val live get() = state == MatchState.LIVE
    val hasScore get() = homeScore.isNotEmpty() && (state == MatchState.LIVE || state == MatchState.FINISHED)
}

/**
 * The live side of an opened match: its current result, its minute and its goals.
 *
 * SofaScore answers first (one small request for the event, one for its incidents); LiveScore — its
 * fixtures feed for the result and its own match page for the goals — answers when SofaScore is
 * unreachable, or on its own when picked in Settings. The copy on disk is opened instantly: it is
 * kept for 30 seconds while the match is in play, five minutes otherwise, and a week once it has
 * finished, so the 5-minute / 30-second checks below cost nothing when nothing is happening.
 */
class LiveRepo(private val context: Context, private val prefs: Prefs) {
    private class Entry(val at: Long, val live: MatchLive)

    private val dir = File(context.filesDir, "live").apply { mkdirs() }

    init {
        // The previous build kept goal copies here; the live copies replace them.
        runCatching { File(context.filesDir, "scorers").deleteRecursively() }
    }
    private val mutex = Mutex()

    /** In debug builds the base can be pointed at a local mock (`--es sofa http://…`), to test the path. */
    private val sofa by lazy {
        val base = (if (BuildConfig.DEBUG) prefs.sofaBase else "").ifEmpty { "https://api.sofascore.com/api/v1" }
        SofaScore(dir, base)
    }

    /** How long the copy stays good: a live match moves, a finished one never does again. */
    private fun ttl(live: MatchLive): Long = when {
        live.live -> 30_000L
        live.state == MatchState.FINISHED -> 7 * 24 * 3600_000L
        else -> 5 * 60_000L
    }

    /**
     * The poll interval the page follows: 30 seconds while the match is in play or within five
     * minutes of kick-off, five minutes otherwise. The decision is made on the source's own status,
     * not on the fixtures feed, so the two always agree.
     */
    fun interval(match: Match, live: MatchLive?): Long {
        if (live?.live == true) return 30_000L
        val toKickoff = Duration.between(Instant.now(), match.kickoff)
        val away = if (toKickoff.isNegative) toKickoff.negated() else toKickoff
        return if (away.seconds <= 5 * 60) 30_000L else 5 * 60_000L
    }

    /** The last copy on disk, whatever its age: the card opens on it instead of an empty box. */
    fun cached(match: Match): MatchLive? {
        LiveMode.of(prefs.liveMode).sources.forEach { src ->
            read(src, match.id)?.let { return it.live }
        }
        return null
    }

    /** The freshest copy available, from the first source that answers; null when none did. */
    suspend fun load(match: Match): MatchLive? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            var stale: MatchLive? = null
            for (src in LiveMode.of(prefs.liveMode).sources) {
                val cached = read(src, match.id)
                if (cached != null && now - cached.at < ttl(cached.live)) return@withLock cached.live
                val fresh = runCatching { fetch(src, match) }.getOrNull()
                if (fresh != null) {
                    save(src, match.id, fresh)
                    Log.i(TAG, "${match.id} ${src.label}: ${fresh.label.ifEmpty { fresh.state.name }} ${fresh.homeScore}-${fresh.awayScore}, goals ${fresh.goals?.size ?: "?"}")
                    return@withLock fresh
                }
                if (cached != null) stale = cached.live
            }
            stale
        }
    }

    private fun fetch(src: LiveSrc, match: Match): MatchLive? = when (src) {
        LiveSrc.SOFA -> sofaLive(match)
        LiveSrc.LIVESCORE -> liveScore(match)
    }

    // ------------------------------------------------------------------ SofaScore

    private fun sofaLive(match: Match): MatchLive? {
        val event = sofa.event(match) ?: return null
        val state = SofaScore.state(event)
        val home = event.optJSONObject("homeScore")?.optInt("current", -1) ?: -1
        val away = event.optJSONObject("awayScore")?.optInt("current", -1) ?: -1
        val scored = state == MatchState.LIVE || state == MatchState.FINISHED
        val id = event.optLong("id")
        // Nobody has scored before kick-off, and a postponed tie never will.
        val goals = when {
            state == MatchState.UPCOMING -> emptyList()
            id <= 0 -> null
            else -> sofa.goals(id)
        }
        return MatchLive(
            LiveSrc.SOFA,
            state,
            if (scored && home >= 0) "$home" else "",
            if (scored && away >= 0) "$away" else "",
            SofaScore.label(event),
            goals,
        )
    }

    // ------------------------------------------------------------------ LiveScore (kept, off by default)

    private fun liveScore(match: Match): MatchLive? {
        val goals = if (match.state == MatchState.UPCOMING) emptyList() else LiveScoreGoals.read(match)
        return MatchLive(LiveSrc.LIVESCORE, match.state, match.homeScore, match.awayScore, match.statusLabel, goals)
    }

    // ------------------------------------------------------------------ disk cache

    private fun file(src: LiveSrc, id: String) = File(dir, "${src.id}-$id.json")

    private fun read(src: LiveSrc, id: String): Entry? = runCatching {
        val o = JSONObject(file(src, id).readText())
        if (o.optString("src") != src.id) return@runCatching null
        val goals = o.optJSONArray("g")?.let { a ->
            List(a.length()) { i ->
                val g = a.getJSONObject(i)
                Goal(g.optInt("m"), g.optInt("a"), g.optString("l"), g.optString("p"), g.optBoolean("h"), g.optString("n"))
            }
        }
        val state = runCatching { MatchState.valueOf(o.optString("st")) }.getOrDefault(MatchState.OFF)
        Entry(
            o.optLong("at"),
            MatchLive(src, state, o.optString("hs"), o.optString("as"), o.optString("l"), goals),
        )
    }.getOrNull()

    private fun save(src: LiveSrc, id: String, live: MatchLive) {
        runCatching {
            val a = JSONArray()
            live.goals.orEmpty().forEach { g ->
                a.put(JSONObject().put("m", g.minute).put("a", g.added).put("l", g.label).put("p", g.player).put("h", g.home).put("n", g.note))
            }
            val o = JSONObject()
                .put("at", System.currentTimeMillis())
                .put("src", src.id)
                .put("st", live.state.name)
                .put("hs", live.homeScore)
                .put("as", live.awayScore)
                .put("l", live.label)
            if (live.goals != null) o.put("g", a)
            val tmp = File(dir, "${src.id}-$id.part")
            tmp.writeText(o.toString())
            tmp.renameTo(file(src, id))
        }
    }
}
