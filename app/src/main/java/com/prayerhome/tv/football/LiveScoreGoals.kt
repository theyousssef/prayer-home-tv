package com.prayerhome.tv.football

import android.util.Log
import org.json.JSONObject

private const val TAG = "LiveScore"

/**
 * Goal scorers from LiveScore's own match page (the server-rendered `__NEXT_DATA__` block).
 *
 * This is the source the app used before SofaScore, kept whole: Settings can switch the match card
 * back to it at any time, and it is still the fallback when SofaScore refuses a request. Returns
 * null when the page can't be read, so the card says so instead of showing "no goals".
 */
internal object LiveScoreGoals {

    fun read(match: Match): List<Goal>? {
        val cat = match.cat
        if (cat.isEmpty()) return null
        val r = Http.get("https://www.livescore.com/en/football/$cat/x/x/${match.id}/", label = "match page")
        if (!r.ok) return null
        val html = r.body ?: return null
        val marker = "id=\"__NEXT_DATA__\""
        val at = html.indexOf(marker)
        if (at < 0) {
            Log.w(TAG, "${match.id}: no __NEXT_DATA__")
            return null
        }
        val start = html.indexOf('>', at)
        val end = html.indexOf("</script>", start)
        if (start < 0 || end <= start) return null
        val root = runCatching { JSONObject(html.substring(start + 1, end)) }.getOrNull() ?: return null
        val event = root.optJSONObject("props")?.optJSONObject("pageProps")
            ?.optJSONObject("initialEventData")?.optJSONObject("event") ?: return null
        val incs = event.optJSONObject("incidents")?.optJSONObject("incs") ?: return emptyList()
        val out = ArrayList<Goal>()
        // Two providers can each hold part of the match: merge them and drop duplicates.
        for (provider in incs.keys()) {
            val minutes = incs.optJSONObject(provider) ?: continue
            for (key in minutes.keys()) {
                val blocks = minutes.optJSONArray(key) ?: continue
                for (b in 0 until blocks.length()) {
                    val block = blocks.optJSONObject(b) ?: continue
                    for (home in booleanArrayOf(true, false)) {
                        val list = block.optJSONArray(if (home) "HOME" else "AWAY") ?: continue
                        for (j in 0 until list.length()) {
                            val item = list.optJSONObject(j) ?: continue
                            val type = item.optString("type")
                            if (!type.contains("Goal")) continue
                            val time = item.optString("time").ifEmpty { key }
                            val player = item.optString("name").ifEmpty { item.optString("shortName") }.ifEmpty { "Goal" }
                            val minute = minuteOf(time)
                            if (minute <= 0) continue
                            val note = when {
                                type.contains("Own") -> "og"
                                type.contains("Penalty") -> "pen"
                                else -> ""
                            }
                            out += Goal(minute, addedOf(time), labelOf(time), player, home, note)
                        }
                    }
                }
            }
        }
        return out
            .distinctBy { "${it.label}|${it.home}|${it.player}" }
            .sortedBy { it.order }
    }

    private fun minuteOf(t: String): Int = Regex("(\\d+)").find(t)?.value?.toIntOrNull() ?: 0

    private fun addedOf(t: String): Int =
        if (t.contains('+')) Regex("\\+\\s*(\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0 else 0

    private fun labelOf(t: String): String = t.replace(" ", "").removeSuffix("'") + "'"
}
