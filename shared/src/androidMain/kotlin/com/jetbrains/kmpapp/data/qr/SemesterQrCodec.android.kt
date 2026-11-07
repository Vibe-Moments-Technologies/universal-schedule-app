package com.jetbrains.kmpapp.data.qr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** JVM/Android: gzip через java.util.zip, base64url без паддинга. */
actual object SemesterQrCodec {

    actual val MAX_PAYLOAD: Int = 2900

    actual fun encode(json: String): String? {
        val compact = json.toByteArray(Charsets.UTF_8)
        val packed = gzip(compact) ?: return null
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
        val payload = PREFIX + b64
        return if (payload.length > MAX_PAYLOAD) null else payload
    }

    actual fun decode(payload: String): String? {
        val body = payload.removePrefix(PREFIX)
        if (body.length > MAX_PAYLOAD * 2) return null
        val packed = runCatching {
            Base64.getUrlDecoder().decode(body)
        }.getOrNull() ?: return null
        val raw = gunzip(packed) ?: return null
        return raw.toString(Charsets.UTF_8)
    }

    private fun gzip(data: ByteArray): ByteArray? = try {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        out.toByteArray()
    } catch (e: Exception) {
        null
    }

    private fun gunzip(data: ByteArray): ByteArray? = try {
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    } catch (e: Exception) {
        null
    }
}

private const val PREFIX = "USCH1:"
