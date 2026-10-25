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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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

private val BG = hex(0x0B1020)
private val CARD = hex(0x111830)
private val CARD_HI = hex(0x1A2340)
private val LINE = hex(0x2A3350)
private val GOLD = hex(0xD9B26F)
private val INK = hex(0xF3EEE3)
private val MUTED = hex(0xA3ABBD)
private val RED = hex(0xFF5A5F)

/** Faint eight-point star lattice behind the whole screen. */
@Composable
private fun StarPattern() {
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val c = GOLD.copy(alpha = 0.07f)
        val stroke = Stroke(1.2f)
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawRect(c, Offset(x + 30, y + 30), Size(36f, 36f), style = stroke)
                rotate(45f, Offset(x + 48, y + 48)) { drawRect(c, Offset(x + 30, y + 30), Size(36f, 36f), style = stroke) }
                drawLine(c, Offset(x, y + 48), Offset(x + 22, y + 48), 1.2f)
                drawLine(c, Offset(x + 74, y + 48), Offset(x + 96, y + 48), 1.2f)
                drawLine(c, Offset(x + 48, y), Offset(x + 48, y + 22), 1.2f)
                drawLine(c, Offset(x + 48, y + 74), Offset(x + 48, y + 96), 1.2f)
                x += 96
            }
            y += 96
        }
    }
}

val Theme01 = HomeTheme(
    name = "Midnight Gold",
    style = PageStyle(
        bg = BG, surface = CARD, surfaceFocus = CARD_HI, border = LINE, accent = GOLD, text = INK, muted = MUTED,
        red = RED, redBg = hex(0x3A1218),
        display = Fonts.Cormorant, displayWeight = 500, body = Fonts.Jost, arabic = Fonts.Amiri, arabicWeight = 700,
        radius = 20, backdrop = { StarPattern() },
    ),
    home = { m, nav, first -> Home01(m, nav, first) },
)

@Composable
private fun Home01(m: HomeModel, nav: Nav, first: FocusRequester) {
    Box(Modifier.fillMaxSize().background(BG)) {
        StarPattern()
        Column(Modifier.fillMaxSize().padding(horizontal = 88.dp, vertical = 52.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            TopBar01(m)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                Clock01(m, Modifier.weight(1f).fillMaxHeight())
                Arch01(m)
            }
            Prayers01(m)
            Row(Modifier.height(266.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    T("MY APPS", Fonts.Jost, 18, MUTED, spacing = 0.3f)
                    HomeApps(
                        m, nav, first, tileW = 200, tileH = 200, gap = 20, radius = 24,
                        look = TileLook(CARD, CARD_HI, LINE, GOLD, INK, MUTED, Fonts.Jost, 24, focusScale = 1.04f, dashColor = hex(0x4A5578)),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader("MY TEAMS", Fonts.Jost, 18, MUTED, GOLD)
                    MatchesCard(nav, Modifier.weight(1f).fillMaxWidth(), radius = 24, bg = CARD, bgFocus = CARD_HI, border = LINE, focusBorder = GOLD) {
                        Matches01(m)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar01(m: HomeModel) {
    val urgent = m.next.urgent
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ico.PIN, GOLD, 24)
        Spacer(Modifier.width(12.dp))
        T("${m.location.uppercase()} · ${m.methodLabel.uppercase()}", Fonts.Jost, 22, MUTED, spacing = 0.08f)
        Spacer(Modifier.weight(1f))
        val chip = if (urgent) RED else MUTED
        Icon(Ico.BELL, chip, 24)
        Spacer(Modifier.width(12.dp))
        AlertLine(m, Fonts.Jost, 22, chip, Fonts.Amiri, spacing = 0.08f)
    }
}

@Composable
private fun Clock01(m: HomeModel, modifier: Modifier) {
    val c = m.clock
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(c.time, Fonts.Cormorant, 230, INK, weight = 500, lineHeight = 0.9f, spacing = -0.02f)
            Spacer(Modifier.width(20.dp))
            T(c.ampm, Fonts.Cormorant, 64, GOLD, modifier = Modifier.padding(bottom = 24.dp))
        }
        T(c.date, Fonts.Jost, 40, INK, weight = 300, spacing = 0.02f, modifier = Modifier.padding(top = 24.dp))
        Ar(c.hijri.full, Fonts.Amiri, 58, GOLD, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Arch01(m: HomeModel) {
    val n = m.next
    val cd = if (n.urgent) RED else GOLD
    val shape = RoundedCornerShape(topStart = 310.dp, topEnd = 310.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
    Column(
        Modifier.width(620.dp).fillMaxHeight().box(CARD, cd, 2.dp, shape).padding(start = 40.dp, end = 40.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        T("NEXT PRAYER", Fonts.Jost, 20, MUTED, spacing = 0.3f)
        Ar(n.name, Fonts.Amiri, 110, INK, weight = 700, lineHeight = 1.1f)
        T("at ${n.time}", Fonts.Jost, 28, MUTED, modifier = Modifier.padding(bottom = 18.dp))
        CountdownGroups(n.groups, cd, Fonts.Cormorant, 100, 600, Fonts.Jost, 17, 0.25f, sepSize = 90, gap = 16)
    }
}

@Composable
private fun Prayers01(m: HomeModel) {
    val urgent = m.next.urgent
    val cd = if (urgent) RED else GOLD
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        for (p in m.prayers) {
            val shape = RoundedCornerShape(20.dp)
            val bd = if (p.isNext) cd else LINE
            Column(
                Modifier.weight(1f).fillMaxHeight().box(if (p.isNext) hex(0x18213D) else hex(0x0F1528), bd, if (p.isNext) 2.dp else 1.dp, shape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                val nc = if (p.isNext) cd else if (p.isPast) hex(0x8C95AA) else INK
                val tc = if (p.isNext) cd else if (p.isPast) hex(0x8C95AA) else hex(0xC9CEDA)
                Ar(p.name, Fonts.Amiri, 46, nc, weight = 700, lineHeight = 1.2f)
                T(p.time, Fonts.Jost, 30, tc)
            }
        }
    }
}

@Composable
private fun Matches01(m: HomeModel) {
    val list = m.homeMatches
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp), verticalArrangement = Arrangement.SpaceBetween) {
        if (list.isEmpty()) {
            NoMatches(m, Fonts.Jost, 24, MUTED)
            return@Column
        }
        val head = list.first()
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(head, m, Fonts.Jost, 18, RED, hex(0xFF8A8E), hex(0x3A1218), MUTED, GOLD)
            Spacer(Modifier.width(18.dp))
            BigScoreLine(head, Modifier.weight(1f), Fonts.Jost, 28, INK, Fonts.Cormorant, 44, 600, GOLD, MUTED, logo = 40)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
        for (x in list.drop(1)) {
            SmallMatchLine(x, m, Fonts.Jost, 24, INK, MUTED, logo = 30, scoreColor = GOLD, scoreFont = Fonts.Cormorant, liveColor = RED)
        }
        if (list.size < 3) Spacer(Modifier.height(1.dp))
    }
}
