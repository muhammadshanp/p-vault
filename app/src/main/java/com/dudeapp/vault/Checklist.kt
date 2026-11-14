package com.dudeapp.vault

/**
 * Tick boxes inside notes. A note stays ordinary text: a line that starts with "[ ] " is
 * an open item and "[x] " a done one, so notes still read fine when shared or exported.
 */
data class NoteLine(
    val text: String,
    /** null for ordinary text, otherwise whether the box is ticked. */
    val checked: Boolean?,
)

private val BOX = Regex("""^(\s*(?:[-*]\s+)?)\[([ xX])](?:\s(.*)|)$""")

internal fun parseNoteLines(note: String): List<NoteLine> =
    note.split('\n').map { raw ->
        val line = raw.removeSuffix("\r")
        val match = BOX.matchEntire(line)
        if (match == null) {
            NoteLine(line, null)
        } else {
            NoteLine(match.groupValues[3], match.groupValues[2] != " ")
        }
    }

/** Ticks or unticks the box on line [index] (as numbered by [parseNoteLines]). */
internal fun toggleNoteLine(note: String, index: Int): String {
    val lines = note.split('\n').toMutableList()
    val raw = lines.getOrNull(index) ?: return note
    val match = BOX.matchEntire(raw.removeSuffix("\r")) ?: return note
    val mark = if (match.groupValues[2] == " ") "x" else " "
    val ending = if (raw.endsWith("\r")) "\r" else ""
    lines[index] = "${match.groupValues[1]}[$mark] ${match.groupValues[3]}$ending"
    return lines.joinToString("\n")
}

/** Adds a new open item at the end of the note. */
internal fun addChecklistItem(note: String, item: String): String {
    val clean = item.replace('\n', ' ').replace('\r', ' ').trim()
    if (clean.isEmpty()) return note
    val base = note.trimEnd('\n', '\r')
    return if (base.isBlank()) "[ ] $clean" else "$base\n[ ] $clean"
}

/** Removes line [index] from the note. */
internal fun removeNoteLine(note: String, index: Int): String {
    val lines = note.split('\n').toMutableList()
    if (index !in lines.indices) return note
    lines.removeAt(index)
    return lines.joinToString("\n")
}

/** What to append to a draft so the cursor lands in a fresh tick-box line. */
internal fun withNewTickBox(draft: String): String = when {
    draft.isEmpty() || draft.endsWith("\n") -> "$draft[ ] "
    else -> "$draft\n[ ] "
}
