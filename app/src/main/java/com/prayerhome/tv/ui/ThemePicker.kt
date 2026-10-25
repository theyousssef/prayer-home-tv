package com.prayerhome.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Snapshots of each theme's home screen, kept for the life of the process so the picker opens with
 * pictures straight away. Rendering one costs the TV a few hundred ms of janky frames, so they are
 * re-taken (one at a time) only when older than [THUMB_MAX_AGE_MS].
 */
private val thumbs = arrayOfNulls<ImageBitmap>(10)
private var thumbsAt = 0L
private const val THUMB_MAX_AGE_MS = 10 * 60_000L

/** 5×2 grid of theme thumbnails. OK applies the focused theme and returns; Back cancels. */
@Composable
fun ThemePicker(s: PageStyle, m: HomeModel, current: Int, onPick: (Int) -> Unit, onCancel: () -> Unit) {
    val shots = remember { mutableStateListOf(*Array(THEMES.size) { thumbs.getOrNull(it) }) }
    // Index of the one theme rendered live right now (to be photographed); THEMES.size = done.
    var rendering by remember {
        val fresh = thumbs.all { it != null } && System.currentTimeMillis() - thumbsAt < THUMB_MAX_AGE_MS
        mutableIntStateOf(if (fresh) THEMES.size else 0)
    }
    val focus = remember { List(THEMES.size) { FocusRequester() } }
    val noNav = remember { Nav(apps = {}, matches = {}, settings = {}, home = {}, launch = {}) }

    BackHandler { onCancel() }
    LaunchedEffect(Unit) { runCatching { focus[current.coerceIn(0, THEMES.size - 1)].requestFocus() } }

    Box(Modifier.fillMaxSize().background(s.bg)) {
        s.backdrop(this)
        Column(Modifier.fillMaxSize().padding(horizontal = s.padX.dp, vertical = s.padY.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
            PageHeader(s, m, "Theme", Nav(apps = {}, matches = {}, settings = {}, home = onCancel, launch = {})) {
                T("OK to apply · Back to cancel", s.body, 22, s.muted)
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterVertically)) {
                for (row in 0 until 2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        for (col in 0 until 5) {
                            val i = row * 5 + col
                            if (i >= THEMES.size) { Spacer(Modifier.weight(1f)); continue }
                            ThemeCard(s, m, i, i == current, shots[i], live = i == rendering, noNav, focus[i], Modifier.weight(1f),
                                onShot = { bmp ->
                                    shots[i] = bmp; thumbs[i] = bmp
                                    rendering = i + 1
                                    if (rendering == THEMES.size) thumbsAt = System.currentTimeMillis()
                                },
                                onClick = { onPick(i) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    s: PageStyle,
    m: HomeModel,
    index: Int,
    isCurrent: Boolean,
    shot: ImageBitmap?,
    live: Boolean,
    noNav: Nav,
    fr: FocusRequester,
    modifier: Modifier,
    onShot: (ImageBitmap) -> Unit,
    onClick: () -> Unit,
) {
    val theme = THEMES[index]
    val r = (s.radius * 0.6f).toInt().coerceAtLeast(0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Focusable(Modifier.fillMaxWidth().aspectRatio(16f / 9f), onClick = onClick, focusRequester = fr) { focused ->
            val bw = if (focused) s.focusBorder.dp else 1.dp
            Box(
                Modifier.matchParentSize()
                    .box(theme.style.bg, if (focused) s.accent else if (isCurrent) s.accent.copy(alpha = 0.6f) else s.border, bw, RoundedCornerShape(r.dp))
                    .padding(bw),
            ) {
                Box(Modifier.matchParentSize().clip(RoundedCornerShape((r - 2).coerceAtLeast(0).dp))) {
                    if (live) LiveThumb(theme, m, noNav, onShot)
                    if (shot != null) Image(shot, theme.name, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            T("${index + 1}", s.body, 22, s.muted)
            Spacer(Modifier.width(12.dp))
            T(theme.name, s.body, 24, s.text, modifier = Modifier.weight(1f, fill = false))
            if (isCurrent) {
                Spacer(Modifier.width(12.dp))
                T("CURRENT", s.body, 16, s.accent, spacing = 0.2f,
                    modifier = Modifier.box(androidx.compose.ui.graphics.Color.Transparent, s.accent, 1.dp, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}

/**
 * The theme's real home screen laid out at 1920×1080 and scaled into the card. It is composed only
 * long enough to be recorded into a bitmap: ten live home screens (clocks, patterns, banners) are too
 * much for the TV, so the picker shows the pictures and keeps one live screen at a time.
 */
@Composable
private fun LiveThumb(theme: HomeTheme, m: HomeModel, nav: Nav, onShot: (ImageBitmap) -> Unit) {
    val layer = rememberGraphicsLayer()
    val dummy = remember { FocusRequester() }
    Box(
        Modifier.fillMaxSize()
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
            .layout { measurable, constraints ->
                val w = 1920.dp.roundToPx()
                val h = 1080.dp.roundToPx()
                val p = measurable.measure(Constraints.fixed(w, h))
                val k = constraints.maxWidth / w.toFloat()
                layout(constraints.maxWidth, constraints.maxHeight) {
                    p.placeWithLayer(0, 0) { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f) }
                }
            },
    ) {
        CompositionLocalProvider(LocalNoFocus provides true) { theme.home(m, nav, dummy) }
    }
    LaunchedEffect(Unit) {
        // Let a few frames pass so app banners and cached club badges are in, then photograph it.
        repeat(3) { withFrameNanos { } }
        delay(250)
        onShot(layer.toImageBitmap())
    }
}
