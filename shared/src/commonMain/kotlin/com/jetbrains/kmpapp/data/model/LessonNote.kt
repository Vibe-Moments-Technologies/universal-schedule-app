package com.jetbrains.kmpapp.data.model

import kotlinx.serialization.Serializable

/** Область заметки: к конкретной паре или к предмету в рамках расписания. */
enum class NoteScope {
    LESSON,
    SUBJECT
}

/**
 * Заметка, привязанная к расписанию.
 *
 * Ключ уникальности:
 *  - LESSON:  targetId + date + bellNumber (конкретная пара)
 *  - SUBJECT: targetId + subject (нормализованный: trim().lowercase())
 */
@Serializable
data class LessonNote(
    val id: String,
    val targetId: Int,
    val scope: NoteScope,
    /** Для LESSON: "2026-09-15_3" (дата_пара). Для SUBJECT: нормализованное имя предмета. */
    val noteKey: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long
)
