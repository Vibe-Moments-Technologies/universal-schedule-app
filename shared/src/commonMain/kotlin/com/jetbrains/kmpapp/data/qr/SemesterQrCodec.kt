package com.jetbrains.kmpapp.data.qr

/**
 * Кодек переноса семестра через QR: compact JSON → gzip → base64url.
 *
 * Префикс `USCH1:` в payload distinguishes наши коды от прочих.
 * Максимальный размер QR (версия 40, L) — 2953 байта; gzip ужимает
 * типовой семестр до 1–2 КБ. Сверх лимита кодек отвечает ошибкой
 * «переносите файлом».
 */
expect object SemesterQrCodec {

    /** JSON → base64url-строку с префиксом; null, если сверх лимита QR. */
    fun encode(json: String): String?

    /** Строка из QR (с префиксом или без) → JSON; null при ошибке. */
    fun decode(payload: String): String?

    /** Лимит полезной нагрузки QR версии 40 с коррекцией L. */
    val MAX_PAYLOAD: Int
}
