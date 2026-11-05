package com.jetbrains.kmpapp.data.model

import androidx.compose.ui.graphics.Color

/**
 * Палитра акцентов для типов занятий. Пользователь может назначить любой тип
 * на любой цвет (в том числе одинаковые) в «Настройки → Внешний вид → Цвета
 * занятий».
 *
 * Первые десять цветов — те, что уже используются приложением для точек
 * календаря и бейджей: они же стоят по умолчанию для соответствующих типов.
 * Последние пять — дополнительные варианты.
 */
enum class LessonColor(
    val displayName: String,
    val light: Color,
    val dark: Color
) {
    BLUE("Синий", Color(0xFF0284C7), Color(0xFF38BDF8)),
    GREEN("Зелёный", Color(0xFF16A34A), Color(0xFF4ADE80)),
    ORANGE("Оранжевый", Color(0xFFEA580C), Color(0xFFFB923C)),
    RED("Красный", Color(0xFFDC2626), Color(0xFFF87171)),
    TEAL("Бирюзовый", Color(0xFF0D9488), Color(0xFF2DD4BF)),
    YELLOW("Жёлтый", Color(0xFFCA8A04), Color(0xFFFACC15)),
    INDIGO("Индиго", Color(0xFF4F46E5), Color(0xFF818CF8)),
    SLATE("Серый", Color(0xFF64748B), Color(0xFF94A3B8)),
    PURPLE("Фиолетовый", Color(0xFF9333EA), Color(0xFFC084FC)),
    PINK("Розовый", Color(0xFFDB2777), Color(0xFFF472B6)),
    CYAN("Циан", Color(0xFF0891B2), Color(0xFF22D3EE)),
    LIME("Лайм", Color(0xFF65A30D), Color(0xFFA3E635)),
    ROSE("Алый", Color(0xFFE11D48), Color(0xFFFB7185)),
    GOLD("Золотой", Color(0xFFB8860B), Color(0xFFFFD54F)),
    BROWN("Коричневый", Color(0xFF7C4A1E), Color(0xFFC08457));

    fun color(isDark: Boolean): Color = if (isDark) dark else light
}

/** Цвет по умолчанию для каждого типа занятия (совпадает с прежней раскраской). */
val defaultLessonColor: Map<LessonType, LessonColor> = mapOf(
    LessonType.LECTURE to LessonColor.BLUE,
    LessonType.PRACTICE to LessonColor.GREEN,
    LessonType.LAB to LessonColor.ORANGE,
    LessonType.EXAM to LessonColor.RED,
    LessonType.CREDIT to LessonColor.TEAL,
    LessonType.COURSE_WORK to LessonColor.YELLOW,
    LessonType.CONSULTATION to LessonColor.INDIGO,
    LessonType.INDIVIDUAL_WORK to LessonColor.SLATE,
    LessonType.OTHER to LessonColor.PURPLE,
    LessonType.ADDITIONAL to LessonColor.PINK,
    LessonType.EVENT to LessonColor.CYAN
)

/**
 * Эффективный цвет типа: переопределённый пользователем либо стандартный.
 *
 * Фолбэк `?: LessonColor.SLATE` вместо `getValue`: если в [LessonType]
 * добавят новый кейс и забудут про [defaultLessonColor], приложение должно
 * продолжить работать с нейтральным цветом, а не падать с
 * NoSuchElementException посреди экрана расписания.
 */
fun resolvedLessonColor(
    type: LessonType,
    overrides: Map<LessonType, LessonColor>
): LessonColor = overrides[type] ?: defaultLessonColor[type] ?: LessonColor.SLATE
