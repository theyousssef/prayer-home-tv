package com.prayerhome.tv.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prayerhome.tv.App
import com.prayerhome.tv.football.Highlight
import com.prayerhome.tv.football.Highlights
import com.prayerhome.tv.football.Match
import kotlinx.coroutines.delay

/**
 * Highlights of a finished match: up to three YouTube videos (longest first) found in the
 * competitions', clubs' and broadcasters' own channels, in Arabic and English.
 * OK plays one in the YouTube app; Back returns to the match list.
 */
@Composable
fun HighlightsScreen(s: PageStyle, m: HomeModel, match: Match, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = App.of(ctx).highlights
    var results by remember(match.id) { mutableStateOf<List<Highlight>?>(null) }
    var error by remember(match.id) { mutableStateOf(false) }
    var tick by remember(match.id) { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    val retry = remember { FocusRequester() }

    LaunchedEffect(match.id, tick) {
        results = null
        error = false
        val list = runCatching { repo.find(match, force = tick > 0) }
            .getOrElse { error = true; emptyList() }
        results = list
        delay(80)
        runCatching { if (list.isNotEmpty()) first.requestFocus() else retry.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(s.bg)) {
        s.backdrop(this)
        Column(
            Modifier.fillMaxSize().padding(horizontal = s.padX.dp, vertical = s.padY.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            val nav = Nav(apps = {}, matches = {}, settings = {}, home = onBack, launch = {})
            PageHeader(s, m, "Highlights", nav, backLabel = "Matches") {
                T(scoreLine(match), s.body, 24, s.muted)
            }
            SectionLabel(s, "${match.league.uppercase()} · OFFICIAL CHANNELS · LONGEST FIRST")

            when {
                results == null -> Searching(s)
                error -> Message(s, "Couldn't reach YouTube", "Check the TV's connection, then try again.", s.red, retry) { tick++ }
                results!!.isEmpty() -> Message(s, "No highlights yet", "No official channel has posted highlights for this match — try again in a little while.", s.muted, retry) { tick++ }
                else -> {
                    val list = results!!
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp),
                    ) {
                    items(list, key = { it.id }) { h ->
                        HighlightRow(s, repo, h, if (h.id == list.first().id) first else null) {
                            if (!playInYouTube(ctx, h.id)) m.toast = "Couldn't open YouTube"
                        }
                    }
                    item(key = "again") {
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                            Pill(s, focusRequester = retry, onClick = { tick++ }) { focused ->
                                Icon(Ico.REFRESH, if (focused) s.accent else s.muted, 22)
                                Spacer(Modifier.width(10.dp))
                                T("Search again", s.body, 22, if (focused) s.text else s.muted)
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

private fun scoreLine(match: Match): String =
    if (match.hasScore) "${match.home.name} ${match.homeScore} – ${match.awayScore} ${match.away.name} · ${match.statusLabel}"
    else "${match.home.name} v ${match.away.name}"

@Composable
private fun ColumnScope.Searching(s: PageStyle) {
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(420)
            dots = dots % 3 + 1
        }
    }
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Ico.VIDEO, s.accent, 68)
        Spacer(Modifier.height(28.dp))
        T("Searching YouTube" + ".".repeat(dots), s.body, 36, s.text)
        Spacer(Modifier.height(12.dp))
        T("Official channels · Arabic and English", s.body, 24, s.muted)
    }
}

@Composable
private fun ColumnScope.Message(s: PageStyle, title: String, detail: String, color: androidx.compose.ui.graphics.Color, retry: FocusRequester, onRetry: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        T(title, s.body, 38, color)
        Spacer(Modifier.height(14.dp))
        T(detail, s.body, 24, s.muted)
        Spacer(Modifier.height(30.dp))
        Pill(s, focusRequester = retry, onClick = onRetry) { focused ->
            Icon(Ico.REFRESH, if (focused) s.accent else s.muted, 22)
            Spacer(Modifier.width(10.dp))
            T("Search again", s.body, 24, if (focused) s.text else s.muted)
        }
    }
}

@Composable
private fun HighlightRow(s: PageStyle, repo: Highlights, h: Highlight, fr: FocusRequester?, onClick: () -> Unit) {
    val img by produceState(repo.cachedThumb(h.id), h.id) { value = repo.thumb(h.id) }
    Focusable(Modifier.fillMaxWidth().height(172.dp), onClick = onClick, focusRequester = fr) { focused ->
        Row(
            Modifier.matchParentSize()
                .box(if (focused) s.surfaceFocus else s.surface, if (focused) s.accent else s.border, if (focused) 3.dp else 1.dp, RoundedCornerShape(s.radius.dp))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(width = 256.dp, height = 144.dp).clip(RoundedCornerShape(12.dp)).background(s.bg),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = img
                if (bitmap != null) Image(bitmap, h.channel, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Ico.VIDEO, s.muted, 48)
            }
            Spacer(Modifier.width(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ListText(h.title, s, 30, s.text, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ListText(h.channel, s, 22, s.muted)
                    Dot(s.border, 6)
                    T(h.duration, s.body, 22, if (focused) s.accent else s.text, weight = 600)
                }
            }
            Spacer(Modifier.width(20.dp))
            Row(
                Modifier.box(if (focused) s.accent else androidx.compose.ui.graphics.Color.Transparent, s.accent, 1.dp, RoundedCornerShape(999.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ico.PLAY, if (focused) s.bg else s.accent, 22)
                Spacer(Modifier.width(8.dp))
                T("Play", s.body, 20, if (focused) s.bg else s.accent, weight = 600)
            }
        }
    }
}

/** Arabic titles in the theme's Arabic face (right-to-left); English titles in the body face. */
@Composable
private fun ListText(text: String, s: PageStyle, size: Int, color: androidx.compose.ui.graphics.Color, maxLines: Int = 1, weight: Int = 400) {
    if (hasArabic(text)) Ar(text, s.arabic, size, color, weight = weight, maxLines = maxLines, align = TextAlign.Left, nbsp = false, latin = s.body, latinSize = size, latinWeight = weight)
    else T(text, s.body, size, color, weight = weight, maxLines = maxLines)
}

/**
 * Opens the video straight in the TV's own YouTube app (the one the user is signed in to), where
 * playback starts immediately. On Android TV the watch link is the one that really deep-links into
 * the player; the vnd.youtube scheme only brings the app to the front there. Nothing else is tried:
 * no browser, no web search page.
 */
fun playInYouTube(ctx: Context, videoId: String): Boolean {
    val tvApp = "com.google.android.youtube.tv"
    val phoneApp = "com.google.android.youtube"
    val watch = Uri.parse("https://www.youtube.com/watch?v=$videoId")
    val vnd = Uri.parse("vnd.youtube:$videoId")
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, watch).setPackage(tvApp),
        Intent(Intent.ACTION_VIEW, watch).setPackage(phoneApp),
        Intent(Intent.ACTION_VIEW, vnd).setPackage(tvApp),
        Intent(Intent.ACTION_VIEW, vnd).setPackage(phoneApp),
        // still the YouTube app, just whichever one Android resolves for YouTube links
        Intent(Intent.ACTION_VIEW, vnd),
    )
    for (i in attempts) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isSuccess) return true
    }
    return false
}
