package com.prayerhome.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * "Keep reciting on the Home screen?" over whatever page is showing, in the theme's colours.
 * Keep playing has focus first; ◀ ▶ move between the two buttons and focus can't leave the card.
 * (Back is handled by the activity and counts as Keep playing.)
 */
@Composable
fun KeepPlayingDialog(s: PageStyle, title: String, onKeep: () -> Unit, onPause: () -> Unit) {
    val keep = remember { FocusRequester() }
    val pause = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // after Home's own "focus the first app" (60 ms after the Home key), which this replaces
        delay(120)
        runCatching { keep.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(min = 760.dp, max = 1100.dp)
                .box(s.surface, s.accent, 2.dp, RoundedCornerShape((s.radius * 1.2f).toInt().dp))
                .padding(horizontal = 64.dp, vertical = 52.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { runCatching { keep.requestFocus() }; true }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { runCatching { pause.requestFocus() }; true }
                        AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Ico.MUSHAF, s.accent, 64, stroke = 1.4f)
            Spacer(Modifier.height(22.dp))
            T("Keep reciting on the Home screen?", s.display, 52, s.text, weight = s.displayWeight, align = TextAlign.Center)
            if (title.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                // surah names may come in Arabic: Ar() draws them RTL in the theme's Arabic face, or as Latin text
                Ar(title, s.arabic, 34, s.muted, weight = s.arabicWeight, latin = s.body, latinSize = 28, align = TextAlign.Center)
            }
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DialogButton(s, "Keep playing", keep, primary = true, onClick = onKeep)
                DialogButton(s, "Pause", pause, primary = false, onClick = onPause)
            }
        }
    }
}

@Composable
private fun DialogButton(s: PageStyle, label: String, fr: FocusRequester, primary: Boolean, onClick: () -> Unit) {
    Focusable(Modifier.width(300.dp).height(84.dp), onClick = onClick, focusRequester = fr) { focused ->
        Box(
            Modifier.matchParentSize().box(
                if (focused) s.accent else s.surfaceFocus,
                if (focused) s.accent else if (primary) s.accent.copy(alpha = 0.6f) else s.border,
                if (focused) s.focusBorder.dp else 1.dp,
                RoundedCornerShape(999.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            T(label, s.body, 28, if (focused) s.bg else s.text, weight = 600)
        }
    }
}

/** A short message near the bottom of the screen, e.g. "Quran Reader isn't installed". */
@Composable
fun ToastBar(s: PageStyle, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.box(s.surface, s.accent, 1.dp, RoundedCornerShape(999.dp)).padding(horizontal = 34.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ico.MUSHAF, s.accent, 30, stroke = 1.5f)
        Spacer(Modifier.width(16.dp))
        T(text, s.body, 26, s.text)
    }
}
