package com.prayerhome.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.prayerhome.tv.App
import com.prayerhome.tv.apps.AppEntry
import com.prayerhome.tv.football.Side

// ---------------------------------------------------------------- text

/** Latin text. Sizes are in design pixels (the UI is laid out on a 1920×1080 canvas). */
@Composable
fun T(
    text: String,
    font: FontFamily,
    size: Int,
    color: Color,
    modifier: Modifier = Modifier,
    weight: Int = 400,
    spacing: Float = 0f,
    lineHeight: Float = 0f,
    align: TextAlign = TextAlign.Unspecified,
    italic: Boolean = false,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier.lineBox(size, lineHeight),
        style = style(font, size, color, weight, spacing, 0f, align, italic),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = maxLines > 1,
    )
}

@Composable
fun T(
    text: AnnotatedString,
    font: FontFamily,
    size: Int,
    color: Color,
    modifier: Modifier = Modifier,
    weight: Int = 400,
    spacing: Float = 0f,
    align: TextAlign = TextAlign.Unspecified,
) {
    BasicText(text, modifier, style(font, size, color, weight, spacing, 0f, align, false), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
}

fun hasArabic(s: String) = s.any { it in '\u0600'..'\u06FF' }

/**
 * Arabic text: right-to-left shaping with the theme's Arabic face.
 * The same slots show English when the language setting is English: text without Arabic letters is
 * drawn as Latin text in [latin] (the theme's display or body face) at [latinSize].
 */
@Composable
fun Ar(
    text: String,
    font: FontFamily,
    size: Int,
    color: Color,
    modifier: Modifier = Modifier,
    weight: Int = 400,
    lineHeight: Float = 0f,
    align: TextAlign = TextAlign.Unspecified,
    maxLines: Int = 1,
    /** Optional face for the digits only (some display faces draw hard-to-read numerals). */
    digits: FontFamily? = null,
    latin: FontFamily? = null,
    latinSize: Int = size,
    latinWeight: Int = weight,
    latinSpacing: Float = 0f,
    latinLineHeight: Float = lineHeight,
    latinItalic: Boolean = false,
) {
    if (!hasArabic(text)) {
        T(text, latin ?: font, latinSize, color, modifier, latinWeight, latinSpacing, latinLineHeight, align, latinItalic, maxLines)
        return
    }
    if (digits != null) {
        val t = text.replace(' ', '\u00A0')
        val annotated = androidx.compose.ui.text.buildAnnotatedString {
            append(t)
            var i = 0
            while (i < t.length) {
                if (t[i] in '\u0660'..'\u0669') {
                    var j = i
                    while (j < t.length && t[j] in '\u0660'..'\u0669') j++
                    addStyle(androidx.compose.ui.text.SpanStyle(fontFamily = digits, fontSize = (size * 0.72f).sp), i, j)
                    i = j
                } else i++
            }
        }
        BasicText(annotated, modifier.lineBox(size, lineHeight), style(font, size, color, weight, 0f, 0f, align, false).copy(textDirection = TextDirection.Rtl), maxLines = maxLines, softWrap = maxLines > 1)
        return
    }
    BasicText(
        // No-break spaces keep Android from shaping each word on its own, which detaches
        // the swash tails some Arabic faces (Amiri's ى) hang into the following space.
        text = text.replace(' ', '\u00A0'),
        modifier = modifier.lineBox(size, lineHeight),
        style = style(font, size, color, weight, 0f, 0f, align, false).copy(textDirection = TextDirection.Rtl),
        maxLines = maxLines,
        softWrap = maxLines > 1,
    )
}

/**
 * CSS-style line-height for single-line text: the box is exactly size × lineHeight tall and the glyphs
 * are centred in it, overflowing when the font is taller (Compose otherwise keeps the font's own height).
 */
fun Modifier.lineBox(size: Int, lineHeight: Float): Modifier =
    if (lineHeight <= 0f) this else this.height((size * lineHeight).dp).wrapContentHeight(Alignment.CenterVertically, unbounded = true)

fun style(font: FontFamily, size: Int, color: Color, weight: Int, spacing: Float, lineHeight: Float, align: TextAlign, italic: Boolean) = TextStyle(
    fontFamily = font,
    fontSize = size.sp,
    color = color,
    fontWeight = FontWeight(weight),
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    letterSpacing = if (spacing != 0f) spacing.em else TextUnit.Unspecified,
    lineHeight = if (lineHeight > 0f) (size * lineHeight).sp else TextUnit.Unspecified,
    textAlign = align,
)

fun hex(v: Long) = Color(0xFF000000 or v)
fun hexA(v: Long) = Color(v)

// ---------------------------------------------------------------- focus

/** True inside a theme thumbnail: its tiles draw but never take focus. */
val LocalNoFocus = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * A D-pad target. OK/Enter clicks; holding OK for half a second fires [onLongClick] once.
 * [content] receives whether this item currently has focus.
 */
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    // Track real focus state (not interactions): it stays correct when focus is set before the window has it.
    var focused by remember { mutableStateOf(false) }
    val longFired = remember { booleanArrayOf(false) }
    Box(
        modifier
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onPreviewKeyEvent { e ->
                val code = e.key.nativeKeyCode
                if (code != AndroidKeyEvent.KEYCODE_DPAD_CENTER && code != AndroidKeyEvent.KEYCODE_ENTER && code != AndroidKeyEvent.KEYCODE_NUMPAD_ENTER) {
                    return@onPreviewKeyEvent false
                }
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        val repeat = e.nativeKeyEvent.repeatCount
                        if (repeat == 0) longFired[0] = false
                        if (onLongClick != null && !longFired[0] && (e.nativeKeyEvent.isLongPress || repeat >= 10)) {
                            longFired[0] = true
                            onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (!longFired[0]) onClick()
                        longFired[0] = false
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .let { if (LocalNoFocus.current) it else it.focusable() },
    ) { content(focused) }
}

/** Border + background helper mirroring the designs' CSS boxes. */
fun Modifier.box(bg: Color, border: Color, width: Dp, shape: Shape): Modifier =
    this.clip(shape).background(bg, shape).border(width, border, shape)

fun Modifier.dashed(color: Color, radius: Dp, width: Float = 1.5f): Modifier = drawBehind {
    val r = radius.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))),
        topLeft = Offset(width / 2, width / 2),
        size = Size(size.width - width, size.height - width),
    )
}

// ---------------------------------------------------------------- icons

/** Stroke icons from the designs (24×24 SVG paths). */
object Ico {
    const val PIN = "M12 21s-7-6.5-7-12a7 7 0 0 1 14 0c0 5.5-7 12-7 12z M14.5 9a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0"
    const val BELL = "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9 M10.3 21a1.94 1.94 0 0 0 3.4 0"
    const val GRID = "M4.5 3h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4a1.5 1.5 0 0 1 1.5-1.5z " +
        "M15.5 3h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4a1.5 1.5 0 0 1 1.5-1.5z " +
        "M4.5 14h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4a1.5 1.5 0 0 1 1.5-1.5z " +
        "M15.5 14h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4a1.5 1.5 0 0 1 1.5-1.5z"
    const val BACK = "M15 18l-6-6 6-6"
    const val NEXT = "M9 18l6-6-6-6"
    const val TACK = "M12 17v5 M9 3h6l-1 7 4 3H6l4-3z"
    const val GEAR = "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
    const val BALL = "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 7l4.5 3.3-1.7 5.2H9.2l-1.7-5.2z M12 7V2.5 M16.5 10.3l4.3-1.4 M14.8 15.5l2.7 3.7 M9.2 15.5l-2.7 3.7 M7.5 10.3L3.2 8.9"
}

private val pathCache = HashMap<String, androidx.compose.ui.graphics.Path>()

@Composable
fun Icon(path: String, color: Color, size: Int, modifier: Modifier = Modifier, stroke: Float = 1.8f) {
    val p = remember(path) { pathCache.getOrPut(path) { PathParser().parsePathString(path).toPath() } }
    Canvas(modifier.size(size.dp)) {
        val k = this.size.width / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun Dot(color: Color, size: Int) {
    Canvas(Modifier.size(size.dp)) { drawCircle(color) }
}

// ---------------------------------------------------------------- apps & logos

/**
 * An app tile showing the app's real TV banner (or its icon when it has none).
 * Wide tiles show just the banner edge to edge; square-ish tiles show the banner above the app name.
 */
@Composable
fun AppArt(app: AppEntry, labelFont: FontFamily, labelSize: Int, labelColor: Color, radius: Int, wide: Boolean, labelWeight: Int = 400) {
    if (wide && app.banner != null) {
        Image(app.banner, app.label, Modifier.fillMaxSize().clip(RoundedCornerShape(radius.dp)), contentScale = ContentScale.Crop)
        return
    }
    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (app.banner != null) {
            Image(app.banner, app.label, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape((radius / 2).dp)), contentScale = ContentScale.Crop)
        } else if (app.icon != null) {
            Image(app.icon, app.label, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
        }
        Spacer(Modifier.size(12.dp))
        T(app.label, labelFont, labelSize, labelColor, weight = labelWeight, align = TextAlign.Center)
    }
}

@Composable
fun AppImage(app: AppEntry, modifier: Modifier, radius: Int) {
    val img: ImageBitmap? = app.banner ?: app.icon
    if (img != null) Image(img, app.label, modifier.clip(RoundedCornerShape(radius.coerceAtLeast(0).dp)), contentScale = if (app.banner != null) ContentScale.Crop else ContentScale.Fit)
}

/** A club badge, loaded from LiveScore's image CDN and cached on disk. */
@Composable
fun TeamLogo(side: Side, size: Int, modifier: Modifier = Modifier) {
    val logos = App.of(LocalContext.current).logos
    val bmp by produceState(logos.cached(side.img), side.img) { value = logos.load(side) }
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, side.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

/** CSS `border-radius: rx rx b b / ry ry b b` — elliptical top corners, as the arched designs use. */
fun archShape(rx: Float, ry: Float, bottom: Float = 0f): Shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val x = minOf(rx, size.width / 2)
    val k = x / rx
    val y = minOf(ry * k, size.height)
    val b = minOf(bottom, size.width / 2)
    addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
            0f, 0f, size.width, size.height,
            topLeftCornerRadius = CornerRadius(x, y), topRightCornerRadius = CornerRadius(x, y),
            bottomLeftCornerRadius = CornerRadius(b, b), bottomRightCornerRadius = CornerRadius(b, b),
        ),
    )
}
