package com.oregontrail.wear.data

import kotlinx.browser.window
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * A [Storage] slot backed by one `localStorage` key.
 *
 * `localStorage` rather than IndexedDB because everything about this fits: the run is a
 * few kilobytes of JSON, the API is synchronous — so [SaveRepository] keeps the
 * write-then-read ordering it relies on, with no promise to await between them — and the
 * data is per-origin and permanent, which is what a save file is. The 5MB quota is three
 * orders of magnitude more than a run needs.
 *
 * There is no atomic-write dance here, unlike [com.oregontrail.wear.data.FileStorage] on
 * the watch. `setItem` either stores the whole string or throws; the browser will not
 * leave half of one behind, so there is no torn write to defend against.
 *
 * Writes can still fail, and for a reason a watch does not have: a browser in private
 * mode, or one whose quota is full, throws `QuotaExceededError`. That is reported as
 * `false` like any other failed save rather than thrown — a player in a private window
 * should get a game that does not remember them, not one that will not start.
 */
class LocalStorage(private val key: String) : Storage {

    override fun read(): String? = try {
        window.localStorage[key]
    } catch (e: Throwable) {
        null
    }

    override fun write(text: String): Boolean = try {
        window.localStorage[key] = text
        true
    } catch (e: Throwable) {
        false
    }

    override fun exists(): Boolean = read() != null

    override fun clear() {
        try {
            window.localStorage.removeItem(key)
        } catch (e: Throwable) {
            // Nothing useful to do: the slot is being thrown away either way.
        }
    }
}
