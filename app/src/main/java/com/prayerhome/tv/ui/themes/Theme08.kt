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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.prayerhome.tv.ui.box
import com.prayerhome.tv.ui.hex
import com.prayerhome.tv.ui.hexA

private val NIGHT = hex(0x0F1A33)
private val SKY = hex(0x14224A)
private val CARD = hexA(0xDB101C38)
private val CARD_HI = hex(0x1B2B57)
private val LINE = hex(0x2A3A66)
private val GOLD = hex(0xE9C46A)
private val MOON = hex(0xF1E6C8)
private val INK = hex(0xF2EEE6)
private val MUTED = hex(0xA9B3CC)
private val SOFT = hex(0xD3D9E8)
private val RED = hex(0xFF5A5F)

/** Stars and the silhouette of Cairo's minarets and domes along the bottom. */
@Composable
fun CairoSkyline() {
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        for ((x, y, r) in listOf(Triple(420f, 70f, 2f), Triple(610f, 140f, 1.5f), Triple(980f, 60f, 2f), Triple(1180f, 120f, 1.5f), Triple(1350f, 50f, 2f), Triple(860f, 180f, 1.5f))) {
            drawCircle(MOON, r, Offset(x, y))
        }
        fun minaret(x: Float, top: Float, w: Float, tipH: Float, balcony: Float?) {
            drawRect(SKY, Offset(x, top), Size(w, 1080f - top))
            drawPath(Path().apply { moveTo(x - 4, top); lineTo(x + w / 2, top - tipH); lineTo(x + w + 4, top); close() }, SKY)
            balcony?.let { drawRect(SKY, Offset(x - 8, it), Size(w + 16, 8f)) }
        }
        fun dome(cx: Float, base: Float, r: Float, bodyW: Float, finial: Boolean) {
            drawPath(Path().apply { addArc(Rect(cx - r, base - r, cx + r, base + r), 180f, 180f); close() }, SKY)
            drawRect(SKY, Offset(cx - bodyW / 2, base), Size(bodyW, 1080f - base))
            if (finial) drawRect(SKY, Offset(cx - 2, base - r - 20), Size(4f, 22f))
        }
        minaret(140f, 760f, 22f, 48f, 830f)
        dome(330f, 900f, 110f, 230f, true)
        minaret(520f, 700f, 26f, 58f, 780f)
        dome(760f, 880f, 150f, 320f, true)
        minaret(980f, 720f, 22f, 48f, null)
        minaret(1050f, 720f, 22f, 48f, null)
        dome(1250f, 930f, 90f, 200f, false)
        minaret(1420f, 680f, 28f, 60f, 770f)
        dome(1620f, 900f, 130f, 280f, false)
        minaret(1800f, 740f, 22f, 46f, null)
        drawRect(SKY, Offset(0f, 1000f), Size(size.width, 80f))
    }
}

@Composable
private fun Crescent(size: Int) {
    Canvas(Modifier.size(size.dp)) {
        val k = this.size.width / 64f
        drawCircle(MOON, 26 * k, Offset(30 * k, 32 * k))
        drawCircle(NIGHT, 24 * k, Offset(42 * k, 26 * k))
    }
}

val Theme08 = HomeTheme(
    name = "Cairo Skyline",
    style = PageStyle(
        bg = NIGHT, surface = CARD, surfaceFocus = CARD_HI, border = LINE, accent = GOLD, text = INK, muted = MUTED,
        red = RED, redBg = hex(0x3A1828),
        display = Fonts.Josefin, displayWeight = 300, body = Fonts.Josefin, arabic = Fonts.Qahiri, arabicWeight = 400,
        radius = 20, labelSpacing = 0.26f, backdrop = { CairoSkyline() },
    ),
    home = { m, nav, first -> Home08(m, nav, first) },
)

@Composable
private fun Home08(m: HomeModel, nav: Nav, first: FocusRequester) {
    val n = m.next
    val cd = if (n.urgent) RED else GOLD
    Box(Modifier.fillMaxSize().background(NIGHT)) {
        CairoSkyline()

        Row(Modifier.padding(start = 88.dp, end = 88.dp, top = 56.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                val c = m.clock
                Row(verticalAlignment = Alignment.Bottom) {
                    T(c.time, Fonts.Josefin, 190, INK, weight = 200, lineHeight = 0.9f)
                    Spacer(Modifier.width(14.dp))
                    T(c.ampm, Fonts.Josefin, 52, GOLD, modifier = Modifier.padding(bottom = 16.dp))
                }
                T(c.date, Fonts.Josefin, 36, INK, weight = 300, spacing = 0.02f, modifier = Modifier.padding(top = 14.dp))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val h = m.clock.hijri
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Crescent(64)
                    Spacer(Modifier.width(22.dp))
                    Ar(h.dayMonthYear, Fonts.Qahiri, 78, GOLD, lineHeight = 1.3f, digits = Fonts.ReemKufi, latin = Fonts.Josefin, latinSize = 54, latinWeight = 300, latinLineHeight = 1.4f)
                }
                Ar(h.weekday, Fonts.Qahiri, 44, INK, latin = Fonts.Josefin, latinSize = 34, latinWeight = 300)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.PIN, GOLD, 18)
                    Spacer(Modifier.width(10.dp))
                    T(m.location.uppercase(), Fonts.Josefin, 20, MUTED, spacing = 0.16f)
                }
            }
        }

        // next prayer band
        Box(Modifier.offset(88.dp, 372.dp).size(1744.dp, 190.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(LINE))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    T("NEXT · ${n.time}", Fonts.Josefin, 18, MUTED, spacing = 0.3f)
                    Ar(n.name, Fonts.Qahiri, 128, INK, lineHeight = 1.15f, latin = Fonts.Josefin, latinSize = 104, latinWeight = 300)
                }
                Box(Modifier.size(2.dp, 120.dp).background(cd))
                CountdownGroups(n.groups, cd, Fonts.Josefin, 120, 300, Fonts.Josefin, 17, 0.3f, sepSize = 104, gap = 14, sepWeight = 200, labelGap = 4)
                val chip = if (n.urgent) RED else MUTED
                Row(Modifier.box(Color.Transparent, chip, 1.dp, RoundedCornerShape(999.dp)).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.BELL, chip, 20)
                    Spacer(Modifier.width(10.dp))
                    T(if (n.urgent) "Chime played" else "Chime ${n.chimeAt}", Fonts.Josefin, 20, chip)
                }
            }
        }

        // prayer pills
        Row(Modifier.offset(88.dp, 592.dp).size(1744.dp, 112.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            for (p in m.prayers) {
                val bg = if (p.isNext) (if (n.urgent) hex(0x3A1828) else CARD_HI) else hex(0x101C38)
                val tc = if (p.isNext) cd else if (p.isPast) hex(0x8E99B5) else SOFT
                val nc = if (p.isNext) cd else if (p.isPast) hex(0x8E99B5) else INK
                Row(
                    Modifier.weight(1f).fillMaxHeight().box(bg, if (p.isNext) cd else LINE, if (p.isNext) 2.dp else 1.dp, RoundedCornerShape(999.dp)),
                    horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(p.time, Fonts.Josefin, 28, tc)
                    Ar(p.name, Fonts.Qahiri, 46, nc, lineHeight = 1.2f, latin = Fonts.Josefin, latinSize = 28, latinWeight = 400)
                }
            }
        }

        // apps + teams
        Row(Modifier.align(Alignment.BottomStart).padding(start = 88.dp, end = 88.dp, bottom = 40.dp).fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            HomeApps(
                m, nav, first, tileW = 190, tileH = 190, gap = 16, radius = 20,
                look = TileLook(CARD, CARD_HI, LINE, GOLD, INK, MUTED, Fonts.Josefin, 22, focusScale = 1.03f, dashColor = hex(0x4D5F92), labelWeight = 400),
            )
            MatchesCard(nav, Modifier.weight(1f).fillMaxHeight(), radius = 20, bg = CARD, bgFocus = CARD_HI, border = LINE, focusBorder = GOLD) {
                val list = m.homeMatches
                Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T("MY TEAMS", Fonts.Josefin, 17, MUTED, spacing = 0.26f)
                        Spacer(Modifier.weight(1f))
                        list.firstOrNull()?.let { StatusChip(it, m, Fonts.Josefin, 17, RED, hex(0xFF8A8E), Color.Transparent, MUTED, GOLD, pill = false) }
                    }
                    if (list.isEmpty()) { NoMatches(m, Fonts.Josefin, 22, MUTED); return@Column }
                    BigScoreLine(list.first(), Modifier.fillMaxWidth(), Fonts.Josefin, 30, INK, Fonts.Josefin, 46, 400, GOLD, MUTED, logo = 40)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        for (x in list.drop(1)) SmallMatchLine(x, m, Fonts.Josefin, 20, SOFT, MUTED, logo = 22, scoreColor = GOLD, liveColor = RED, vs = "v", rightSize = 18, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
