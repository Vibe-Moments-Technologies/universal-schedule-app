package com.jetbrains.kmpapp.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Разбор сохранённого варианта палитры «Киберпанка».
 *
 * Значение приходит строкой из хранилища, поэтому критично, что неизвестное или
 * отсутствующее имя не роняет приложение и не даёт «случайный» вариант.
 */
class CyberpunkVariantTest {

    @Test
    fun defaultIsNeon() {
        assertEquals(CyberpunkVariant.NEON, CyberpunkVariant.DEFAULT)
    }

    @Test
    fun knownNamesRoundTrip() {
        CyberpunkVariant.entries.forEach { variant ->
            assertEquals(variant, CyberpunkVariant.fromName(variant.name))
        }
    }

    @Test
    fun nullFallsBackToDefault() {
        // Ключа ещё нет: свежая установка или сброс настроек.
        assertEquals(CyberpunkVariant.DEFAULT, CyberpunkVariant.fromName(null))
    }

    @Test
    fun unknownValueFallsBackToDefault() {
        // Например, вариант удалили из enum в новой версии, а значение осталось
        // в хранилище от старой.
        assertEquals(CyberpunkVariant.DEFAULT, CyberpunkVariant.fromName("NO_SUCH_VARIANT"))
    }

    @Test
    fun variantsHaveDistinctLabels() {
        val labels = CyberpunkVariant.entries.map { it.displayName }
        assertEquals(labels.size, labels.distinct().size)
        assertNotEquals(
            CyberpunkVariant.NEON.displayName,
            CyberpunkVariant.CLASSIC.displayName
        )
    }
}
