package com.oregontrail.wear.art

/**
 * The Apple II hi-res palette, as packed ARGB.
 *
 * These are the values AppleWin actually renders, not the naive full-saturation
 * primaries — real hardware produced its colours through NTSC artifacting, so a pure
 * `#00FF00` green reads as wrong to anyone who remembers the machine. Extracted from
 * AppleWin's `source/RGBMonitor.cpp` before that dependency was removed; see
 * docs/reference/game-mechanics-1985.md.
 *
 * Deliberately plain Ints rather than Compose Colors so the art format and its parser
 * stay free of Android dependencies and testable on the JVM.
 */
object ApplePalette {
    const val TRANSPARENT: Int = 0x00000000
    const val BLACK: Int = 0xFF000000.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val GREEN: Int = 0xFF38CB00.toInt()
    const val VIOLET: Int = 0xFFC734FF.toInt()
    const val ORANGE: Int = 0xFFF25E00.toInt()
    const val BLUE: Int = 0xFF0DA1FF.toInt()

    /**
     * The character each colour is written as in a `.pix` file.
     *
     * Chosen to be visually distinct in a monospace editor and mnemonic: the initial of
     * the colour, with `.` for transparent so that empty space reads as empty.
     */
    val byChar: Map<Char, Int> = mapOf(
        '.' to TRANSPARENT,
        'K' to BLACK,
        'W' to WHITE,
        'G' to GREEN,
        'V' to VIOLET,
        'O' to ORANGE,
        'B' to BLUE,
    )

    val legend: String
        get() = ". transparent, K black, W white, G green, V violet, O orange, B blue"
}

/**
 * A block of pixel art: a fixed grid of packed ARGB values.
 *
 * Not a data class — it holds an [IntArray], whose equality is by identity, which would
 * make a generated `equals` quietly wrong.
 */
class PixelArt(
    val name: String,
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "$name: size must be positive, got ${width}x$height" }
        require(pixels.size == width * height) {
            "$name: expected ${width * height} pixels for ${width}x$height, got ${pixels.size}"
        }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    /** True if every pixel is transparent — usually a sign of an unfinished asset. */
    val isBlank: Boolean get() = pixels.all { it == ApplePalette.TRANSPARENT }
}

class PixelArtException(message: String) : IllegalArgumentException(message)

/**
 * Parser for the `.pix` art format.
 *
 * The format is plain text so that art can be written, reviewed and diffed as source
 * rather than as opaque binaries — every change to a sprite shows up in `git diff` as
 * the pixels that actually changed.
 *
 * ```
 * # Anything after a hash is a comment.
 * name: wagon
 * size: 8x3
 * pixels:
 * ..KKKK..
 * .KWWWWK.
 * ..K..K..
 * ```
 *
 * Errors carry line numbers and say what was expected, because these files are written
 * by hand and by model, and a silent misparse would show up as corrupt art on a watch
 * screen rather than as a build failure.
 */
object PixelArtParser {

    fun parse(source: String, fallbackName: String = "unnamed"): PixelArt {
        var name: String? = null
        var width: Int? = null
        var height: Int? = null
        val rows = mutableListOf<String>()
        var inPixels = false

        source.lineSequence().forEachIndexed { index, raw ->
            val lineNumber = index + 1
            // Comments are only stripped in the header; a '#' can't occur in pixel data
            // anyway, and stripping there would silently truncate a row.
            val line = if (inPixels) raw.trimEnd() else raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed

            if (!inPixels) {
                when {
                    line.startsWith("name:") -> name = line.removePrefix("name:").trim()
                    line.startsWith("size:") -> {
                        val value = line.removePrefix("size:").trim()
                        val parts = value.split('x', 'X')
                        if (parts.size != 2) {
                            throw PixelArtException(
                                "line $lineNumber: size must look like '48x24', got '$value'"
                            )
                        }
                        width = parts[0].trim().toIntOrNull()
                            ?: throw PixelArtException("line $lineNumber: bad width '${parts[0]}'")
                        height = parts[1].trim().toIntOrNull()
                            ?: throw PixelArtException("line $lineNumber: bad height '${parts[1]}'")
                    }
                    line == "pixels:" -> inPixels = true
                    else -> throw PixelArtException(
                        "line $lineNumber: expected 'name:', 'size:' or 'pixels:', got '$line'"
                    )
                }
            } else {
                rows += line
            }
        }

        val artName = name ?: fallbackName
        val w = width ?: throw PixelArtException("$artName: missing 'size:' header")
        val h = height ?: throw PixelArtException("$artName: missing 'size:' header")
        if (!inPixels) throw PixelArtException("$artName: missing 'pixels:' section")

        if (rows.size != h) {
            throw PixelArtException(
                "$artName: declared height $h but found ${rows.size} rows of pixels"
            )
        }

        val pixels = IntArray(w * h)
        rows.forEachIndexed { y, row ->
            if (row.length != w) {
                throw PixelArtException(
                    "$artName: row ${y + 1} is ${row.length} characters, expected $w"
                )
            }
            row.forEachIndexed { x, char ->
                val colour = ApplePalette.byChar[char] ?: throw PixelArtException(
                    "$artName: row ${y + 1}, column ${x + 1}: '$char' is not a palette " +
                        "character (${ApplePalette.legend})"
                )
                pixels[y * w + x] = colour
            }
        }

        return PixelArt(artName, w, h, pixels)
    }
}
