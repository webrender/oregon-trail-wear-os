package com.oregontrail.wear.data

import java.io.File
import java.io.IOException

/**
 * A [Storage] slot backed by one file in the app's private directory.
 *
 * Writes are atomic: full write to a temporary file, then rename. A watch can be killed
 * mid-write, and a half-written JSON file would fail to parse and silently discard
 * whatever it held. Rename is atomic on the same filesystem, so what is on disk is always
 * either the previous contents or the new ones, never a fragment.
 */
class FileStorage(private val file: File) : Storage {

    private val temp: File get() = File(file.parentFile, "${file.name}.tmp")

    override fun read(): String? =
        if (!file.exists()) null else try {
            file.readText()
        } catch (e: IOException) {
            null
        }

    override fun write(text: String): Boolean = try {
        temp.writeText(text)
        if (file.exists()) file.delete()
        temp.renameTo(file)
    } catch (e: IOException) {
        false
    }

    override fun exists(): Boolean = file.exists()

    override fun clear() {
        file.delete()
        temp.delete()
    }
}
