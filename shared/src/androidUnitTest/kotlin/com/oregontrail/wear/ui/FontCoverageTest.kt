package com.oregontrail.wear.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that every character the UI can put on screen is one Shaston actually draws.
 *
 * This is the third of the silent-failure guards, alongside `ArtNamesTest` and
 * `MapLabelTest`, and it is the one the web port made necessary. A character missing from
 * the font does not warn, does not throw, and on Android does not even look wrong: the
 * platform walks its fallback chain, finds the glyph in Roboto or Noto, and draws it.
 * The result is a screen that is *almost* all Apple II pixel art with one glyph quietly
 * set in a modern sans — visible if you look, invisible if you don't.
 *
 * A browser has no such chain. It is handed one embedded font and draws nothing at all
 * for anything that font lacks, which is how three characters were found: the store's
 * minus sign and the rafting readout's two circles, all of which had been rendering in
 * the system font on the watch since they were written.
 *
 * So the check is run against the font rather than against a list, and the strings come
 * out of the source rather than being restated here — a list would have to be remembered,
 * and the failure it guards against is precisely the kind nobody remembers.
 */
class FontCoverageTest {

    private val font = Shaston(File("src/androidMain/assets/fonts/shaston_320.ttf"))

    private val sources = File("src/commonMain/kotlin")

    /**
     * String literals, with comments removed first.
     *
     * KDoc in this project is prose and uses en dashes and curly quotes freely; none of it
     * reaches a screen, and leaving it in would fail this test for every paragraph written
     * about the font rather than in it.
     */
    private val literals = Regex("\"([^\"\\\\\\n]*(?:\\\\.[^\"\\\\\\n]*)*)\"")
    private val blockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val lineComments = Regex("//[^\\n]*")

    @Test
    fun `every character in a UI string is one the font can draw`() {
        val missing = mutableMapOf<Char, MutableSet<String>>()
        var scanned = 0

        for (file in sources.walkTopDown().filter { it.extension == "kt" }) {
            scanned++
            val code = file.readText()
                .replace(blockComments, "")
                .replace(lineComments, "")
            for (match in literals.findAll(code)) {
                for (character in match.groupValues[1]) {
                    // ASCII is wholly covered and includes the escape sequences' own
                    // backslashes and letters, which are not characters on screen anyway.
                    if (character.code <= 0x7E) continue
                    if (!font.hasGlyph(character)) {
                        missing.getOrPut(character) { mutableSetOf() } += file.name
                    }
                }
            }
        }

        // Guards the guard. A test that walks the wrong directory finds nothing missing
        // and passes, which is the same result as everything being fine — and the whole
        // point of this file is that "looks fine" is not evidence.
        assertTrue(
            "scanned $scanned files under ${sources.absolutePath}; the source tree moved",
            scanned > 20,
        )

        assertTrue(
            missing.entries.joinToString(prefix = "Shaston cannot draw: ") { (character, files) ->
                "'$character' (U+%04X) in %s".format(character.code, files.sorted())
            },
            missing.isEmpty(),
        )
    }

    /**
     * Pins the three characters that were found missing, so that a well-meaning edit does
     * not put them back. The test above would catch it — this one says what happened.
     */
    @Test
    fun `the characters the port had to replace are still absent`() {
        for (character in listOf('−', '○', '●')) {
            assertTrue(
                "U+%04X is in the font now; the substitution in StoreScreens/RaftScreen "
                    .format(character.code) + "can be reverted, and this test deleted",
                !font.hasGlyph(character),
            )
        }
    }

    /** The substitutes themselves, so a font swap cannot silently undo the fix. */
    @Test
    fun `the substitutes are in the font`() {
        for (character in listOf('–', '•', '·')) {
            assertTrue("U+%04X is not in the font".format(character.code), font.hasGlyph(character))
        }
    }
}
