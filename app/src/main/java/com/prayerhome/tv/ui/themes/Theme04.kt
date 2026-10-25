package com.prayerhome.tv.ui.themes

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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import com.prayerhome.tv.ui.hex

private val PAPER = hex(0xEFE6D4)
private val CARD = hex(0xF7F1E4)
private val INK = hex(0x1E1A16)
private val RULE = hex(0xCDBFA6)
private val TEAL = hex(0x1F4E5F)
private val MUTED = hex(0x6B6257)
private val SOFT = hex(0x4F473E)
private val RED = hex(0xB3261E)

val Theme04 = HomeTheme(
    name = "Sand & Ink",
    style = PageStyle(
        bg = PAPER, surface = CARD, surfaceFocus = hex(0xFFFBF2), border = RULE, accent = TEAL, text = INK, muted = MUTED,
        red = RED, redBg = hex(0xF3DAD3),
        display = Fonts.DmSerif, displayWeight = 400, body = Fonts.WorkSans, arabic = Fonts.ArefRuqaa, arabicWeight = 700,
        radius = 0, labelSpacing = 0.22f, focusBorder = 3,
    ),
    home = { m, nav, first -> Home04(m, nav, first) },
)

@Composable
private fun Rule(h: Int = 1, color: Color = INK) = Box(Modifier.fillMaxWidth().height(h.dp).background(color))

@Composable
private fun Home04(m: HomeModel, nav: Nav, first: FocusRequester) {
    val n = m.next
    val cd = if (n.urgent) RED else TEAL
    Column(Modifier.fillMaxSize().background(PAPER).padding(horizontal = 88.dp, vertical = 52.dp)) {
        // masthead
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            T(m.location.replace(", ", " · ").uppercase(), Fonts.WorkSans, 20, MUTED, spacing = 0.18f, modifier = Modifier.weight(1f))
            T("The Daily Prayer", Fonts.DmSerif, 26, INK, italic = true, spacing = 0.02f)
            T(m.methodLabel.uppercase(), Fonts.WorkSans, 20, MUTED, spacing = 0.18f, modifier = Modifier.weight(1f), align = TextAlign.End)
        }
        Rule(3)
        Spacer(Modifier.height(4.dp))
        Rule(1)

        Row(Modifier.padding(top = 30.dp).fillMaxWidth().height(410.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(end = 56.dp), verticalArrangement = Arrangement.Center) {
                val c = m.clock
                Row(verticalAlignment = Alignment.Bottom) {
                    T(c.time, Fonts.DmSerif, 230, INK, lineHeight = 0.88f, spacing = -0.02f)
                    Spacer(Modifier.width(16.dp))
                    T(c.ampm.lowercase(), Fonts.DmSerif, 64, TEAL, italic = true, modifier = Modifier.padding(bottom = 18.dp))
                }
                T(c.date, Fonts.DmSerif, 44, INK, italic = true, modifier = Modifier.padding(top = 18.dp))
                Ar(c.hijri.full, Fonts.ArefRuqaa, 62, TEAL, weight = 700, lineHeight = 1.5f)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(RULE))
            Column(Modifier.width(640.dp).fillMaxHeight().padding(start = 56.dp), verticalArrangement = Arrangement.Center) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T("NEXT PRAYER · ${n.time}", Fonts.WorkSans, 20, MUTED, spacing = 0.22f)
                    Spacer(Modifier.weight(1f))
                    val chip = if (n.urgent) RED else MUTED
                    Icon(Ico.BELL, chip, 20)
                    Spacer(Modifier.width(8.dp))
                    T(if (n.urgent) "Chime played" else "Chime ${n.chimeAt}", Fonts.WorkSans, 20, chip, spacing = 0.06f)
                }
                Ar(n.name, Fonts.ArefRuqaa, 136, INK, weight = 700, lineHeight = 1.35f, align = TextAlign.Right, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.End), verticalAlignment = Alignment.Bottom) {
                    for ((v, l) in n.groups) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            T(v, Fonts.DmSerif, 120, cd, lineHeight = 1f)
                            T(if (l == "HRS") "hr" else l.lowercase(), Fonts.DmSerif, 32, cd, italic = true, modifier = Modifier.padding(bottom = 16.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        Rule(1)
        Row(Modifier.fillMaxWidth().height(150.dp)) {
            m.prayers.forEachIndexed { i, p ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(RULE))
                val bg = if (p.isNext) (if (n.urgent) hex(0xF3DAD3) else CARD) else Color.Transparent
                val c = if (p.isNext) cd else if (p.isPast) MUTED else INK
                Column(Modifier.weight(1f).fillMaxHeight().background(bg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Ar(p.name, Fonts.ArefRuqaa, 50, c, weight = 700, lineHeight = 1.35f)
                    T(p.time, Fonts.WorkSans, 28, c, weight = 500)
                }
            }
        }
        Rule(1)

        Row(Modifier.weight(1f).fillMaxWidth().padding(top = 28.dp)) {
            Column(Modifier.padding(end = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T("YOUR APPS", Fonts.WorkSans, 18, MUTED, spacing = 0.22f)
                HomeApps(
                    m, nav, first, tileW = 180, tileH = 170, gap = 18, radius = 0,
                    look = TileLook(CARD, hex(0xFFFBF2), RULE, INK, INK, SOFT, Fonts.WorkSans, 22, focusScale = 1.03f, dashColor = hex(0x8A7E6B), shape = { RectangleShape }),
                )
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(RULE))
            Column(Modifier.weight(1f).padding(start = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("FIXTURES · MY TEAMS", Fonts.WorkSans, 18, MUTED, TEAL, spacing = 0.22f)
                MatchesCard(nav, Modifier.weight(1f).fillMaxWidth(), radius = 0, bg = Color.Transparent, bgFocus = CARD, border = Color.Transparent, focusBorder = INK, shape = RectangleShape, focusWidth = 2) {
                    Matches04(m)
                }
            }
        }
    }
}

@Composable
private fun Matches04(m: HomeModel) {
    val list = m.homeMatches
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (list.isEmpty()) { NoMatches(m, Fonts.WorkSans, 24, MUTED); return@Column }
        val head = list.first()
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            HeadChip(head, m)
            Spacer(Modifier.width(20.dp))
            BigScoreLine(head, Modifier.weight(1f), Fonts.WorkSans, 28, INK, Fonts.DmSerif, 44, 400, INK, MUTED, logo = 36)
        }
        Rule(1, RULE)
        list.drop(1).forEachIndexed { i, x ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamLogoSafe(x.home, 26)
                Spacer(Modifier.width(10.dp))
                T(
                    buildAnnotatedString {
                        append(x.home.name)
                        withStyle(SpanStyle(fontFamily = Fonts.DmSerif, fontStyle = FontStyle.Italic, color = MUTED)) {
                            append(if (x.hasScore) "  ${x.homeScore}–${x.awayScore}  " else " v ")
                        }
                        append(x.away.name)
                    },
                    Fonts.WorkSans, 24, INK,
                )
                Spacer(Modifier.width(10.dp))
                TeamLogoSafe(x.away, 26)
                Spacer(Modifier.weight(1f))
                RightStatus(x, m, Fonts.WorkSans, 24, SOFT, RED, TEAL)
            }
            if (i == 0 && list.size > 2) Rule(1, RULE)
        }
    }
}

@Composable
private fun HeadChip(x: Match, m: HomeModel) {
    when (x.state) {
        MatchState.LIVE -> Row(Modifier.background(RED).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(Color.White, 8); Spacer(Modifier.width(8.dp)); T("LIVE ${x.statusLabel}", Fonts.WorkSans, 17, Color.White, weight = 600, spacing = 0.1f)
        }
        MatchState.UPCOMING -> T(m.matchTime(x), Fonts.WorkSans, 17, TEAL, weight = 600, spacing = 0.06f)
        else -> Box(Modifier.size(width = 56.dp, height = 32.dp).background(INK), contentAlignment = Alignment.Center) {
            T(x.statusLabel.take(3), Fonts.WorkSans, 17, PAPER, weight = 600, spacing = 0.1f)
        }
    }
}
