package com.jetbrains.kmpapp.data.qr

/**
 * QR-матрица: [width]×[height], [get] по индексу — тёмная ли клетка.
 * UI рисует её Canvas'ом — без PNG-энкодинга.
 */
class QrMatrix(val width: Int, val height: Int, private val cells: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = cells[y * width + x]
}

/**
 * Генерация QR-матрицы — чистый Kotlin (порт Nayuki), общий для платформ.
 */
object QrImageGenerator {
    /** Матрица QR или null (payload не влезает). */
    fun generate(payload: String): QrMatrix? = try {
        val qr = QrCode.encodeBinary(payload.encodeToByteArray(), Ecc.LOW)
        val size = qr.size
        val cells = BooleanArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                cells[y * size + x] = qr.getModule(x, y)
            }
        }
        QrMatrix(size, size, cells)
    } catch (e: Exception) {
        null
    }
}
