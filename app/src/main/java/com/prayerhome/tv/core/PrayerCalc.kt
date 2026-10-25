package com.prayerhome.tv.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

enum class Prayer(val arabic: String, val english: String, val isPrayer: Boolean) {
    FAJR("الفجر", "Fajr", true),
    SUNRISE("الشروق", "Sunrise", false),
    DHUHR("الظهر", "Dhuhr", true),
    ASR("العصر", "Asr", true),
    MAGHRIB("المغرب", "Maghrib", true),
    ISHA("العشاء", "Isha", true);

    fun label(inEnglish: Boolean) = if (inEnglish) english else arabic
}

/** Isha is either an angle or, when [ishaMinutes] > 0, a fixed interval after maghrib. */
enum class CalcMethod(val id: String, val label: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0) {
    EGYPT("egypt", "Egyptian General Authority of Survey", 19.5, 17.5),
    MWL("mwl", "Muslim World League", 18.0, 17.0),
    UMM_AL_QURA("makkah", "Umm al-Qura, Makkah", 18.5, 0.0, 90),
    KARACHI("karachi", "University of Islamic Sciences, Karachi", 18.0, 18.0),
    ISNA("isna", "Islamic Society of North America", 15.0, 15.0),
    DUBAI("dubai", "Dubai", 18.2, 18.2),
    KUWAIT("kuwait", "Kuwait", 18.0, 17.5),
    QATAR("qatar", "Qatar", 18.0, 0.0, 90),
    TURKEY("turkey", "Diyanet, Turkey", 18.0, 17.0);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: EGYPT
    }
}

/**
 * Astronomical prayer-time calculation (the classic PrayTimes algorithm).
 *
 * Everything is computed in UTC and returned as [Instant]s, so daylight-saving
 * time never enters the maths: converting an Instant to wall-clock time through
 * the zone's tz rules applies whatever offset is in force on that day, including
 * the DST switch-over days.
 */
object PrayerCalc {

    fun compute(date: LocalDate, lat: Double, lng: Double, method: CalcMethod, hanafi: Boolean, offsets: IntArray): List<Instant> {
        // The calculation date's Julian day, referenced to local solar noon at this longitude.
        val jDate = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15.0 * 24.0)

        // initial guesses, in hours
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) {
            val p = t.map { it / 24.0 }
            t = doubleArrayOf(
                sunAngleTime(jDate, method.fajr, p[0], lat, ccw = true),
                sunAngleTime(jDate, riseSetAngle(), p[1], lat, ccw = true),
                midDay(jDate, p[2]),
                asrTime(jDate, if (hanafi) 2.0 else 1.0, p[3], lat),
                sunAngleTime(jDate, riseSetAngle(), p[4], lat, ccw = false),
                if (method.ishaMinutes > 0) 0.0 else sunAngleTime(jDate, method.isha, p[5], lat, ccw = false),
            )
        }
        if (method.ishaMinutes > 0) t[5] = t[4] + method.ishaMinutes / 60.0

        // high-latitude safety: fall back to the "night middle" rule if an angle never occurs
        val night = (t[1] - t[4] + 24.0) % 24.0
        if (t[0].isNaN()) t[0] = t[1] - night / 2
        if (t[5].isNaN()) t[5] = t[4] + night / 2

        val midnightUtc = date.atStartOfDay().toInstant(ZoneOffset.UTC).epochSecond
        return t.mapIndexed { i, hoursLocalSolar ->
            val utcHours = hoursLocalSolar - lng / 15.0
            // round to the nearest minute like published timetables do
            val minutes = (utcHours * 60.0).roundToLong() + offsets.getOrElse(i) { 0 }
            Instant.ofEpochSecond(midnightUtc + minutes * 60)
        }
    }

    private fun riseSetAngle() = 0.833

    private fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0
        var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    private class SunPos(val declination: Double, val equation: Double)

    private fun sunPosition(jd: Double): SunPos {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return SunPos(decl, eqt)
    }

    private fun midDay(jDate: Double, t: Double): Double = fixHour(12 - sunPosition(jDate + t).equation)

    private fun sunAngleTime(jDate: Double, angle: Double, t: Double, lat: Double, ccw: Boolean): Double {
        val decl = sunPosition(jDate + t).declination
        val noon = midDay(jDate, t)
        val cosT = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
        if (cosT < -1 || cosT > 1) return Double.NaN
        val tt = darccos(cosT) / 15.0
        return noon + if (ccw) -tt else tt
    }

    private fun asrTime(jDate: Double, factor: Double, t: Double, lat: Double): Double {
        val decl = sunPosition(jDate + t).declination
        val angle = -darccot(factor + dtan(abs(lat - decl)))
        return sunAngleTime(jDate, angle, t, lat, ccw = false)
    }

    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun darccot(x: Double) = Math.toDegrees(atan(1 / x))
    private fun fixAngle(a: Double) = (a - 360.0 * floor(a / 360.0))
    private fun fixHour(a: Double) = (a - 24.0 * floor(a / 24.0))
}
