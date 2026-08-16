package com.oregontrail.wear.ui

import java.io.File

/**
 * Just enough TrueType to answer "how wide is this string".
 *
 * Reading the font is the only honest way to check the budget, and Android's text stack is
 * not on the unit-test classpath — the same reason `ArtNamesTest` reads PNG dimensions
 * straight out of the IHDR chunk rather than pulling in an image library. Three tables are
 * needed: `head` for the em size, `hhea`/`hmtx` for the advances, and `cmap` to turn
 * characters into the glyph ids those advances are indexed by.
 */
internal class Shaston(file: File) {

    private val bytes = file.readBytes()

    private fun u8(at: Int): Int = bytes[at].toInt() and 0xFF
    private fun u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)
    private fun s16(at: Int): Int = u16(at).toShort().toInt()
    private fun u32(at: Int): Int = (u16(at) shl 16) or u16(at + 2)

    private val tables: Map<String, Int> = buildMap {
        for (i in 0 until u16(4)) {
            val record = 12 + 16 * i
            put(String(bytes, record, 4, Charsets.ISO_8859_1), u32(record + 8))
        }
    }

    private fun table(tag: String): Int =
        tables[tag] ?: error("font has no '$tag' table")

    private val unitsPerEm = u16(table("head") + 18)
    private val longMetrics = u16(table("hhea") + 34)
    private val hmtx = table("hmtx")

    /** The format 4 subtable — the only one this font is expected to carry. */
    private val cmap: Int = run {
        val base = table("cmap")
        (0 until u16(base + 2))
            .map { base + u32(base + 4 + 8 * it + 4) }
            .firstOrNull { u16(it) == 4 }
            ?: error("font has no format 4 cmap")
    }

    private val segments = u16(cmap + 6) / 2
    private val endCodes = cmap + 14
    private val startCodes = endCodes + segments * 2 + 2
    private val deltas = startCodes + segments * 2
    private val rangeOffsets = deltas + segments * 2

    private fun glyphOf(character: Char): Int {
        val code = character.code
        for (segment in 0 until segments) {
            if (code > u16(endCodes + segment * 2)) continue
            val start = u16(startCodes + segment * 2)
            if (code < start) return 0
            val delta = s16(deltas + segment * 2)
            val rangeOffset = u16(rangeOffsets + segment * 2)
            if (rangeOffset == 0) return (code + delta) and 0xFFFF
            val at = rangeOffsets + segment * 2 + rangeOffset + (code - start) * 2
            val glyph = u16(at)
            return if (glyph == 0) 0 else (glyph + delta) and 0xFFFF
        }
        return 0
    }

    /** Trailing glyphs share the last entry's advance — the point of `numberOfHMetrics`. */
    private fun advanceOf(glyph: Int): Int =
        u16(hmtx + 4 * minOf(glyph, longMetrics - 1))

    fun widthPx(text: String, fontSizePx: Double): Double =
        text.sumOf { advanceOf(glyphOf(it)) } * fontSizePx / unitsPerEm

    /**
     * Whether the font draws this character itself.
     *
     * Glyph 0 is `.notdef` — the "no such character" glyph — and a `cmap` lookup that
     * falls through returns it. What happens next is entirely up to the platform, which
     * is the problem [FontCoverageTest] exists to catch: Android walks a fallback chain
     * and finds the character in some other family, while a browser handed one embedded
     * font has nothing to fall back to and draws nothing.
     */
    fun hasGlyph(character: Char): Boolean = glyphOf(character) != 0
}
