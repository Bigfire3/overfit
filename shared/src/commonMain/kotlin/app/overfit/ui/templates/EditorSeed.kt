package app.overfit.ui.templates

import app.overfit.share.ImportPreview

/**
 * A workout that came from outside (clipboard, QR, file, link, chat proposal) and opens in the normal workout editor
 * instead of a preview page. Nothing is saved until the editor's "Save".
 *
 * [hints] are short notes shown at the top of the editor (chat message, diff to the old workout). [replaceId] makes
 * "Save" overwrite that workout. [onSaved] runs after saving (e.g. acknowledge a chat proposal), [onDiscard] adds a
 * "Discard" button at the top (chat proposals).
 */
class EditorSeed(
    val preview: ImportPreview,
    val hints: List<String> = emptyList(),
    val replaceId: Long? = null,
    val discardLabel: String? = null,
    val onDiscard: (suspend () -> Unit)? = null,
    val onSaved: (suspend () -> Unit)? = null,
)

/** Hands a seed to the editor route that is about to open. */
class EditorSeedHolder {
    private var seed: EditorSeed? = null
    fun put(value: EditorSeed) { seed = value }
    fun take(): EditorSeed? = seed.also { seed = null }
}
