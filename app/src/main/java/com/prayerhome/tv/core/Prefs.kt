package com.prayerhome.tv.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class TeamRef(val id: String, val name: String, val img: String = "")

data class Location(
    val name: String,
    val lat: Double,
    val lng: Double,
    /** IANA zone id; empty means "follow the TV's timezone". */
    val zone: String = "",
)

/**
 * All user settings, backed by one SharedPreferences file.
 * Every setter bumps [version] so screens observing it can refresh.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeIndex: Int
        get() = sp.getInt("theme", 0)
        set(v) = edit { putInt("theme", v) }

    var location: Location
        get() = sp.getString("location", null)?.let(::parseLocation) ?: Cities.default
        set(v) = edit {
            putString("location", JSONObject().put("name", v.name).put("lat", v.lat).put("lng", v.lng).put("zone", v.zone).toString())
        }

    var methodId: String
        get() = sp.getString("method", CalcMethod.EGYPT.id)!!
        set(v) = edit { putString("method", v) }

    var hanafiAsr: Boolean
        get() = sp.getBoolean("hanafi", false)
        set(v) = edit { putBoolean("hanafi", v) }

    /** Per-prayer minute adjustments, index = Prayer.ordinal. */
    var offsets: IntArray
        get() = IntArray(6) { sp.getInt("off$it", 0) }
        set(v) = edit { v.forEachIndexed { i, m -> putInt("off$i", m) } }

    var hijriOffset: Int
        get() = sp.getInt("hijriOffset", 0)
        set(v) = edit { putInt("hijriOffset", v) }

    var chimeEnabled: Boolean
        get() = sp.getBoolean("chime", true)
        set(v) = edit { putBoolean("chime", v) }

    /** 0..100 */
    var chimeVolume: Int
        get() = sp.getInt("chimeVolume", 80)
        set(v) = edit { putInt("chimeVolume", v) }

    var chimeOverApps: Boolean
        get() = sp.getBoolean("chimeOverApps", true)
        set(v) = edit { putBoolean("chimeOverApps", v) }

    var pinnedApps: List<String>
        get() = sp.getString("pinned", null)?.split('|')?.filter { it.isNotEmpty() }
            ?: listOf("com.google.android.youtube.tv", "net.mbc.shahidTV", "com.locflix.tv.debug")
        set(v) = edit { putString("pinned", v.joinToString("|")) }

    var teams: List<TeamRef>
        get() = sp.getString("teams", null)?.let(::parseTeams) ?: DEFAULT_TEAMS
        set(v) = edit {
            val a = JSONArray()
            v.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("img", it.img)) }
            putString("teams", a.toString())
        }

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        listeners.forEach { it() }
    }

    private val listeners = mutableListOf<() -> Unit>()
    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    private fun parseLocation(s: String): Location? = runCatching {
        val o = JSONObject(s)
        Location(o.getString("name"), o.getDouble("lat"), o.getDouble("lng"), o.optString("zone"))
    }.getOrNull()

    private fun parseTeams(s: String): List<TeamRef> = runCatching {
        val a = JSONArray(s)
        List(a.length()) { a.getJSONObject(it).let { o -> TeamRef(o.getString("id"), o.getString("name"), o.optString("img")) } }
    }.getOrDefault(DEFAULT_TEAMS)

    companion object {
        // LiveScore team ids
        val DEFAULT_TEAMS = listOf(
            TeamRef("456", "Zamalek", "enet/80591.png"),
            TeamRef("4009", "Real Madrid"),
            TeamRef("2911", "Barcelona"),
            TeamRef("3340", "Liverpool"),
            TeamRef("3910", "Paris Saint-Germain"),
            TeamRef("12633", "Manchester City", "enet/8456.png"),
            TeamRef("2773", "Arsenal"),
            TeamRef("2940", "Bayern Munich"),
        )
    }
}

object Cities {
    val all = listOf(
        Location("Giza, Egypt", 30.0131, 31.2089, "Africa/Cairo"),
        Location("Cairo, Egypt", 30.0444, 31.2357, "Africa/Cairo"),
        Location("Alexandria, Egypt", 31.2001, 29.9187, "Africa/Cairo"),
        Location("Mansoura, Egypt", 31.0409, 31.3785, "Africa/Cairo"),
        Location("Tanta, Egypt", 30.7865, 31.0004, "Africa/Cairo"),
        Location("Port Said, Egypt", 31.2653, 32.3019, "Africa/Cairo"),
        Location("Suez, Egypt", 29.9668, 32.5498, "Africa/Cairo"),
        Location("Ismailia, Egypt", 30.5965, 32.2715, "Africa/Cairo"),
        Location("Asyut, Egypt", 27.1783, 31.1859, "Africa/Cairo"),
        Location("Luxor, Egypt", 25.6872, 32.6396, "Africa/Cairo"),
        Location("Aswan, Egypt", 24.0889, 32.8998, "Africa/Cairo"),
        Location("Hurghada, Egypt", 27.2579, 33.8116, "Africa/Cairo"),
        Location("Sharm El Sheikh, Egypt", 27.9158, 34.3300, "Africa/Cairo"),
        Location("Makkah, Saudi Arabia", 21.3891, 39.8579, "Asia/Riyadh"),
        Location("Madinah, Saudi Arabia", 24.5247, 39.5692, "Asia/Riyadh"),
        Location("Riyadh, Saudi Arabia", 24.7136, 46.6753, "Asia/Riyadh"),
        Location("Dubai, UAE", 25.2048, 55.2708, "Asia/Dubai"),
        Location("Kuwait City, Kuwait", 29.3759, 47.9774, "Asia/Kuwait"),
        Location("Doha, Qatar", 25.2854, 51.5310, "Asia/Qatar"),
        Location("Amman, Jordan", 31.9454, 35.9284, "Asia/Amman"),
        Location("Istanbul, Turkey", 41.0082, 28.9784, "Europe/Istanbul"),
        Location("London, UK", 51.5072, -0.1276, "Europe/London"),
    )
    val default = all[0]
}
