package com.prayerhome.tv.football

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.GZIPInputStream

private const val TAG = "Highlights"

/** One YouTube result for a finished match. */
data class Highlight(
    val id: String,
    val title: String,
    val channel: String,
    /** Length in seconds. */
    val seconds: Int,
    /** Roughly how many days ago it was published; -1 when YouTube didn't say. */
    val publishedDays: Int,
    /** A competition, club, broadcaster or other official-looking channel. */
    val official: Boolean,
) {
    val duration: String
        get() {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
        }
}

/**
 * Finds YouTube highlights for a finished match without an API key.
 *
 * It searches YouTube in English and Arabic, plus the channels that show these leagues
 * (beIN SPORTS, AD Sports, TOD, and Kora Plus for Egyptian football), then keeps only videos
 * that really are this match: both clubs must be named in the title (English or Arabic name),
 * the score must not contradict it, the season/year must match, and the upload must be recent.
 * Results are cached per match, so opening the same match twice is instant.
 */
class Highlights(private val context: Context) {
    private val dir = File(context.filesDir, "highlights").apply { mkdirs() }
    private val thumbsDir = File(context.filesDir, "highlight-thumbs").apply { mkdirs() }
    private val thumbMem = LruCache<String, ImageBitmap>(24)
    private val mutex = Mutex()
    private val namesFile = File(context.filesDir, "arabic-names.json")
    private val arabicNames = HashMap<String, String>()

    init {
        runCatching {
            val o = JSONObject(namesFile.readText())
            o.keys().forEach { k -> o.optString(k).takeIf { it.isNotEmpty() }?.let { arabicNames[k] = it } }
        }
    }

    suspend fun find(match: Match, force: Boolean = false): List<Highlight> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = Instant.now()
            if (!force) cached(match.id, now)?.let { return@withLock it }
            Log.i(TAG, "searching highlights for ${match.home.name} v ${match.away.name}")
            val found = search(match, now)
            save(match.id, found, now)
            Log.i(TAG, "found ${found.size} highlights for ${match.home.name} v ${match.away.name}")
            found
        }
    }

    // ------------------------------------------------------------------ search

    private suspend fun search(match: Match, now: Instant): List<Highlight> = coroutineScope {
        val arHome = arabic(match.home.name)
        val arAway = arabic(match.away.name)
        val arNames = if (arHome != null && arAway != null) "$arHome $arAway" else null
        val enNames = "${match.home.name} ${match.away.name}"
        val raw = ArrayList<Raw>()
        val seen = HashSet<String>()

        /** Returns how many pages were actually downloaded (0 means YouTube is unreachable). */
        suspend fun fetchAll(urls: List<String>): Int {
            // fetch and parse each page inside its own task, so no page's HTML is kept around
            val pages = urls.map { url ->
                async(Dispatchers.IO) { fetch(url)?.let { it to extract(it) } }
            }.awaitAll()
            var ok = 0
            pages.forEach { page -> if (page != null) { ok++; page.second.forEach { if (seen.add(it.id)) raw += it } } }
            return ok
        }

        // fast path: two ordinary searches already surface the clubs' and competitions' own channels
        val globals = ArrayList<String>(2)
        globals += searchUrl("$enNames highlights")
        if (arNames != null) globals += searchUrl("$arNames ملخص")
        if (fetchAll(globals) == 0) throw IOException("YouTube unreachable")
        var found = rank(raw, match, arHome, arAway, now)
        if (found.size < 3) {
            // not enough yet: ask the broadcasters' own channels too
            val channels = ArrayList<String>(4)
            channels += channelUrl("beINSPORTS", arNames ?: enNames)
            channels += channelUrl("adsportstv", arNames ?: enNames)
            channels += channelUrl("tod_bybein", enNames)
            if (isEgyptian(match)) channels += channelUrl("koraplusegy", arNames ?: enNames)
            fetchAll(channels)
            found = rank(raw, match, arHome, arAway, now)
        }
        pick(found, 3)
    }

    private fun rank(videos: List<Raw>, match: Match, arHome: String?, arAway: String?, now: Instant): List<Pair<Int, Highlight>> {
        val homeNames = listOfNotNull(match.home.name, arHome)
        val awayNames = listOfNotNull(match.away.name, arAway)
        val egyptian = isEgyptian(match)
        val score = Score.of(match)
        val ageMinutes = Duration.between(match.kickoff, now).toMinutes().toDouble()
        val scored = ArrayList<Pair<Int, Highlight>>()

        for (v in videos) {
            val title = v.title
            if (title.isEmpty() || BAD.containsMatchIn(title)) continue
            val text = norm(title)
            val h = sideHit(text, homeNames)
            val a = sideHit(text, awayNames)
            if (h == 0 || a == 0) continue
            val conf = h + a
            if (v.seconds < 60 || v.seconds > MAX_SECONDS) continue
            if (!yearOk(title, match)) continue
            val sc = score?.points(title) ?: 0
            if (sc < 0) continue
            if (!GOOD.containsMatchIn(title) && sc == 0) continue
            if (v.publishedMinutes >= 0) {
                // highlights go up after the final whistle; an upload from before kick-off is a preview or another match
                if (v.publishedMinutes > ageMinutes + 180) continue
            }
            val authority = authority(v.channel, match, egyptian, arHome, arAway)
            if (authority < 3) continue
            val recent = if (v.publishedMinutes >= 0) (4 - kotlin.math.abs(v.publishedMinutes - ageMinutes) / 1440).toInt().coerceIn(0, 4) else 0
            val lengthBonus = (v.seconds / 180).coerceAtMost(8)
            val total = conf * 10 + authority * 5 + (if (sc > 0) 6 else 0) + (if (GOOD.containsMatchIn(title)) 4 else 0) + recent + lengthBonus
            val days = if (v.publishedMinutes < 0) -1 else v.publishedMinutes / 1440
            scored += total to Highlight(v.id, title, v.channel, v.seconds, days, authority >= 3)
        }

        // the same clip is often uploaded twice under different titles: keep the better copy
        val unique = ArrayList<Pair<Int, Highlight>>()
        for (cand in scored.sortedByDescending { it.first }) {
            val h = cand.second
            if (unique.none { it.second.channel.equals(h.channel, true) && kotlin.math.abs(it.second.seconds - h.seconds) <= 30 }) unique += cand
        }
        return unique
    }

    /** The longest [n] of the best candidates by score (score keeps the relevant ones, length breaks the tie). */
    private fun pick(list: List<Pair<Int, Highlight>>, n: Int): List<Highlight> =
        list.take(8).sortedByDescending { it.second.seconds }.take(n).map { it.second }.sortedByDescending { it.seconds }

    // ------------------------------------------------------------------ queries

    private fun searchUrl(q: String) = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8")

    private fun channelUrl(handle: String, q: String) = "https://www.youtube.com/@$handle/search?query=" + URLEncoder.encode(q, "UTF-8")

    /** The Arabic name of a club, from the built-in list or Google's public translate endpoint (cached on disk forever). */
    private fun arabic(name: String): String? {
        arabicNames[name]?.let { return it }
        ARABIC_NAMES[name.trim().lowercase(Locale.ROOT)]?.let { arabicNames[name] = it; return it }
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=ar&dt=t&q=" + URLEncoder.encode(name, "UTF-8")
        val body = fetch(url) ?: return null
        val translated = runCatching { JSONArray(body).getJSONArray(0).optJSONArray(0)?.optString(0) }.getOrNull().orEmpty()
        if (translated.isEmpty() || translated.none { it in '\u0600'..'\u06FF' } || norm(translated) == norm(name)) return null
        arabicNames[name] = translated
        runCatching {
            val o = JSONObject()
            arabicNames.forEach { (k, v) -> o.put(k, v) }
            namesFile.writeText(o.toString())
        }
        return translated
    }

    // ------------------------------------------------------------------ network

    private fun fetch(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        c.setRequestProperty("Accept-Encoding", "gzip")
        c.setRequestProperty("Cookie", "CONSENT=YES+cb")
        return try {
            if (c.responseCode != 200) {
                Log.w(TAG, "HTTP ${c.responseCode} for $url")
                return null
            }
            val stream = if (c.contentEncoding?.contains("gzip", true) == true) GZIPInputStream(c.inputStream) else c.inputStream
            stream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed for $url: ${e.message}")
            null
        } finally {
            c.disconnect()
        }
    }

    // ------------------------------------------------------------------ parse

    private class Raw(val id: String, val title: String, val channel: String, val seconds: Int, val publishedMinutes: Int, val verified: Boolean)

    /** Every video card in a YouTube results page (search results or a channel's search). */
    private fun extract(html: String): List<Raw> {
        val out = ArrayList<Raw>()
        val seen = HashSet<String>()
        for (key in KEYS) {
            var from = 0
            while (out.size < 160) {
                val at = html.indexOf(key, from)
                if (at < 0) break
                val start = at + key.length - 1
                val end = objectEnd(html, start)
                if (end < 0) break
                runCatching { JSONObject(html.substring(start, end + 1)) }.getOrNull()?.let { o ->
                    raw(o)?.let { r -> if (seen.add(r.id)) out += r }
                }
                from = end + 1
            }
        }
        return out
    }

    private fun raw(o: JSONObject): Raw? {
        val id = o.optString("videoId")
        if (!VIDEO_ID.matches(id)) return null
        val title = text(o.optJSONObject("title"))
        if (title.isEmpty()) return null
        val channel = text(o.optJSONObject("ownerText"))
            .ifEmpty { text(o.optJSONObject("longBylineText")) }
            .ifEmpty { text(o.optJSONObject("shortBylineText")) }
        val seconds = seconds(o.optJSONObject("lengthText")?.optString("simpleText").orEmpty())
        if (seconds <= 0) return null
        val published = publishedMinutes(o.optJSONObject("publishedTimeText")?.optString("simpleText").orEmpty())
        var verified = false
        val badges = o.optJSONArray("ownerBadges")
        if (badges != null) for (i in 0 until badges.length()) {
            if (badges.optJSONObject(i)?.optJSONObject("metadataBadgeRenderer")?.optString("style") == "BADGE_STYLE_TYPE_VERIFIED") verified = true
        }
        return Raw(id, title, channel, seconds, published, verified)
    }

    private fun text(o: JSONObject?): String {
        if (o == null) return ""
        o.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString()
    }

    /** End index of the JSON object that starts at [start], or -1. */
    private fun objectEnd(s: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return i }
                }
            }
            i++
        }
        return -1
    }

    // ------------------------------------------------------------------ thumbnails

    fun cachedThumb(id: String): ImageBitmap? = thumbMem.get(id)

    suspend fun thumb(id: String): ImageBitmap? = withContext(Dispatchers.IO) {
        thumbMem.get(id)?.let { return@withContext it }
        val f = File(thumbsDir, "$id.jpg")
        if (!f.exists()) {
            if (!download("https://i.ytimg.com/vi/$id/mqdefault.jpg", f) && !download("https://i.ytimg.com/vi/$id/hqdefault.jpg", f)) return@withContext null
        }
        runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()?.also { thumbMem.put(id, it) }
    }

    private fun download(url: String, f: File): Boolean = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 8000
        try {
            if (c.responseCode != 200) return@runCatching false
            val tmp = File(f.parentFile, f.name + ".part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(f)
        } finally {
            c.disconnect()
        }
    }.getOrDefault(false)

    // ------------------------------------------------------------------ cache

    private fun cached(id: String, now: Instant): List<Highlight>? = runCatching {
        val o = JSONObject(File(dir, "$id.json").readText())
        val list = o.optJSONArray("v") ?: return null
        val age = Duration.between(Instant.ofEpochSecond(o.optLong("t")), now)
        // a fresh match's highlights can take a while to appear: retry an empty answer soon
        if (age > if (list.length() == 0) Duration.ofMinutes(15) else Duration.ofHours(12)) return null
        List(list.length()) { i ->
            val v = list.getJSONObject(i)
            Highlight(v.getString("id"), v.optString("title"), v.optString("channel"), v.optInt("d"), v.optInt("p", -1), v.optBoolean("o"))
        }
    }.getOrNull()

    private fun save(id: String, list: List<Highlight>, now: Instant) = runCatching {
        val a = JSONArray()
        list.forEach { h -> a.put(JSONObject().put("id", h.id).put("title", h.title).put("channel", h.channel).put("d", h.seconds).put("p", h.publishedDays).put("o", h.official)) }
        File(dir, "$id.json").writeText(JSONObject().put("t", now.epochSecond).put("v", a).toString())
    }

    // ------------------------------------------------------------------ matching

    private class Score(val home: Int, val away: Int) {
        /** 2 when the title's score matches, -2 when it clearly doesn't, 0 when it has none. */
        fun points(title: String): Int {
            val found = SCORE.findAll(title)
                .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
                .filter { (a, b) -> a <= 12 && b <= 12 }
                .toList()
            if (found.isEmpty()) return 0
            return if (found.any { (a, b) -> (a == home && b == away) || (a == away && b == home) }) 2 else -2
        }

        companion object {
            fun of(m: Match): Score? {
                val h = m.homeScore.toIntOrNull()
                val a = m.awayScore.toIntOrNull()
                return if (h == null || a == null) null else Score(h, a)
            }
        }
    }

    private fun yearOk(title: String, match: Match): Boolean {
        val z = match.kickoff.atZone(ZoneOffset.UTC)
        val year = z.year
        val season = SEASON.find(title)
        if (season != null) {
            val y1 = seasonYear(season.groupValues[1], season.groupValues[2])
            val y2 = seasonYear(season.groupValues[3], season.groupValues[4])
            if (y1 > 0 && y2 == y1 + 1) {
                val start = LocalDate.of(y1, 7, 1)
                val end = LocalDate.of(y2, 6, 30)
                return !z.toLocalDate().isBefore(start) && !z.toLocalDate().isAfter(end)
            }
        }
        val years = YEAR.findAll(title).map { it.value.toInt() }.toList()
        if (years.isEmpty()) return true
        return years.any { it == year || (z.monthValue <= 2 && it == year - 1) }
    }

    private fun seasonYear(prefix: String, two: String): Int {
        val n = two.toInt()
        return if (prefix.isNotEmpty()) prefix.toInt() * 100 + n else 2000 + n
    }

    private fun sideHit(text: String, names: List<String>): Int {
        var best = 0
        for (name in names) {
            val n = norm(name)
            if (n.isEmpty()) continue
            val tk = tokens(name)
            if (containsName(text, n) || (tk.isNotEmpty() && containsName(text, tk.joinToString(" ")))) {
                best = maxOf(best, 3)
                continue
            }
            if (tk.isNotEmpty()) {
                val hits = tk.count { containsName(text, it) }
                if (hits == tk.size) best = maxOf(best, 2)
                else if (hits > 0) best = maxOf(best, 1)
            }
        }
        return best
    }

    private fun containsName(text: String, name: String): Boolean {
        if (name.isEmpty()) return false
        return if (name.any { it in '\u0600'..'\u06FF' }) text.contains(name)
        else (" $text ").contains(" $name ")
    }

    private fun isEgyptian(match: Match): Boolean =
        match.league.contains("egypt", true) || EGYPT_CLUBS.any { c ->
            match.home.name.contains(c, true) || match.away.name.contains(c, true)
        }

    private fun authority(channel: String, match: Match, egyptian: Boolean, arHome: String?, arAway: String?): Int {
        val c = norm(channel)
        var a = 0
        if (c.contains("kora plus") || c.contains("koraplus") || c.contains("كوره بلس") || c.contains("كورة بلس")) a = maxOf(a, if (egyptian) 5 else 3)
        if (c.contains("bein")) a = maxOf(a, 5)
        if (c.contains("ad sports") || c.contains("adsport") || c.contains("ابوظبي") || c.contains("ابو ظبي")) a = maxOf(a, 5)
        if (TOD.containsMatchIn(c)) a = maxOf(a, 5)
        if (c.contains("sports united")) a = maxOf(a, 5)
        if (c.contains("mbc action")) a = maxOf(a, 5)
        if (ON_SPORT.containsMatchIn(c)) a = maxOf(a, 5)
        if (OFFICIAL.containsMatchIn(c)) a = maxOf(a, 3)
        for (team in listOfNotNull(match.home.name, match.away.name, arHome, arAway)) {
            val n = norm(team)
            if (n.length >= 4 && c.contains(n)) a = maxOf(a, 6)
        }
        if (c.contains("official") || c.contains("الرسمي")) a++
        return a.coerceIn(0, 6)
    }

    private fun seconds(s: String): Int {
        val parts = s.trim().split(':').mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }

    private fun publishedMinutes(s: String): Int {
        val m = PUBLISHED.find(s.lowercase(Locale.ROOT)) ?: return -1
        val n = m.groupValues[1].toInt()
        val unit = m.groupValues[2]
        val minutes = when {
            unit.startsWith("sec") || unit == "s" -> 0
            unit.startsWith("min") || unit == "m" -> 1
            unit.startsWith("hour") || unit == "h" -> 60
            unit.startsWith("day") || unit == "d" -> 1440
            unit.startsWith("week") || unit == "w" -> 7 * 1440
            unit.startsWith("mo") -> 30 * 1440
            unit.startsWith("year") || unit == "y" -> 365 * 1440
            else -> 0
        }
        return n * minutes
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        private const val MAX_SECONDS = 35 * 60
        private val KEYS = arrayOf("\"videoRenderer\":{", "\"compactVideoRenderer\":{")
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val SCORE = Regex("""(?<!\d)(\d{1,2})\s*[-–—:]\s*(\d{1,2})(?!\d)""")
        private val YEAR = Regex("""(?<!\d)(20\d{2})(?!\d)""")
        private val SEASON = Regex("""(?<!\d)(20)?(\d{2})\s*[/\-–]\s*(20)?(\d{2})(?!\d)""")
        private val PUBLISHED = Regex("""(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?|mo|s|m|h|d|w|y)""")
        private val TOD = Regex("""\btod\b""")
        private val ON_SPORT = Regex("""\bon\s*sport\b|أون\s*سبورت|اون\s*سبورت|ontime\s*sports?""")
        private val GOOD = Regex("""ملخص|أهداف|اهداف|highlights?|goals?|extended|recap|match summary|ملحق|özet|maç özeti|tüm goller|geniş özet""", RegexOption.IGNORE_CASE)
        private val BAD = Regex(
            """كاملة|كامل\b|full match|full game|بث مباشر|live stream|مباشر|press conference|مؤتمر صحفي|تدريب|training|preview|تشكيل|lineup|خط التشكيل|replay|اعادة|إعادة|مباراة كاملة|ردة فعل|رد فعل|reaction|تحليل|analysis|استوديو|watch along|fan cam|vlog|podcast|interview|tartışmalı|analiz|maç sonu|açıklamalar|geçmişten günümüze|الملخص الكامل""",
            RegexOption.IGNORE_CASE,
        )
        private val OFFICIAL = Regex(
            """\b(uefa|caf|fifa|afc|conmebol|concacaf|premier league|efl|laliga|la liga|serie a|bundesliga|ligue 1|eredivisie|primeira liga|süper lig|super lig|champions league|europa league|conference league|libertadores|roshn|thmanyah|a spor|trt spor|ntv spor|supersport|super sport|espn|sky sport|dazn|canal\+|tnt sports|viaplay|alkass)\b|الدوري المصري|دوري أبطال|الرابطة|كأس|ثمانية|الرياضية""",
            RegexOption.IGNORE_CASE,
        )
        private val EGYPT_CLUBS = listOf("Al Ahly", "Zamalek", "Pyramids", "Ismaily", "Al Masry", "ENPPI", "Ceramica", "Smouha")
        /** Common clubs whose Arabic name is worth knowing without a translation request. */
        private val ARABIC_NAMES = mapOf(
            "real madrid" to "ريال مدريد", "barcelona" to "برشلونة", "atletico madrid" to "أتلتيكو مدريد",
            "sevilla" to "إشبيلية", "liverpool" to "ليفربول", "manchester city" to "مانشستر سيتي",
            "manchester united" to "مانشستر يونايتد", "arsenal" to "أرسنال", "chelsea" to "تشيلسي",
            "tottenham" to "توتنهام", "bayern munich" to "بايرن ميونخ", "borussia dortmund" to "بوروسيا دورتموند",
            "paris saint-germain" to "باريس سان جيرمان", "juventus" to "يوفنتوس", "inter" to "إنتر ميلان",
            "ac milan" to "ميلان", "napoli" to "نابولي", "roma" to "روما",
            "al ahly" to "الأهلي", "zamalek" to "الزمالك", "pyramids" to "بيراميدز", "ismaily" to "الإسماعيلي",
            "al masry" to "المصري", "galatasaray" to "غلطة سراي", "fenerbahce" to "فنربخشة",
            "besiktas" to "بشكتاش", "al hilal" to "الهلال", "al nassr" to "النصر", "al ittihad" to "الاتحاد",
            "al ahli" to "الأهلي", "esperance" to "الترجي", "wydad" to "الوداد",
        )

        private val GENERIC = setOf("fc", "cf", "sc", "ac", "afc", "club", "the", "of", "de", "and", "vs", "v", "نادي", "فريق", "اس", "اف", "سي", "كلوب")

        private fun norm(s: String): String {
            // NFD + dropping combining marks makes "Beşiktaş" match "Besiktas" and "أ" match "ا".
            val src = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            val sb = StringBuilder(src.length)
            var space = true
            for (raw in src) {
                var c = raw
                if (java.lang.Character.getType(c) == java.lang.Character.NON_SPACING_MARK.toInt() || c in '\u0610'..'\u061A' || c in '\u064B'..'\u065F' || c == '\u0670' || c in '\u06D6'..'\u06ED' || c == '\u0640') continue
                c = when (c) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى' -> 'ي'
                    'ة' -> 'ه'
                    'ؤ' -> 'و'
                    'ئ' -> 'ي'
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    else -> c
                }
                if (c.isLetterOrDigit()) {
                    sb.append(c.lowercaseChar())
                    space = false
                } else if (!space) {
                    sb.append(' ')
                    space = true
                }
            }
            return sb.toString().trim()
        }

        private fun tokens(name: String): List<String> =
            norm(name).split(' ').filter { it.length >= 3 && it !in GENERIC }
    }
}
