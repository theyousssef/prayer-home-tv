package com.prayerhome.tv.football

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val TAG = "Standings"

/** One team's line in a league table (or a cup group table). */
data class TableRow(
    val teamId: String,
    val name: String,
    val short: String,
    val img: String,
    val rank: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDiff: Int,
    val points: Int,
)

/** One table. A stage can hold more than one (a league split into championship / relegation groups). */
data class TableGroup(val name: String, val rows: List<TableRow>)

/** A stage of a competition: the league itself, or one group of a cup's group phase. */
data class TableStage(val id: String, val name: String, val groups: List<TableGroup>) {
    /** "Scottish League Cup: Group A" → "Group A", "Premier League" → "Premier League". */
    val shortName: String get() = name.substringAfterLast(':').trim().ifEmpty { name }
}

/** One knockout tie. Sides are empty ("TBD") until the draw fills them in; byes have one side only. */
data class Tie(
    val id: String,
    val home: Side,
    val away: Side,
    val homeScore: String,
    val awayScore: String,
    val homePens: String,
    val awayPens: String,
    val status: String,
    val statusCode: Int,
    val kickoff: Instant,
    /** 1 = home won, 2 = away won, 0 = not decided (or unknown). */
    val winner: Int,
) {
    val state: MatchState get() = stateOf(status, statusCode)
    val isLive: Boolean get() = state == MatchState.LIVE
    val hasScore: Boolean get() = homeScore.isNotEmpty() && state != MatchState.UPCOMING
    val statusLabel: String get() = statusText(status)

    /** "4–3 pens" when the tie went to a shoot-out. */
    val pens: String get() = if (homePens.isNotEmpty() && awayPens.isNotEmpty()) "$homePens–$awayPens pens" else ""
}

data class BracketRound(val number: Int, val name: String, val ties: List<Tie>)
data class BracketDraw(val name: String, val rounds: List<BracketRound>)
data class Bracket(val draws: List<BracketDraw>)

/** A goal or status change for one live tie. */
data class LiveEvent(val homeScore: String, val awayScore: String, val status: String, val statusCode: Int)

/** What is happening right now in one competition: which teams are on the pitch, and the live ties. */
data class LiveNow(
    /** team id → its live label ("67'", "HT", "45+2'"). */
    val teams: Map<String, String> = emptyMap(),
    /** match id → its live score and status. */
    val events: Map<String, LiveEvent> = emptyMap(),
) {
    val isEmpty: Boolean get() = teams.isEmpty() && events.isEmpty()
}

/**
 * Everything the standings panel shows for one competition: its table (if it has one), its cup
 * draw (if it has one), and who is playing right now.
 */
data class Competition(
    val id: String,
    val name: String,
    val country: String,
    val tables: List<TableStage> = emptyList(),
    val bracket: Bracket? = null,
    val live: LiveNow = LiveNow(),
) {
    val hasTable: Boolean get() = tables.any { it.groups.any { g -> g.rows.isNotEmpty() } }
    val hasBracket: Boolean get() = bracket?.draws?.any { it.rounds.isNotEmpty() } == true

    /**
     * Merges a 30-second live poll into this snapshot and reports whether a match it was showing
     * as live has since ended (which means the table / draw itself is now out of date).
     */
    fun applyLive(now: LiveNow): Pair<Competition, Boolean> {
        val ended = live.teams.keys.any { it !in now.teams } || live.events.keys.any { it !in now.events }
        if (now == live) return this to ended
        // A tie that was live in the previous poll and is no longer in the feed has finished: show the
        // final whistle with the last score we saw rather than leaving a stuck "80'".
        val finished = live.events.keys - now.events.keys
        val b = bracket?.let { br ->
            br.copy(draws = br.draws.map { d ->
                if (d.rounds.none { r -> r.ties.any { it.id in now.events || it.id in finished } }) d
                else d.copy(rounds = d.rounds.map { r ->
                    r.copy(ties = r.ties.map { t ->
                        val e = now.events[t.id]
                        when {
                            e != null -> t.copy(
                                homeScore = e.homeScore.ifEmpty { t.homeScore },
                                awayScore = e.awayScore.ifEmpty { t.awayScore },
                                status = e.status.ifEmpty { t.status },
                                statusCode = if (e.statusCode != 0) e.statusCode else t.statusCode,
                            )
                            t.id in finished && t.isLive -> t.copy(status = "FT", statusCode = 6)
                            else -> t
                        }
                    })
                })
            })
        }
        return copy(live = now, bracket = b) to ended
    }
}

/**
 * Downloads a competition's standings and draw, keeps the last few in memory and saves a copy on
 * disk for a week, so the panel opens instantly (then refreshes in the background) even after a
 * restart. The week is per competition and restarts every time that competition is opened.
 */
class StandingsRepo(private val context: Context) {
    private class Entry(val data: Competition, val at: Instant)

    private val dir = File(context.filesDir, "standings").apply { mkdirs() }
    private val mem = HashMap<String, Entry>()
    private val mutex = Mutex()
    private var swept = false

    /** What is in memory right now, for an immediate first frame. */
    fun cached(compId: String): Competition? = mem[compId]?.data

    /**
     * The saved copy of [compId] (memory first, then disk), or null when there is none or it is
     * older than [WEEK_MS]. Opening it restarts that week.
     */
    suspend fun loadCached(compId: String): Competition? = withContext(Dispatchers.IO) {
        if (compId.isEmpty()) return@withContext null
        mem[compId]?.data?.let { touch(compId); return@withContext it }
        mutex.withLock {
            mem[compId]?.data?.let { touch(compId); return@withLock it }
            if (!swept) { sweep(); swept = true }
            val f = file(compId)
            if (!f.exists()) return@withLock null
            if (System.currentTimeMillis() - f.lastModified() > WEEK_MS) {
                f.delete()
                return@withLock null
            }
            val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return@withLock null
            val data = runCatching { readCache(compId, o) }.getOrNull()
            if (data == null || (!data.hasTable && !data.hasBracket)) return@withLock null
            mem[compId] = Entry(data, Instant.now())
            touch(compId)
            data
        }
    }

    /** Downloads the competition now (the panel does this once per open) and saves it for a week. */
    suspend fun refresh(compId: String): Competition = withContext(Dispatchers.IO) {
        mutex.withLock {
            val got = fetch(compId)
            save(compId, got.table, got.draw)
            mem[compId] = Entry(got.data, Instant.now())
            if (mem.size > KEEP) mem.entries.minByOrNull { it.value.at }?.let { mem.remove(it.key) }
            got.data
        }
    }

    /** The matches of [compId] in play right now; a ~10–30 KB request. */
    suspend fun live(compId: String): LiveNow = withContext(Dispatchers.IO) {
        liveOf(LiveScore.liveAll(), compId)
    }

    private fun liveOf(all: List<Match>, compId: String): LiveNow {
        val matches = all.filter { it.comp == compId }
        val teams = HashMap<String, String>(matches.size * 2)
        val events = HashMap<String, LiveEvent>(matches.size)
        for (m in matches) {
            teams[m.home.id] = m.statusLabel
            teams[m.away.id] = m.statusLabel
            events[m.id] = LiveEvent(m.homeScore, m.awayScore, m.status, m.statusCode)
        }
        return LiveNow(teams, events)
    }

    // ------------------------------------------------------------------ disk cache

    private fun file(compId: String) = File(dir, "$compId.json")

    /** Pushes the week out again, so a competition you keep opening never gets swept. */
    private fun touch(compId: String) {
        runCatching { file(compId).setLastModified(System.currentTimeMillis()) }
    }

    private fun sweep() {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > WEEK_MS) it.delete() }
    }

    private fun save(compId: String, table: String?, draw: String?) {
        runCatching {
            val o = JSONObject()
                .put("v", 1)
                .put("at", Instant.now().epochSecond)
                .put("table", table.orEmpty())
                .put("draw", draw.orEmpty())
            val f = file(compId)
            val tmp = File(dir, "$compId.part")
            tmp.writeText(o.toString())
            tmp.renameTo(f)
        }
    }

    private fun readCache(compId: String, o: JSONObject): Competition {
        val table = o.optString("table")
        val draw = o.optString("draw")
        val tables = if (table.isNotEmpty()) parseTables(table) else emptyList()
        val bracket = if (draw.isNotEmpty()) parseBracket(draw) else null
        val (name, country) = names(table)
        return Competition(compId, name, country, tables, bracket)
    }

    // ------------------------------------------------------------------ network

    private class Fetched(val data: Competition, val table: String?, val draw: String?)

    private suspend fun fetch(compId: String): Fetched = coroutineScope {
        val tableBody = async { runCatching { LiveScore.competitionTable(compId) } }
        val drawBody = async { runCatching { LiveScore.competitionDraw(compId) } }
        val liveAll = async { runCatching { LiveScore.liveAll() }.getOrNull() }
        val t = tableBody.await()
        val d = drawBody.await()
        val table = t.getOrNull()
        val draw = d.getOrNull()
        val tables = table?.let { b -> runCatching { parseTables(b) }.getOrDefault(emptyList()) } ?: emptyList()
        val bracket = draw?.let { b -> runCatching { parseBracket(b) }.getOrNull() }
        // A competition that answers "nothing" is fine (LiveScore does that for some cups); one that
        // failed while yielding nothing at all is an error the panel can offer to retry.
        if (tables.isEmpty() && bracket == null) {
            (t.exceptionOrNull() ?: d.exceptionOrNull())?.let { throw it }
        }
        val (name, country) = names(table)
        val live = liveAll.await()?.let { liveOf(it, compId) } ?: LiveNow()
        Log.i(TAG, "competition $compId: ${tables.size} stages, ${bracket?.draws?.size ?: 0} draws, ${live.teams.size / 2} live teams")
        Fetched(Competition(compId, name, country, tables, bracket, live), table, draw)
    }

    private fun names(table: String?): Pair<String, String> {
        if (table.isNullOrEmpty()) return "" to ""
        return runCatching {
            val stage = JSONObject(table).optJSONArray("Stages")?.optJSONObject(0)
            (stage?.optString("CompN").orEmpty()) to (stage?.optString("CompD").orEmpty())
        }.getOrDefault("" to "")
    }

    private fun parseTables(body: String): List<TableStage> {
        val out = ArrayList<TableStage>()
        val stages = JSONObject(body).optJSONArray("Stages") ?: return out
        for (i in 0 until stages.length()) {
            val s = stages.getJSONObject(i)
            val l = s.optJSONObject("LeagueTable")?.optJSONArray("L") ?: continue
            val groups = ArrayList<TableGroup>()
            for (j in 0 until l.length()) {
                val item = l.getJSONObject(j)
                val tables = item.optJSONArray("Tables") ?: continue
                // 1 = overall, 2 = home, 3 = away; the overall one is the interesting one.
                var pick: JSONObject? = null
                for (k in 0 until tables.length()) {
                    val t = tables.getJSONObject(k)
                    if (t.optInt("LTT", 0) == 1) { pick = t; break }
                }
                if (pick == null && tables.length() > 0) pick = tables.getJSONObject(0)
                val rows = pick?.optJSONArray("team")?.let { teamRows(it) } ?: emptyList()
                if (rows.isNotEmpty()) {
                    val stageName = s.optString("Snm").ifEmpty { s.optString("CompN") }
                    val name = if (l.length() > 1) "${stageName.ifEmpty { "Table" }} ${j + 1}" else stageName
                    groups += TableGroup(name, rows)
                }
            }
            if (groups.isNotEmpty()) {
                out += TableStage(s.optString("Sid"), s.optString("Snm").ifEmpty { s.optString("CompN") }, groups)
            }
        }
        return mergeGroups(out)
    }

    /**
     * LiveScore gives some group phases one stage per group ("Group A", "Group B"...). Merge those into
     * one table so a World Cup's twelve groups are one scroll instead of twelve tabs. Split seasons
     * (a championship round and a relegation round) keep their stage picker.
     */
    private fun mergeGroups(stages: List<TableStage>): List<TableStage> {
        if (stages.size < 2) return stages
        if (stages.any { it.groups.size != 1 }) return stages
        if (stages.any { !it.shortName.startsWith("Group ", ignoreCase = true) }) return stages
        val groups = stages.mapIndexed { i, st -> st.groups[0].copy(name = stages[i].shortName) }
        val title = stages.first().name.substringBefore(':').trim().ifEmpty { "Groups" }
        return listOf(TableStage(stages.first().id, title, groups))
    }

    private fun teamRows(a: JSONArray): List<TableRow> {
        val rows = ArrayList<TableRow>(a.length())
        for (i in 0 until a.length()) {
            val t = a.getJSONObject(i)
            val won = t.optInt("win")
            val drawn = t.optInt("drw")
            val lost = t.optInt("lst")
            rows += TableRow(
                teamId = t.optString("Tid"),
                name = t.optString("Tnm"),
                short = t.optString("Snm"),
                img = t.optString("Img"),
                rank = t.optInt("rnk", i + 1),
                played = t.optInt("pld", won + drawn + lost),
                won = won,
                drawn = drawn,
                lost = lost,
                goalsFor = t.optInt("gf"),
                goalsAgainst = t.optInt("ga"),
                goalDiff = t.optInt("gd"),
                points = t.optInt("pts"),
            )
        }
        return rows
    }

    private fun parseBracket(body: String): Bracket? {
        val draws = JSONObject(body).optJSONArray("Draws") ?: return null
        val out = ArrayList<BracketDraw>()
        for (i in 0 until draws.length()) {
            val d = draws.getJSONObject(i)
            val rounds = ArrayList<BracketRound>()
            val rs = d.optJSONArray("Draw") ?: continue
            for (j in 0 until rs.length()) {
                val r = rs.getJSONObject(j)
                val evs = r.optJSONArray("Events") ?: continue
                val ties = ArrayList<Tie>(evs.length())
                for (k in 0 until evs.length()) {
                    val e = evs.getJSONObject(k)
                    val home = side(e.optJSONArray("T1"))
                    val away = side(e.optJSONArray("T2"))
                    val hs = e.optString("Tr1")
                    val as_ = e.optString("Tr2")
                    val hp = e.optString("Trp1")
                    val ap = e.optString("Trp2")
                    val code = e.optInt("Esid")
                    val status = e.optString("Eps")
                    ties += Tie(
                        id = e.optString("Eid"),
                        home = home,
                        away = away,
                        homeScore = hs,
                        awayScore = as_,
                        homePens = hp,
                        awayPens = ap,
                        status = status,
                        statusCode = code,
                        kickoff = kickoff(e.optLong("Esd", 0L)),
                        winner = winnerOf(e.optInt("Ewt"), hs, as_, hp, ap, status, code),
                    )
                }
                if (ties.isNotEmpty()) rounds += BracketRound(r.optInt("Rnd"), r.optString("RndTx"), ties)
            }
            if (rounds.isNotEmpty()) out += BracketDraw(d.optString("Nm"), rounds)
        }
        return if (out.isEmpty()) null else Bracket(out)
    }

    private fun side(a: JSONArray?): Side {
        val o = a?.optJSONObject(0) ?: return Side("", "TBD", "", "")
        return Side(o.optString("ID"), o.optString("Nm").ifEmpty { "TBD" }, o.optString("Abr"), o.optString("Img"))
    }

    private val ESD = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    private fun kickoff(esd: Long): Instant =
        if (esd <= 0) Instant.EPOCH else runCatching {
            LocalDateTime.parse(esd.toString(), ESD).toInstant(ZoneOffset.UTC)
        }.getOrDefault(Instant.EPOCH)

    private fun winnerOf(ewt: Int, hs: String, as_: String, hp: String, ap: String, status: String, code: Int): Int {
        if (ewt == 1 || ewt == 2) return ewt
        if (stateOf(status, code) != MatchState.FINISHED) return 0
        val hPens = hp.toIntOrNull()
        val aPens = ap.toIntOrNull()
        if (hPens != null && aPens != null && hPens != aPens) return if (hPens > aPens) 1 else 2
        val h = hs.toIntOrNull()
        val a = as_.toIntOrNull()
        return if (h != null && a != null && h != a) (if (h > a) 1 else 2) else 0
    }

    companion object {
        /** How long a competition's saved table / draw lives after it was last opened. */
        private const val WEEK_MS = 7L * 24 * 3600 * 1000

        /** How many competitions' tables / draws stay in memory as well. */
        private const val KEEP = 3
    }
}
