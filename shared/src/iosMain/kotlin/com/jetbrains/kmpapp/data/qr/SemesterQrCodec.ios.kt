package com.jetbrains.kmpapp.data.qr

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_OK
import platform.zlib.compress2
import platform.zlib.compressBound
import platform.zlib.uncompress

private const val PREFIX = "USCH1:"

/** iOS: zlib compress2/uncompress, base64url — своя таблица. */
@OptIn(ExperimentalForeignApi::class)
actual object SemesterQrCodec {

    actual val MAX_PAYLOAD: Int = 2900

    actual fun encode(json: String): String? {
        val raw = json.encodeToByteArray()
        val packed = zlibCompress(raw) ?: return null
        val b64 = base64UrlEncode(packed)
        val payload = PREFIX + b64
        return if (payload.length > MAX_PAYLOAD) null else payload
    }

    actual fun decode(payload: String): String? {
        val body = payload.removePrefix(PREFIX)
        if (body.length > MAX_PAYLOAD * 2) return null
        val packed = base64UrlDecode(body) ?: return null
        val raw = zlibDecompress(packed) ?: return null
        return raw.decodeToString()
    }

    private fun zlibCompress(data: ByteArray): ByteArray? = memScoped {
        val bound = compressBound(data.size.convert<ULong>()).toInt()
        val out = allocArray<UByteVar>(bound)
        val outLen = allocArray<ULongVar>(1)
        val rc = data.usePinned { pinned ->
            compress2(out, outLen, pinned.addressOf(0).reinterpret(), data.size.convert<ULong>(), 6)
        }
        if (rc != Z_OK) return@memScoped null
        val n = readLongAt(outLen).toInt()
        if (n <= 0 || n > bound) return@memScoped null
        out.reinterpret<kotlinx.cinterop.ByteVar>().readBytes(n)
    }

    private fun zlibDecompress(data: ByteArray): ByteArray? {
        // Размер распакованных данных неизвестен заранее: растём буфером,
        // пока uncompress не согласится.
        var capacity = (data.size * 4).coerceAtLeast(1024)
        repeat(12) {
            val out = memScoped {
                val buf = allocArray<UByteVar>(capacity)
                val outLen = allocArray<ULongVar>(1)
                val rc = data.usePinned { pinned ->
                    uncompress(buf, outLen, pinned.addressOf(0).reinterpret(), data.size.convert<ULong>())
                }
                if (rc == Z_OK) {
                    val n = readLongAt(outLen).toInt()
                    if (n in 1..capacity) {
                        buf.reinterpret<kotlinx.cinterop.ByteVar>().readBytes(n)
                    } else null
                } else null
            }
            if (out != null) return out
            capacity *= 2
        }
        return null
    }

    /** Чтение ULong из C-памяти через memcpy в байты (LE-машинный порядок). */
    private fun readLongAt(source: kotlinx.cinterop.CPointer<kotlinx.cinterop.ULongVar>): Long {
        val bytes = kotlin.ByteArray(8)
        bytes.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), source, 8.toULong())
        }
        var value = 0L
        for (i in 7 downTo 0) {
            value = (value shl 8) or (bytes[i].toLong() and 0xFF)
        }
        return value
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun base64UrlEncode(data: ByteArray): String {
        val out = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i + 2 < data.size) {
            val n = (data[i].toInt() and 0xFF shl 16) or
                (data[i + 1].toInt() and 0xFF shl 8) or
                (data[i + 2].toInt() and 0xFF)
            out.append(ALPHABET[(n shr 18) and 63])
            out.append(ALPHABET[(n shr 12) and 63])
            out.append(ALPHABET[(n shr 6) and 63])
            out.append(ALPHABET[n and 63])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val n = (data[i].toInt() and 0xFF shl 16)
                out.append(ALPHABET[(n shr 18) and 63])
                out.append(ALPHABET[(n shr 12) and 63])
            }
            2 -> {
                val n = (data[i].toInt() and 0xFF shl 16) or (data[i + 1].toInt() and 0xFF shl 8)
                out.append(ALPHABET[(n shr 18) and 63])
                out.append(ALPHABET[(n shr 12) and 63])
                out.append(ALPHABET[(n shr 6) and 63])
            }
        }
        return out.toString()
    }

    private fun base64UrlDecode(text: String): ByteArray? {
        val rev = HashMap<Char, Int>()
        ALPHABET.forEachIndexed { i, c -> rev[c] = i }
        if (text.length % 4 == 1) return null
        val out = mutableListOf<Byte>()
        var buffer = 0
        var bits = 0
        for (c in text) {
            val v = rev[c] ?: return null
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
