package com.prayerhome.tv.football

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * LiveScore's public JSON feed (the one livescore.com itself uses). No key needed.
 * Only a few calls are used: every match on a UTC day, every match live right now, a
 * competition's league table and its cup draw.
 */
object LiveScore {
    private const val BASE = "https://prod-public-api.livescore.com/v1/api/app"
    private val DAY = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val ESD = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    class Page(val matches: List<Match>, val teams: List<TeamInfo>)
    data class TeamInfo(val id: String, val name: String, val img: String, val country: String)

    /** All football matches kicking off on a UTC calendar day. */
    fun day(date: LocalDate, teamIds: Set<String>, collectTeams: Boolean): Page =
        parse(get("$BASE/date/soccer/${date.format(DAY)}/0"), teamIds, collectTeams)

    /** Matches in play right now (a small payload, ~5–50 KB). */
    fun live(teamIds: Set<String>): List<Match> =
        parse(get("$BASE/live/soccer/0"), teamIds, false).matches

    /** Every football match in play right now, whatever the competition (~10–30 KB). */
    fun liveAll(): List<Match> =
        parse(get("$BASE/live/soccer/0"), null, false).matches

    /** One competition's standings, grouped by stage (a league, or a cup's group stage). */
    fun competitionTable(compId: String): String = get("$BASE/competition/$compId/leagueTable")

    /** One competition's knockout draw, or null when the competition has none (LiveScore answers 410). */
    fun competitionDraw(compId: String): String? = getOrNull("$BASE/competition/$compId/draw")

    /**
     * One stage's table and fixtures, by category and stage slug ("egypt", "league-cup-group-a").
     * This is how a competition that the day feed lists without a competition id is reached; a stage
     * that doesn't exist answers 410, which is null here.
     */
    fun stageTable(cat: String, scd: String): String? = getOrNull("$BASE/stage/soccer/$cat/$scd/0")

    /** [teamIds] null keeps every match; an empty set keeps none. */
    private fun parse(body: String, teamIds: Set<String>?, collectTeams: Boolean): Page {
        val stages = JSONObject(body).optJSONArray("Stages") ?: JSONArray()
        val out = ArrayList<Match>()
        val teams = if (collectTeams) LinkedHashMap<String, TeamInfo>() else null
        for (i in 0 until stages.length()) {
            val s = stages.getJSONObject(i)
            val events = s.optJSONArray("Events") ?: continue
            val comp = s.optString("CompId")
            val stage = s.optString("Sid")
            // The site uses the transliterated category ("turkiye"), which Ccd can spell differently ("turkey").
            val cat = s.optString("CnmT").ifEmpty { s.optString("Ccd") }
            var league: String? = null
            for (j in 0 until events.length()) {
                val e = events.getJSONObject(j)
                val t1 = e.getJSONArray("T1").getJSONObject(0)
                val t2 = e.getJSONArray("T2").getJSONObject(0)
                val country = s.optString("Cnm")
                if (teams != null) {
                    for (t in arrayOf(t1, t2)) {
                        val id = t.optString("ID")
                        if (id.isNotEmpty() && (teams[id]?.img?.isEmpty() ?: true)) teams[id] = TeamInfo(id, t.optString("Nm"), t.optString("Img"), country)
                    }
                }
                if (teamIds != null && t1.optString("ID") !in teamIds && t2.optString("ID") !in teamIds) continue
                if (league == null) league = leagueName(s)
                out += Match(
                    id = e.getString("Eid"),
                    kickoff = LocalDateTime.parse(e.getLong("Esd").toString(), ESD).toInstant(ZoneOffset.UTC),
                    home = side(t1),
                    away = side(t2),
                    homeScore = e.optString("Tr1"),
                    awayScore = e.optString("Tr2"),
                    status = e.optString("Eps"),
                    statusCode = e.optInt("Esid"),
                    league = league,
                    comp = comp,
                    stage = stage,
                    cat = cat,
                    scd = s.optString("Scd"),
                )
            }
        }
        return Page(out, teams?.values?.toList() ?: emptyList())
    }

    private fun side(t: JSONObject) = Side(t.optString("ID"), t.optString("Nm"), t.optString("Abr"), t.optString("Img"))

    private val BIG = setOf("England", "Spain", "Germany", "France", "Italy", "International")
    private val CONFEDERATIONS = listOf("UEFA", "CAF", "FIFA", "CONCACAF", "CONMEBOL", "AFC")

    private fun leagueName(s: JSONObject): String {
        val cnm = s.optString("Cnm")
        var name = s.optString("CompN").ifEmpty { s.optString("Snm").substringBefore(':') }
        val conf = CONFEDERATIONS.firstOrNull { cnm.startsWith(it) || s.optString("CompD") == it }
        when {
            conf != null && cnm.startsWith(conf) && !name.startsWith(conf) -> name = cnm
            conf != null && !name.startsWith(conf) && name.length < 24 -> name = "$conf $name"
            conf == null && cnm.isNotEmpty() && cnm !in BIG && !name.contains(cnm) -> name = "$cnm $name"
        }
        return name
    }

    internal fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Like [get], but "not here" (404/410) means the feed simply has nothing for this URL. */
    internal fun getOrNull(url: String): String? = try {
        get(url)
    } catch (e: IOException) {
        if (e.message == "HTTP 404" || e.message == "HTTP 410") null else throw e
    }

    fun toJson(m: Match): JSONObject = JSONObject()
        .put("id", m.id).put("k", m.kickoff.epochSecond)
        .put("h", sideJson(m.home)).put("a", sideJson(m.away))
        .put("hs", m.homeScore).put("as", m.awayScore)
        .put("st", m.status).put("sc", m.statusCode).put("lg", m.league).put("cp", m.comp).put("sg", m.stage).put("ct", m.cat).put("sd", m.scd)

    fun fromJson(o: JSONObject) = Match(
        o.getString("id"), Instant.ofEpochSecond(o.getLong("k")),
        sideFrom(o.getJSONObject("h")), sideFrom(o.getJSONObject("a")),
        o.optString("hs"), o.optString("as"), o.optString("st"), o.optInt("sc"), o.optString("lg"),
        o.optString("cp"), o.optString("sg"), o.optString("ct"), o.optString("sd"),
    )

    private fun sideJson(s: Side) = JSONObject().put("i", s.id).put("n", s.name).put("s", s.short).put("m", s.img)
    private fun sideFrom(o: JSONObject) = Side(o.optString("i"), o.optString("n"), o.optString("s"), o.optString("m"))
}
