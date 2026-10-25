package com.prayerhome.tv.ui.themes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.apps.AppEntry
import com.prayerhome.tv.ui.Ar
import com.prayerhome.tv.ui.Focusable
import com.prayerhome.tv.ui.Fonts
import com.prayerhome.tv.ui.HomeModel
import com.prayerhome.tv.ui.HomeTheme
import com.prayerhome.tv.ui.Ico
import com.prayerhome.tv.ui.Icon
import com.prayerhome.tv.ui.Nav
import com.prayerhome.tv.ui.PageStyle
import com.prayerhome.tv.ui.T
import com.prayerhome.tv.ui.box
import com.prayerhome.tv.ui.dashed
import com.prayerhome.tv.ui.hex

private val BG = hex(0x080808)
private val CARD = hex(0x121110)
private val CARD_HI = hex(0x171512)
private val LINE = hex(0x2A2620)
private val RING = hex(0x3A352C)
private val GOLD = hex(0xCDAA6B)
private val INK = hex(0xF4F1EA)
private val MUTED = hex(0x9A958C)
private val SOFT = hex(0xC9C4BA)
private val PAST = hex(0x8A857C)
private val RED = hex(0xFF5A5F)

val Theme10 = HomeTheme(
    name = "Calligraphy Ambient",
    style = PageStyle(
        bg = BG, surface = CARD, surfaceFocus = CARD_HI, border = LINE, accent = GOLD, text = INK, muted = MUTED,
        red = RED, redBg = hex(0x2A1212),
        display = Fonts.Italiana, displayWeight = 400, body = Fonts.Karla, arabic = Fonts.Ruwudu, arabicWeight = 700,
        radius = 18, labelSpacing = 0.3f,
    ),
    home = { m, nav, first -> Home10(m, nav, first) },
)

@Composable
private fun Ornament() {
    Canvas(Modifier.size(26.dp)) {
        val st = Stroke(1.4f)
        drawRect(GOLD, Offset(7f, 7f), Size(12f, 12f), style = st)
        rotate(45f) { drawRect(GOLD, Offset(7f, 7f), Size(12f, 12f), style = st) }
    }
}

@Composable
private fun Home10(m: HomeModel, nav: Nav, first: FocusRequester) {
    val n = m.next
    val cd = if (n.urgent) RED else GOLD
    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.offset(88.dp, 60.dp)) {
            val c = m.clock
            Row(verticalAlignment = Alignment.Bottom) {
                T(c.time, Fonts.Italiana, 128, INK, lineHeight = 1f)
                Spacer(Modifier.width(12.dp))
                T(c.ampm, Fonts.Italiana, 40, GOLD, modifier = Modifier.padding(bottom = 12.dp))
            }
            T(c.date, Fonts.Karla, 26, INK, weight = 300, spacing = 0.06f, modifier = Modifier.padding(top = 8.dp))
        }
        Column(Modifier.align(Alignment.TopEnd).padding(end = 88.dp, top = 60.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Ar(m.clock.hijri.full, Fonts.Ruwudu, 56, GOLD, weight = 500, lineHeight = 1.5f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.PIN, GOLD, 18)
                Spacer(Modifier.width(8.dp))
                T(m.location.uppercase(), Fonts.Karla, 18, MUTED, spacing = 0.2f)
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("NEXT PRAYER · ${n.time}", Fonts.Karla, 18, MUTED, spacing = 0.5f, modifier = Modifier.padding(bottom = 30.dp))
            Ar(n.name, Fonts.Ruwudu, 250, if (n.urgent) INK else GOLD, weight = 700, lineHeight = 1.35f)
            Row(Modifier.width(760.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(hex(0x4A4130)))
                Ornament()
                Box(Modifier.weight(1f).height(1.dp).background(hex(0x4A4130)))
            }
            Spacer(Modifier.height(18.dp))
            CountdownGroups(n.groups, cd, Fonts.Italiana, 136, 400, Fonts.Karla, 16, 0.4f, sepSize = 120, gap = 16)
            val chip = if (n.urgent) RED else MUTED
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.BELL, chip, 18)
                Spacer(Modifier.width(8.dp))
                AlertLine(m, Fonts.Karla, 18, chip, Fonts.Ruwudu, spacing = 0.08f, withName = false)
            }
        }

        Row(Modifier.offset(0.dp, 808.dp).fillMaxWidth().padding(horizontal = 88.dp), horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            for (p in m.prayers) {
                val tc = if (p.isNext) cd else if (p.isPast) PAST else SOFT
                val nc = if (p.isNext) cd else if (p.isPast) PAST else INK
                Row(
                    Modifier.box(Color.Transparent, if (p.isNext) cd else Color.Transparent, 1.dp, RoundedCornerShape(999.dp)).padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    T(p.time, Fonts.Karla, 22, tc)
                    Ar(p.name, Fonts.Ruwudu, 38, nc, weight = 600, lineHeight = 1.4f)
                }
            }
        }

        // round app icons
        Row(Modifier.align(Alignment.BottomStart).padding(start = 88.dp, bottom = 48.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            m.pinned.forEachIndexed { i, app -> RoundApp(app, if (i == 0) first else null) { nav.launch(app) } }
            Focusable(onClick = nav.apps, focusRequester = if (m.pinned.isEmpty()) first else null) { f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(96.dp).scale(if (f) 1.06f else 1f).let { if (f) it.box(CARD_HI, GOLD, 3.dp, CircleShape) else it.dashed(hex(0x5A5446), 48.dp) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ico.GRID, if (f) GOLD else hex(0xA8A397), 34, stroke = 1.5f) }
                    T("All apps", Fonts.Karla, 18, hex(0xA8A397))
                }
            }
            Focusable(onClick = nav.settings) { f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(96.dp).scale(if (f) 1.06f else 1f).let { if (f) it.box(CARD_HI, GOLD, 3.dp, CircleShape) else it.dashed(hex(0x5A5446), 48.dp) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ico.GEAR, if (f) GOLD else hex(0xA8A397), 34, stroke = 1.5f) }
                    T("Settings", Fonts.Karla, 18, hex(0xA8A397))
                }
            }
        }

        MatchesCard(
            nav, Modifier.align(Alignment.BottomEnd).padding(end = 72.dp, bottom = 40.dp).width(652.dp).height(170.dp),
            radius = 18, bg = Color.Transparent, bgFocus = CARD, border = Color.Transparent, focusBorder = GOLD, focusWidth = 1,
        ) {
            val list = m.homeMatches
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (list.isEmpty()) { NoMatches(m, Fonts.Karla, 22, MUTED); return@Column }
                val head = list.first()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(head, m, Fonts.Karla, 17, RED, hex(0xFF8A8E), Color.Transparent, MUTED, GOLD, pill = false)
                    Spacer(Modifier.width(16.dp))
                    BigScoreLine(head, Modifier.weight(1f), Fonts.Karla, 26, INK, Fonts.Italiana, 42, 400, GOLD, MUTED, logo = 32)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
                for (x in list.drop(1)) SmallMatchLine(x, m, Fonts.Karla, 20, SOFT, MUTED, logo = 22, scoreColor = GOLD, liveColor = RED, vs = "v", rightSize = 20)
            }
        }
    }
}

@Composable
private fun RoundApp(app: AppEntry, fr: FocusRequester?, onClick: () -> Unit) {
    Focusable(onClick = onClick, focusRequester = fr) { f ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(96.dp).scale(if (f) 1.06f else 1f).box(if (f) CARD_HI else CARD, if (f) GOLD else RING, if (f) 3.dp else 1.dp, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val img = app.icon ?: app.banner
                if (img != null) Image(img, app.label, Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
            }
            T(app.label, Fonts.Karla, 18, INK)
        }
    }
}
