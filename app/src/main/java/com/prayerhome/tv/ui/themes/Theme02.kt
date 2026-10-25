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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.prayerhome.tv.ui.box
import com.prayerhome.tv.ui.hex

private val BG = hex(0x0D3328)
private val DEEP = hex(0x0A281F)
private val TILE_HI = hex(0x134638)
private val LINE = hex(0x24564A)
private val BRASS = hex(0xC9A45C)
private val INK = hex(0xF4EEDC)
private val MUTED = hex(0xA9BFB4)
private val RED = hex(0xFF6B6B)

@Composable
private fun Rosette(size: Int, color: Color) {
    Canvas(Modifier.size(size.dp)) {
        val k = this.size.width / 96f
        val st = Stroke(2.5f * k)
        drawRect(color, Offset(20 * k, 20 * k), Size(56 * k, 56 * k), style = st)
        rotate(45f) { drawRect(color, Offset(20 * k, 20 * k), Size(56 * k, 56 * k), style = st) }
        drawCircle(color, 12 * k, style = st)
    }
}

val Theme02 = HomeTheme(
    name = "Emerald Mihrab",
    style = PageStyle(
        bg = BG, surface = DEEP, surfaceFocus = TILE_HI, border = LINE, accent = BRASS, text = INK, muted = MUTED,
        red = RED, redBg = hex(0x3A1A1C),
        display = Fonts.Marcellus, displayWeight = 400, body = Fonts.Manrope, arabic = Fonts.ReemKufi, arabicWeight = 600,
        radius = 20, padX = 64, padY = 64,
    ),
    home = { m, nav, first -> Home02(m, nav, first) },
)

@Composable
private fun Home02(m: HomeModel, nav: Nav, first: FocusRequester) {
    Row(Modifier.fillMaxSize().background(BG).padding(64.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        // left: clock, dates, apps
        Column(Modifier.width(540.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.PIN, BRASS, 22)
                    Spacer(Modifier.width(10.dp))
                    T(m.location.uppercase(), Fonts.Manrope, 20, MUTED, spacing = 0.14f)
                }
                val c = m.clock
                Row(Modifier.padding(top = 36.dp), verticalAlignment = Alignment.Bottom) {
                    T(c.time, Fonts.Marcellus, 190, INK, lineHeight = 0.9f)
                    Spacer(Modifier.width(14.dp))
                    T(c.ampm, Fonts.Marcellus, 52, BRASS, modifier = Modifier.padding(bottom = 14.dp))
                }
                T(c.date, Fonts.Manrope, 34, INK, weight = 300, modifier = Modifier.padding(top = 26.dp))
                Box(Modifier.padding(top = 26.dp, bottom = 20.dp).size(120.dp, 2.dp).background(BRASS))
                Ar("${c.hijri.weekday} ${c.hijri.day} ${c.hijri.month}", Fonts.ReemKufi, 48, BRASS, weight = 500, lineHeight = 1.4f)
                Ar("${c.hijri.year} هـ", Fonts.ReemKufi, 48, BRASS, weight = 500, lineHeight = 1.4f)
            }
            HomeApps(
                m, nav, first, tileW = 260, tileH = 150, gap = 20, radius = 20, columns = 2, weightTiles = true,
                look = TileLook(DEEP, TILE_HI, LINE, BRASS, INK, MUTED, Fonts.Manrope, 24, focusScale = 1.03f, dashColor = hex(0x4E7D70)),
            )
        }

        // middle: the double mihrab arch, running off the bottom edge
        val n = m.next
        val inner = if (n.urgent) RED else LINE
        val cd = if (n.urgent) RED else INK
        Box(Modifier.width(640.dp).fillMaxHeight().wrapContentHeight(Alignment.Top, unbounded = true).requiredHeight(1030.dp)) {
            Box(
                Modifier.fillMaxSize().box(Color.Transparent, BRASS, 3.dp, RoundedCornerShape(topStart = 320.dp, topEnd = 320.dp))
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp),
            ) {
                Column(
                    Modifier.fillMaxSize().box(DEEP, inner, 2.dp, RoundedCornerShape(topStart = 302.dp, topEnd = 302.dp)).padding(bottom = 140.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Rosette(88, BRASS)
                    Spacer(Modifier.height(30.dp))
                    T("NEXT PRAYER", Fonts.Manrope, 20, MUTED, spacing = 0.32f)
                    Ar(n.name, Fonts.ReemKufi, 150, INK, weight = 600, lineHeight = 1.3f)
                    T("at ${n.time}", Fonts.Manrope, 30, MUTED)
                    Spacer(Modifier.height(44.dp))
                    CountdownGroups(n.groups, cd, Fonts.Marcellus, 130, 400, Fonts.Manrope, 18, 0.28f, sepSize = 110, gap = 14, labelGap = 8)
                    Spacer(Modifier.height(40.dp))
                    val chip = if (n.urgent) RED else MUTED
                    Row(
                        Modifier.box(Color.Transparent, if (n.urgent) RED else LINE, 1.dp, RoundedCornerShape(999.dp)).padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ico.BELL, chip, 22)
                        Spacer(Modifier.width(10.dp))
                        AlertLine(m, Fonts.Manrope, 20, chip, Fonts.ReemKufi, withName = false)
                    }
                }
            }
        }

        // right: prayer table and teams
        Column(Modifier.width(540.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("PRAYER TIMES", Fonts.Manrope, 18, MUTED, spacing = 0.3f, modifier = Modifier.padding(bottom = 6.dp))
                for (p in m.prayers) {
                    val on = n.urgent
                    val bg = if (p.isNext) (if (on) hex(0x3A1A1C) else TILE_HI) else Color.Transparent
                    val bd = if (p.isNext) (if (on) RED else BRASS) else hex(0x1C4A3E)
                    Row(
                        Modifier.fillMaxWidth().height(66.dp).box(bg, bd, if (p.isNext) 2.dp else 1.dp, RoundedCornerShape(16.dp)).padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tc = if (p.isNext) (if (on) RED else INK) else if (p.isPast) hex(0x86A396) else INK
                        val nc = if (p.isNext) (if (on) RED else BRASS) else if (p.isPast) hex(0x86A396) else INK
                        T(p.time, Fonts.Marcellus, 32, tc)
                        Spacer(Modifier.weight(1f))
                        Ar(p.name, Fonts.ReemKufi, 36, nc, weight = 500)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader("MY TEAMS", Fonts.Manrope, 18, MUTED, BRASS)
                MatchesCard(nav, Modifier.weight(1f).fillMaxWidth(), radius = 18, bg = Color.Transparent, bgFocus = hex(0x0F3A2E), border = Color.Transparent, focusBorder = BRASS) {
                    Matches02(m)
                }
            }
        }
    }
}

@Composable
private fun Matches02(m: HomeModel) {
    val list = m.homeMatches
    Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (list.isEmpty()) { NoMatches(m, Fonts.Manrope, 22, MUTED); return@Column }
        val head = list.first()
        Column(
            Modifier.fillMaxWidth().box(DEEP, LINE, 1.dp, RoundedCornerShape(18.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(head, m, Fonts.Manrope, 17, hex(0xFF5A5F), hex(0xFF8A8E), Color.Transparent, MUTED, BRASS, pill = false)
                Spacer(Modifier.weight(1f))
                T(head.league, Fonts.Manrope, 16, MUTED)
            }
            BigScoreLine(head, Modifier.fillMaxWidth(), Fonts.Manrope, 24, INK, Fonts.Marcellus, 40, 400, BRASS, MUTED, logo = 34)
        }
        list.drop(1).forEachIndexed { i, x ->
            SmallMatchLine(
                x, m, Fonts.Manrope, 20, INK, MUTED, logo = 26, scoreColor = BRASS, liveColor = RED, rightSize = 18,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            )
            if (i == 0 && list.size > 2) Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
        }
    }
}
