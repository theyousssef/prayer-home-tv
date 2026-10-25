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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.ui.Ar
import com.prayerhome.tv.ui.Fonts
import com.prayerhome.tv.ui.HomeModel
import com.prayerhome.tv.ui.HomeTheme
import com.prayerhome.tv.ui.Ico
import com.prayerhome.tv.ui.Icon
import com.prayerhome.tv.ui.Nav
import com.prayerhome.tv.ui.PageStyle
import com.prayerhome.tv.ui.T
import com.prayerhome.tv.ui.archShape
import com.prayerhome.tv.ui.box
import com.prayerhome.tv.ui.hex

private val BG = hex(0x10202E)
private val CARD = hex(0x162B3D)
private val CARD_HI = hex(0x1E3850)
private val LINE = hex(0x2B4660)
private val SAFFRON = hex(0xE3B04B)
private val TERRA = hex(0xD2693F)
private val BLUE = hex(0x4A9BD1)
private val INK = hex(0xF3EBDD)
private val MUTED = hex(0xA7B4C2)
private val SOFT = hex(0xD5DDE5)
private val RED = hex(0xFF5A5F)

/** A strip of zellige tiles (the band at the top and bottom of the screen). */
@Composable
fun ZelligeBand(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(56.dp).graphicsLayer()) {
        var x = 0f
        while (x < size.width) {
            drawRect(CARD, Offset(x, 0f), Size(56f, 56f))
            val corners = Path().apply {
                moveTo(x, 0f); lineTo(x + 10, 0f); lineTo(x, 10f); close()
                moveTo(x + 56, 0f); lineTo(x + 46, 0f); lineTo(x + 56, 10f); close()
                moveTo(x, 56f); lineTo(x + 10, 56f); lineTo(x, 46f); close()
                moveTo(x + 56, 56f); lineTo(x + 46, 56f); lineTo(x + 56, 46f); close()
            }
            drawPath(corners, BLUE)
            drawRect(TERRA, Offset(x + 17, 17f), Size(22f, 22f))
            rotate(45f, Offset(x + 28, 28f)) { drawRect(TERRA, Offset(x + 17, 17f), Size(22f, 22f)) }
            drawCircle(SAFFRON, 5f, Offset(x + 28, 28f))
            drawLine(INK, Offset(x + 28, 0f), Offset(x + 28, 9f), 2f)
            drawLine(INK, Offset(x + 28, 47f), Offset(x + 28, 56f), 2f)
            drawLine(INK, Offset(x, 28f), Offset(x + 9, 28f), 2f)
            drawLine(INK, Offset(x + 47, 28f), Offset(x + 56, 28f), 2f)
            x += 56
        }
    }
}

@Composable
private fun Diamond(size: Int, color: Color) {
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.width
        drawRect(color, Offset(s * 0.22f, s * 0.22f), Size(s * 0.56f, s * 0.56f))
        rotate(45f) { drawRect(color, Offset(s * 0.22f, s * 0.22f), Size(s * 0.56f, s * 0.56f)) }
    }
}

val Theme05 = HomeTheme(
    name = "Andalusian Zellige",
    style = PageStyle(
        bg = BG, surface = CARD, surfaceFocus = CARD_HI, border = LINE, accent = SAFFRON, text = INK, muted = MUTED,
        red = RED, redBg = hex(0x3A1C24),
        display = Fonts.Cinzel, displayWeight = 500, body = Fonts.Mulish, arabic = Fonts.ElMessiri, arabicWeight = 700,
        numbersWeight = 600, radius = 16, labelSpacing = 0.22f, padY = 90,
        backdrop = {
            ZelligeBand(Modifier.align(Alignment.TopCenter))
            ZelligeBand(Modifier.align(Alignment.BottomCenter))
        },
    ),
    home = { m, nav, first -> Home05(m, nav, first) },
)

@Composable
private fun Home05(m: HomeModel, nav: Nav, first: FocusRequester) {
    val n = m.next
    val cd = if (n.urgent) RED else SAFFRON
    Column(Modifier.fillMaxSize().background(BG)) {
        ZelligeBand()
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 88.dp, vertical = 38.dp), verticalArrangement = Arrangement.spacedBy(34.dp)) {
            Row(Modifier.fillMaxWidth().height(340.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.PIN, SAFFRON, 20)
                        Spacer(Modifier.width(10.dp))
                        T(m.location.replace(", ", " · "), Fonts.Cinzel, 20, MUTED, spacing = 0.2f)
                    }
                    val c = m.clock
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
                        T(c.time, Fonts.Cinzel, 176, INK, weight = 500, lineHeight = 1f)
                        Spacer(Modifier.width(18.dp))
                        T(c.ampm, Fonts.Cinzel, 48, SAFFRON, modifier = Modifier.padding(bottom = 18.dp))
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(c.date, Fonts.Mulish, 32, INK, weight = 300)
                        Spacer(Modifier.width(26.dp))
                        Diamond(18, TERRA)
                        Spacer(Modifier.width(26.dp))
                        Ar(c.hijri.full, Fonts.ElMessiri, 42, SAFFRON, weight = 600)
                    }
                }
                Column(
                    Modifier.width(600.dp).fillMaxHeight().box(CARD, cd, 2.dp, archShape(300f, 170f, 20f)).padding(start = 40.dp, end = 40.dp, top = 30.dp, bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    T("NEXT PRAYER · ${n.time}", Fonts.Cinzel, 20, MUTED, spacing = 0.24f)
                    Ar(n.name, Fonts.ElMessiri, 84, INK, weight = 700, lineHeight = 1.3f)
                    CountdownGroups(n.groups, cd, Fonts.Cinzel, 80, 600, Fonts.Cinzel, 16, 0.24f, sepSize = 70, gap = 14, labelGap = 4)
                    val chip = if (n.urgent) RED else MUTED
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.BELL, chip, 18)
                        Spacer(Modifier.width(8.dp))
                        AlertLine(m, Fonts.Mulish, 18, chip, Fonts.ElMessiri, withName = false)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                for (p in m.prayers) {
                    val bg = if (p.isNext) (if (n.urgent) hex(0x3A1C24) else hex(0x2A3B3F)) else CARD
                    val c = if (p.isNext) cd else if (p.isPast) hex(0x8798A9) else INK
                    val tc = if (p.isNext) cd else if (p.isPast) hex(0x8798A9) else SOFT
                    Column(
                        Modifier.weight(1f).fillMaxHeight().box(bg, if (p.isNext) cd else LINE, if (p.isNext) 3.dp else 1.dp, archShape(140f, 70f, 14f)).padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
                    ) {
                        Ar(p.name, Fonts.ElMessiri, 46, c, weight = 600, lineHeight = 1.4f)
                        T(p.time, Fonts.Mulish, 28, tc, weight = 500)
                    }
                }
            }

            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                HomeApps(
                    m, nav, first, tileW = 200, tileH = 186, gap = 18, radius = 16,
                    look = TileLook(CARD, CARD_HI, LINE, SAFFRON, INK, MUTED, Fonts.Mulish, 22, focusScale = 1.03f, dashColor = hex(0x4F6B86)),
                )
                MatchesCard(nav, Modifier.weight(1f).fillMaxHeight(), radius = 16, bg = CARD, bgFocus = CARD_HI, border = LINE, focusBorder = SAFFRON) {
                    Matches05(m)
                }
            }
        }
        ZelligeBand()
    }
}

@Composable
private fun Matches05(m: HomeModel) {
    val list = m.homeMatches
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("MY TEAMS", Fonts.Cinzel, 18, MUTED, spacing = 0.22f)
            Spacer(Modifier.weight(1f))
            list.firstOrNull()?.let { StatusChip(it, m, Fonts.Mulish, 17, RED, hex(0xFF8A8E), Color.Transparent, MUTED, SAFFRON, pill = false) }
            Spacer(Modifier.width(18.dp))
            T("ALL MATCHES ›", Fonts.Cinzel, 16, SAFFRON, spacing = 0.12f)
        }
        if (list.isEmpty()) { NoMatches(m, Fonts.Mulish, 24, MUTED); return@Column }
        BigScoreLine(list.first(), Modifier.fillMaxWidth(), Fonts.Mulish, 30, INK, Fonts.Cinzel, 46, 600, SAFFRON, MUTED, logo = 40, nameWeight = 500)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            for (x in list.drop(1)) {
                SmallMatchLine(x, m, Fonts.Mulish, 20, SOFT, MUTED, logo = 24, scoreColor = SAFFRON, liveColor = RED, vs = "v", rightSize = 18, modifier = Modifier.weight(1f))
            }
        }
    }
}
