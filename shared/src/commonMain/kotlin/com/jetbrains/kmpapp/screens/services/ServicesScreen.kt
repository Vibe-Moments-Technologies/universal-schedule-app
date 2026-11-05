package com.jetbrains.kmpapp.screens.services
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.screens.components.AppTab
import com.jetbrains.kmpapp.screens.components.LayeredNavHost
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler
import com.jetbrains.kmpapp.screens.components.halloweenEmoji
import com.jetbrains.kmpapp.screens.components.isService
import com.jetbrains.kmpapp.screens.compare.CompareScheduleScreen
import com.jetbrains.kmpapp.screens.compare.CompareScheduleViewModel
import com.jetbrains.kmpapp.screens.map.MapScreen
import com.jetbrains.kmpapp.screens.notes.NotesScreen
import com.jetbrains.kmpapp.screens.notes.NotesViewModel
import com.jetbrains.kmpapp.screens.games.GamesScreen
import com.jetbrains.kmpapp.screens.games.GamesViewModel
import com.jetbrains.kmpapp.screens.rooms.FreeRoomsScreen
import com.jetbrains.kmpapp.screens.rooms.FreeRoomsViewModel
import com.jetbrains.kmpapp.screens.tasks.TasksScreen
import com.jetbrains.kmpapp.screens.tasks.TasksViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModel

/**
 * Раздел «Сервисы»: страница-концентратор страниц, не добавленных на панель.
 * Сервис открывается поверх списка (послойная навигация): назад — свайп,
 * системная кнопка или стрелка на иконке «Сервисы» на панели. Карта — без
 * свайпа (жестами управляет сама).
 */
class ServicesViewModel : ViewModel() {
    private val _activeService = MutableStateFlow<AppTab?>(null)
    val activeService: StateFlow<AppTab?> = _activeService.asStateFlow()

    fun openService(tab: AppTab) {
        _activeService.value = tab
    }

    fun closeService() {
        _activeService.value = null
    }
}

private val SERVICE_DESCRIPTIONS = mapOf(
    AppTab.FREE_ROOMS to "Поиск свободных аудиторий по корпусу и звонку",
    AppTab.TASKS to "Дедлайны и задания по предметам",
    AppTab.MAP to "Интерактивные схемы этажей корпусов",
    AppTab.NOTES to "Заметки с цветными полями, хранятся на устройстве",
    AppTab.COMPARE to "Сравнение расписаний нескольких групп",
    AppTab.GAMES to "Раздел игр: сейчас внутри «Сапер», новые игры в будущих обновлениях"
)

// Развёрнутые имена только на этой странице; в доке и блоке — краткие.
private val SERVICE_DISPLAY_TITLES = mapOf(
    AppTab.FREE_ROOMS to "Свободные аудитории"
)

@Composable
fun ServicesScreen(
    viewModel: ServicesViewModel,
    dockTabs: List<AppTab>,
    tasksViewModel: TasksViewModel = org.koin.compose.viewmodel.koinViewModel(),
    freeRoomsViewModel: FreeRoomsViewModel = org.koin.compose.viewmodel.koinViewModel(),
    compareViewModel: CompareScheduleViewModel = org.koin.compose.viewmodel.koinViewModel(),
    notesViewModel: NotesViewModel = org.koin.compose.viewmodel.koinViewModel(),
    gamesViewModel: GamesViewModel = org.koin.compose.viewmodel.koinViewModel(),
    modifier: Modifier = Modifier
) {
    val activeService by viewModel.activeService.collectAsState()
    // Показываем сервисы, которых нет на панели (на панели они и так под рукой).
    val services = remember(dockTabs) {
        AppTab.entries.filter { it.isService && it !in dockTabs.take(5) }
    }

    LayeredNavHost(
        screen = activeService,
        parentScreen = null,
        onBackToParent = { viewModel.closeService() },
        // Возврат из другой вкладки с открытым сервисом — показать сразу.
        initiallyRevealed = remember { activeService != null },
        // Карта управляет горизонтальными жестами сама.
        swipeGestureEnabled = { it != AppTab.MAP },
        rootContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                GlitchText(
                    text = "Сервисы",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
                if (services.isEmpty()) {
                    GlitchText(
                        text = "Все разделы уже добавлены на панель — управляйте ими в «Настройках панели страниц».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        // Свободное место под плавающую панель страниц.
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp)
                    ) {
                        items(services, key = { it.name }) { tab ->
                            ServiceCard(tab = tab, onClick = { viewModel.openService(tab) })
                        }
                    }
                }
            }
        },
        screenContent = { service, back ->
            when (service as AppTab) {
                AppTab.FREE_ROOMS -> {
                    PlatformBackHandler(onBack = back)
                    FreeRoomsScreen(viewModel = freeRoomsViewModel)
                }
                AppTab.TASKS -> {
                    PlatformBackHandler(onBack = back)
                    TasksScreen(viewModel = tasksViewModel)
                }
                AppTab.MAP -> {
                    PlatformBackHandler(onBack = back)
                    MapScreen()
                }
                AppTab.NOTES -> {
                    PlatformBackHandler(onBack = back)
                    NotesScreen(viewModel = notesViewModel)
                }
                AppTab.COMPARE -> {
                    PlatformBackHandler(onBack = back)
                    CompareScheduleScreen(viewModel = compareViewModel)
                }
                AppTab.GAMES -> {
                    PlatformBackHandler(onBack = back)
                    GamesScreen(viewModel = gamesViewModel)
                }
                else -> {}
            }
        },
        modifier = modifier
    )
}

@Composable
private fun ServiceCard(tab: AppTab, onClick: () -> Unit) {
    val halloween = com.jetbrains.kmpapp.theme.LocalHalloweenEnabled.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (halloween) {
                    // Декоративно, как и Icon ниже: название карточки читается
                    // отдельным текстом, эмодзи не должно попадать в скринридер.
                    Text(
                        text = tab.halloweenEmoji,
                        fontSize = 18.sp,
                        modifier = Modifier.clearAndSetSemantics {}
                    )
                } else {
                    Icon(
                        imageVector = tab.selectedIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                GlitchText(
                    text = SERVICE_DISPLAY_TITLES[tab] ?: tab.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                GlitchText(
                    text = SERVICE_DESCRIPTIONS[tab] ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
