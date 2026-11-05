package com.jetbrains.kmpapp.screens.other
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.AppVersion
import com.jetbrains.kmpapp.data.update.UpdateCheckResult
import com.jetbrains.kmpapp.data.update.UpdateUrgency
import com.jetbrains.kmpapp.screens.components.AppTab

/** Концентратор вкладок, спрятанных из дока (виден только если такие есть). */
@Composable
internal fun HiddenTabsCard(
    hiddenTabs: List<AppTab>,
    onNavigateToTab: (AppTab) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Горизонтальная галерея без заголовка: пилюли самоочевидны
            // (иконка + название), а заголовок съедал вертикальный бюджет
            // главной «Другого», когда блок виден.
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(hiddenTabs) { tab ->
                    Surface(
                        onClick = { onNavigateToTab(tab) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = tab.selectedIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            GlitchText(
                                text = tab.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Мини-виджет версии/обновления в шапке «Другого» (репорт владельца):
 * капсула-овал, слева иконка статуса (галочка / обновление / спиннер),
 * справа версия — текущая или новая, если вышла. Заливка — та же
 * 3-ступенчатая палитра, что была у большой карточки обновлений.
 *
 * Тап: нет обновления — ручная проверка; есть (репорт владельца:
 * «красный не кликается») — открывает предупреждение о новой версии
 * (там же чекбоксом можно снять подавление).
 */
@Composable
internal fun UpdateMiniBadge(
    updateResult: UpdateCheckResult?,
    isCheckingUpdate: Boolean,
    onCheckForUpdates: () -> Unit,
    onShowUpdateDialog: () -> Unit = {}
) {
    val urgency = updateResult?.urgency ?: UpdateUrgency.UP_TO_DATE
    val hasUpdate = updateResult?.hasUpdate == true

    val containerColor = when (urgency) {
        UpdateUrgency.CRITICAL -> Color(0xFF581C87).copy(alpha = 0.40f)
        UpdateUrgency.NEW_VERSION -> Color(0xFFDC2626).copy(alpha = 0.30f)
        UpdateUrgency.MINOR_BUILD -> Color(0xFFD97706).copy(alpha = 0.30f)
        // Заливка всегда видима (репорт владельца: «нет заливки»):
        // surfaceContainerHigh сливался с фоном шапки — капсула выглядела
        // прозрачной. Нейтральное состояние — primaryContainer с прозрачностью
        // (паттерн воскресного баннера).
        UpdateUrgency.UP_TO_DATE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    }

    val accentTint = when (urgency) {
        UpdateUrgency.CRITICAL -> Color(0xFFC084FC)
        UpdateUrgency.NEW_VERSION -> Color(0xFFEF4444)
        UpdateUrgency.MINOR_BUILD -> Color(0xFFF59E0B)
        UpdateUrgency.UP_TO_DATE -> if (isCheckingUpdate) MaterialTheme.colorScheme.primary else Color(0xFF22C55E)
    }

    // Только сам номер версии, без сборки и префиксов (репорт владельца:
    // «номер сборки занимает весь верх»). dev/beta-суффиксы отрезаем —
    // в капсуле место только для X.Y.Z.
    val versionText = when {
        isCheckingUpdate -> "…"
        hasUpdate -> shortVersion(updateResult?.latestVersion) ?: AppVersion.VERSION_NAME
        else -> AppVersion.VERSION_NAME
    }

    val statusIcon = when {
        isCheckingUpdate -> Icons.Default.Refresh
        urgency == UpdateUrgency.CRITICAL -> Icons.Default.SystemUpdate
        urgency == UpdateUrgency.NEW_VERSION -> Icons.Default.SystemUpdate
        urgency == UpdateUrgency.MINOR_BUILD -> Icons.Default.Refresh
        else -> Icons.Default.CheckCircle
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            // Есть обновление — тап открывает предупреждение (репорт
            // владельца: «красный не кликается»); нет — ручная проверка.
            .clickable(enabled = !isCheckingUpdate) {
                if (hasUpdate) onShowUpdateDialog() else onCheckForUpdates()
            }
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = accentTint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        GlitchText(
            text = versionText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hasUpdate) accentTint else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/**
 * Карточка версии/обновления — информационная. Никаких переходов и
 * установщиков: приложение обновляется маркетом, sideload — вручную.
 * Тап при актуальной версии — повторная проверка.
 *
 * СКРЫТА с редизайна «Другого» (репорт владельца): заменена мини-
 * виджетом UpdateMiniBadge в шапке. Оставлена «на всякий».
 */
@Composable
internal fun UpdateStatusCard(
    updateResult: UpdateCheckResult?,
    isCheckingUpdate: Boolean,
    onCheckForUpdates: () -> Unit
) {
    val urgency = updateResult?.urgency ?: UpdateUrgency.UP_TO_DATE
    val hasUpdate = updateResult?.hasUpdate == true

    val cardContainerColor = when (urgency) {
        UpdateUrgency.CRITICAL -> Color(0xFF581C87).copy(alpha = 0.20f)
        UpdateUrgency.NEW_VERSION -> Color(0xFFDC2626).copy(alpha = 0.16f)
        UpdateUrgency.MINOR_BUILD -> Color(0xFFD97706).copy(alpha = 0.16f)
        UpdateUrgency.UP_TO_DATE -> MaterialTheme.colorScheme.surfaceContainer
    }

    val iconBoxColor = when (urgency) {
        UpdateUrgency.CRITICAL -> Color(0xFF581C87).copy(alpha = 0.40f)
        UpdateUrgency.NEW_VERSION -> Color(0xFFDC2626).copy(alpha = 0.30f)
        UpdateUrgency.MINOR_BUILD -> Color(0xFFD97706).copy(alpha = 0.30f)
        UpdateUrgency.UP_TO_DATE -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val accentTint = when (urgency) {
        UpdateUrgency.CRITICAL -> Color(0xFFC084FC)
        UpdateUrgency.NEW_VERSION -> Color(0xFFEF4444)
        UpdateUrgency.MINOR_BUILD -> Color(0xFFF59E0B)
        UpdateUrgency.UP_TO_DATE -> if (isCheckingUpdate) MaterialTheme.colorScheme.primary else Color(0xFF22C55E)
    }

    val titleText = when {
        isCheckingUpdate -> "Проверка обновлений..."
        urgency == UpdateUrgency.CRITICAL -> "Важное обновление!"
        urgency == UpdateUrgency.NEW_VERSION -> "Вышла новая версия!"
        urgency == UpdateUrgency.MINOR_BUILD -> "Доступна новая сборка"
        else -> "У вас актуальная версия"
    }

    val subtitleText = when {
        urgency == UpdateUrgency.CRITICAL ->
            "Версия ${updateResult?.latestVersion} • Важные исправления безопасности"
        hasUpdate ->
            "Версия ${updateResult?.latestVersion} • Обновитесь в магазине приложений"
        else -> AppVersion.DISPLAY_VERSION
    }

    val statusIcon = when {
        isCheckingUpdate -> Icons.Default.Refresh
        urgency == UpdateUrgency.CRITICAL -> Icons.Default.SystemUpdate
        urgency == UpdateUrgency.NEW_VERSION -> Icons.Default.SystemUpdate
        urgency == UpdateUrgency.MINOR_BUILD -> Icons.Default.Refresh
        else -> Icons.Default.CheckCircle
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            // Информационная карточка: кликабельна только для ручной
            // повторной проверки, когда обновление не найдено.
            .clickable(enabled = !hasUpdate && !isCheckingUpdate) { onCheckForUpdates() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBoxColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = accentTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    GlitchText(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    GlitchText(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasUpdate) accentTint else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** «26.4.0-dev.105» → «26.4.0»: в мини-виджете версии место только для X.Y.Z. */
internal fun shortVersion(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val dash = raw.indexOf('-')
    return if (dash > 0) raw.substring(0, dash) else raw
}


