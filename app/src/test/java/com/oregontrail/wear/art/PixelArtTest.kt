package com.oregontrail.wear.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PixelArtTest {

    private val simple = """
        # a tiny test sprite
        name: dot
        size: 3x2
        pixels:
        .K.
        WGW
    """.trimIndent()

    @Test
    fun `a well formed sprite parses`() {
        val art = PixelArtParser.parse(simple)

        assertEquals("dot", art.name)
        assertEquals(3, art.width)
        assertEquals(2, art.height)
        assertEquals(ApplePalette.TRANSPARENT, art[0, 0])
        assertEquals(ApplePalette.BLACK, art[1, 0])
        assertEquals(ApplePalette.WHITE, art[0, 1])
        assertEquals(ApplePalette.GREEN, art[1, 1])
    }

    @Test
    fun `every palette character maps to a colour`() {
        val art = PixelArtParser.parse(
            """
            name: palette
            size: 7x1
            pixels:
            .KWGVOB
            """.trimIndent()
        )
        assertEquals(
            listOf(
                ApplePalette.TRANSPARENT, ApplePalette.BLACK, ApplePalette.WHITE,
                ApplePalette.GREEN, ApplePalette.VIOLET, ApplePalette.ORANGE,
                ApplePalette.BLUE,
            ),
            art.pixels.toList(),
        )
    }

    @Test
    fun `comments and blank lines are ignored in the header`() {
        val art = PixelArtParser.parse(
            """
            # leading comment

            name: spaced   # trailing comment
            size: 2x1

            pixels:
            KW
            """.trimIndent()
        )
        assertEquals("spaced", art.name)
        assertEquals(2, art.width)
    }

    @Test
    fun `the file name is used when no name header is given`() {
        val art = PixelArtParser.parse("size: 1x1\npixels:\nK", fallbackName = "fallback")
        assertEquals("fallback", art.name)
    }

    @Test
    fun `a blank sprite is detected`() {
        assertTrue(PixelArtParser.parse("size: 2x1\npixels:\n..").isBlank)
        assertFalse(PixelArtParser.parse("size: 2x1\npixels:\n.K").isBlank)
    }

    // ---- Error reporting ----
    //
    // These files are written by hand and by model, so a misparse has to fail loudly
    // with a legible message rather than silently produce corrupt art on a watch.

    @Test
    fun `a short row is rejected with its row number`() {
        val error = runCatching {
            PixelArtParser.parse("name: bad\nsize: 4x2\npixels:\nKKKK\nKK")
        }.exceptionOrNull()

        assertTrue(error is PixelArtException)
        assertTrue("was: ${error?.message}", error!!.message!!.contains("row 2"))
        assertTrue(error.message!!.contains("expected 4"))
    }

    @Test
    fun `too few rows is rejected`() {
        val error = runCatching {
            PixelArtParser.parse("name: bad\nsize: 2x3\npixels:\nKK\nKK")
        }.exceptionOrNull()

        assertTrue(error is PixelArtException)
        assertTrue("was: ${error?.message}", error!!.message!!.contains("height 3"))
    }

    @Test
    fun `an unknown palette character is rejected with its position`() {
        val error = runCatching {
            PixelArtParser.parse("name: bad\nsize: 3x1\npixels:\nKZK")
        }.exceptionOrNull()

        assertTrue(error is PixelArtException)
        assertTrue("was: ${error?.message}", error!!.message!!.contains("column 2"))
        assertTrue(error.message!!.contains("'Z'"))
    }

    @Test
    fun `a missing size header is rejected`() {
        val error = runCatching { PixelArtParser.parse("name: bad\npixels:\nK") }
            .exceptionOrNull()
        assertTrue(error is PixelArtException)
        assertTrue("was: ${error?.message}", error!!.message!!.contains("size"))
    }

    @Test
    fun `a missing pixels section is rejected`() {
        val error = runCatching { PixelArtParser.parse("name: bad\nsize: 1x1") }
            .exceptionOrNull()
        assertTrue(error is PixelArtException)
        assertTrue("was: ${error?.message}", error!!.message!!.contains("pixels"))
    }

    @Test
    fun `a malformed size is rejected`() {
        val error = runCatching { PixelArtParser.parse("size: 12\npixels:\nK") }
            .exceptionOrNull()
        assertTrue(error is PixelArtException)
        assertTrue("was: ${error?.message}", error!!.message!!.contains("48x24"))
    }

    @Test
    fun `an unrecognised header line is rejected rather than ignored`() {
        val error = runCatching { PixelArtParser.parse("colour: red\nsize: 1x1\npixels:\nK") }
            .exceptionOrNull()
        assertTrue(error is PixelArtException)
    }

    // ---- The real assets ----

    /**
     * Parses every `.pix` file that actually ships. This is the guard that catches a
     * malformed sprite at build time rather than as a blank rectangle on the watch —
     * which matters because these assets are generated in bulk.
     */
    @Test
    fun `every shipped asset parses and is not blank`() {
        val dir = File("src/main/assets/art")
        assertTrue("art directory not found at ${dir.absolutePath}", dir.isDirectory)

        val files = dir.listFiles { f: File -> f.extension == "pix" }.orEmpty()
        assertTrue("no art assets found", files.isNotEmpty())

        for (file in files) {
            val art = try {
                PixelArtParser.parse(file.readText(), fallbackName = file.nameWithoutExtension)
            } catch (e: PixelArtException) {
                throw AssertionError("${file.name}: ${e.message}", e)
            }
            assertFalse("${file.name} is entirely transparent", art.isBlank)
            assertEquals(
                "${file.name}: name header should match the file name",
                file.nameWithoutExtension,
                art.name,
            )
        }
    }
}
