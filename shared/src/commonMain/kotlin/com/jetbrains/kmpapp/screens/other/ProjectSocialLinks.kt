package com.jetbrains.kmpapp.screens.other
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.config.RemoteConfigLoader
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import androidx.compose.runtime.collectAsState

/**
 * Блок-ссылки на соцсети проекта: квадратные кнопки-иконки без подписей.
 * Ссылки берутся из удалённого конфига (config.json на gh-pages).
 * Пустая ссылка → тост «скоро».
 */
@Composable
internal fun ProjectSocialLinks() {
    val uriHandler = LocalUriHandler.current
    val configLoader: RemoteConfigLoader = koinInject()
    val config by configLoader.config.collectAsState()
    var toastMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SocialIcon(
                icon = GitHubMark,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = "GitHub",
                onClick = {
                    uriHandler.openUri(config.socialLinks.github)
                },
                modifier = Modifier.weight(1f)
            )
            SocialIcon(
                icon = TelegramMark,
                tint = Color(0xFF29A9EB),
                contentDescription = "Telegram",
                onClick = {
                    uriHandler.openUri(config.socialLinks.telegram)
                },
                modifier = Modifier.weight(1f)
            )
            SocialIcon(
                icon = BoostyMark,
                tint = Color(0xFFF15F2F),
                contentDescription = "Boosty",
                onClick = {
                    val url = config.socialLinks.boosty
                    if (url.isNotBlank()) uriHandler.openUri(url)
                    else toastMessage = "Поддержка разработчиков скоро появится"
                },
                modifier = Modifier.weight(1f)
            )
        }
        ComingSoonToast(
            message = toastMessage,
            onDismiss = { toastMessage = null },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Лёгкий тост в теме приложения: fade-анимация, автоскрытие ~2.5с, тап
 * закрывает. Проще и тише системного снекбара, который рвёт тему.
 */
@Composable
private fun ComingSoonToast(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(2500)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            GlitchText(
                text = message ?: "",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SocialIcon(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Логотип GitHub (Simple Icons, 24×24). */
internal val GitHubMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "GitHubMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color(0xFF181717))) {
            moveTo(12f, 0.297f)
            curveTo(5.37f, 0.297f, 0f, 5.667f, 0f, 12.297f)
            curveTo(0f, 17.6f, 3.438f, 22.097f, 8.205f, 23.682f)
            curveTo(8.805f, 23.795f, 9.025f, 23.422f, 9.025f, 23.1f)
            curveTo(9.025f, 22.812f, 9.015f, 22.05f, 9.015f, 21.15f)
            curveTo(5.67f, 21.88f, 4.965f, 19.535f, 4.965f, 19.535f)
            curveTo(4.41f, 18.13f, 3.63f, 17.76f, 3.63f, 17.76f)
            curveTo(2.55f, 17.025f, 3.705f, 17.04f, 3.705f, 17.04f)
            curveTo(4.89f, 17.115f, 5.515f, 18.27f, 5.515f, 18.27f)
            curveTo(6.585f, 20.1f, 8.295f, 19.575f, 9.05f, 19.26f)
            curveTo(9.15f, 18.48f, 9.48f, 17.955f, 9.84f, 17.655f)
            curveTo(7.185f, 17.355f, 4.395f, 16.35f, 4.395f, 11.745f)
            curveTo(4.395f, 10.425f, 4.845f, 9.345f, 5.595f, 8.505f)
            curveTo(5.475f, 8.19f, 5.085f, 6.975f, 5.685f, 5.31f)
            curveTo(5.685f, 5.31f, 6.705f, 4.98f, 8.97f, 6.525f)
            curveTo(9.945f, 6.255f, 10.95f, 6.12f, 12f, 6.12f)
            curveTo(13.05f, 6.12f, 14.1f, 6.27f, 15.03f, 6.525f)
            curveTo(17.295f, 4.98f, 18.315f, 5.31f, 18.315f, 5.31f)
            curveTo(18.915f, 6.975f, 18.51f, 8.19f, 18.39f, 8.505f)
            curveTo(19.155f, 9.345f, 19.605f, 10.425f, 19.605f, 11.745f)
            curveTo(19.605f, 16.365f, 16.8f, 17.355f, 14.13f, 17.655f)
            curveTo(14.58f, 18.015f, 14.985f, 18.735f, 14.985f, 19.83f)
            curveTo(14.985f, 21.39f, 14.97f, 22.65f, 14.97f, 23.1f)
            curveTo(14.97f, 23.43f, 15.18f, 23.79f, 15.795f, 23.685f)
            curveTo(20.565f, 22.11f, 24f, 17.61f, 24f, 12.3f)
            curveTo(24f, 5.667f, 18.63f, 0.297f, 12f, 0.297f)
            close()
        }
    }.build()
}

/** Логотип Telegram (Simple Icons, 24×24). */
internal val TelegramMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "TelegramMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color(0xFF29A9EB))) {
            moveTo(11.944f, 0f)
            curveTo(5.351f, 0f, 0f, 5.351f, 0f, 11.944f)
            curveTo(0f, 18.538f, 5.351f, 23.889f, 11.944f, 23.889f)
            curveTo(18.538f, 23.889f, 23.889f, 18.538f, 23.889f, 11.944f)
            curveTo(23.889f, 5.351f, 18.538f, 0f, 11.944f, 0f)
            close()
            moveTo(17.488f, 8.161f)
            curveTo(17.31f, 10.037f, 16.539f, 14.590f, 16.146f, 16.692f)
            curveTo(15.981f, 17.579f, 15.652f, 17.877f, 15.337f, 17.905f)
            curveTo(14.650f, 17.968f, 14.129f, 17.452f, 13.464f, 17.016f)
            curveTo(12.421f, 16.333f, 11.834f, 15.908f, 10.822f, 15.241f)
            curveTo(9.650f, 14.468f, 10.404f, 14.045f, 11.068f, 13.354f)
            curveTo(11.241f, 13.173f, 14.275f, 10.411f, 14.334f, 10.163f)
            curveTo(14.341f, 10.131f, 14.349f, 10.012f, 14.275f, 9.960f)
            curveTo(14.202f, 9.907f, 14.098f, 9.926f, 14.02f, 9.941f)
            curveTo(13.913f, 9.963f, 12.256f, 11.057f, 9.045f, 13.224f)
            curveTo(8.572f, 13.549f, 8.143f, 13.707f, 7.757f, 13.698f)
            curveTo(7.331f, 13.688f, 6.514f, 13.457f, 5.905f, 13.259f)
            curveTo(5.157f, 13.017f, 4.564f, 12.889f, 4.616f, 12.478f)
            curveTo(4.643f, 12.264f, 4.936f, 12.045f, 5.493f, 11.822f)
            curveTo(8.936f, 10.322f, 11.231f, 9.333f, 12.379f, 8.855f)
            curveTo(15.658f, 7.491f, 16.34f, 7.253f, 16.784f, 7.245f)
            curveTo(16.882f, 7.243f, 17.102f, 7.268f, 17.245f, 7.383f)
            curveTo(17.365f, 7.480f, 17.398f, 7.611f, 17.414f, 7.703f)
            curveTo(17.434f, 7.816f, 17.447f, 8.007f, 17.437f, 8.161f)
            close()
        }
    }.build()
}

/** Логотип Boosty (Simple Icons, 24×24 — точный путь). */
private val BoostyMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "BoostyMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color(0xFFF15F2F))) {
            moveTo(2.661f, 14.337f)
            lineTo(6.801f, 0f)
            lineTo(13.163f, 0f)
            lineTo(11.88f, 4.444f)
            lineTo(11.842f, 4.521f)
            lineTo(8.464f, 16.254f)
            lineTo(11.614f, 16.254f)
            curveTo(10.293f, 19.543f, 9.264f, 22.121f, 8.528f, 23.987f)
            curveTo(2.712f, 23.924f, 1.086f, 19.759f, 2.508f, 14.832f)
            close()
            moveTo(8.554f, 24f)
            lineTo(16.224f, 12.965f)
            lineTo(12.974f, 12.965f)
            lineTo(15.804f, 5.892f)
            curveTo(20.656f, 6.4f, 22.941f, 10.222f, 21.595f, 14.844f)
            curveTo(20.16f, 19.81f, 14.344f, 24f, 8.68f, 24f)
            lineTo(8.553f, 24f)
            close()
        }
    }.build()
}
