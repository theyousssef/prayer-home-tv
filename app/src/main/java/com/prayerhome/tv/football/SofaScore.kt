package com.prayerhome.tv.football

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

private const val TAG = "SofaScore"

/** A short HTTP answer: the status code (‑1 when the request never got there) and the body. */
internal class Resp(val code: Int, val body: String?) {
    val ok get() = code in 200..299 && body != null
}

internal object Http {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Smart TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    fun get(url: String, json: Boolean = false, label: String = ""): Resp {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 20000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", if (json) "application/json" else "text/html,application/xhtml+xml")
            c.setRequestProperty("Accept-Language", "en")
            val code = c.responseCode
            val body = if (code in 200..299) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
            if (label.isNotEmpty() && code !in 200..299) Log.w(TAG, "$label HTTP $code")
            Resp(code, body)
        } catch (e: Exception) {
            if (label.isNotEmpty()) Log.w(TAG, "$label ${e.javaClass.simpleName} ${e.message.orEmpty()}")
            Resp(-1, null)
        } finally {
            runCatching { c?.disconnect() }
        }
    }
}

/**
 * SofaScore's public JSON: the day's schedule (to find a fixture's event id), the event itself (the
 * live score and minute) and its incidents (the goals).
 *
 * Its API sits behind Cloudflare, which answers 403 to app clients on many networks. A refusal is
 * remembered for six hours, so the page falls straight through to LiveScore instead of paying for a
 * request that cannot work on every open.
 */
internal class SofaScore(private val dir: File, private val base: String) {

    private val blockedFile = File(dir, ".blocked")
    private val idsFile = File(dir, "ids.json")

    // ------------------------------------------------------------------ the event

    /** The fixture's SofaScore event, with its score and status. Null when it can't be read. */
    fun event(match: Match): JSONObject? {
        if (blocked()) return null
        val mapped = readId(match.id)
        val id = mapped ?: resolve(match) ?: return null
        val body = request("$base/event/$id", "event")
        if (body != null) return JSONObject(body).optJSONObject("event")
        // A mapping from an earlier season can go stale: look the fixture up once more.
        if (mapped != null) {
            forgetId(match.id)
            val again = resolve(match) ?: return null
            return request("$base/event/$again", "event")?.let { JSONObject(it).optJSONObject("event") }
        }
        return null
    }

    /** The match's goals, oldest first; null when they couldn't be read. */
    fun goals(eventId: Long): List<Goal>? {
        if (blocked()) return null
        val body = request("$base/event/$eventId/incidents", "incidents") ?: return null
        val arr = JSONObject(body).optJSONArray("incidents") ?: return emptyList()
        val out = ArrayList<Goal>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("incidentType") != "goal") continue
            val minute = o.optInt("time", 0)
            if (minute <= 0) continue
            val added = o.optInt("addedTime", 0).coerceAtLeast(0)
            val player = o.optJSONObject("player")?.optString("name").orEmpty().ifEmpty { "Goal" }
            val home = o.optBoolean("isHome", true)
            val note = when (o.optString("incidentClass")) {
                "penalty" -> "pen"
                "ownGoal" -> "og"
                else -> ""
            }
            out += Goal(minute, added, labelOf(minute, added), player, home, note)
        }
        return out
            .distinctBy { "${it.label}|${it.home}|${it.player}" }
            .sortedBy { it.order }
    }

    // ------------------------------------------------------------------ fixture → event id

    private fun resolve(match: Match): Long? {
        val day = match.kickoff.atZone(ZoneOffset.UTC).toLocalDate()
        // A late kick-off can sit on the next day in the feed's calendar, and vice versa.
        for (d in listOf(day, day.minusDays(1), day.plusDays(1))) {
            // One refusal is enough: the other days would be refused too.
            if (blocked()) return null
            val events = dayEvents(d) ?: continue
            val found = pick(events, match) ?: continue
            val id = found.optLong("id")
            if (id > 0) {
                saveId(match.id, id)
                return id
            }
        }
        return null
    }

    /** Today's schedule is cached for ten minutes, older days for six hours. */
    private fun dayEvents(date: LocalDate): JSONArray? {
        val f = File(dir, "day-$date.json")
        val ttl = if (date == LocalDate.now(ZoneOffset.UTC)) 10 * 60_000L else 6 * 3600_000L
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < ttl) {
            runCatching { JSONObject(f.readText()).optJSONArray("events") }.getOrNull()?.let { return it }
        }
        val body = request("$base/sport/football/scheduled-events/$date", "schedule") ?: return null
        runCatching { f.writeText(body) }
        return runCatching { JSONObject(body).optJSONArray("events") }.getOrNull()
    }

    /** The same fixture in SofaScore's day: closest kick-off wins, with matching names as a tie-breaker. */
    private fun pick(events: JSONArray, match: Match): JSONObject? {
        var best: JSONObject? = null
        var bestValue = Long.MAX_VALUE
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val diff = abs(e.optLong("startTimestamp") - match.kickoff.epochSecond)
            if (diff > 20 * 60) continue
            val home = e.optJSONObject("homeTeam")?.optString("name").orEmpty()
            val away = e.optJSONObject("awayTeam")?.optString("name").orEmpty()
            val names = similar(match.home.name, home) + similar(match.away.name, away)
            // A name match is worth up to a quarter of an hour of kick-off drift.
            val value = diff - names * 900L
            if (value < bestValue) {
                bestValue = value
                best = e
            }
        }
        return best
    }

    private fun similar(a: String, b: String): Int {
        val ta = words(a)
        val tb = words(b)
        return if (ta.isEmpty() || tb.isEmpty()) 0 else ta.count { it in tb }
    }

    private fun words(s: String) = s.lowercase()
        .replace('é', 'e').replace('á', 'a').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
        .replace('ñ', 'n').replace('ö', 'o').replace('ü', 'u').replace('ç', 'c').replace('ã', 'a')
        .split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()

    // ------------------------------------------------------------------ ids on disk

    private fun readId(matchId: String): Long? = runCatching {
        val id = JSONObject(idsFile.readText()).optJSONObject(matchId)?.optLong("e", 0) ?: 0
        if (id > 0) id else null
    }.getOrNull()

    private fun saveId(matchId: String, eventId: Long) = runCatching {
        val o = runCatching { JSONObject(idsFile.readText()) }.getOrDefault(JSONObject())
        o.put(matchId, JSONObject().put("e", eventId).put("at", System.currentTimeMillis() / 1000))
        // Keep the file small: a mapping is only useful for a few weeks.
        val old = System.currentTimeMillis() / 1000 - 30 * 86400
        o.keys().asSequence().toList().forEach { k ->
            if (o.optJSONObject(k)?.optLong("at", 0L)?.let { it < old } != false) o.remove(k)
        }
        idsFile.writeText(o.toString())
    }

    private fun forgetId(matchId: String) = runCatching {
        val o = JSONObject(idsFile.readText())
        o.remove(matchId)
        idsFile.writeText(o.toString())
    }

    // ------------------------------------------------------------------ refusals

    private fun blocked(): Boolean =
        blockedFile.exists() && System.currentTimeMillis() - blockedFile.lastModified() < 6 * 3600_000L

    private fun block() = runCatching { blockedFile.writeText("") }

    private fun request(url: String, label: String): String? {
        val r = Http.get(url, json = true, label = label)
        return when {
            r.ok -> {
                runCatching { blockedFile.delete() }
                r.body
            }
            r.code == 403 || r.code == 429 -> {
                block()
                null
            }
            else -> null
        }
    }

    companion object {
        /** SofaScore's status → the state the page shows. */
        fun state(event: JSONObject): MatchState = when (event.optJSONObject("status")?.optString("type")) {
            "inprogress" -> MatchState.LIVE
            "finished" -> MatchState.FINISHED
            "notstarted" -> MatchState.UPCOMING
            else -> MatchState.OFF
        }

        /** "85'", "HT", "ET 105'", "FT", "AET", "FT (pens)" … */
        fun label(event: JSONObject): String {
            val status = event.optJSONObject("status") ?: return ""
            val type = status.optString("type")
            val desc = status.optString("description")
            val played = status.optJSONObject("time")?.optInt("played", -1) ?: -1
            return when (type) {
                "notstarted" -> ""
                "finished" -> when {
                    desc.contains("pen", true) -> "FT (pens)"
                    desc.contains("extra", true) || desc.equals("AET", true) -> "AET"
                    else -> "FT"
                }
                "inprogress" -> when {
                    desc.equals("HT", true) || desc.contains("half-time", true) || desc.contains("halftime", true) -> "HT"
                    desc.contains("penalt", true) -> if (played > 0) "PEN $played'" else "PEN"
                    desc.contains("extra", true) -> if (played > 0) "ET $played'" else "ET"
                    played > 0 -> "$played'"
                    else -> "LIVE"
                }
                else -> desc.ifEmpty { type }
            }
        }

        private fun labelOf(minute: Int, added: Int) = if (added > 0) "$minute+$added'" else "$minute'"
    }
}
