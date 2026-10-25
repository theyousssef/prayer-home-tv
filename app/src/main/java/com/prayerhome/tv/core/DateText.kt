package com.prayerhome.tv.core

import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateText {
    private val HIJRI_MONTHS = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )
    private val HIJRI_MONTHS_EN = arrayOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Shaban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah",
    )
    // java.time DayOfWeek: MONDAY=1 .. SUNDAY=7
    private val AR_DAYS = arrayOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

    data class Hijri(val weekday: String, val day: String, val month: String, val year: String, val english: Boolean = false) {
        /** e.g. "السبت ١٣ جمادى الأولى ١٤٤٨ هـ", or "Saturday 13 Jumada al-Ula 1448 AH" */
        val full get() = "$weekday $day $month $yearLabel"
        val dayMonthYear get() = "$day $month $yearLabel"
        /** "١٤٤٨ هـ" / "1448 AH" */
        val yearLabel get() = if (english) "$year AH" else "$year هـ"
    }

    /** Umm al-Qura Hijri date for a civil date, shifted by [offsetDays]; in English with Latin digits when [english]. */
    fun hijri(date: LocalDate, offsetDays: Int, english: Boolean = false): Hijri {
        val shifted = date.plusDays(offsetDays.toLong())
        val cal = IslamicCalendar(TimeZone.GMT_ZONE)
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.timeInMillis = shifted.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() + 12 * 3600_000L
        val d = cal.get(IslamicCalendar.DAY_OF_MONTH)
        val m = cal.get(IslamicCalendar.MONTH)
        val y = cal.get(IslamicCalendar.YEAR)
        if (english) return Hijri(date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH), "$d", HIJRI_MONTHS_EN[m], "$y", true)
        return Hijri(AR_DAYS[date.dayOfWeek.value - 1], arabicDigits(d), HIJRI_MONTHS[m], arabicDigits(y))
    }

    fun arabicDigits(n: Int): String = arabicDigits(n.toString())
    fun arabicDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') ('٠' + (c - '0')) else c)
    }

    private val CLOCK = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
    private val CLOCK_SEC = DateTimeFormatter.ofPattern("ss", Locale.ENGLISH)
    private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun clock(t: ZonedDateTime): String = t.format(CLOCK)
    fun seconds(t: ZonedDateTime): String = t.format(CLOCK_SEC)
    fun ampm(t: ZonedDateTime): String = if (t.hour < 12) "AM" else "PM"
    fun longDate(t: ZonedDateTime): String = t.format(LONG_DATE)
    fun time(i: Instant, zone: ZoneId): String = i.atZone(zone).format(TIME)
    fun timeNoAmPm(i: Instant, zone: ZoneId): String = i.atZone(zone).format(CLOCK)
    fun ampm(i: Instant, zone: ZoneId): String = ampm(i.atZone(zone))

    /** "Today · 9:00 PM", "Tomorrow · 6:30 PM", "Sun · 6:30 PM", "Yesterday" ... */
    fun dayLabel(i: Instant, zone: ZoneId, now: Instant): String {
        val d = i.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when (d) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> if (d.isAfter(today) && d.isBefore(today.plusDays(7)))
                d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            else d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
        }
    }
}
