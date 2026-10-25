package com.prayerhome.tv.ui.themes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.core.Prayer
import com.prayerhome.tv.football.Match
import com.prayerhome.tv.football.MatchState
import com.prayerhome.tv.ui.Ar
import com.prayerhome.tv.ui.Dot
import com.prayerhome.tv.ui.Fonts
import com.prayerhome.tv.ui.HomeModel
import com.prayerhome.tv.ui.HomeTheme
import com.prayerhome.tv.ui.Ico
import com.prayerhome.tv.ui.Icon
import com.prayerhome.tv.ui.Nav
import com.prayerhome.tv.ui.PageStyle
import com.prayerhome.tv.ui.T
import com.prayerhome.tv.ui.box
import com.prayerhome.tv.ui.hex
import java.time.Duration

private val BG = hex(0x0A0A0A)
private val CARD = hex(0x111111)
private val CARD_HI = hex(0x0F2421)
private val LINE = hex(0x262626)
private val TEAL = hex(0x3CC8B4)
private val INK = hex(0xF2F2F2)
private val MUTED = hex(0x8E8E8E)
private val RED = hex(0xFF5A5F)

/** Square-kufic key pattern used inside the countdown panel. */
@Composable
private fun KufiPattern(alpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val c = TEAL.copy(alpha = alpha)
        val st = Stroke(2f)
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                val p = Path().apply {
                    moveTo(x + 6, y + 6); lineTo(x + 42, y + 6); lineTo(x + 42, y + 14); lineTo(x + 14, y + 14)
                    lineTo(x + 14, y + 42); lineTo(x + 6, y + 42); close()
                    moveTo(x + 22, y + 22); lineTo(x + 42, y + 22); lineTo(x + 42, y + 42); lineTo(x + 22, y + 42); close()
                }
                drawPath(p, c, style = st)
                x += 48
            }
            y += 48
        }
    }
}

val Theme03 = HomeTheme(
    name = "Kufi Timeline",
    style = PageStyle(
        bg = BG, surface = CARD, surfaceFocus = CARD_HI, border = LINE, accent = TEAL, text = INK, muted = MUTED,
        red = RED, redBg = hex(0x1A0D0E),
        display = Fonts.Archivo, displayWeight = 500, body = Fonts.Archivo, arabic = Fonts.Kufam, arabicWeight = 600,
        numbers = Fonts.JetBrains, numbersWeight = 400, radius = 0, labelSpacing = 0.2f, padX = 80, padY = 60,
    ),
    home = { m, nav, first -> Home03(m, nav, first) },
)

@Composable
private fun Home03(m: HomeModel, nav: Nav, first: FocusRequester) {
    Column(Modifier.fillMaxSize().background(BG).padding(horizontal = 80.dp, vertical = 60.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
        Row(Modifier.fillMaxWidth().height(380.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.PIN, TEAL, 20, stroke = 2f)
                    Spacer(Modifier.width(10.dp))
                    T(m.location.uppercase() + " · " + m.coordsText, Fonts.JetBrains, 20, MUTED, spacing = 0.06f)
                }
                val c = m.clock
                Row(verticalAlignment = Alignment.Bottom) {
                    T(c.time, Fonts.JetBrains, 200, INK, weight = 300, lineHeight = 0.9f, spacing = -0.04f)
                    Spacer(Modifier.width(18.dp))
                    T(c.ampm, Fonts.JetBrains, 48, TEAL, modifier = Modifier.padding(bottom = 14.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(c.date, Fonts.Archivo, 34, INK)
                    Spacer(Modifier.width(28.dp))
                    Box(Modifier.size(2.dp, 40.dp).background(TEAL))
                    Spacer(Modifier.width(28.dp))
                    Ar(c.hijri.full, Fonts.Kufam, 38, TEAL, weight = 500, latin = Fonts.Archivo, latinSize = 32, latinWeight = 400)
                }
            }
            val n = m.next
            val cd = if (n.urgent) RED else TEAL
            Box(Modifier.width(640.dp).fillMaxHeight().box(hex(0x101010), cd, 2.dp, RectangleShape).clipToBounds()) {
                KufiPattern(0.12f)
                Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T("NEXT · ${n.time}", Fonts.JetBrains, 20, MUTED, spacing = 0.2f)
                        Spacer(Modifier.weight(1f))
                        val chip = if (n.urgent) RED else MUTED
                        Icon(Ico.BELL, chip, 20)
                        Spacer(Modifier.width(8.dp))
                        T(if (n.urgent) "CHIME PLAYED" else "CHIME ${n.chimeAt}", Fonts.JetBrains, 18, chip)
                    }
                    Ar(n.name, Fonts.Blaka, 120, INK, lineHeight = 1.1f, align = TextAlign.Right, modifier = Modifier.fillMaxWidth(), latin = Fonts.Archivo, latinSize = 112, latinWeight = 600, latinSpacing = -0.02f)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        for ((v, l) in n.groups) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                T(v, Fonts.JetBrains, 104, cd, lineHeight = 1f)
                                T(l, Fonts.JetBrains, 26, cd, modifier = Modifier.padding(bottom = 14.dp))
                            }
                        }
                    }
                }
            }
        }

        Timeline03(m)

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T("APPS", Fonts.JetBrains, 18, MUTED, spacing = 0.2f)
                HomeApps(
                    m, nav, first, tileW = 190, tileH = 190, gap = 20, radius = 0,
                    look = TileLook(CARD, CARD_HI, LINE, TEAL, INK, hex(0xA8A8A8), Fonts.Archivo, 22, focusScale = 1.03f, dashColor = hex(0x444444), shape = { RectangleShape }),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader("MY TEAMS", Fonts.JetBrains, 18, MUTED, TEAL, spacing = 0.2f)
                MatchesCard(nav, Modifier.fillMaxWidth().height(190.dp), radius = 0, bg = Color.Transparent, bgFocus = Color.Transparent, border = Color.Transparent, focusBorder = TEAL, shape = RectangleShape, focusWidth = 2) {
                    val list = m.homeMatches
                    if (list.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp)) { NoMatches(m, Fonts.Archivo, 24, MUTED) }
                    else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        for (x in list) MatchTile03(x, m, Modifier.weight(1f).fillMaxHeight())
                        repeat(3 - list.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchTile03(x: Match, m: HomeModel, modifier: Modifier) {
    val live = x.isLive
    Column(
        modifier.box(if (live) hex(0x1A0D0E) else CARD, if (live) hex(0x5A2226) else LINE, 1.dp, RectangleShape).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        when (x.state) {
            MatchState.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(RED, 10); Spacer(Modifier.width(8.dp)); T("LIVE ${x.statusLabel}", Fonts.JetBrains, 18, hex(0xFF8A8E))
            }
            MatchState.UPCOMING -> T(m.matchTime(x).uppercase().replace(" · ", " "), Fonts.JetBrains, 18, TEAL)
            else -> T("${m.matchDay(x).uppercase()} · ${x.statusLabel}", Fonts.JetBrains, 18, MUTED)
        }
        for ((side, score) in listOf(x.home to x.homeScore, x.away to x.awayScore)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TeamLogoSafe(side, 30)
                Spacer(Modifier.width(10.dp))
                T(side.name, Fonts.Archivo, 24, INK, modifier = Modifier.weight(1f))
                if (x.hasScore) T(score, Fonts.JetBrains, 26, INK, weight = 500)
            }
        }
    }
}

/** The day as a line: prayers placed by time, filled up to "now". */
@Composable
private fun Timeline03(m: HomeModel) {
    val rows = m.prayers
    val fajr = rows.first { it.prayer == Prayer.FAJR }.at
    val isha = rows.first { it.prayer == Prayer.ISHA }.at
    val start = fajr.minus(Duration.ofMinutes(96))
    val end = isha.plus(Duration.ofMinutes(85))
    val span = Duration.between(start, end).seconds.toFloat()
    fun frac(i: java.time.Instant) = (Duration.between(start, i).seconds / span).coerceIn(0f, 1f)
    val urgent = m.next.urgent
    val cd = if (urgent) RED else TEAL
    BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp)) {
        val w = maxWidth.value
        val nowX = frac(m.now) * w
        Box(Modifier.offset(0.dp, 98.dp).fillMaxWidth().height(4.dp).background(LINE))
        Box(Modifier.offset(0.dp, 98.dp).width(nowX.dp).height(4.dp).background(TEAL))
        for (p in rows) {
            val x = frac(p.at) * w
            Column(Modifier.offset((x - 90).dp, 0.dp).width(180.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val nc = if (p.isNext) cd else if (p.isPast) MUTED else INK
                Box(Modifier.height(70.dp), contentAlignment = Alignment.BottomCenter) { Ar(p.name, Fonts.Kufam, 36, nc, weight = 600, latin = Fonts.Archivo, latinSize = 30, latinWeight = 500) }
                Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    val d = if (p.isNext) 28 else 16
                    val mod = Modifier.size(d.dp)
                    when {
                        p.isPast -> Box(mod.background(TEAL, CircleShape))
                        p.isNext -> Box(mod.box(BG, cd, 4.dp, CircleShape))
                        else -> Box(mod.box(BG, hex(0x6A6A6A), 2.dp, CircleShape))
                    }
                }
                T(p.time, Fonts.JetBrains, 24, if (p.isNext) cd else MUTED)
            }
        }
        Column(Modifier.offset((nowX - 30).dp, 166.dp).width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(14.dp, 10.dp)) {
                drawPath(Path().apply { moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, INK)
            }
            T("NOW", Fonts.JetBrains, 16, INK, spacing = 0.15f)
        }
    }
}
