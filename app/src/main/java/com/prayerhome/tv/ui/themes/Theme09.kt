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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.App
import com.prayerhome.tv.football.Match
import com.prayerhome.tv.football.MatchState
import com.prayerhome.tv.ui.Ar
import com.prayerhome.tv.ui.Dot
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

private val BG = hex(0x0E1411)
private val CARD = hex(0x151D19)
private val CARD_HI = hex(0x1B2620)
private val PITCH = hex(0x12241A)
private val PITCH_LINE = hex(0x3FA36F)
private val LINE = hex(0x25302B)
private val CHIP = hex(0x3A4A42)
private val GOLD = hex(0xE3C27A)
private val INK = hex(0xF1F4F2)
private val MUTED = hex(0x9DAAA3)
private val SOFT = hex(0xB5C1BA)
private val RED = hex(0xFF5A5F)

val Theme09 = HomeTheme(
    name = "Match Day",
    style = PageStyle(
        bg = BG, surface = CARD, surfaceFocus = CARD_HI, border = LINE, accent = GOLD, text = INK, muted = MUTED,
        red = RED, redBg = hex(0x3A1618),
        display = Fonts.BarlowCondensed, displayWeight = 600, body = Fonts.Barlow, arabic = Fonts.Alexandria, arabicWeight = 600,
        numbersWeight = 600, radius = 20, labelSpacing = 0.18f, padX = 72,
    ),
    home = { m, nav, first -> Home09(m, nav, first) },
)

/** 0..1 through 90 minutes, from LiveScore's status text ("67'", "45+2'", "HT"...). */
private fun matchProgress(x: Match): Float = when {
    x.state == MatchState.FINISHED -> 1f
    x.status == "HT" -> 0.5f
    x.status.endsWith("'") -> (x.status.trimEnd('\'').substringBefore('+').toIntOrNull() ?: 0) / 90f
    else -> 0f
}.coerceIn(0f, 1f)

@Composable
private fun Home09(m: HomeModel, nav: Nav, first: FocusRequester) {
    val app = App.of(LocalContext.current)
    val n = m.next
    val cd = if (n.urgent) RED else GOLD
    val list = m.homeMatches
    Row(Modifier.fillMaxSize().background(BG).padding(horizontal = 72.dp, vertical = 52.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("MY TEAMS", Fonts.BarlowCondensed, 30, INK, weight = 600, spacing = 0.16f)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (t in app.prefs.teams.take(3)) {
                        Box(Modifier.box(CARD, CHIP, 1.dp, RoundedCornerShape(999.dp)).padding(horizontal = 18.dp, vertical = 8.dp)) { T(t.name, Fonts.Barlow, 20, INK) }
                    }
                    if (app.prefs.teams.size > 3) T("+${app.prefs.teams.size - 3}", Fonts.Barlow, 20, MUTED)
                    Focusable(onClick = nav.settings) { f ->
                        Box(
                            Modifier.let { if (f) it.box(CARD_HI, GOLD, 2.dp, RoundedCornerShape(999.dp)) else it.dashed(hex(0x5B6D64), 999.dp) }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        ) { T("+ Edit teams", Fonts.Barlow, 20, if (f) INK else SOFT) }
                    }
                }
            }

            MatchesCard(nav, Modifier.fillMaxWidth().height(390.dp), radius = 28, bg = PITCH, bgFocus = hex(0x163024), border = hex(0x1F7A4D), focusBorder = GOLD) {
                Featured(list.firstOrNull(), m)
            }

            Column {
                T("COMING UP", Fonts.BarlowCondensed, 22, MUTED, weight = 600, spacing = 0.18f, modifier = Modifier.padding(bottom = 8.dp))
                val rest = list.drop(1)
                if (rest.isEmpty()) T("Nothing else in the next few days", Fonts.Barlow, 24, MUTED, modifier = Modifier.height(60.dp).padding(top = 14.dp))
                rest.forEachIndexed { i, x ->
                    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(240.dp)) {
                            when (x.state) {
                                MatchState.LIVE -> T("LIVE ${x.statusLabel}", Fonts.BarlowCondensed, 28, RED, weight = 600)
                                MatchState.UPCOMING -> T(m.matchTime(x).uppercase().replace(" · ", " "), Fonts.BarlowCondensed, 28, GOLD, weight = 500)
                                else -> T("${m.matchDay(x).uppercase()} · ${x.statusLabel}", Fonts.BarlowCondensed, 28, MUTED, weight = 500)
                            }
                        }
                        TeamLogoSafe(x.home, 32)
                        Spacer(Modifier.width(12.dp))
                        T(
                            buildAnnotatedString {
                                append(x.home.name)
                                withStyle(SpanStyle(color = MUTED)) { append(if (x.hasScore) "  ${x.homeScore} – ${x.awayScore}  " else "  vs  ") }
                                append(x.away.name)
                            },
                            Fonts.Barlow, 26, INK,
                        )
                        Spacer(Modifier.width(12.dp))
                        TeamLogoSafe(x.away, 32)
                        Spacer(Modifier.weight(1f))
                        T(x.league, Fonts.Barlow, 20, MUTED, align = TextAlign.End)
                    }
                    if (i < rest.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
                }
            }

            HomeApps(
                m, nav, first, tileW = 260, tileH = 150, gap = 18, radius = 20, weightTiles = true,
                look = TileLook(CARD, CARD_HI, LINE, GOLD, INK, SOFT, Fonts.Barlow, 24, focusScale = 1.03f, dashColor = hex(0x5B6D64)),
            )
        }

        // prayer column
        Column(Modifier.width(560.dp).fillMaxHeight().box(CARD, LINE, 1.dp, RoundedCornerShape(28.dp)).padding(horizontal = 40.dp, vertical = 34.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.PIN, GOLD, 18)
                Spacer(Modifier.width(8.dp))
                T(m.location.uppercase(), Fonts.Barlow, 18, MUTED, spacing = 0.16f)
            }
            val c = m.clock
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                T(c.time, Fonts.BarlowCondensed, 130, INK, lineHeight = 1f)
                Spacer(Modifier.width(12.dp))
                T(c.ampm, Fonts.BarlowCondensed, 40, GOLD, modifier = Modifier.padding(bottom = 12.dp))
            }
            T(c.date, Fonts.Barlow, 26, INK, weight = 300)
            Ar(c.hijri.full, Fonts.Alexandria, 34, GOLD, weight = 500, lineHeight = 1.5f, modifier = Modifier.padding(top = 8.dp), latin = Fonts.BarlowCondensed, latinSize = 34, latinWeight = 500)
            Box(Modifier.padding(vertical = 20.dp).fillMaxWidth().height(1.dp).background(LINE))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("NEXT · ${n.time}", Fonts.Barlow, 18, MUTED, spacing = 0.2f)
                Spacer(Modifier.weight(1f))
                val chip = if (n.urgent) RED else MUTED
                Icon(Ico.BELL, chip, 18)
                Spacer(Modifier.width(6.dp))
                T(if (n.urgent) "Chime played" else "Chime ${n.chimeAt}", Fonts.Barlow, 17, chip)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((v, l) in n.groups) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            T(v, Fonts.BarlowCondensed, 96, cd, weight = 500, lineHeight = 1.1f)
                            T(if (l == "HRS") "HR" else l, Fonts.BarlowCondensed, 24, cd, modifier = Modifier.padding(bottom = 18.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Ar(n.name, Fonts.Alexandria, 64, INK, weight = 600, latin = Fonts.BarlowCondensed, latinSize = 76, latinWeight = 600)
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) {
                for (p in m.prayers) {
                    val bg = if (p.isNext) (if (n.urgent) hex(0x3A1618) else hex(0x1F2A24)) else Color.Transparent
                    val tc = if (p.isNext) cd else if (p.isPast) hex(0x86948C) else hex(0xD2DAD5)
                    val nc = if (p.isNext) cd else if (p.isPast) hex(0x86948C) else INK
                    Row(
                        Modifier.fillMaxWidth().height(50.dp).box(bg, if (p.isNext) cd else Color.Transparent, if (p.isNext) 2.dp else 1.dp, RoundedCornerShape(12.dp)).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(p.time, Fonts.BarlowCondensed, 28, tc)
                        Spacer(Modifier.weight(1f))
                        Ar(p.name, Fonts.Alexandria, 28, nc, weight = 500, latin = Fonts.Barlow, latinSize = 26, latinWeight = 500)
                    }
                }
            }
        }
    }
}

@Composable
private fun Featured(x: Match?, m: HomeModel) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val c = PITCH_LINE.copy(alpha = 0.22f)
            val w = size.width
            val h = size.height
            drawLine(c, Offset(w / 2, 0f), Offset(w / 2, h), 2f)
            drawCircle(c, 90f, Offset(w / 2, h / 2), style = Stroke(2f))
            drawRect(c, Offset(-2f, h / 2 - 100), Size(120f, 200f), style = Stroke(2f))
            drawRect(c, Offset(w - 118, h / 2 - 100), Size(120f, 200f), style = Stroke(2f))
        }
        if (x == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NoMatches(m, Fonts.Barlow, 30, SOFT) }
            return@Box
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 30.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                when (x.state) {
                    MatchState.LIVE -> {
                        Row(Modifier.background(RED, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(hex(0x1A0607), 10); Spacer(Modifier.width(10.dp)); T("LIVE", Fonts.BarlowCondensed, 24, hex(0x1A0607), weight = 700, spacing = 0.12f)
                        }
                        Spacer(Modifier.width(16.dp))
                        T(x.statusLabel, Fonts.BarlowCondensed, 40, GOLD, weight = 600)
                    }
                    MatchState.UPCOMING -> T(m.matchTime(x).uppercase(), Fonts.BarlowCondensed, 36, GOLD, weight = 600)
                    else -> T("${m.matchDay(x).uppercase()} · ${x.statusLabel}", Fonts.BarlowCondensed, 36, SOFT, weight = 600)
                }
                Spacer(Modifier.weight(1f))
                T(x.league, Fonts.Barlow, 22, SOFT)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                for ((i, side) in listOf(x.home, x.away).withIndex()) {
                    if (i == 1) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (x.hasScore) T(
                                buildAnnotatedString {
                                    append(x.homeScore); withStyle(SpanStyle(color = hex(0x5B6D64))) { append(" – ") }; append(x.awayScore)
                                },
                                Fonts.BarlowCondensed, 190, INK, weight = 600, spacing = 0.02f,
                            ) else T(m.matchTimeOnly(x), Fonts.BarlowCondensed, 120, INK, weight = 600)
                        }
                    }
                    Column(Modifier.width(300.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CrestRing(side, 110, CHIP, CARD_HI)
                        T(side.name, Fonts.Barlow, 34, INK, weight = 500)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val p = matchProgress(x)
                Box(Modifier.weight(1f).height(8.dp).background(hex(0x22382C), RoundedCornerShape(999.dp))) {
                    Box(Modifier.fillMaxWidth(p).height(8.dp).background(PITCH_LINE, RoundedCornerShape(999.dp)))
                }
                Spacer(Modifier.width(18.dp))
                val minute = (p * 90).toInt()
                T(if (x.state == MatchState.UPCOMING) "KICK-OFF ${m.matchTimeOnly(x)}" else "$minute / 90", Fonts.BarlowCondensed, 22, SOFT)
            }
        }
    }
}
