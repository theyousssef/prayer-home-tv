package com.prayerhome.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prayerhome.tv.App
import com.prayerhome.tv.apps.AppEntry
import kotlinx.coroutines.delay

/** Page chrome shared by Apps, Matches and Settings: back pill, title, next-prayer chip and clock. */
@Composable
fun PageHeader(s: PageStyle, m: HomeModel, title: String, nav: Nav, backFocus: FocusRequester? = null, backLabel: String = "Home", extra: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Pill(s, focusRequester = backFocus, onClick = nav.home) { focused ->
            Icon(Ico.BACK, if (focused) s.accent else s.muted, 22)
            Spacer(Modifier.width(10.dp))
            T(backLabel, s.body, 22, if (focused) s.text else s.muted)
        }
        Spacer(Modifier.width(28.dp))
        T(title, s.display, 72, s.text, weight = s.displayWeight)
        Spacer(Modifier.width(28.dp))
        extra()
        Spacer(Modifier.weight(1f))
        val n = m.next
        val chipColor = if (n.urgent) s.red else s.accent
        Row(
            Modifier.box(androidx.compose.ui.graphics.Color.Transparent, chipColor, 1.dp, RoundedCornerShape(999.dp)).padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ar(n.name, s.arabic, 30, chipColor, weight = s.arabicWeight, latin = s.body, latinSize = 24, latinWeight = 600)
            Spacer(Modifier.width(12.dp))
            T("in ${n.short}", s.body, 22, chipColor)
        }
        Spacer(Modifier.width(28.dp))
        val c = m.clock
        T(
            buildAnnotatedString {
                append(c.time)
                withStyle(SpanStyle(fontSize = 30.sp, color = s.accent)) { append(" ${c.ampm}") }
            },
            s.display, 56, s.text, weight = s.displayWeight,
        )
    }
}

@Composable
fun Pill(s: PageStyle, focusRequester: FocusRequester? = null, onClick: () -> Unit, content: @Composable (Boolean) -> Unit) {
    Focusable(onClick = onClick, focusRequester = focusRequester) { focused ->
        Row(
            Modifier.box(if (focused) s.surfaceFocus else s.surface, if (focused) s.accent else s.border, if (focused) 2.dp else 1.dp, RoundedCornerShape(999.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content(focused) }
    }
}

@Composable
fun SectionLabel(s: PageStyle, text: String, modifier: Modifier = Modifier) {
    T(text, s.body, s.labelSize, s.muted, spacing = s.labelSpacing, modifier = modifier)
}

@Composable
fun AppsPage(s: PageStyle, m: HomeModel, nav: Nav) {
    val app = App.of(LocalContext.current)
    val first = remember { FocusRequester() }
    var toast by remember { mutableStateOf("") }
    val pinnedIds = m.pinnedIds
    val pinned = m.pinned
    val rest = m.allApps.filter { it.pkg !in pinnedIds }

    fun togglePin(a: AppEntry) {
        // only installed apps count, so an uninstalled pin never blocks a free slot
        val installed = m.allApps.map { it.pkg }.toSet()
        val cur = app.prefs.pinnedApps.filter { it in installed }
        toast = when {
            a.pkg in cur -> { app.prefs.pinnedApps = cur - a.pkg; "${a.label} removed from Home" }
            cur.size >= 3 -> "Home shows 3 apps — hold OK on a pinned app to unpin it first"
            else -> { app.prefs.pinnedApps = cur + a.pkg; "${a.label} pinned to Home" }
        }
    }

    LaunchedEffect(toast) { if (toast.isNotEmpty()) { delay(3500); toast = "" } }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(s.bg)) {
        s.backdrop(this)
        Column(Modifier.fillMaxSize().padding(horizontal = s.padX.dp, vertical = s.padY.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
            PageHeader(s, m, "All apps", nav) {
                Pill(s, onClick = nav.settings) { focused ->
                    Icon(Ico.GEAR, if (focused) s.accent else s.muted, 22)
                    Spacer(Modifier.width(10.dp))
                    T("Settings", s.body, 22, if (focused) s.text else s.muted)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    SectionLabel(s, "PINNED TO HOME · ${pinned.size} OF 3")
                    Spacer(Modifier.weight(1f))
                    SectionLabel(s, "HOLD OK TO UNPIN")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    pinned.forEachIndexed { i, a ->
                        Box(Modifier.weight(1f)) {
                            AppGridTile(s, a, pinnedBadge = true, focusRequester = if (i == 0) first else null, onClick = { nav.launch(a) }, onLong = { togglePin(a) })
                        }
                    }
                    repeat(6 - pinned.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionLabel(s, "EVERYTHING ELSE · A–Z")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rest, key = { it.pkg }) { a ->
                        AppGridTile(
                            s, a, pinnedBadge = false,
                            focusRequester = if (pinned.isEmpty() && a == rest.first()) first else null,
                            onClick = { nav.launch(a) }, onLong = { togglePin(a) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (toast.isNotEmpty()) {
                    T(toast, s.body, 22, s.accent)
                } else {
                    Box(Modifier.box(androidx.compose.ui.graphics.Color.Transparent, s.muted, 1.dp, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        T("Hold OK", s.body, 22, s.text, weight = 500)
                    }
                    Spacer(Modifier.width(14.dp))
                    T("on any app to pin it to Home · only 3 apps show on Home", s.body, 22, s.muted)
                }
            }
        }
    }
}

@Composable
private fun AppGridTile(s: PageStyle, a: AppEntry, pinnedBadge: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit, onLong: () -> Unit) {
    Focusable(Modifier.fillMaxWidth().aspectRatio(16f / 9f), onClick = onClick, onLongClick = onLong, focusRequester = focusRequester) { focused ->
        val shape = RoundedCornerShape(s.radius.dp)
        Box(
            Modifier.matchParentSize().scale(if (focused) 1.05f else 1f)
                .box(if (focused) s.surfaceFocus else s.surface, if (focused) s.accent else if (pinnedBadge) s.accent.copy(alpha = 0.6f) else s.border, if (focused) s.focusBorder.dp else 1.dp, shape),
        ) {
            if (a.banner != null) {
                AppImage(a, Modifier.matchParentSize().padding(if (focused) s.focusBorder.dp else 1.dp), s.radius - 2)
            } else {
                Row(Modifier.matchParentSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppImage(a, Modifier.size(64.dp), 14)
                    Spacer(Modifier.width(16.dp))
                    T(a.label, s.body, 22, s.text, maxLines = 2)
                }
            }
            if (pinnedBadge) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(10.dp).background(s.bg.copy(alpha = 0.85f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ico.TACK, s.accent, 14, stroke = 2f)
                    Spacer(Modifier.width(6.dp))
                    T("PINNED", s.body, 14, s.accent, spacing = 0.2f, weight = 600)
                }
            }
        }
    }
}
