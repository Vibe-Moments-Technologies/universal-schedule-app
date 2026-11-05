package com.jetbrains.kmpapp.screens.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Послойная навигация «родитель под дочерним» вместо AnimatedContent:
 *  - родитель всегда скомпонован ПОД дочерним экраном — при свайпе он
 *    виден сразу, «мёртвой зоны» и однотонной подложки нет;
 *  - анимируется ОДИН слой (graphicsLayer translationX) — на Android
 *    это радикально дешевле, чем две анимируемые композиции
 *    AnimatedContent (меньше «рваности»);
 *  - вперёд — въезд справа с плавным торможением (300 мс), родитель
 *    уезжает с параллаксом на четверть ширины (ripple нажатой карточки
 *    не «обрезается»); назад — продолжение жеста
 *    (350 мс, линейный старт → плавное торможение);
 *  - быстрый короткий флик — сразу назад; медленный малый сдвиг —
 *    отскок; дотянул четверть экрана и отпустил — назад.
 *
 * `screen == null` — корень; иначе сверху дочерний слой, под ним
 * `parentScreen` (или корень). Все кнопки «назад» зову́т один и тот же
 * `back` — выход к родителю через анимацию.
 */
@Composable
fun LayeredNavHost(
    screen: Any?,
    parentScreen: Any?,
    onBackToParent: () -> Unit,
    // Возврат из другой вкладки с уже открытой подстраницей: показать её
    // сразу, без повторной анимации въезда («настройки сами открываются»).
    initiallyRevealed: Boolean = false,
    // Свайп-назад не для всех экранов: карта управляет жестом сама.
    swipeGestureEnabled: (screen: Any?) -> Boolean = { true },
    modifier: Modifier = Modifier,
    rootContent: @Composable () -> Unit,
    screenContent: @Composable (screen: Any?, back: () -> Unit) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val widthPx = constraints.maxWidth.toFloat()
        val scope = rememberCoroutineScope()

        // Корень — без слоёв и параллакса: ранний return, как до
        // перестройки (перестройка «постоянного дерева» ломала корень —
        // параллакс-сдвиг layerX=0 уводил его влево, правая половина
        // экрана уходила в чёрный, репорт владельца).
        if (screen == null) {
            rootContent()
            return@BoxWithConstraints
        }

        var isBackTransition by remember { mutableStateOf(false) }
        // Одноразовый флаг: подстраница уже открыта в момент композиции
        // (возврат из другой вкладки) — без въезда; потребляется первым же
        // LaunchedEffect и не влияет на последующие переходы.
        var skipInitialEnter by remember { mutableStateOf(initiallyRevealed) }
        var committed by remember(screen) { mutableStateOf(false) }

        // Баг «вход в подстраницу иногда без анимации, потом сам
        // пропадает» (репорт владельца): isBackTransition оставался true
        // после выхода — СЛЕДУЮЩИЙ вход рождался «на месте» (layerX=0),
        // без въезда; ещё один вход позже — флаг уже сброшен, анимация
        // возвращалась. Отсюда плавающий характер. Фикс: флаг гасится
        // в момент, когда слой его прочитал, — следующий вход всегда
        // анимируется.
        val enterOnPlace = isBackTransition || skipInitialEnter
        if (isBackTransition) isBackTransition = false
        // Возврат: экран-родитель уже был под пальцем — стартуем сразу «на
        // месте» (0), иначе — въезд из-за правого края. Флаг читаем в момент
        // композиции: LaunchedEffect сработал бы кадром позже и новый экран
        // успевал мигнуть «за пределами» (баг на подстраницах 2-го уровня).
        val layerX = remember(screen) {
            mutableFloatStateOf(if (enterOnPlace) 0f else widthPx)
        }

        val back: (Float) -> Unit = { initialVelocity ->
            committed = true
            scope.launch {
                animate(
                    layerX.floatValue, widthPx, initialVelocity,
                    animationSpec = tween(350, easing = LinearOutSlowInEasing)
                ) { v, _ -> layerX.floatValue = v }
                isBackTransition = true
                onBackToParent()
            }
        }

        LaunchedEffect(screen) {
            if (enterOnPlace) {
                // Возврат или уже открытая подстраница: слой уже на месте,
                // только гасим одноразовый флаг пропуска въезда.
                skipInitialEnter = false
            } else {
                // Въезд — с плавным торможением (LinearOutSlowIn): без
                // «вылета» рывком с правого края.
                animate(
                    layerX.floatValue, 0f,
                    animationSpec = tween(300, easing = LinearOutSlowInEasing)
                ) { v, _ -> layerX.floatValue = v }
            }
        }

        // Слой родителя с параллаксом, как в iOS: пока дочерний закрывает
        // экран, родитель уезжает на четверть ширины; по мере раскрытия —
        // доезжает до места. Ripple нажатой карточки уезжает вместе с
        // родителем, а не «обрезается» въезжающим экраном. Одна
        // graphicsLayer-трансформация статичного слоя — дешево для Android.
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = -(1f - layerX.floatValue / widthPx) * widthPx * 0.25f
                }
        ) {
            if (parentScreen != null) {
                // back недостижим: родитель полностью закрыт дочерним экраном.
                screenContent(parentScreen) {}
            } else {
                rootContent()
            }
        }

        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = layerX.floatValue.coerceIn(0f, widthPx) }
                .swipeBackLayer(
                    layerX = layerX,
                    widthPx = widthPx,
                    // Жест обязан перезапускаться при смене экрана: узел
                    // pointerInput держит ссылки на состояния старого экрана,
                    // иначе после возврата свайп пишет в мёртвый слой
                    // (баг «после выхода жест не работает»).
                    restartKey = screen,
                    gestureEnabled = swipeGestureEnabled(screen),
                    isCommitted = { committed },
                    onCommit = { velocity -> back(velocity) }
                )
        ) {
            screenContent(screen) { back(0f) }
        }
    }
}

/**
 * Жест сдвига дочернего слоя. Пишет прямо в layerX хоста:
 * палец двигает слой 1:1, коммит — флик по скорости или четверть экрана,
 * иначе отскок на место.
 */
private fun Modifier.swipeBackLayer(
    layerX: androidx.compose.runtime.MutableFloatState,
    widthPx: Float,
    restartKey: Any?,
    gestureEnabled: Boolean,
    isCommitted: () -> Boolean,
    onCommit: (velocityPxPerSec: Float) -> Unit
): Modifier = composed {
    var startedAtEdge by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val flingVelocityPx = with(LocalDensity.current) { 800.dp.toPx() }

    // gestureEnabled в ключе: смена экрана или включенность жеста
    // перезапускают узел (карта жест отключает насовсем).
    pointerInput(restartKey, gestureEnabled) {
        if (!gestureEnabled) return@pointerInput
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                startedAtEdge = offset.x <= 200f
                velocityTracker.resetTracking()
            },
            onHorizontalDrag = { change, dragAmount ->
                if (startedAtEdge && !isCommitted()) {
                    // Экран следует за пальцем до самого отпускания — коммита
                    // «на лету» нет, решение принимается в onDragEnd.
                    layerX.floatValue = (layerX.floatValue + dragAmount).coerceIn(0f, widthPx)
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                }
            },
            onDragEnd = {
                if (startedAtEdge && !isCommitted()) {
                    val vx = velocityTracker.calculateVelocity().x
                    // Назад: быстрый флик ИЛИ дотянул четверть экрана.
                    // Скорость жеста передаётся в анимацию — страница уходит
                    // с той же скоростью, с которой её отпустили (без рывка).
                    val shouldGoBack = vx >= flingVelocityPx || layerX.floatValue >= max(120f, widthPx * 0.25f)
                    if (shouldGoBack) {
                        onCommit(vx)
                    } else {
                        scope.launch {
                            animate(
                                layerX.floatValue, 0f,
                                animationSpec = tween(200)
                            ) { v, _ -> layerX.floatValue = v }
                        }
                    }
                }
                startedAtEdge = false
            },
            onDragCancel = {
                if (startedAtEdge && !isCommitted()) {
                    scope.launch {
                        animate(
                            layerX.floatValue, 0f,
                            animationSpec = tween(200)
                        ) { v, _ -> layerX.floatValue = v }
                    }
                }
                startedAtEdge = false
            }
        )
    }
}
