package com.jetbrains.kmpapp.screens.other

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
import com.jetbrains.kmpapp.data.analytics.AnalyticsEvents
import com.jetbrains.kmpapp.data.analytics.AppAnalytics
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
                    AppAnalytics.logEvent(AnalyticsEvents.NAV_SOCIAL_OPEN, mapOf("network" to "github"))
                    uriHandler.openUri(config.socialLinks.github)
                },
                modifier = Modifier.weight(1f)
            )
            SocialIcon(
                icon = TelegramMark,
                tint = Color(0xFF29A9EB),
                contentDescription = "Telegram",
                onClick = {
                    AppAnalytics.logEvent(AnalyticsEvents.NAV_SOCIAL_OPEN, mapOf("network" to "telegram"))
                    uriHandler.openUri(config.socialLinks.telegram)
                },
                modifier = Modifier.weight(1f)
            )
            SocialIcon(
                icon = DiscordMark,
                tint = Color(0xFF5865F2),
                contentDescription = "Discord",
                onClick = {
                    AppAnalytics.logEvent(AnalyticsEvents.NAV_SOCIAL_OPEN, mapOf("network" to "discord"))
                    val url = config.socialLinks.discord
                    if (url.isNotBlank()) uriHandler.openUri(url)
                    else toastMessage = "Discord-сервер скоро появится"
                },
                modifier = Modifier.weight(1f)
            )
            SocialIcon(
                icon = BoostyMark,
                tint = Color(0xFFF15F2F),
                contentDescription = "Boosty",
                onClick = {
                    AppAnalytics.logEvent(AnalyticsEvents.NAV_SOCIAL_OPEN, mapOf("network" to "boosty"))
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
            Text(
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
        // Широкие лого (Discord 640×512): оба размера явно из пропорции
        // вьюпорта, иначе Icon сплющивает по одному измерению
        val isWide = icon.viewportWidth > icon.viewportHeight * 1.1f
        val h = if (isWide) 18.dp else 20.dp
        val w = if (isWide) h * icon.viewportWidth / icon.viewportHeight else h
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(width = w, height = h)
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

/** Логотип Discord (Font Awesome brands, 640×512, чистые кубические кривые). */
private val DiscordMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "DiscordMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 640f,
        viewportHeight = 512f
    ).apply {
        path(fill = SolidColor(Color(0xFF5865F2))) {
            moveTo(524.531f, 69.836f)
            curveTo(524.031f, 69.086f, 523.767f, 69.136f, 523.081f, 69.136f)
            curveTo(477.581f, 46.211f, 428.831f, 32.586f, 404.081f, 32.03f)
            curveTo(403.081f, 32.03f, 402.158f, 32.53f, 402.03f, 32.94f)
            curveTo(394.08f, 47.7f, 387.081f, 63.54f, 387.081f, 63.54f)
            curveTo(342.531f, 58.586f, 299.281f, 58.586f, 252.655f, 63.54f)
            curveTo(252.655f, 63.54f, 245.24f, 46.535f, 237.52f, 32.94f)
            curveTo(237.391f, 32.53f, 236.469f, 32.03f, 235.469f, 32.03f)
            curveTo(210.719f, 32.586f, 161.969f, 46.211f, 116.469f, 69.136f)
            curveTo(116.077f, 69.366f, 115.75f, 69.694f, 115.585f, 69.811f)
            curveTo(38.568f, 183.651f, 17.686f, 294.69f, 27.93f, 404.354f)
            curveTo(28.03f, 404.954f, 28.43f, 405.655f, 28.695f, 405.729f)
            curveTo(78.695f, 431.354f, 127.02f, 446.918f, 176.02f, 479.918f)
            curveTo(176.52f, 480.418f, 177.583f, 480.242f, 178.083f, 479.242f)
            curveTo(190.443f, 461.082f, 201.69f, 442.042f, 208.12f, 430.4f)
            curveTo(208.64f, 429.36f, 208.101f, 428.28f, 207.101f, 427.812f)
            curveTo(191.681f, 421.812f, 177.151f, 414.482f, 161.233f, 405.959f)
            curveTo(160.233f, 405.459f, 160.148f, 403.959f, 161.048f, 403.126f)
            curveTo(164.13f, 400.817f, 167.214f, 398.415f, 170.157f, 395.989f)
            curveTo(170.957f, 395.275f, 172.057f, 395.733f, 173.057f, 395.989f)
            curveTo(269.286f, 439.906f, 373.467f, 439.906f, 468.557f, 395.989f)
            curveTo(469.557f, 395.733f, 470.481f, 395.275f, 471.481f, 395.989f)
            curveTo(474.425f, 398.415f, 477.508f, 400.84f, 480.613f, 403.149f)
            curveTo(481.513f, 403.982f, 481.451f, 405.482f, 480.451f, 405.982f)
            curveTo(434.561f, 427.812f, 434.723f, 427.675f, 434.723f, 427.675f)
            curveTo(433.723f, 428.14f, 433.285f, 429.36f, 433.805f, 430.4f)
            curveTo(440.385f, 442.042f, 451.631f, 461.082f, 463.819f, 479.115f)
            curveTo(464.319f, 480.115f, 465.382f, 480.291f, 465.882f, 479.791f)
            curveTo(514.882f, 446.918f, 563.207f, 431.354f, 613.207f, 405.729f)
            curveTo(613.572f, 405.655f, 613.972f, 404.954f, 614.072f, 404.354f)
            curveTo(624.316f, 294.69f, 603.42f, 183.651f, 526.404f, 69.811f)
            close()
            moveTo(222.491f, 337.58f)
            curveTo(193.519f, 337.58f, 169.647f, 310.993f, 169.647f, 278.341f)
            curveTo(169.647f, 245.689f, 193.056f, 219.1f, 222.491f, 219.1f)
            curveTo(252.156f, 219.1f, 275.797f, 245.92f, 275.334f, 278.339f)
            curveTo(275.334f, 310.993f, 251.924f, 337.58f, 222.491f, 337.58f)
            close()
            moveTo(417.871f, 337.58f)
            curveTo(388.9f, 337.58f, 365.028f, 310.993f, 365.028f, 278.341f)
            curveTo(365.028f, 245.689f, 388.437f, 219.1f, 417.871f, 219.1f)
            curveTo(447.538f, 219.1f, 471.178f, 245.92f, 470.715f, 278.339f)
            curveTo(470.715f, 310.993f, 447.298f, 337.58f, 417.871f, 337.58f)
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
