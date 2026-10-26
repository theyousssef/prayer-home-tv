package com.prayerhome.tv.ui.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayerhome.tv.football.Match
import com.prayerhome.tv.football.MatchState
import com.prayerhome.tv.ui.AppArt
import com.prayerhome.tv.ui.Dot
import com.prayerhome.tv.ui.Focusable
import com.prayerhome.tv.ui.HomeModel
import com.prayerhome.tv.ui.Ico
import com.prayerhome.tv.ui.Icon
import com.prayerhome.tv.ui.Nav
import com.prayerhome.tv.ui.T
import com.prayerhome.tv.ui.box
import com.prayerhome.tv.ui.dashed

/** Colours and type for a theme's app tiles. */
class TileLook(
    val bg: Color,
    val bgFocus: Color,
    val border: Color,
    val focusBorder: Color,
    val text: Color,
    val muted: Color,
    val font: FontFamily,
    val fontSize: Int,
    val focusScale: Float = 1.04f,
    val dashColor: Color = muted,
    val focusBorderWidth: Int = 3,
    val labelWeight: Int = 500,
    val shape: ((Int) -> Shape)? = null,
    val textFocus: Color = text,
)

/**
 * The three pinned apps plus the "All apps" tile, laid out [columns] per row.
 * The first tile takes [first] so the remote lands on it when Home is pressed.
 */
@Composable
fun HomeApps(
    m: HomeModel,
    nav: Nav,
    first: FocusRequester,
    tileW: Int,
    tileH: Int,
    gap: Int,
    radius: Int,
    look: TileLook,
    columns: Int = 4,
    allAppsLabel: String = "All apps",
    weightTiles: Boolean = false,
    labelBelow: Boolean = false,
) {
    // null stands for the "All apps" tile
    val items: List<com.prayerhome.tv.apps.AppEntry?> = m.pinned + listOf(null)
    Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        items.chunked(columns).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                row.forEachIndexed { c, app ->
                    val mod = if (weightTiles) Modifier.weight(1f) else Modifier
                    val fr = if (r == 0 && c == 0) first else null
                    if (app == null) {
                        AllAppsTile(mod, fr, tileW, tileH, radius, look, allAppsLabel, labelBelow, nav)
                    } else {
                        AppTile(mod, fr, tileW, tileH, radius, look, labelBelow, app.label, { nav.launch(app) }) { f ->
                            AppArt(app, look.font, look.fontSize, if (f) look.textFocus else look.text, radius, wide = tileW.toFloat() / tileH >= 1.5f, labelWeight = look.labelWeight)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    modifier: Modifier,
    fr: FocusRequester?,
    w: Int,
    h: Int,
    radius: Int,
    look: TileLook,
    labelBelow: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    val shape = look.shape?.invoke(radius) ?: RoundedCornerShape(radius.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(
            modifier = (if (modifier == Modifier) Modifier.width(w.dp) else Modifier.fillMaxWidth()).height(h.dp),
            onClick = onClick,
            focusRequester = fr,
        ) { focused ->
            Box(
                Modifier.matchParentSize()
                    .scale(if (focused) look.focusScale else 1f)
                    .box(if (focused) look.bgFocus else look.bg, if (focused) look.focusBorder else look.border, if (focused) look.focusBorderWidth.dp else 1.dp, shape),
                contentAlignment = Alignment.Center,
            ) { content(focused) }
        }
        if (labelBelow) {
            Spacer(Modifier.height(10.dp))
            T(label, look.font, look.fontSize, look.text, weight = look.labelWeight)
        }
    }
}

/**
 * "All apps" over two smaller buttons, "Quran" (opens the Quran Reader) and "Settings", together the
 * size of one app tile. On narrow tiles the small buttons put their label under the icon.
 */
@Composable
private fun AllAppsTile(modifier: Modifier, fr: FocusRequester?, w: Int, h: Int, radius: Int, look: TileLook, label: String, labelBelow: Boolean, nav: Nav) {
    val gap = 10
    val each = (w - gap) / 2
    val stacked = each < 120
    val smallH = if (stacked) 76 else (h * 0.3f).toInt().coerceAtLeast(48)
    val width = if (modifier == Modifier) Modifier.width(w.dp) else Modifier.fillMaxWidth()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        DashedButton(width.height((h - smallH - gap).dp), fr, radius, look, nav.apps, Ico.GRID, if (labelBelow) null else label, w, h - smallH - gap)
        Row(width.height(smallH.dp), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
            DashedButton(Modifier.weight(1f).fillMaxHeight(), null, radius, look, nav.quran, Ico.MUSHAF, "Quran", each, smallH, small = true, stacked = stacked, accent = true)
            DashedButton(Modifier.weight(1f).fillMaxHeight(), null, radius, look, nav.settings, Ico.GEAR, "Settings", each, smallH, small = true, stacked = stacked)
        }
        if (labelBelow) T(label, look.font, look.fontSize, look.muted)
    }
}

/**
 * A dashed outline button. An [accent] one (Quran) is drawn as a solid tile like the apps beside it,
 * with its icon in the theme's focus colour, so it reads as an app rather than a system button.
 */
@Composable
private fun DashedButton(
    modifier: Modifier, fr: FocusRequester?, radius: Int, look: TileLook, onClick: () -> Unit, icon: String, label: String?, w: Int, h: Int,
    small: Boolean = false, stacked: Boolean = false, accent: Boolean = false,
) {
    val r = if (small) minOf(radius, h / 2, w / 2) else radius
    val shape = look.shape?.invoke(r) ?: RoundedCornerShape(r.dp)
    Focusable(modifier = modifier, onClick = onClick, focusRequester = fr) { focused ->
        Box(
            Modifier.matchParentSize().scale(if (focused) look.focusScale else 1f)
                .let {
                    when {
                        focused -> it.box(look.bgFocus, look.focusBorder, look.focusBorderWidth.dp, shape)
                        accent -> it.box(look.bg, look.border, 1.dp, shape)
                        else -> it.dashed(look.dashColor, r.dp)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (focused || accent) look.focusBorder else look.muted
            val textColor = if (focused || accent) look.text else look.muted
            val fontSize = if (stacked) (look.fontSize - 6).coerceAtLeast(15) else if (small) (look.fontSize - 4).coerceAtLeast(16) else look.fontSize
            if (stacked) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(icon, tint, 30, stroke = 1.5f)
                    if (label != null) T(label, look.font, fontSize, textColor)
                }
            } else if (small || w.toFloat() / h >= 1.5f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, tint, if (small) 24 else 40, stroke = 1.5f)
                    if (label != null) {
                        Spacer(Modifier.width(if (small) 10.dp else 16.dp))
                        T(label, look.font, fontSize, textColor)
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, tint, 48, stroke = 1.5f)
                    if (label != null) T(label, look.font, fontSize, textColor)
                }
            }
        }
    }
}

/** Section label with an "All matches" hint on the right. */
@Composable
fun SectionHeader(label: String, font: FontFamily, size: Int, color: Color, accent: Color, spacing: Float = 0.3f, hint: String = "ALL MATCHES  ›") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(label, font, size, color, spacing = spacing)
        Spacer(Modifier.weight(1f))
        T(hint, font, size - 2, accent, spacing = spacing * 0.6f)
    }
}

/** The matches panel: focusable, OK opens the full matches page. */
@Composable
fun MatchesCard(
    nav: Nav,
    modifier: Modifier,
    radius: Int,
    bg: Color,
    bgFocus: Color,
    border: Color,
    focusBorder: Color,
    shape: Shape = RoundedCornerShape(radius.dp),
    focusWidth: Int = 3,
    content: @Composable () -> Unit,
) {
    Focusable(modifier, onClick = nav.matches) { focused ->
        Box(Modifier.matchParentSize().box(if (focused) bgFocus else bg, if (focused) focusBorder else border, if (focused) focusWidth.dp else 1.dp, shape)) {
            content()
        }
    }
}

/** "Chime at 3:41 PM · 10 min before العصر" with the prayer name in the Arabic face (or "… before Asr"). */
@Composable
fun AlertLine(m: HomeModel, font: FontFamily, size: Int, color: Color, arabic: FontFamily, spacing: Float = 0f, withName: Boolean = true, arabicScale: Float = 1.25f) {
    val text = m.alertText
    val n = m.next
    val s = buildAnnotatedString {
        append(text)
        if (withName && text.startsWith("Chime at")) {
            append(" · ${m.leadMinutes} min before ")
            if (com.prayerhome.tv.ui.hasArabic(n.name)) {
                withStyle(SpanStyle(fontFamily = arabic, fontWeight = FontWeight.Bold, fontSize = (size * arabicScale).sp, letterSpacing = 0.sp)) { append(n.name) }
            } else append(n.name)
        }
    }
    T(s, font, size, color, spacing = spacing)
}

/** The big countdown numbers ("01 : 53" with HRS / MIN under them). */
@Composable
fun CountdownGroups(
    groups: List<Pair<String, String>>,
    color: Color,
    numFont: FontFamily,
    numSize: Int,
    numWeight: Int,
    labelFont: FontFamily,
    labelSize: Int,
    labelSpacing: Float,
    sepSize: Int,
    gap: Int,
    sepFont: FontFamily = numFont,
    labelColor: Color = color,
    labelGap: Int = 6,
    sepWeight: Int = 400,
    showLabels: Boolean = true,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
        groups.forEachIndexed { i, (v, l) ->
            if (i > 0) T(":", sepFont, sepSize, color, weight = sepWeight, lineHeight = 0.95f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(v, numFont, numSize, color, weight = numWeight, lineHeight = 1f)
                if (showLabels) T(l, labelFont, labelSize, labelColor, spacing = labelSpacing, modifier = Modifier.padding(top = labelGap.dp))
            }
        }
    }
}

@Composable
fun NoMatches(m: HomeModel, font: FontFamily, size: Int, color: Color) {
    val status = m.scoresStatus
    val noTeams = com.prayerhome.tv.App.prefs(androidx.compose.ui.platform.LocalContext.current).teams.isEmpty()
    T(
        when {
            noTeams -> "No teams yet · OK here to add some"
            status.isNotEmpty() -> "$status — retrying"
            else -> "No matches for your teams this week"
        },
        font, size, color,
    )
}

/** "● LIVE 67'" pill, or "FT", or the kickoff time. */
@Composable
fun StatusChip(match: Match, m: HomeModel, font: FontFamily, size: Int, red: Color, redText: Color, redBg: Color, muted: Color, accent: Color, pill: Boolean = true) {
    when (match.state) {
        MatchState.LIVE -> Row(
            Modifier.let { if (pill) it.background(redBg, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 6.dp) else it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(red, 10)
            Spacer(Modifier.width(8.dp))
            T("LIVE ${match.statusLabel}", font, size, redText, weight = 600, spacing = 0.08f)
        }
        MatchState.FINISHED -> T(match.statusLabel, font, size, muted, weight = 600, spacing = 0.08f)
        MatchState.OFF -> T(match.statusLabel.uppercase(), font, size, muted, weight = 600, spacing = 0.08f)
        MatchState.UPCOMING -> T(m.matchTime(match), font, size, accent, weight = 500, spacing = 0.04f)
    }
}

/** "[logo] Al Ahly      2 – 1      Pyramids FC [logo]". Shows "vs" before kickoff. */
@Composable
fun BigScoreLine(
    match: Match,
    modifier: Modifier,
    nameFont: FontFamily,
    nameSize: Int,
    ink: Color,
    scoreFont: FontFamily,
    scoreSize: Int,
    scoreWeight: Int,
    scoreColor: Color,
    muted: Color,
    logo: Int,
    nameWeight: Int = 400,
    dash: String = " – ",
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            TeamLogoSafe(match.home, logo)
            Spacer(Modifier.width(12.dp))
            T(match.home.name, nameFont, nameSize, ink, weight = nameWeight)
        }
        if (match.hasScore) T("${match.homeScore}$dash${match.awayScore}", scoreFont, scoreSize, scoreColor, weight = scoreWeight, modifier = Modifier.padding(horizontal = 16.dp))
        else T("vs", nameFont, (nameSize * 0.8f).toInt(), muted, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            T(match.away.name, nameFont, nameSize, ink, weight = nameWeight, align = TextAlign.End)
            Spacer(Modifier.width(12.dp))
            TeamLogoSafe(match.away, logo)
        }
    }
}

/** "[logo] Real Madrid vs Barcelona [logo] ............ Today · 9:00 PM" (or the score / live minute). */
@Composable
fun SmallMatchLine(
    match: Match,
    m: HomeModel,
    font: FontFamily,
    size: Int,
    ink: Color,
    muted: Color,
    logo: Int,
    scoreColor: Color,
    scoreFont: FontFamily = font,
    liveColor: Color = scoreColor,
    modifier: Modifier = Modifier.fillMaxWidth(),
    weight: Int = 400,
    vs: String = "vs",
    rightSize: Int = size,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            TeamLogoSafe(match.home, logo)
            Spacer(Modifier.width(10.dp))
            val names = buildAnnotatedString {
                append(match.home.name)
                withStyle(SpanStyle(color = muted)) { append(if (match.hasScore) "  ${match.homeScore}–${match.awayScore}  " else " $vs ") }
                append(match.away.name)
            }
            T(names, font, size, ink, weight = weight, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(10.dp))
            TeamLogoSafe(match.away, logo)
        }
        if (rightSize > 0) {
            Spacer(Modifier.width(12.dp))
            RightStatus(match, m, font, rightSize, muted, liveColor, scoreColor)
        }
    }
}

@Composable
fun RightStatus(match: Match, m: HomeModel, font: FontFamily, size: Int, muted: Color, live: Color, accent: Color) {
    when (match.state) {
        MatchState.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(live, 9); Spacer(Modifier.width(8.dp)); T("LIVE ${match.statusLabel}", font, size, live, weight = 600)
        }
        MatchState.UPCOMING -> T(m.matchTime(match), font, size, muted)
        else -> T("${m.matchDay(match)} · ${match.statusLabel}", font, size, muted)
    }
}

@Composable
fun TeamLogoSafe(side: com.prayerhome.tv.football.Side, size: Int) {
    if (size > 0) com.prayerhome.tv.ui.TeamLogo(side, size)
}

/** A round placeholder for a club crest, used by themes that print the short name inside a ring. */
@Composable
fun CrestRing(side: com.prayerhome.tv.football.Side, size: Int, ring: Color, bg: Color) {
    Box(Modifier.size(size.dp).box(bg, ring, 2.dp, CircleShape), contentAlignment = Alignment.Center) {
        TeamLogoSafe(side, (size * 0.62f).toInt())
    }
}
