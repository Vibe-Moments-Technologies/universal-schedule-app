package com.jetbrains.kmpapp.screens.games
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jetbrains.kmpapp.screens.components.LayeredNavHost
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler
import com.jetbrains.kmpapp.theme.GlitchTitle
import com.jetbrains.kmpapp.theme.MonoTitle

/**
 * Раздел «Игры»: корневое меню со списком игр («Сапер», «Тетрис»).
 * Игра открывается послойно: игры → меню/партия, назад — свайп,
 * системная кнопка или стрелка.
 */
@Composable
fun GamesScreen(
    viewModel: GamesViewModel,
    modifier: Modifier = Modifier
) {
    val activeGame by viewModel.activeGame.collectAsState()

    LayeredNavHost(
        screen = activeGame,
        parentScreen = activeGame?.parent,
        onBackToParent = {
            when (activeGame) {
                GamesViewModel.Game.MINESWEEPER -> viewModel.backFromField()
                GamesViewModel.Game.SAPER_MENU -> viewModel.closeGame()
                GamesViewModel.Game.TETRIS -> viewModel.closeTetris()
                null -> {}
            }
        },
        initiallyRevealed = remember { activeGame != null },
        swipeGestureEnabled = { true },
        rootContent = {
            GamesHome(viewModel = viewModel, modifier = modifier)
        },
        screenContent = { game, back ->
            when (game) {
                GamesViewModel.Game.SAPER_MENU -> {
                    PlatformBackHandler(onBack = back)
                    SaperMenu(viewModel = viewModel, onBack = back)
                }
                GamesViewModel.Game.MINESWEEPER -> {
                    PlatformBackHandler(onBack = back)
                    MinesweeperScreen(viewModel = viewModel, onBack = back)
                }
                GamesViewModel.Game.TETRIS -> {
                    PlatformBackHandler(onBack = {
                        viewModel.closeTetris()
                        back()
                    })
                    TetrisScreen(viewModel = viewModel, onBack = {
                        viewModel.closeTetris()
                        back()
                    })
                }
                null -> {}
            }
        },
        modifier = modifier
    )
}

/** Корневое меню игр: сюда добавляются новые игры, сейчас внутри — «Сапер». */
@Composable
private fun GamesHome(
    viewModel: GamesViewModel,
    modifier: Modifier = Modifier
) {
    val errorActive by viewModel.errorActive.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            // Свободное место под плавающую панель страниц.
            .padding(bottom = 120.dp)
    ) {
        MonoTitle(
            text = "Игры",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            glitch = errorActive,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))
        GlitchText(
            text = "Раздел игр: «Сапер» и «Тетрис». Играйте и ставьте новые рекорды!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        GameCard(
            title = "Сапер",
            subtitle = "Варианты поля от детского сада до пенсионера, у каждого свой рекорд",
            recordText = null,
            recordPlaceholder = null,
            icon = Icons.Filled.Bolt,
            glitch = errorActive,
            onClick = { viewModel.openSaper() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        val tetrisRecord by viewModel.tetrisRecord.collectAsState()
        val tetrisRecordText = tetrisRecord?.let {
            "${it.score} очков · Ур. ${it.level} (${it.lines} лин.)"
        }
        GameCard(
            title = "Тетрис",
            subtitle = "Классический тетрис: 7 фигур, уровни, призрачная фигура и удержание",
            recordText = tetrisRecordText,
            recordPlaceholder = "Рекорд пока не установлен",
            icon = Icons.Filled.SportsEsports,
            glitch = errorActive,
            onClick = { viewModel.openTetris() }
        )
    }
}

/**
 * Меню вариантов «Сапера». Тема Error включена, пока существует хоть один
 * «сломанный» рекорд (0 с) в любом из вариантов. Это не состояние партии,
 * а глобальное состояние приложения: вернувшись из игры в меню выбора
 * сложности, тема не сбрасывается, а остаётся на всём экране. Снимается только
 * кнопкой «Починить ошибку» (удаляет рекорды 0 с).
 */
@Composable
private fun SaperMenu(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val records by viewModel.records.collectAsState()
    val errorActive by viewModel.errorActive.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            // Свободное место под плавающую панель страниц.
            .padding(bottom = 120.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "В меню игр"
                )
            }
            MonoTitle(
                text = "Сапер",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                glitch = errorActive
            )
        }

        if (records.values.any { it.seconds == 0 }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlitchText(
                    text = "Тема Error активна",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF5252)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlitchText(
                        text = "Починить ошибку",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = false,
                        onCheckedChange = { if (it) viewModel.fixError() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        GlitchText(
            text = "Варианты поля от детского сада до пенсионера, у каждого свой рекорд.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        MinesweeperDifficulty.entries.forEach { option ->
            val record = records[GamesViewModel.recordKey(option)]
            if (record?.seconds == 0) {
                // Вариант «решён» с первого нажатия: режим сломан, вместо
                // карточки — кнопка ERROR, включающая тему сбоя.
                ErrorGameCard(
                    subtitle = "${option.width}×${option.height} · ${option.mines} мин",
                    onClick = { viewModel.openGame(option) }
                )
            } else {
                GameCard(
                    title = option.title,
                    subtitle = "${option.width}×${option.height} · ${option.mines} мин",
                    recordText = record?.let { GamesViewModel.formatRecord(it) },
                    icon = Icons.Filled.Bolt,
                    glitch = errorActive,
                    onClick = { viewModel.openGame(option) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ErrorGameCard(
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0B0B)),
        border = BorderStroke(1.dp, Color(0xFF7A1A1A)),
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF4A1010)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Report,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                GlitchTitle(
                    text = "ERROR",
                    style = MaterialTheme.typography.titleMedium
                )
                GlitchText(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE57373)
                )
                GlitchText(
                    text = "Рекорд: 0 с — сбой. Нажми, чтобы активировать тему",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF8A80)
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    title: String,
    subtitle: String,
    recordText: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    glitch: Boolean = false,
    recordPlaceholder: String? = "Рекорд пока не установлен",
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                MonoTitle(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    glitch = glitch
                )
                GlitchText(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (recordText != null || recordPlaceholder != null) {
                    GlitchText(
                        text = recordText?.let { "Рекорд: $it" } ?: recordPlaceholder.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (recordText != null) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (recordText != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}