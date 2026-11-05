package com.jetbrains.kmpapp.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Разрешение цвета типа занятия: пользовательское переопределение, стандартный
 * цвет и поведение на неизвестном типе.
 */
class LessonColorTest {

    @Test
    fun overrideWinsOverDefault() {
        val resolved = resolvedLessonColor(
            LessonType.LECTURE,
            mapOf(LessonType.LECTURE to LessonColor.ROSE)
        )
        assertEquals(LessonColor.ROSE, resolved)
    }

    @Test
    fun missingOverrideFallsBackToDefault() {
        // Переопределён другой тип — этот остаётся стандартным.
        val resolved = resolvedLessonColor(
            LessonType.LECTURE,
            mapOf(LessonType.LAB to LessonColor.ROSE)
        )
        assertEquals(LessonColor.BLUE, resolved)
    }

    @Test
    fun emptyOverridesAreAllDefaults() {
        LessonType.entries.forEach { type ->
            assertEquals(
                defaultLessonColor[type],
                resolvedLessonColor(type, emptyMap()),
                message = "тип $type должен оставаться стандартным без переопределений"
            )
        }
    }

    @Test
    fun everyLessonTypeHasDefaultColor() {
        // Гарантирует, что добавление нового типа занятия не оставит его без
        // цвета: resolvedLessonColor не должен доходить до фолбэка SLATE.
        LessonType.entries.forEach { type ->
            assertEquals(
                true,
                defaultLessonColor.containsKey(type),
                message = "для типа $type нет цвета по умолчанию"
            )
        }
    }

    @Test
    fun identicalColorsForDifferentTypesAreAllowed() {
        // Осознанный выбор пользователя, а не ошибка: два типа одного цвета.
        val overrides = mapOf(
            LessonType.LECTURE to LessonColor.SLATE,
            LessonType.PRACTICE to LessonColor.SLATE
        )
        assertSame(
            resolvedLessonColor(LessonType.LECTURE, overrides),
            resolvedLessonColor(LessonType.PRACTICE, overrides)
        )
    }

    @Test
    fun colorPicksSchemeVariant() {
        val color = LessonColor.BLUE
        assertEquals(color.light, color.color(isDark = false))
        assertEquals(color.dark, color.color(isDark = true))
    }

    @Test
    fun everyColorHasDistinctName() {
        // Имена идут в хранилище ("LECTURE=BLUE"), поэтому дубли сломали бы
        // восстановление настройки: два варианта схлопнулись бы в один.
        val names = LessonColor.entries.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }
}
