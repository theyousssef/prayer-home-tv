package com.prayerhome.tv.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.prayerhome.tv.apps.AppEntry
import com.prayerhome.tv.ui.themes.Theme01
import com.prayerhome.tv.ui.themes.Theme02
import com.prayerhome.tv.ui.themes.Theme03
import com.prayerhome.tv.ui.themes.Theme04
import com.prayerhome.tv.ui.themes.Theme05
import com.prayerhome.tv.ui.themes.Theme06
import com.prayerhome.tv.ui.themes.Theme07
import com.prayerhome.tv.ui.themes.Theme08
import com.prayerhome.tv.ui.themes.Theme09
import com.prayerhome.tv.ui.themes.Theme10

class Nav(
    val apps: () -> Unit,
    val matches: () -> Unit,
    val settings: () -> Unit,
    val home: () -> Unit,
    val launch: (AppEntry) -> Unit,
)

/**
 * Design tokens each theme hands to the shared Apps, Matches and Settings pages,
 * so those pages wear the same colours, type and ornament as the theme's home screen.
 */
class PageStyle(
    val bg: Color,
    val surface: Color,
    val surfaceFocus: Color,
    val border: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val red: Color,
    val redBg: Color,
    val display: FontFamily,
    val displayWeight: Int,
    val body: FontFamily,
    val arabic: FontFamily,
    val arabicWeight: Int,
    val radius: Int,
    /** Letter-spacing of the small all-caps section labels, in em. */
    val labelSpacing: Float = 0.3f,
    val labelSize: Int = 18,
    /** Numbers in scores and clocks. */
    val numbers: FontFamily = display,
    val numbersWeight: Int = displayWeight,
    /** Painted behind the page (pattern, skyline, border...). */
    val backdrop: @Composable BoxScope.() -> Unit = {},
    /** Horizontal page padding, so headers line up with the home screen. */
    val padX: Int = 88,
    val padY: Int = 52,
    val focusBorder: Int = 3,
)

class HomeTheme(
    val name: String,
    val style: PageStyle,
    val home: @Composable (model: HomeModel, nav: Nav, firstFocus: FocusRequester) -> Unit,
)

val THEMES: List<HomeTheme> by lazy {
    listOf(Theme01, Theme02, Theme03, Theme04, Theme05, Theme06, Theme07, Theme08, Theme09, Theme10)
}
