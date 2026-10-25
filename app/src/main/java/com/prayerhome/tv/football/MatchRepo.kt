package com.prayerhome.tv.football

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.prayerhome.tv.App
import com.prayerhome.tv.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

private const val TAG = "MatchRepo"

/**
 * Keeps the selected teams' matches from yesterday to a week ahead.
 *
 * - The full window is re-downloaded at most every 2 hours (day files, ~10–100 KB each).
 * - While the home screen is visible and one of the teams is playing (or about to),
 *   the small "live" feed is polled every 15 seconds, so score and minute stay current.
 * - While another app is in front, [MatchWatch]'s alarms call [backgroundTick] every 30 s, but only
 *   while a match is on (or about to start) and its kick-off/goal banners are wanted.
 */
class MatchRepo(private val context: Context, private val prefs: Prefs) {
    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val cacheFile = File(context.filesDir, "matches.json")
    private val teamsFile = File(context.filesDir, "teams-index.json")
    private val mutex = Mutex()
    private val pollMutex = Mutex()
    private var lastFull = Instant.EPOCH
    private var lastTeamKey = ""
    private val dayRefetchAt = HashMap<String, Instant>()

    init {
        runCatching {
            if (cacheFile.exists()) {
                val o = JSONObject(cacheFile.readText())
                lastFull = Instant.ofEpochSecond(o.getLong("t"))
                lastTeamKey = o.optString("teams")
                val a = o.getJSONArray("m")
                _matches.value = List(a.length()) { LiveScore.fromJson(a.getJSONObject(it)) }
            }
        }
    }

    private fun teamKey() = prefs.teams.joinToString(",") { it.id }

    /** Runs until cancelled (i.e. while the launcher is in the foreground). */
    suspend fun runWhileVisible() = withContext(Dispatchers.IO) {
        while (isActive) {
            val now = Instant.now()
            if (teamKey() != lastTeamKey || Duration.between(lastFull, now) > Duration.ofHours(2)) {
                refreshAll()
            }
            val hot = _matches.value.filter { inLiveWindow(it, now) }
            if (hot.isNotEmpty()) {
                pollLive(hot)
                delay(15_000)
            } else {
                delay(60_000)
            }
        }
    }

    suspend fun refreshAll() = mutex.withLock {
        val ids = prefs.teams.map { it.id }.toSet()
        val key = teamKey()
        val today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate()
        val days = (-1L..7L).map { today.plusDays(it) }
        try {
            val pages = coroutineScope {
                days.map { d -> async(Dispatchers.IO) { LiveScore.day(d, ids, collectTeams = true) } }.awaitAll()
            }
            val all = pages.flatMap { it.matches }.distinctBy { it.id }.sortedBy { it.kickoff }
            _matches.value = all
            lastFull = Instant.now()
            lastTeamKey = key
            _status.value = ""
            save()
            saveTeamIndex(pages.flatMap { it.teams })
            Log.i(TAG, "refreshed ${all.size} matches")
            MatchAlerts.observe(context, all)
            MatchWatch.schedule(context)
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed", e)
            _status.value = "Scores offline"
        }
    }

    /**
     * One background check, called by [MatchWatch]'s alarm while another app is in front: a full
     * refresh if the fixtures are old, then the small live feed if a match is on.
     */
    suspend fun backgroundTick() = withContext(Dispatchers.IO) {
        // Home polls on its own while visible; nobody would see a banner while the screen is off
        if (App.homeVisible || !context.getSystemService(PowerManager::class.java).isInteractive) return@withContext
        val now = Instant.now()
        if (teamKey() != lastTeamKey || Duration.between(lastFull, now) > Duration.ofHours(6)) refreshAll()
        val hot = _matches.value.filter { inLiveWindow(it, now) }
        if (hot.isNotEmpty()) pollLive(hot)
    }

    private suspend fun pollLive(hot: List<Match>) {
        pollMutex.withLock { pollLiveLocked(hot) }
    }

    private fun pollLiveLocked(hot: List<Match>) {
        val ids = prefs.teams.map { it.id }.toSet()
        val live = try {
            LiveScore.live(ids).associateBy { it.id }
        } catch (e: Exception) {
            Log.w(TAG, "live poll failed", e)
            _status.value = "Scores offline"
            return
        }
        _status.value = ""
        val now = Instant.now()
        var changed = false
        val updated = _matches.value.map { m -> live[m.id]?.also { if (it != m) changed = true } ?: m }.toMutableList()
        // A hot match that isn't in the live feed has either not kicked off yet or just ended:
        // re-read its day (at most once a minute) to pick up the final whistle.
        for (m in hot) {
            if (m.id in live || now < m.kickoff) continue
            val day = m.kickoff.atZone(ZoneOffset.UTC).toLocalDate()
            val key = day.toString()
            val last = dayRefetchAt[key]
            if (last != null && Duration.between(last, now) < Duration.ofMinutes(1)) continue
            dayRefetchAt[key] = now
            runCatching { LiveScore.day(day, ids, collectTeams = false).matches }.getOrNull()?.forEach { fresh ->
                val idx = updated.indexOfFirst { it.id == fresh.id }
                if (idx >= 0 && updated[idx] != fresh) { updated[idx] = fresh; changed = true }
            }
        }
        if (changed) {
            _matches.value = updated
            save()
        }
        MatchAlerts.observe(context, updated)
    }

    private fun save() {
        runCatching {
            val a = JSONArray()
            _matches.value.forEach { a.put(LiveScore.toJson(it)) }
            cacheFile.writeText(JSONObject().put("t", lastFull.epochSecond).put("teams", lastTeamKey).put("m", a).toString())
        }
    }

    private fun saveTeamIndex(fresh: List<LiveScore.TeamInfo>) {
        runCatching {
            val known = loadTeamIndex().associateBy { it.id }.toMutableMap()
            // Keep a badge learned earlier if this sighting of the team came without one.
            fresh.forEach { t -> known[t.id] = if (t.img.isEmpty()) t.copy(img = known[t.id]?.img.orEmpty()) else t }
            val a = JSONArray()
            known.values.forEach { a.put(JSONObject().put("i", it.id).put("n", it.name).put("m", it.img).put("c", it.country)) }
            teamsFile.writeText(a.toString())
        }
    }

    /** Every team seen in recent fixtures, for the "add team" search. */
    fun loadTeamIndex(): List<LiveScore.TeamInfo> = runCatching {
        val a = JSONArray(teamsFile.readText())
        List(a.length()) { a.getJSONObject(it).let { o -> LiveScore.TeamInfo(o.getString("i"), o.getString("n"), o.optString("m"), o.optString("c")) } }
    }.getOrDefault(emptyList())

    companion object {
        /** Live polling starts this long before kick-off. */
        val LEAD: Duration = Duration.ofMinutes(5)

        fun inLiveWindow(m: Match, now: Instant): Boolean = when (m.state) {
            MatchState.LIVE -> true
            MatchState.UPCOMING -> now > m.kickoff.minus(LEAD) && now < m.kickoff.plus(Duration.ofHours(4))
            else -> false
        }

        /** Home screen: live matches first, then whichever are closest to now (before or after). */
        fun closest(all: List<Match>, now: Instant, count: Int = 3): List<Match> {
            val (live, rest) = all.filter { it.state != MatchState.OFF || it.kickoff > now }.partition { it.isLive }
            // Results fade fast: a game that ended a couple of hours ago still counts as "closest",
            // but yesterday's results shouldn't push out tonight's fixtures.
            val distance = { x: Match ->
                val d = Duration.between(now, x.kickoff).seconds
                if (d >= 0) d else ((-d) - 2 * 3600).coerceAtLeast(0) * 3
            }
            val picked = live.sortedBy { it.kickoff } + rest.sortedBy(distance)
            return picked.take(count).sortedWith(compareByDescending<Match> { it.isLive }.thenBy { it.kickoff })
        }

        /** Matches page: everything within 24 h either side of now, plus the home-screen three. */
        fun window(all: List<Match>, now: Instant): List<Match> {
            val from = now.minus(Duration.ofHours(24))
            val to = now.plus(Duration.ofHours(24))
            return (all.filter { it.kickoff in from..to } + closest(all, now)).distinctBy { it.id }.sortedBy { it.kickoff }
        }
    }
}
