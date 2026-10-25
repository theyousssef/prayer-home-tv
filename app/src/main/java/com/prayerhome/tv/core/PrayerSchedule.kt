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

/** The countdown to [PrayerDay.next]. */
data class Countdown(val remaining: Duration) {
    val urgent get() = remaining <= Duration.ofMinutes(CHIME_MINUTES)

    /** Two big number groups as the designs show them: HRS:MIN normally, MIN:SEC in the last 10 minutes. */
    val groups: List<Pair<String, String>>
        get() {
            val s = remaining.seconds.coerceAtLeast(0)
            return if (urgent) {
                listOf(pad(s / 60) to "MIN", pad(s % 60) to "SEC")
            } else {
                // round up so the display reaches 00:10 exactly as the red phase begins
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

const val CHIME_MINUTES = 10L

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

    /** The next moment a chime should sound (10 minutes before a prayer), strictly after [now]. */
    fun nextChime(prefs: Prefs, now: Instant): Pair<Instant, PrayerSlot> {
        val zone = zoneOf(prefs)
        val date = now.atZone(zone).toLocalDate()
        for (d in 0L..2L) {
            for (slot in slotsFor(prefs, date.plusDays(d))) {
                if (!slot.prayer.isPrayer) continue
                val at = slot.at.minus(Duration.ofMinutes(CHIME_MINUTES))
                if (at > now) return at to slot
            }
        }
        error("no prayer in the next 3 days")
    }
}
