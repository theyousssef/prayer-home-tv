package com.prayerhome.tv.ui.themes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
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

private val BG = hex(0x1A1020)
private val CARD = hex(0x24172C)
private val CARD_HI = hex(0x33213D)
private val LINE = hex(0x3A2944)
private val AMBER = hex(0xF2A541)
private val INK = hex(0xFBEFD9)
private val CREAM = hex(0xE5D3BE)
private val MUTED = hex(0xB9A8BF)
private val RED = hex(0xFF5A5F)

/** The fanous: a brass lantern whose glass glows amber, or red in the last ten minutes. */
@Composable
private fun Lantern(urgent: Boolean) {
    val glow = if (urgent) hex(0xFF3B3F) else AMBER
    val glass = if (urgent) hex(0x3A1420) else hex(0x2E1E2A)
    val glassLine = if (urgent) RED else AMBER
    Canvas(Modifier.size(560.dp, 1000.dp)) {
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), glow.copy(alpha = 0f)), Offset(280f, 510f), 330f), 330f, Offset(280f, 510f))
        val s3 = Stroke(3f)
        drawCircle(AMBER, 20f, Offset(280f, 38f), style = Stroke(4f))
        drawLine(AMBER, Offset(280f, 58f), Offset(280f, 92f), 4f)
        val dome = Path().apply { moveTo(200f, 172f); quadraticTo(280f, 70f, 360f, 172f); close() }
        drawPath(dome, hex(0x2C1D36)); drawPath(dome, AMBER, style = s3)
        drawRoundRect(hex(0x2C1D36), Offset(166f, 170f), Size(228f, 30f), CornerRadius(4f))
        drawRoundRect(AMBER, Offset(166f, 170f), Size(228f, 30f), CornerRadius(4f), style = s3)
        val body = Path().apply {
            moveTo(150f, 200f); lineTo(410f, 200f); lineTo(470f, 300f); lineTo(470f, 720f); lineTo(410f, 820f)
            lineTo(150f, 820f); lineTo(90f, 720f); lineTo(90f, 300f); close()
        }
        drawPath(body, hex(0x24172C)); drawPath(body, AMBER, style = s3)
        val pane = Path().apply {
            moveTo(170f, 230f); lineTo(390f, 230f); lineTo(440f, 315f); lineTo(440f, 705f); lineTo(390f, 790f)
            lineTo(170f, 790f); lineTo(120f, 705f); lineTo(120f, 315f); close()
        }
        drawPath(pane, glass)
        clipPath(pane) {
            val lattice = AMBER.copy(alpha = 0.18f)
            var y = 0f
            while (y < 1000f) {
                var x = 0f
                while (x < 560f) {
                    drawRect(lattice, Offset(x + 12, y + 12), Size(16f, 16f), style = Stroke(1f))
                    rotate(45f, Offset(x + 20, y + 20)) { drawRect(lattice, Offset(x + 12, y + 12), Size(16f, 16f), style = Stroke(1f)) }
                    x += 40
                }
                y += 40
            }
        }
        drawPath(pane, glassLine, style = Stroke(2f))
        drawLine(AMBER.copy(alpha = 0.6f), Offset(90f, 300f), Offset(470f, 300f), 2f)
        drawLine(AMBER.copy(alpha = 0.6f), Offset(90f, 720f), Offset(470f, 720f), 2f)
        drawRoundRect(hex(0x2C1D36), Offset(166f, 820f), Size(228f, 30f), CornerRadius(4f))
        drawRoundRect(AMBER, Offset(166f, 820f), Size(228f, 30f), CornerRadius(4f), style = s3)
        val foot = Path().apply { moveTo(200f, 850f); lineTo(360f, 850f); lineTo(320f, 905f); lineTo(240f, 905f); close() }
        drawPath(foot, hex(0x2C1D36)); drawPath(foot, AMBER, style = s3)
        drawCircle(AMBER, 10f, Offset(280f, 925f))
    }
}

val Theme06 = HomeTheme(
    name = "Fanous Lantern",
    style = PageStyle(
        bg = BG, surface = CARD, surfaceFocus = CARD_HI, border = LINE, accent = AMBER, text = INK, muted = MUTED,
        red = RED, redBg = hex(0x3A1620),
        display = Fonts.Fraunces, displayWeight = 300, body = Fonts.Figtree, arabic = Fonts.NotoNaskh, arabicWeight = 700,
        numbersWeight = 500, radius = 18, labelSpacing = 0.12f, padY = 48,
    ),
    home = { m, nav, first -> Home06(m, nav, first) },
)

@Composable
private fun Home06(m: HomeModel, nav: Nav, first: FocusRequester) {
    val n = m.next
    val cd = if (n.urgent) RED else AMBER
    Box(Modifier.fillMaxSize().background(BG)) {
        Box(Modifier.offset(80.dp, 40.dp)) { Lantern(n.urgent) }
        Column(
            Modifier.offset(200.dp, 300.dp).size(320.dp, 500.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            T("NEXT PRAYER", Fonts.Figtree, 18, CREAM, spacing = 0.3f)
            Ar(n.name, Fonts.Rakkas, 108, INK, lineHeight = 1.35f, modifier = Modifier.padding(top = 14.dp), latin = Fonts.Fraunces, latinSize = 76, latinWeight = 400, latinLineHeight = 1.3f)
            T("at ${n.time}", Fonts.Figtree, 26, CREAM, modifier = Modifier.padding(bottom = 20.dp))
            CountdownGroups(n.groups, cd, Fonts.Fraunces, 80, 500, Fonts.Figtree, 15, 0.26f, sepSize = 70, gap = 10)
        }

        Column(Modifier.padding(start = 700.dp, end = 88.dp, top = 48.dp, bottom = 48.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.PIN, AMBER, 20)
                Spacer(Modifier.width(10.dp))
                T(m.location.uppercase(), Fonts.Figtree, 20, MUTED, spacing = 0.12f)
                Spacer(Modifier.weight(1f))
                val chip = if (n.urgent) RED else MUTED
                Row(Modifier.box(Color.Transparent, chip, 1.dp, RoundedCornerShape(999.dp)).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.BELL, chip, 18)
                    Spacer(Modifier.width(10.dp))
                    AlertLine(m, Fonts.Figtree, 20, chip, Fonts.NotoNaskh, spacing = 0.04f, withName = false)
                }
            }
            Column {
                val c = m.clock
                Row(verticalAlignment = Alignment.Bottom) {
                    T(c.time, Fonts.Fraunces, 190, INK, weight = 300, lineHeight = 0.92f)
                    Spacer(Modifier.width(16.dp))
                    T(c.ampm, Fonts.Fraunces, 54, AMBER, modifier = Modifier.padding(bottom = 16.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(c.date, Fonts.Figtree, 36, INK, weight = 300)
                    Spacer(Modifier.weight(1f))
                    Ar(c.hijri.full, Fonts.NotoNaskh, 46, AMBER, weight = 600, latin = Fonts.Fraunces, latinSize = 40, latinWeight = 400)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                for (row in m.prayers.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        for (p in row) {
                            val bg = if (p.isNext) (if (n.urgent) hex(0x3A1620) else CARD_HI) else CARD
                            val tc = if (p.isNext) cd else if (p.isPast) hex(0x9C8BA3) else CREAM
                            val nc = if (p.isNext) cd else if (p.isPast) hex(0x9C8BA3) else INK
                            Row(
                                Modifier.weight(1f).height(104.dp).box(bg, if (p.isNext) cd else LINE, if (p.isNext) 2.dp else 1.dp, RoundedCornerShape(18.dp)).padding(horizontal = 28.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T(p.time, Fonts.Fraunces, 34, tc)
                                Spacer(Modifier.weight(1f))
                                Ar(p.name, Fonts.NotoNaskh, 40, nc, weight = 700, latin = Fonts.Fraunces, latinSize = 34, latinWeight = 500)
                            }
                        }
                    }
                }
            }
            MatchesCard(nav, Modifier.fillMaxWidth().height(132.dp), radius = 18, bg = CARD, bgFocus = CARD_HI, border = LINE, focusBorder = AMBER) {
                val list = m.homeMatches
                if (list.isEmpty()) Box(Modifier.fillMaxSize().padding(26.dp)) { NoMatches(m, Fonts.Figtree, 22, MUTED) }
                else Row(Modifier.fillMaxSize()) {
                    list.forEachIndexed { i, x ->
                        if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(LINE))
                        Cell06(x, m, Modifier.weight(1f).fillMaxHeight())
                    }
                    repeat(3 - list.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            HomeApps(
                m, nav, first, tileW = 270, tileH = 170, gap = 18, radius = 18, weightTiles = true,
                look = TileLook(CARD, CARD_HI, LINE, AMBER, INK, MUTED, Fonts.Figtree, 24, focusScale = 1.03f, dashColor = hex(0x5E4A6A)),
            )
        }
    }
}

@Composable
private fun Cell06(x: Match, m: HomeModel, modifier: Modifier) {
    Column(modifier.padding(horizontal = 26.dp, vertical = 18.dp), verticalArrangement = Arrangement.SpaceBetween) {
        when (x.state) {
            MatchState.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(RED, 10); Spacer(Modifier.width(8.dp)); T("LIVE ${x.statusLabel}", Fonts.Figtree, 16, hex(0xFF8A8E), weight = 600, spacing = 0.1f)
            }
            MatchState.UPCOMING -> T(m.matchTime(x).uppercase(), Fonts.Figtree, 16, MUTED, spacing = 0.1f)
            else -> T("${m.matchDay(x).uppercase()} · ${x.statusLabel}", Fonts.Figtree, 16, MUTED, spacing = 0.1f)
        }
        if (x.hasScore) {
            BigScoreLine(x, Modifier.fillMaxWidth(), Fonts.Figtree, 20, INK, Fonts.Fraunces, 32, 400, AMBER, MUTED, logo = 24, dash = "–")
        } else {
            SmallMatchLine(x, m, Fonts.Figtree, 22, INK, MUTED, logo = 28, scoreColor = AMBER, vs = "v", rightSize = 0)
        }
    }
}
