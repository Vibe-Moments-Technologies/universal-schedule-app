package com.jetbrains.kmpapp.data.qr

/**
 * Чистый Kotlin-порт QR-генератора (Nayuki QR-Code-generator, MIT License,
 * https://www.nayuki.io/page/qr-code-generator-library). Версии 1–40,
 * все уровни коррекции, режим byte/alphanumeric/numeric.
 *
 * Общий для Android/iOS — без платформенных зависимостей.
 */

/** QR-символ: квадрат boolean-модулей. */
internal class QrCode private constructor(
    val version: Int,
    val errorCorrectionLevel: Ecc,
    /** Финальный номер маски (0–7). */
    var mask: Int,
    /** Размер сетки (модулей): version*4+17. */
    val size: Int
) {
    private lateinit var modules: Array<BooleanArray>
    private lateinit var isFunction: Array<BooleanArray>

    companion object {
        const val MIN_VERSION = 1
        const val MAX_VERSION = 40

        private const val PENALTY_N1 = 3
        private const val PENALTY_N2 = 3
        private const val PENALTY_N3 = 40
        private const val PENALTY_N4 = 10

        fun encodeText(text: String, ecl: Ecc): QrCode {
            val segs = QrSegment.makeSegments(text)
            return encodeSegments(segs, ecl)
        }

        fun encodeBinary(data: ByteArray, ecl: Ecc): QrCode {
            val seg = QrSegment.makeBytes(data)
            return encodeSegments(listOf(seg), ecl)
        }

        internal fun encodeSegments(
            segs: List<QrSegment>,
            ecl: Ecc,
            minVersion: Int = MIN_VERSION,
            maxVersion: Int = MAX_VERSION,
            mask: Int = -1,
            boostEcl: Boolean = true
        ): QrCode {
            var level = ecl
            require(minVersion in MIN_VERSION..maxVersion && maxVersion <= MAX_VERSION && mask in -1..7)

            var version = minVersion
            var dataUsedBits: Int
            while (true) {
                val dataCapacityBits = getNumDataCodewords(version, level) * 8
                dataUsedBits = QrSegment.getTotalBits(segs, version)
                if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits) break
                if (version >= maxVersion) {
                    throw IllegalArgumentException("Data too long")
                }
                version++
            }

            if (boostEcl) {
                for (newEcl in Ecc.entries) {
                    if (dataUsedBits <= getNumDataCodewords(version, newEcl) * 8) {
                        level = newEcl
                    }
                }
            }

            val bb = BitBuffer()
            for (seg in segs) {
                bb.appendBits(seg.mode.modeBits, 4)
                bb.appendBits(seg.numChars, seg.mode.numCharCountBits(version))
                bb.appendData(seg.data)
            }

            val dataCapacityBits = getNumDataCodewords(version, level) * 8
            bb.appendBits(0, minOf(4, dataCapacityBits - bb.bitLength))
            bb.appendBits(0, (8 - bb.bitLength % 8) % 8)
            var padByte = 0xEC
            while (bb.bitLength < dataCapacityBits) {
                bb.appendBits(padByte, 8)
                padByte = padByte xor (0xEC xor 0x11)
            }

            val dataCodewords = ByteArray(bb.bitLength / 8)
            for (i in 0 until bb.bitLength) {
                dataCodewords[i ushr 3] =
                    (dataCodewords[i ushr 3].toInt() or (if (bb.getBit(i)) 1 else 0) shl (7 - (i and 7))).toByte()
            }

            return QrCode(version, level, dataCodewords, mask, size = version * 4 + 17)
        }

        /** Число 8-битных data-кодовых слов для версии и уровня коррекции. */
        internal fun getNumDataCodewords(ver: Int, ecl: Ecc): Int =
            getNumRawDataModules(ver) / 8 -
                ECC_CODEWORDS_PER_BLOCK[ecl.ordinal][ver] *
                NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal][ver]

        private fun getNumRawDataModules(ver: Int): Int {
            if (ver < MIN_VERSION || ver > MAX_VERSION) throw IllegalArgumentException()
            val size = ver * 4 + 17
            var result = size * size
            result -= 8 * 8 * 3
            result -= 15 * 2 + 1
            result -= (size - 16) * 2
            if (ver >= 2) {
                val numAlign = ver / 7 + 2
                result -= (numAlign - 1) * (numAlign - 1) * 25
                result -= (numAlign - 2) * 2 * 20
                if (ver >= 7) result -= 6 * 3 * 2
            }
            return result
        }

        private fun reedSolomonComputeDivisor(degree: Int): ByteArray {
            require(degree in 1..255)
            val result = ByteArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in result.indices) {
                    result[j] = reedSolomonMultiply(result[j].toInt() and 0xFF, root).toByte()
                    if (j + 1 < result.size) {
                        result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
                    }
                }
                root = reedSolomonMultiply(root, 0x02)
            }
            return result
        }

        private fun reedSolomonComputeRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
            val result = ByteArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() xor result[0].toInt()) and 0xFF
                for (i in 0 until result.size - 1) {
                    result[i] = result[i + 1]
                }
                result[result.size - 1] = 0
                for (i in result.indices) {
                    result[i] =
                        (result[i].toInt() xor reedSolomonMultiply(divisor[i].toInt() and 0xFF, factor)).toByte()
                }
            }
            return result
        }

        private fun reedSolomonMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = ((z shl 1) xor ((z ushr 7) * 0x11D))
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        internal fun getBit(x: Int, i: Int): Boolean = ((x ushr i) and 1) != 0

        private val ECC_CODEWORDS_PER_BLOCK = arrayOf(
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
            intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30)
        )

        private val NUM_ERROR_CORRECTION_BLOCKS = arrayOf(
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
            intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
            intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81)
        )
    }

    private constructor(ver: Int, ecl: Ecc, dataCodewords: ByteArray, msk: Int, size: Int) : this(
        ver, ecl, msk, size
    ) {
        modules = Array(size) { BooleanArray(size) }
        isFunction = Array(size) { BooleanArray(size) }
        var mask = msk

        drawFunctionPatterns()
        val allCodewords = addEccAndInterleave(dataCodewords)
        drawCodewords(allCodewords)

        if (mask == -1) {
            var minPenalty = Int.MAX_VALUE
            for (i in 0 until 8) {
                applyMask(i)
                drawFormatBits(i)
                val penalty = getPenaltyScore()
                if (penalty < minPenalty) {
                    mask = i
                    minPenalty = penalty
                }
                applyMask(i)
            }
        }
        this.mask = mask
        applyMask(mask)
        drawFormatBits(mask)
    }

    fun getModule(x: Int, y: Int): Boolean =
        x in 0 until size && y in 0 until size && modules[y][x]

    private fun drawFunctionPatterns() {
        for (i in 0 until size) {
            setFunctionModule(6, i, i % 2 == 0)
            setFunctionModule(i, 6, i % 2 == 0)
        }
        drawFinderPattern(3, 3)
        drawFinderPattern(size - 4, 3)
        drawFinderPattern(3, size - 4)

        val alignPatPos = getAlignmentPatternPositions()
        val numAlign = alignPatPos.size
        for (i in 0 until numAlign) {
            for (j in 0 until numAlign) {
                if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0)) {
                    drawAlignmentPattern(alignPatPos[i], alignPatPos[j])
                }
            }
        }
        drawFormatBits(0)
        drawVersion()
    }

    private fun drawFormatBits(msk: Int) {
        val data = errorCorrectionLevel.formatBits shl 3 or msk
        var rem = data
        for (i in 0 until 10) {
            rem = ((rem shl 1) xor ((rem ushr 9) * 0x537))
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) setFunctionModule(8, i, getBit(bits, i))
        setFunctionModule(8, 7, getBit(bits, 6))
        setFunctionModule(8, 8, getBit(bits, 7))
        setFunctionModule(7, 8, getBit(bits, 8))
        for (i in 9 until 15) setFunctionModule(14 - i, 8, getBit(bits, i))

        for (i in 0 until 8) setFunctionModule(size - 1 - i, 8, getBit(bits, i))
        for (i in 8 until 15) setFunctionModule(8, size - 15 + i, getBit(bits, i))
        setFunctionModule(8, size - 8, true)
    }

    private fun drawVersion() {
        if (version < 7) return
        var rem = version
        for (i in 0 until 12) {
            rem = ((rem shl 1) xor ((rem ushr 11) * 0x1F25))
        }
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val bit = getBit(bits, i)
            val a = size - 11 + i % 3
            val b = i / 3
            setFunctionModule(a, b, bit)
            setFunctionModule(b, a, bit)
        }
    }

    private fun drawFinderPattern(x: Int, y: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until size && yy in 0 until size) {
                    setFunctionModule(xx, yy, dist != 2 && dist != 4)
                }
            }
        }
    }

    private fun drawAlignmentPattern(x: Int, y: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                setFunctionModule(x + dx, y + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
        }
    }

    private fun setFunctionModule(x: Int, y: Int, isDark: Boolean) {
        modules[y][x] = isDark
        isFunction[y][x] = true
    }

    private fun addEccAndInterleave(data: ByteArray): ByteArray {
        val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal][version]
        val blockEccLen = ECC_CODEWORDS_PER_BLOCK[errorCorrectionLevel.ordinal][version]
        val rawCodewords = getNumRawDataModules(version) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks

        val blocks = arrayOfNulls<ByteArray>(numBlocks)
        val rsDiv = reedSolomonComputeDivisor(blockEccLen)
        var k = 0
        for (i in 0 until numBlocks) {
            val datLen = shortBlockLen - blockEccLen + (if (i < numShortBlocks) 0 else 1)
            val dat = data.copyOfRange(k, k + datLen)
            k += dat.size
            val block = dat.copyOf(shortBlockLen + 1)
            val ecc = reedSolomonComputeRemainder(dat, rsDiv)
            ecc.copyInto(block, block.size - blockEccLen)
            blocks[i] = block
        }

        val result = ByteArray(rawCodewords)
        var idx = 0
        for (i in 0 until blocks[0]!!.size) {
            for (j in blocks.indices) {
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[idx] = blocks[j]!![i]
                    idx++
                }
            }
        }
        return result
    }

    private fun drawCodewords(data: ByteArray) {
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!isFunction[y][x] && i < data.size * 8) {
                        modules[y][x] = getBit(data[i ushr 3].toInt(), 7 - (i and 7))
                        i++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(msk: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val invert = when (msk) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    7 -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    else -> false
                }
                modules[y][x] = modules[y][x] xor (invert && !isFunction[y][x])
            }
        }
    }

    private fun getPenaltyScore(): Int {
        var result = 0

        for (y in 0 until size) {
            var runColor = false
            var runX = 0
            val runHistory = IntArray(7)
            for (x in 0 until size) {
                if (modules[y][x] == runColor) {
                    runX++
                    if (runX == 5) result += PENALTY_N1
                    else if (runX > 5) result++
                } else {
                    finderPenaltyAddHistory(runX, runHistory)
                    if (!runColor) result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                    runColor = modules[y][x]
                    runX = 1
                }
            }
            result += finderPenaltyTerminateAndCount(runColor, runX, runHistory) * PENALTY_N3
        }

        for (x in 0 until size) {
            var runColor = false
            var runY = 0
            val runHistory = IntArray(7)
            for (y in 0 until size) {
                if (modules[y][x] == runColor) {
                    runY++
                    if (runY == 5) result += PENALTY_N1
                    else if (runY > 5) result++
                } else {
                    finderPenaltyAddHistory(runY, runHistory)
                    if (!runColor) result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                    runColor = modules[y][x]
                    runY = 1
                }
            }
            result += finderPenaltyTerminateAndCount(runColor, runY, runHistory) * PENALTY_N3
        }

        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val color = modules[y][x]
                if (color == modules[y][x + 1] && color == modules[y + 1][x] && color == modules[y + 1][x + 1]) {
                    result += PENALTY_N2
                }
            }
        }

        var dark = 0
        for (row in modules) for (color in row) if (color) dark++
        val total = size * size
        val k = (kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1
        result += k * PENALTY_N4
        return result
    }

    private fun getAlignmentPatternPositions(): IntArray {
        if (version == 1) return IntArray(0)
        val numAlign = version / 7 + 2
        val step = (version * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4) * 2
        val result = IntArray(numAlign)
        result[0] = 6
        var pos = size - 7
        for (i in result.size - 1 downTo 1) {
            result[i] = pos
            pos -= step
        }
        return result
    }

    private fun finderPenaltyCountPatterns(runHistory: IntArray): Int {
        val n = runHistory[1]
        val core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n
        return (if (core && runHistory[0] >= n * 4 && runHistory[6] >= n) 1 else 0) +
            (if (core && runHistory[6] >= n * 4 && runHistory[0] >= n) 1 else 0)
    }

    private fun finderPenaltyTerminateAndCount(
        currentRunColor: Boolean,
        currentRunLength: Int,
        runHistory: IntArray
    ): Int {
        var runLength = currentRunLength
        if (currentRunColor) {
            finderPenaltyAddHistory(runLength, runHistory)
            runLength = 0
        }
        runLength += size
        finderPenaltyAddHistory(runLength, runHistory)
        return finderPenaltyCountPatterns(runHistory)
    }

    private fun finderPenaltyAddHistory(currentRunLength: Int, runHistory: IntArray) {
        var len = currentRunLength
        if (runHistory[0] == 0) len += size
        for (i in runHistory.size - 1 downTo 1) {
            runHistory[i] = runHistory[i - 1]
        }
        runHistory[0] = len
    }
}

/** Уровень коррекции ошибок QR. */
internal enum class Ecc(val formatBits: Int) {
    LOW(1),
    MEDIUM(0),
    QUARTILE(3),
    HIGH(2)
}

/** Сегмент данных QR. */
internal class QrSegment(val mode: Mode, val numChars: Int, val data: ByteArray) {

    enum class Mode(val modeBits: Int) {
        NUMERIC(1),
        ALPHANUMERIC(2),
        BYTE(4),
        KANJI(8);

        fun numCharCountBits(ver: Int): Int = when (this) {
            NUMERIC -> if (ver <= 9) 10 else if (ver <= 26) 12 else 14
            ALPHANUMERIC -> if (ver <= 9) 9 else if (ver <= 26) 11 else 13
            BYTE -> if (ver <= 9) 8 else 16
            KANJI -> if (ver <= 9) 8 else if (ver <= 26) 10 else 12
        }
    }

    companion object {
        fun makeBytes(data: ByteArray): QrSegment = QrSegment(Mode.BYTE, data.size, data)

        private val ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

        fun makeAlphanumeric(text: String): QrSegment {
            var bitBuffer = 0
            var bitLen = 0
            val data = ArrayList<Byte>(text.length * 5 / 8 + 1)
            for (c in text) {
                val idx = ALPHANUMERIC_CHARSET.indexOf(c)
                if (idx == -1) throw IllegalArgumentException()
                bitBuffer = (bitBuffer shl 5) or idx
                bitLen += 5
                if (bitLen >= 8) {
                    bitLen -= 8
                    data.add(((bitBuffer ushr bitLen) and 0xFF).toByte())
                }
            }
            if (bitLen > 0) {
                data.add(((bitBuffer shl (8 - bitLen)) and 0xFF).toByte())
            }
            return QrSegment(Mode.ALPHANUMERIC, text.length, data.toByteArray())
        }

        fun makeNumeric(text: String): QrSegment {
            var bitBuffer = 0
            var bitLen = 0
            val data = ArrayList<Byte>(text.length * 10 / 8 + 1)
            var i = 0
            while (i < text.length) {
                val n = minOf(i + 3, text.length)
                val chunk = text.substring(i, n)
                val num = chunk.toInt()
                bitBuffer = (bitBuffer shl (3 * chunk.length)) or num
                bitLen += 3 * chunk.length
                while (bitLen >= 8) {
                    bitLen -= 8
                    data.add(((bitBuffer ushr bitLen) and 0xFF).toByte())
                }
                i = n
            }
            if (bitLen > 0) {
                data.add(((bitBuffer shl (8 - bitLen)) and 0xFF).toByte())
            }
            return QrSegment(Mode.NUMERIC, text.length, data.toByteArray())
        }

        fun makeSegments(text: String): List<QrSegment> {
            if (text.isEmpty()) return emptyList()
            if (NUMERIC_REGEX.matches(text)) return listOf(makeNumeric(text))
            if (ALPHANUMERIC_REGEX.matches(text)) return listOf(makeAlphanumeric(text))
            return listOf(makeBytes(text.encodeToByteArray()))
        }

        private val NUMERIC_REGEX = Regex("[0-9]*")
        private val ALPHANUMERIC_REGEX = Regex("[0-9A-Z $%*+\\-./:]*")

        fun getTotalBits(segs: List<QrSegment>, version: Int): Int {
            var result = 0
            for (seg in segs) {
                val ccbits = seg.mode.numCharCountBits(version)
                if (seg.numChars >= (1L shl ccbits)) return -1
                result += 4 + ccbits + seg.data.size * 8
            }
            return result
        }
    }
}

/** Битовый буфер (big-endian). */
internal class BitBuffer {
    private val bits = ArrayList<Boolean>()

    val bitLength: Int get() = bits.size

    fun appendBits(value: Int, length: Int) {
        require(length in 0..31 && value ushr length == 0)
        for (i in length - 1 downTo 0) {
            bits.add(((value ushr i) and 1) != 0)
        }
    }

    fun appendData(data: ByteArray) {
        for (b in data) {
            appendBits(b.toInt() and 0xFF, 8)
        }
    }

    fun getBit(i: Int): Boolean = bits[i]
}
