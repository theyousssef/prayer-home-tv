package com.prayerhome.tv.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A short message near the bottom of the screen, e.g. "Quran Reader isn't installed". */
@Composable
fun ToastBar(s: PageStyle, text: String, modifier: Modifier = Modifier, icon: String = Ico.MUSHAF) {
    Row(
        modifier.box(s.surface, s.accent, 1.dp, RoundedCornerShape(999.dp)).padding(horizontal = 34.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, s.accent, 30, stroke = 1.5f)
        Spacer(Modifier.width(16.dp))
        T(text, s.body, 26, s.text)
    }
}
