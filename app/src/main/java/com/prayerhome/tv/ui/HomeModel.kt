package com.prayerhome.tv.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prayerhome.tv.App
import com.prayerhome.tv.apps.AppEntry
import com.prayerhome.tv.core.Countdown
import com.prayerhome.tv.core.DateText
import com.prayerhome.tv.core.Prayer
import com.prayerhome.tv.core.PrayerDay
import com.prayerhome.tv.core.PrayerSchedule
import com.prayerhome.tv.football.Match
import com.prayerhome.tv.football.MatchRepo
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Immutable
data class ClockView(
    val time: String,       // "1:58"
    val ampm: String,       // "PM"
    val date: String,       // "Saturday, 24 October 2026"
    val hijri: DateText.Hijri,
    val zone: ZoneId,
)

/** One row of the prayer table, with its display state relative to now. */
@Immutable
data class PrayerRow(
    val prayer: Prayer,
    val name: String,       // Arabic, or English when the language setting says so
    val time: String,       // "3:51 PM"
    val timeShort: String,  // "3:51"
    val ampm: String,
    val at: Instant,
    val isNext: Boolean,
    val isPast: Boolean,
)

@Immutable
data class NextView(
    val name: String,       // Arabic or English
    val time: String,       // "3:51 PM"
    val chimeAt: String,    // "3:41 PM"
    val groups: List<Pair<String, String>>,
    val urgent: Boolean,
    val short: String,      // "1h 53m"
    /** 0..1 progress from the previous prayer to the next one (used by the sundial / timelines). */
    val progress: Float,
)

/**
 * Everything the home screens show, derived from a single ticking clock.
 * Each value is a derived state, so a screen only recomposes when the *text* it shows changes
 * (the clock once a minute, the countdown once a minute — or every second in the last 10 minutes).
 */
class HomeModel(private val app: App) {
    var now by mutableStateOf(Instant.now())
    private var prefsVersion by mutableIntStateOf(0)

    var allMatches by mutableStateOf<List<Match>>(emptyList())
    var allApps by mutableStateOf<List<AppEntry>>(emptyList())
    var pinnedIds by mutableStateOf(app.prefs.pinnedApps)
    var themeIndex by mutableIntStateOf(app.prefs.themeIndex)
    var scoresStatus by mutableStateOf("")
    /** Prayer names and Hijri date in English. */
    var english by mutableStateOf(app.prefs.english)
        private set
    /** The chime's lead time, which is also when the countdown turns red. */
    var leadMinutes by mutableIntStateOf(app.prefs.chimeMinutes)
        private set
    /** A short themed message shown at the bottom of the screen (e.g. "Quran Reader isn't installed"). */
    var toast by mutableStateOf<String?>(null)

    val location: String get() = app.prefs.location.name
    val methodLabel: String get() = com.prayerhome.tv.core.CalcMethod.byId(app.prefs.methodId).label
    val coordsText: String
        get() = app.prefs.location.let {
            "%.2f°%s %.2f°%s".format(java.util.Locale.ENGLISH, kotlin.math.abs(it.lat), if (it.lat >= 0) "N" else "S", kotlin.math.abs(it.lng), if (it.lng >= 0) "E" else "W")
        }

    init {
        app.prefs.addListener {
            prefsVersion++
            pinnedIds = app.prefs.pinnedApps
            themeIndex = app.prefs.themeIndex
            english = app.prefs.english
            leadMinutes = app.prefs.chimeMinutes
        }
    }

    private val minute: Long by derivedStateOf { now.epochSecond / 60 }

    val day: PrayerDay by derivedStateOf {
        prefsVersion
        // recompute every minute: cheap (a few trig calls) and keeps "next" and the date correct across midnight & DST
        minute
        PrayerSchedule.dayAt(app.prefs, now)
    }

    val clock: ClockView by derivedStateOf {
        prefsVersion
        val z = now.atZone(day.zone)
        ClockView(DateText.clock(z), DateText.ampm(z), DateText.longDate(z), DateText.hijri(z.toLocalDate(), app.prefs.hijriOffset, english), day.zone)
    }

    val seconds: String by derivedStateOf { DateText.seconds(now.atZone(day.zone)) }

    val prayers: List<PrayerRow> by derivedStateOf {
        val d = day
        val en = english
        d.slots.map { s ->
            PrayerRow(
                prayer = s.prayer,
                name = s.prayer.label(en),
                time = DateText.time(s.at, d.zone),
                timeShort = DateText.timeNoAmPm(s.at, d.zone),
                ampm = DateText.ampm(s.at, d.zone),
                at = s.at,
                isNext = s.at == d.next.at,
                isPast = s.at <= now && s.at != d.next.at,
            )
        }
    }

    val next: NextView by derivedStateOf {
        val d = day
        val lead = leadMinutes
        val cd = Countdown(Duration.between(now, d.next.at), lead)
        val prev = d.slots.lastOrNull { it.prayer.isPrayer && it.at <= now }?.at ?: d.next.at.minus(Duration.ofHours(9))
        val span = Duration.between(prev, d.next.at).seconds.coerceAtLeast(1)
        val done = Duration.between(prev, now).seconds
        NextView(
            name = d.next.prayer.label(english),
            time = DateText.time(d.next.at, d.zone),
            chimeAt = DateText.time(d.next.at.minus(Duration.ofMinutes(lead.toLong())), d.zone),
            groups = cd.groups,
            urgent = cd.urgent,
            short = cd.short,
            progress = ((done.toFloat() / span) * 100).toInt() / 100f,
        )
    }

    /** "Chime at 3:41 PM" (AlertLine adds "· 10 min before العصر") or, once inside the window, "Chime played · ...". */
    val alertText: String by derivedStateOf {
        prefsVersion
        val n = next
        if (!app.prefs.chimeEnabled) "Chime off"
        else if (n.urgent) "Chime played · under $leadMinutes minutes"
        else "Chime at ${n.chimeAt}"
    }

    val homeMatches: List<Match> by derivedStateOf { MatchRepo.closest(allMatches, Instant.ofEpochSecond(minute * 60)) }
    val windowMatches: List<Match> by derivedStateOf { MatchRepo.window(allMatches, Instant.ofEpochSecond(minute * 60)) }

    val pinned: List<AppEntry> by derivedStateOf {
        pinnedIds.mapNotNull { id -> allApps.firstOrNull { it.pkg == id } }
    }

    fun matchTime(m: Match): String = DateText.dayLabel(m.kickoff, day.zone, now) + " · " + DateText.time(m.kickoff, day.zone)
    fun matchTimeOnly(m: Match): String = DateText.time(m.kickoff, day.zone)
    fun matchDay(m: Match): String = DateText.dayLabel(m.kickoff, day.zone, now)
}
