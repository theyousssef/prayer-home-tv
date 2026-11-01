package com.prayerhome.tv.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PrayerSlot(val prayer: Prayer, val at: Instant)

data class PrayerDay(
    val date: LocalDate,
    val zone: ZoneId,
    val slots: List<PrayerSlot>,
    /** The upcoming prayer (sunrise is shown in the list but never counted as "next"). */
    val next: PrayerSlot,
)

/** The countdown to [PrayerDay.next]; it turns [urgent] (red) once the chime's lead time is reached. */
data class Countdown(val remaining: Duration, val leadMinutes: Int) {
    val urgent get() = remaining <= Duration.ofMinutes(leadMinutes.toLong())

    /** Two big number groups as the designs show them: HRS:MIN normally, MIN:SEC in the red phase. */
    val groups: List<Pair<String, String>>
        get() {
            val s = remaining.seconds.coerceAtLeast(0)
            return if (urgent) {
                listOf(pad(s / 60) to "MIN", pad(s % 60) to "SEC")
            } else {
                // round up so the display reaches the lead time exactly as the red phase begins
                val m = (s + 59) / 60
                listOf(pad(m / 60) to "HRS", pad(m % 60) to "MIN")
            }
        }

    /** Compact form like "1h 53m" or "7m 48s". */
    val short: String
        get() {
            val s = remaining.seconds.coerceAtLeast(0)
            return if (urgent) "${s / 60}m ${pad(s % 60)}s" else {
                val m = (s + 59) / 60
                if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
            }
        }

    private fun pad(v: Long) = v.toString().padStart(2, '0')
}

object PrayerSchedule {
    fun zoneOf(prefs: Prefs): ZoneId =
        prefs.location.zone.takeIf { it.isNotEmpty() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()

    fun slotsFor(prefs: Prefs, date: LocalDate): List<PrayerSlot> {
        val loc = prefs.location
        val times = PrayerCalc.compute(date, loc.lat, loc.lng, CalcMethod.byId(prefs.methodId), prefs.hanafiAsr, prefs.offsets)
        return Prayer.entries.mapIndexed { i, p -> PrayerSlot(p, times[i]) }
    }

    fun dayAt(prefs: Prefs, now: Instant): PrayerDay {
        val zone = zoneOf(prefs)
        val date = now.atZone(zone).toLocalDate()
        val today = slotsFor(prefs, date)
        val next = today.firstOrNull { it.prayer.isPrayer && it.at > now }
            ?: slotsFor(prefs, date.plusDays(1)).first { it.prayer == Prayer.FAJR }
        return PrayerDay(date, zone, today, next)
    }

    /** How long before [prayer] the chime sounds and the countdown turns red: Isha gets at least 20 minutes. */
    fun leadFor(prefs: Prefs, prayer: Prayer?): Int =
        if (prayer == Prayer.ISHA) maxOf(prefs.chimeMinutes, ISHA_LEAD) else prefs.chimeMinutes

    /** The middle of the night that begins on [date]: halfway from its maghrib to the next day's fajr (the end of Isha's time). */
    fun midnightAfter(prefs: Prefs, date: LocalDate): Instant {
        val maghrib = slotsFor(prefs, date).first { it.prayer == Prayer.MAGHRIB }.at
        val fajr = slotsFor(prefs, date.plusDays(1)).first { it.prayer == Prayer.FAJR }.at
        return maghrib.plus(Duration.between(maghrib, fajr).dividedBy(2))
    }

    /** The next chime strictly after [now]: [leadFor] minutes before a prayer, or [Prefs.chimeMinutes] before midnight. */
    fun nextChime(prefs: Prefs, now: Instant): ChimeTarget {
        val zone = zoneOf(prefs)
        val date = now.atZone(zone).toLocalDate()
        // from yesterday: last night's midnight can fall after 00:00 today
        return (-1L..2L).flatMap { d ->
            val day = date.plusDays(d)
            slotsFor(prefs, day).filter { it.prayer.isPrayer }.map { ChimeTarget(it.prayer, it.at, leadFor(prefs, it.prayer)) } +
                ChimeTarget(null, midnightAfter(prefs, day), prefs.chimeMinutes)
        }.sortedBy { it.chimeAt }.firstOrNull { it.chimeAt > now } ?: error("no prayer in the next 3 days")
    }

    private const val ISHA_LEAD = 20
}

/** A chime [leadMinutes] before [at]: a prayer, or midnight when [prayer] is null. */
data class ChimeTarget(val prayer: Prayer?, val at: Instant, val leadMinutes: Int) {
    val chimeAt: Instant get() = at.minus(Duration.ofMinutes(leadMinutes.toLong()))

    fun label(inEnglish: Boolean) = prayer?.label(inEnglish) ?: if (inEnglish) "Midnight" else "منتصف الليل"
}
