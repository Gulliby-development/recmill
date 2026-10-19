package io.github.gulliby.recmill.core;

import static io.github.gulliby.recmill.core.Text.show;
import static io.github.gulliby.recmill.core.Text.upper;
import static io.github.gulliby.recmill.core.Text.line;
import static io.github.gulliby.recmill.core.Text.renderTable;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class TextTest {
    @Test
    void showQuotesEmptyText() {
        assertEquals("\"\"", show(""));
    }

    @Test
    void showPreservesPrintableBoundaryCharacters() {
        assertEquals("\" ~\"", show(" ~"));
    }

    @Test
    void showReplacesControlsAndNonAsciiCodePoints() {
        assertEquals("\"a????\"", show("a\t\né\u007f"));
    }

    @Test
    void showReplacesSupplementaryCharacterOnce() {
        assertEquals("\"?\"", show("\uD83D\uDE00"));
    }

    @Test
    void showPreservesPrintableQuotesAndBackslashes() {
        assertEquals("\"\"\\\"", show("\"\\"));
    }

    @Test
    void upperChangesOnlyAsciiLowercaseLetters() {
        assertEquals("AZAZ09-_", upper("azAZ09-_"));
    }

    @Test
    void upperLeavesNonAsciiLettersUnchanged() {
        assertEquals("éıß", upper("éıß"));
    }

    @Test
    void linePadsShortKeysAndEndsWithExplicitLf() {
        assertEquals("length      15\n", line("length", "15"));
    }

    @Test
    void lineKeepsTenCharacterKeysAndAddsTwoSpaces() {
        assertEquals("abcdefghij  x\n", line("abcdefghij", "x"));
    }

    @Test
    void renderTableAlignsColumnsToWidestCell() {
        assertEquals(List.of("path    n", "A       12", "LONGER  3"),
                renderTable(List.of("path", "n"), List.of(List.of("A", "12"), List.of("LONGER", "3"))));
    }

    @Test
    void renderTableReturnsOnlyHeaderWhenThereAreNoRows() {
        assertEquals(List.of("a  long"), renderTable(List.of("a", "long"), List.of()));
    }

    @Test
    void renderTableRemovesPaddingAfterAnEmptyFinalCell() {
        assertEquals(List.of("a  b", "x"),
                renderTable(List.of("a", "b"), List.of(List.of("x", ""))));
    }
}
