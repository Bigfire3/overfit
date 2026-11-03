package app.overfit.ui.chat

/** One displayed line of a chat note. */
data class MdLine(val text: String, val heading: Boolean = false, val bullet: Boolean = false)

/**
 * Just enough Markdown for notes from the chat: `#` headings, `-`/`*` bullets and `**bold**`/`` `code` `` markers are
 * rendered as plain lines (no styling spans, in keeping with the app's flat look).
 */
object MarkdownLite {
    fun lines(markdown: String): List<MdLine> {
        val out = mutableListOf<MdLine>()
        var blank = false
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) { if (out.isNotEmpty()) blank = true; continue }
            if (blank) { out += MdLine(""); blank = false }
            val trimmed = line.trimStart()
            out += when {
                trimmed.startsWith("#") -> MdLine(inline(trimmed.trimStart('#').trim()), heading = true)
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> MdLine(inline(trimmed.drop(2).trim()), bullet = true)
                else -> MdLine(inline(trimmed))
            }
        }
        return out
    }

    private fun inline(text: String) = text.replace("**", "").replace("__", "").replace("`", "")
}
