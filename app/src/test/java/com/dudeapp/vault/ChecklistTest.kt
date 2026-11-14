package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistTest {
    @Test
    fun textAndBoxesAreToldApart() {
        val lines = parseNoteLines("Shopping\n[ ] Milk\n[x] Bread\n- [X] Eggs\n[] not a box\n[ ]")
        assertEquals(
            listOf(
                NoteLine("Shopping", null),
                NoteLine("Milk", false),
                NoteLine("Bread", true),
                NoteLine("Eggs", true),
                NoteLine("[] not a box", null),
                NoteLine("", false),
            ),
            lines,
        )
    }

    @Test
    fun togglingChangesOnlyThatLine() {
        val note = "Shopping\n[ ] Milk\n[x] Bread\n- [ ] Eggs"
        assertEquals("Shopping\n[x] Milk\n[x] Bread\n- [ ] Eggs", toggleNoteLine(note, 1))
        assertEquals("Shopping\n[ ] Milk\n[ ] Bread\n- [ ] Eggs", toggleNoteLine(note, 2))
        assertEquals("Shopping\n[ ] Milk\n[x] Bread\n- [x] Eggs", toggleNoteLine(note, 3))
        // A text line, or a line that does not exist, leaves the note as it is.
        assertEquals(note, toggleNoteLine(note, 0))
        assertEquals(note, toggleNoteLine(note, 9))
    }

    @Test
    fun togglingTwiceGivesTheNoteBack() {
        val note = "[ ] One\r\n[x] Two\r\nplain"
        assertEquals(note, toggleNoteLine(toggleNoteLine(note, 0), 0))
        assertEquals(note, toggleNoteLine(toggleNoteLine(note, 1), 1))
    }

    @Test
    fun itemsAreAddedAtTheEnd() {
        assertEquals("[ ] Milk", addChecklistItem("", "Milk"))
        assertEquals("List\n[ ] Milk", addChecklistItem("List\n\n", " Milk "))
        assertEquals("List", addChecklistItem("List", "   "))
        assertEquals("[ ] a b", addChecklistItem("", "a\nb"))
    }

    @Test
    fun linesCanBeRemoved() {
        assertEquals("a\nc", removeNoteLine("a\n[ ] b\nc", 1))
        assertEquals("a", removeNoteLine("a", 5))
    }

    @Test
    fun newTickBoxStartsOnItsOwnLine() {
        assertEquals("[ ] ", withNewTickBox(""))
        assertEquals("abc\n[ ] ", withNewTickBox("abc"))
        assertEquals("abc\n[ ] ", withNewTickBox("abc\n"))
    }
}
