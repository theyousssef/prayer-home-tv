package com.prayerhome.tv.ui.themes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.core.Prayer
import com.prayerhome.tv.ui.Ar
import com.prayerhome.tv.ui.Fonts
import com.prayerhome.tv.ui.HomeModel
import com.prayerhome.tv.ui.HomeTheme
import com.prayerhome.tv.ui.Ico
import com.prayerhome.tv.ui.Icon
import com.prayerhome.tv.ui.Nav
import com.prayerhome.tv.ui.PageStyle
import com.prayerhome.tv.ui.T
import com.prayerhome.tv.ui.hex
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

private val BG = hex(0x0C0E1E)
private val CARD = hex(0x14172B)
private val CARD_HI = hex(0x1D2140)
private val LINE = hex(0x262A45)
private val GOLD = hex(0xE8C27A)
private val INK = hex(0xEEF0FA)
private val MUTED = hex(0xA4A9C6)
private val DIM = hex(0x7D83A8)
private val PAST = hex(0x8B90B0)
private val SOFT = hex(0xC9CCE0)
private val RED = hex(0xFF5A5F)

val Theme07 = HomeTheme(
    name = "Prayer Sundial",
    style = PageStyle(
        bg = BG, surface = CARD, surfaceFocus = CARD_HI, border = LINE, accent = GOLD, text = INK, muted = MUTED,
        red = RED, redBg = hex(0x3A1420),
        display = Fonts.Sora, displayWeight = 200, body = Fonts.Sora, arabic = Fonts.NotoKufi, arabicWeight = 600,
        numbersWeight = 300, radius = 22, labelSpacing = 0.24f,
    ),
    home = { m, nav, first -> Home07(m, nav, first) },
)

private const val CX = 500f
private const val CY = 450f
private const val R = 310f

/** Clockwise degrees from the top of the dial, where the top is noon. */
private fun angle(i: Instant, zone: ZoneId): Double {
    val t = i.atZone(zone)
    val h = t.hour + t.minute / 60.0 + t.second / 3600.0
    return (h - 12.0) / 24.0 * 360.0
}

private fun point(deg: Double, r: Float) = Offset(CX + r * sin(Math.toRadians(deg)).toFloat(), CY - r * cos(Math.toRadians(deg)).toFloat())

@Composable
private fun Dial(m: HomeModel) {
    val zone = m.clock.zone
    val rows = m.prayers
    val n = m.next
    val cd = if (n.urgent) RED else GOLD
    val rise = rows.first { it.prayer == Prayer.SUNRISE }.at
    val set = rows.first { it.prayer == Prayer.MAGHRIB }.at
    val nowDeg = angle(m.now, zone)
    val nextDeg = angle(rows.firstOrNull { it.isNext }?.at ?: m.now, zone).let { if (it < nowDeg) it + 360 else it }

    Box(Modifier.size(1000.dp, 900.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(LINE, R, Offset(CX, CY), style = Stroke(10f))
            drawCircle(hex(0x1C1F38), 262f, Offset(CX, CY), style = Stroke(1f))
            val box = Offset(CX - R, CY - R)
            val sz = Size(R * 2, R * 2)
            // Canvas arcs start at 3 o'clock; the dial's 0° is 12 o'clock.
            val a0 = angle(rise, zone)
            val a1 = angle(set, zone)
            drawArc(GOLD.copy(alpha = 0.45f), (a0 - 90).toFloat(), (a1 - a0).toFloat(), false, box, sz, style = Stroke(10f, cap = StrokeCap.Round))
            drawArc(cd, (nowDeg - 90).toFloat(), (nextDeg - nowDeg).toFloat().coerceAtLeast(0.5f), false, box, sz, style = Stroke(14f, cap = StrokeCap.Round))
            for (p in rows) {
                val c = point(angle(p.at, zone), R)
                when {
                    p.isNext -> { drawCircle(BG, 15f, c); drawCircle(cd, 15f, c, style = Stroke(5f)) }
                    p.isPast -> drawCircle(DIM, 9f, c)
                    else -> { drawCircle(BG, 9f, c); drawCircle(SOFT, 9f, c, style = Stroke(3f)) }
                }
            }
            drawLine(INK, point(nowDeg, 276f), point(nowDeg, 344f), 5f, cap = StrokeCap.Round)
        }
        for ((label, deg) in listOf("12 PM" to 0.0, "6 PM" to 90.0, "12 AM" to 180.0, "6 AM" to 270.0)) {
            val p = point(deg, 236f)
            Box(Modifier.offset((p.x - 60).dp, (p.y - 14).dp).size(120.dp, 28.dp), contentAlignment = Alignment.Center) {
                T(label, Fonts.Sora, 18, DIM, spacing = 0.11f)
            }
        }
        for (p in rows) {
            val deg = angle(p.at, zone)
            val anchor = point(deg, 350f)
            val right = sin(Math.toRadians(deg)) >= -0.05
            val nc = if (p.isNext) cd else if (p.isPast) PAST else INK
            val tc = if (p.isNext) cd else if (p.isPast) PAST else SOFT
            Column(
                Modifier.offset((if (right) anchor.x else anchor.x - 260f).dp, (anchor.y - 52).dp).width(260.dp),
                horizontalAlignment = if (right) Alignment.Start else Alignment.End,
            ) {
                Ar(p.name, Fonts.NotoKufi, 36, nc, weight = 600)
                T(p.time, Fonts.Sora, 24, tc)
            }
        }
    }
}

@Composable
private fun Home07(m: HomeModel, nav: Nav, first: FocusRequester) {
    val n = m.next
    val cd = if (n.urgent) RED else GOLD
    Box(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.offset(88.dp, 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ico.PIN, GOLD, 20)
            Spacer(Modifier.width(10.dp))
            T(m.location.uppercase(), Fonts.Sora, 20, MUTED, spacing = 0.14f)
        }
        Box(Modifier.offset(40.dp, 110.dp)) { Dial(m) }
        Column(
            Modifier.offset(340.dp, 380.dp).size(400.dp, 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            T("NEXT PRAYER", Fonts.Sora, 18, MUTED, spacing = 0.3f)
            Ar(n.name, Fonts.NotoKufi, 88, INK, weight = 600, lineHeight = 1.5f)
            CountdownGroups(n.groups, cd, Fonts.Sora, 84, 300, Fonts.Sora, 15, 0.26f, sepSize = 72, gap = 10, sepWeight = 200, labelGap = 8)
            val chip = if (n.urgent) RED else MUTED
            Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.BELL, chip, 18)
                Spacer(Modifier.width(8.dp))
                AlertLine(m, Fonts.Sora, 18, chip, Fonts.NotoKufi, withName = false)
            }
        }

        Column(Modifier.padding(start = 1100.dp, end = 88.dp, top = 60.dp, bottom = 52.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(34.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                val c = m.clock
                Row(verticalAlignment = Alignment.Bottom) {
                    T(c.time, Fonts.Sora, 170, INK, weight = 200, lineHeight = 1f, spacing = -0.03f)
                    Spacer(Modifier.width(14.dp))
                    T(c.ampm, Fonts.Sora, 46, GOLD, modifier = Modifier.padding(bottom = 16.dp))
                }
                T(c.date, Fonts.Sora, 34, INK, weight = 300, modifier = Modifier.padding(top = 10.dp))
                Ar(c.hijri.full, Fonts.NotoKufi, 40, GOLD, weight = 500, modifier = Modifier.padding(top = 10.dp))
            }
            MatchesCard(nav, Modifier.fillMaxWidth().height(250.dp), radius = 24, bg = CARD, bgFocus = CARD_HI, border = LINE, focusBorder = GOLD) {
                val list = m.homeMatches
                Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T("MY TEAMS", Fonts.Sora, 17, MUTED, spacing = 0.24f)
                        Spacer(Modifier.weight(1f))
                        list.firstOrNull()?.let { StatusChip(it, m, Fonts.Sora, 16, RED, hex(0xFF8A8E), BG, MUTED, GOLD, pill = false) }
                    }
                    if (list.isEmpty()) { NoMatches(m, Fonts.Sora, 22, MUTED); return@Column }
                    BigScoreLine(list.first(), Modifier.fillMaxWidth(), Fonts.Sora, 26, INK, Fonts.Sora, 42, 500, GOLD, MUTED, logo = 36)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
                    for (x in list.drop(1)) SmallMatchLine(x, m, Fonts.Sora, 21, INK, MUTED, logo = 24, scoreColor = GOLD, liveColor = RED, vs = "v", rightSize = 20)
                }
            }
            HomeApps(
                m, nav, first, tileW = 170, tileH = 290, gap = 16, radius = 22, weightTiles = true,
                look = TileLook(CARD, CARD_HI, LINE, GOLD, INK, MUTED, Fonts.Sora, 20, focusScale = 1.03f, dashColor = hex(0x4A5078), labelWeight = 400),
            )
        }
    }
}
