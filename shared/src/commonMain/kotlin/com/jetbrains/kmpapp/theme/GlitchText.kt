package com.jetbrains.kmpapp.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Когда [enabled] выключен — полностью прозрачная замена
 * `MaterialTheme.typography`-оформленного [Text].
 */
val LocalGlitchTextEnabled = compositionLocalOf { false }

private val GhostCyan = Color(0x6600E5FF)
private val GhostRed = Color(0x66FF3B30)

// Кровавый вариант глитча на Хэллоуин: тёмно-багровый и кроваво-красный
// «двойники», смещённые вниз — как потёки крови под надписью.
private val BloodDark = Color(0xAA7A0000)
private val BloodRed = Color(0xBBFF1744)

/**
 * Замена [Text] для темы Error: пока [LocalGlitchTextEnabled] включён и текст
 * достаточно светлый (белые надписи), по нему рисуются циановый и красный
 * «двойники» со смещением — объём, как у заголовка ERROR. Тёмные и цветные
 * подписи (розовые/красные) не трогаются и рендерятся как обычный Text.
 */
@Composable
fun GlitchText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    GlitchText(
        text = AnnotatedString(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

/**
 * Замена [Text], принимающая [AnnotatedString]. В остальном то же поведение,
 * что у [GlitchText] для строки.
 */
@Composable
fun GlitchText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val enabled = LocalGlitchTextEnabled.current
    val halloween = LocalHalloweenEnabled.current
    val resolvedColor = if (color.isSpecified) color else LocalContentColor.current
    val glitch = enabled && resolvedColor.luminance() > 0.6f
    if (!glitch) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style
        )
        return
    }

    val merged = style.merge(
        TextStyle(
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            lineHeight = lineHeight
        )
    )
    Box(modifier = modifier) {
        Text(
            text = text,
            modifier = Modifier.offset((-2).dp, if (halloween) 2.dp else 1.dp),
            color = if (halloween) BloodDark else GhostCyan,
            textAlign = textAlign,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = merged
        )
        Text(
            text = text,
            modifier = Modifier.offset(2.dp, if (halloween) 3.dp else (-1).dp),
            color = if (halloween) BloodRed else GhostRed,
            textAlign = textAlign,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = merged
        )
        Text(
            text = text,
            modifier = Modifier.offset(0.dp, 0.dp),
            color = color,
            textAlign = textAlign,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = merged
        )
    }
}