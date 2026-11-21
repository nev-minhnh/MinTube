package com.hhst.youtubelite.tv.qr

/**
 * A QR code: [size] x [size] modules, row-major, true = dark. Quiet zone not included.
 */
class TvQrMatrix internal constructor(val size: Int, val version: Int, val mask: Int, private val modules: BooleanArray) {
    fun isDark(x: Int, y: Int): Boolean = modules[y * size + x]

    /** Rows as "1"/"0" strings (tests and debugging). */
    fun rows(): List<String> = (0 until size).map { y -> buildString { for (x in 0 until size) append(if (isDark(x, y)) '1' else '0') } }
}

/**
 * Minimal pure-Kotlin QR encoder (ISO/IEC 18004): byte mode, error correction level M, versions 1-10
 * (up to 213 bytes, plenty for the sign-in and source URLs). No dependency: the app ships no QR
 * library and the TV only needs to draw a couple of links. Structure follows the public-domain
 * reference design by Project Nayuki.
 */
object TvQrCode {
    const val MAX_VERSION = 10

    /** Error-correction codewords per block, level M, index = version. */
    private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
    /** Error-correction blocks, level M, index = version. */
    private val BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)
    private const val ECL_FORMAT_BITS_M = 0

    /** Encodes [text] as UTF-8. Null when it does not fit version [MAX_VERSION]. [mask] forces a mask (tests). */
    fun encode(text: String, mask: Int? = null): TvQrMatrix? {
        require(mask == null || mask in 0..7)
        val bytes = text.toByteArray(Charsets.UTF_8)
        val version = (1..MAX_VERSION).firstOrNull { bytes.size <= byteCapacity(it) } ?: return null
        val data = dataCodewords(bytes, version)
        val codewords = addEccAndInterleave(data, version)
        val builder = Builder(version)
        builder.drawFunctionPatterns()
        builder.drawCodewords(codewords)
        val chosen = mask ?: (0..7).minBy { candidate ->
            builder.applyMask(candidate)
            builder.drawFormatBits(candidate)
            val penalty = builder.penalty()
            builder.applyMask(candidate) // XOR undoes it
            penalty
        }
        builder.applyMask(chosen)
        builder.drawFormatBits(chosen)
        return TvQrMatrix(builder.size, version, chosen, builder.modules.copyOf())
    }

    internal fun rawDataModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val align = version / 7 + 2
            result -= (25 * align - 10) * align - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    internal fun dataCodewordCount(version: Int): Int = rawDataModules(version) / 8 - ECC_PER_BLOCK[version] * BLOCKS[version]

    /** Bytes that fit in byte mode: 4 mode bits + 8 count bits (versions 1-9) or 16 (10+). */
    internal fun byteCapacity(version: Int): Int {
        val countBits = if (version < 10) 8 else 16
        return (dataCodewordCount(version) * 8 - 4 - countBits) / 8
    }

    private fun dataCodewords(bytes: ByteArray, version: Int): IntArray {
        val bits = BitBuffer()
        bits.append(0b0100, 4)
        bits.append(bytes.size, if (version < 10) 8 else 16)
        bytes.forEach { bits.append(it.toInt() and 0xFF, 8) }
        val capacity = dataCodewordCount(version) * 8
        bits.append(0, minOf(4, capacity - bits.length))
        bits.append(0, (8 - bits.length % 8) % 8)
        var pad = 0xEC
        while (bits.length < capacity) {
            bits.append(pad, 8)
            pad = pad xor 0xEC xor 0x11
        }
        return IntArray(capacity / 8) { index -> (0 until 8).fold(0) { acc, bit -> (acc shl 1) or bits[index * 8 + bit] } }
    }

    private fun addEccAndInterleave(data: IntArray, version: Int): IntArray {
        val blocks = BLOCKS[version]
        val eccLen = ECC_PER_BLOCK[version]
        val raw = rawDataModules(version) / 8
        val shortBlocks = blocks - raw % blocks
        val shortLen = raw / blocks
        val divisor = reedSolomonDivisor(eccLen)
        val built = ArrayList<IntArray>(blocks)
        var k = 0
        for (i in 0 until blocks) {
            val len = shortLen - eccLen + if (i < shortBlocks) 0 else 1
            val dat = data.copyOfRange(k, k + len)
            k += len
            val ecc = reedSolomonRemainder(dat, divisor)
            // Short blocks get a placeholder byte so every block has the same length; it is skipped below.
            val block = IntArray(shortLen + 1)
            dat.copyInto(block)
            ecc.copyInto(block, shortLen + 1 - eccLen)
            built += block
        }
        val result = IntArray(raw)
        var out = 0
        for (i in 0 until shortLen + 1) {
            for (j in 0 until blocks) {
                if (i != shortLen - eccLen || j >= shortBlocks) result[out++] = built[j][i]
            }
        }
        return result
    }

    private fun reedSolomonDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
        }
        return result
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private class BitBuffer {
        private val bits = ArrayList<Int>()
        val length: Int get() = bits.size
        operator fun get(index: Int): Int = bits[index]
        fun append(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits += (value ushr i) and 1
        }
    }

    private class Builder(val version: Int) {
        val size = version * 4 + 17
        val modules = BooleanArray(size * size)
        private val function = BooleanArray(size * size)

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y * size + x] = dark
            function[y * size + x] = true
        }

        private fun get(x: Int, y: Int) = modules[y * size + x]

        fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val positions = alignmentPositions()
            val last = positions.size - 1
            for (i in positions.indices) for (j in positions.indices) {
                if (i == 0 && j == 0 || i == 0 && j == last || i == last && j == 0) continue
                for (dy in -2..2) for (dx in -2..2) {
                    set(positions[i] + dx, positions[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
                }
            }
            drawFormatBits(0) // reserves the format area; real bits are drawn after masking
            drawVersion()
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x !in 0 until size || y !in 0 until size) continue
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                set(x, y, dist != 2 && dist != 4)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
            val result = IntArray(count)
            result[0] = 6
            var pos = size - 7
            for (i in count - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        fun drawFormatBits(mask: Int) {
            val data = ECL_FORMAT_BITS_M shl 3 or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = (data shl 10 or rem) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = version shl 12 or rem
            for (i in 0 until 18) {
                val dark = (bits ushr i) and 1 != 0
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, dark)
                set(b, a, dark)
            }
        }

        fun drawCodewords(data: IntArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0 until 2) {
                        val x = right - j
                        val upward = (right + 1) and 2 == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!function[y * size + x] && i < data.size * 8) {
                            modules[y * size + x] = (data[i ushr 3] ushr (7 - (i and 7))) and 1 != 0
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                val index = y * size + x
                if (invert && !function[index]) modules[index] = !modules[index]
            }
        }

        /** Standard mask penalty (runs, 2x2 blocks, finder-like patterns, dark balance). */
        fun penalty(): Int {
            var result = 0
            fun line(get: (Int) -> Boolean) {
                var run = 1
                for (i in 1 until size) {
                    if (get(i) == get(i - 1)) {
                        run++
                        if (run == 5) result += 3 else if (run > 5) result++
                    } else run = 1
                }
                // 1:1:3:1:1 with four light modules on one side (outside the symbol counts as light).
                fun at(i: Int) = i in 0 until size && get(i)
                for (i in -4 until size) {
                    val core = at(i) && !at(i + 1) && at(i + 2) && at(i + 3) && at(i + 4) && !at(i + 5) && at(i + 6)
                    if (!core) continue
                    val before = (1..4).none { at(i - it) }
                    val after = (7..10).none { at(i + it) }
                    if (before || after) result += 40
                }
            }
            for (y in 0 until size) line { x -> get(x, y) }
            for (x in 0 until size) line { y -> get(x, y) }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = get(x, y)
                if (c == get(x + 1, y) && c == get(x, y + 1) && c == get(x + 1, y + 1)) result += 3
            }
            val dark = modules.count { it }
            val total = size * size
            val k = (kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1
            return result + k * 10
        }
    }
}
