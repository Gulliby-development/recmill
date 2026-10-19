package io.github.gulliby.recmill.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.core.Failure;
import org.junit.jupiter.api.Test;

class WordsTest {
    @Test
    void tokenizesWhitespaceAndPreservesSpelling() {
        assertEquals(
                java.util.List.of(java.util.List.of("01", "r"), java.util.List.of("05", "a", "PIC", "X")),
                Words.entries("01 r.\t05 a PIC X.\r\n"));
    }

    @Test
    void acceptsPeriodAsSeparateWord() {
        assertEquals(java.util.List.of(java.util.List.of("01", "R")), Words.entries("01 R ."));
    }

    @Test
    void retainsEntryClosedByLonePeriod() {
        assertEquals(java.util.List.of(java.util.List.of()), Words.entries("."));
    }

    @Test
    void removesExactlyOneTrailingPeriod() {
        assertEquals(java.util.List.of(java.util.List.of("01", "R.")), Words.entries("01 R.."));
    }

    @Test
    void retainsInteriorPeriods() {
        assertEquals(java.util.List.of(java.util.List.of("01", "R.A")), Words.entries("01 R.A."));
    }

    @Test
    void endsEmbeddedCommentAtCarriageReturn() {
        assertEquals(
                java.util.List.of(java.util.List.of("01", "R"), java.util.List.of("05", "A", "PIC", "X")),
                Words.entries("01 R*>ignored\r. 05 A PIC X."));
    }

    @Test
    void endsCommentAtLineFeed() {
        assertEquals(
                java.util.List.of(java.util.List.of("01", "R"), java.util.List.of("05", "A")),
                Words.entries("01 R. *>ignored\n05 A."));
    }

    @Test
    void endsCommentAtEndOfInput() {
        assertEquals(java.util.List.of(java.util.List.of("01", "R")), Words.entries("01 R. *>tail"));
    }

    @Test
    void doesNotTreatFormFeedAsWhitespace() {
        assertEquals(java.util.List.of(java.util.List.of("01\fR")), Words.entries("01\fR."),
                "[[\"01\\fR\"]]");
    }

    @Test
    void rejectsEmptyLayout() {
        assertFailure("", "layout is empty");
    }

    @Test
    void rejectsWhitespaceAndCommentOnlyLayout() {
        assertFailure(" \t\r\n*> ignored", "layout is empty");
    }

    @Test
    void reportsUnfinishedFinalEntryAfterCompletedEntry() {
        assertFailure("01 R. 05 A PIC X", "entry 2: missing period at the end");
    }

    @Test
    void scansWholeLayoutBeforeEntryValidation() {
        assertFailure("bad R. 05 A", "entry 2: missing period at the end");
    }

    private void assertFailure(String text, String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> Words.entries(text));
        assertEquals(expectedMessage, failure.getMessage());
    }
}
