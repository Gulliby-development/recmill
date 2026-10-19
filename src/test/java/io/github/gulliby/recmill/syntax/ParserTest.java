package io.github.gulliby.recmill.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.core.Failure;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParserTest {
    @Test
    void parsesExactEntryApiAndNormalizesNames() {
        assertEquals(
                List.of(
                        new Entry(1, 1, "R", null, 0, null),
                        new Entry(2, 5, "A", new Picture(false, false, 1, 0, 1), 1, "B")),
                Parser.parse("01 r. 5 a pic x occurs 1 redefines b."));
    }

    @Test
    void rejectsEmptyLayout() {
        Failure failure = assertThrows(Failure.class, () -> Parser.parse(""));
        assertEquals("layout is empty", failure.getMessage());
    }

    @Test
    void rejectsWhitespaceAndCommentOnlyLayout() {
        Failure failure = assertThrows(Failure.class, () -> Parser.parse(" \t\r\n*> ignored"));
        assertEquals("layout is empty", failure.getMessage());
    }

    @Test
    void reportsMissingFinalPeriod() {
        Failure failure = assertThrows(Failure.class, () -> Parser.parse("01 R. 05 A PIC X"));
        assertEquals("entry 2: missing period at the end", failure.getMessage());
    }

    @Test
    void scansBeforeValidatingCompletedEntries() {
        Failure failure = assertThrows(Failure.class, () -> Parser.parse("bad R. 05 A"));
        assertEquals("entry 2: missing period at the end", failure.getMessage());
    }

    @Test
    void rejectsLonePeriodAsInvalidLevel() {
        Failure failure = assertThrows(Failure.class, () -> Parser.parse("."));
        assertEquals("entry 1: invalid level \"\"", failure.getMessage());
    }

    @Test
    void acceptsAsciiLevelBoundariesAndSpellings() {
        assertEquals(
                List.of(
                        new Entry(1, 1, "R", null, 0, null),
                        new Entry(2, 1, "A", null, 0, null),
                        new Entry(3, 5, "B", null, 0, null),
                        new Entry(4, 49, "C", null, 0, null)),
                Parser.parse("1 R. 01 A. 5 B. 49 C."));
    }

    @Test
    void rejectsInvalidAsciiLevels() {
        assertFailure("0 R.", "entry 1: invalid level \"0\"");
        assertFailure("00 R.", "entry 1: invalid level \"00\"");
        assertFailure("50 R.", "entry 1: invalid level \"50\"");
        assertFailure("001 R.", "entry 1: invalid level \"001\"");
        assertFailure("+1 R.", "entry 1: invalid level \"+1\"");
        assertFailure("\u0661 R.", "entry 1: invalid level \"?\"");
    }

    @Test
    void validatesLevelBeforeName() {
        assertFailure("01 R. 5x A PIC X.", "entry 2: invalid level \"5x\"");
        assertFailure("bad PIC.", "entry 1: invalid level \"bad\"");
    }

    @Test
    void acceptsAsciiNameCharactersAndMaximumLength() {
        assertEquals(
                List.of(new Entry(1, 1, "A_9-Z", null, 0, null)),
                Parser.parse("01 a_9-z."));
        assertEquals(
                List.of(new Entry(1, 1, "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234", null, 0, null)),
                Parser.parse("01 ABCDEFGHIJKLMNOPQRSTUVWXYZ1234."));
    }

    @Test
    void rejectsNamesLongerThanThirtyCharacters() {
        assertFailure(
                "01 ABCDEFGHIJKLMNOPQRSTUVWXYZ12345.",
                "entry 1: invalid name \"ABCDEFGHIJKLMNOPQRSTUVWXYZ12345\"");
    }

    @Test
    void rejectsMissingAndMalformedNames() {
        assertFailure("01.", "entry 1: invalid name \"\"");
        assertFailure("01 9A.", "entry 1: invalid name \"9A\"");
        assertFailure("01 A!.", "entry 1: invalid name \"A!\"");
        assertFailure("01 \u00e9.", "entry 1: invalid name \"?\"");
    }

    @Test
    void rejectsReservedNamesRegardlessOfCase() {
        assertFailure("01 pic.", "entry 1: invalid name \"pic\"");
        assertFailure("01 picture.", "entry 1: invalid name \"picture\"");
        assertFailure("01 is.", "entry 1: invalid name \"is\"");
        assertFailure("01 occurs.", "entry 1: invalid name \"occurs\"");
        assertFailure("01 times.", "entry 1: invalid name \"times\"");
        assertFailure("01 redefines.", "entry 1: invalid name \"redefines\"");
    }

    @Test
    void allowsRepeatedFillerNames() {
        assertEquals(
                List.of(
                        new Entry(1, 1, "R", null, 0, null),
                        new Entry(2, 5, "FILLER", null, 0, null),
                        new Entry(3, 5, "FILLER", null, 0, null)),
                Parser.parse("01 R. 05 filler. 05 FiLlEr."));
    }

    @Test
    void parsesEveryClausePermutation() {
        Entry expected = new Entry(1, 5, "A", new Picture(false, false, 1, 0, 1), 2, "B");
        assertEquals(List.of(expected), Parser.parse("05 a PIC X OCCURS 2 REDEFINES b."));
        assertEquals(List.of(expected), Parser.parse("05 a PIC X REDEFINES b OCCURS 2."));
        assertEquals(List.of(expected), Parser.parse("05 a OCCURS 2 PIC X REDEFINES b."));
        assertEquals(List.of(expected), Parser.parse("05 a OCCURS 2 REDEFINES b PIC X."));
        assertEquals(List.of(expected), Parser.parse("05 a REDEFINES b PIC X OCCURS 2."));
        assertEquals(List.of(expected), Parser.parse("05 a REDEFINES b OCCURS 2 PIC X."));
    }

    @Test
    void acceptsMixedCaseKeywordsAndOptionalClauseWords() {
        assertEquals(
                List.of(new Entry(1, 5, "A", new Picture(false, false, 1, 0, 1), 2, "B")),
                Parser.parse("05 a pIcTuRe iS x oCcUrS 2 tImEs rEdEfInEs b."));
    }

    @Test
    void acceptsMaximumOccursCount() {
        assertEquals(List.of(new Entry(1, 5, "A", null, 99, null)), Parser.parse("05 A OCCURS 99."));
    }

    @Test
    void delegatesPictureParsing() {
        assertEquals(
                List.of(new Entry(1, 5, "A", new Picture(true, true, 3, 2, 5), 0, null)),
                Parser.parse("05 A PIC S9(3)V99."));
    }

    @Test
    void rejectsMissingAndInvalidPictureOperands() {
        assertFailure("05 A PIC.", "entry 1: invalid PIC \"\"");
        assertFailure("05 A PICTURE IS.", "entry 1: invalid PIC \"\"");
        assertFailure("05 A PIC X(0).", "entry 1: invalid PIC \"X(0)\"");
        assertFailure("05 A PIC OCCURS 2.", "entry 1: invalid PIC \"OCCURS\"");
    }

    @Test
    void rejectsMissingAndInvalidOccursOperands() {
        assertFailure("05 A OCCURS.", "entry 1: invalid OCCURS \"\"");
        assertFailure("05 A OCCURS 0.", "entry 1: invalid OCCURS \"0\"");
        assertFailure("05 A OCCURS 01.", "entry 1: invalid OCCURS \"01\"");
        assertFailure("05 A OCCURS 100.", "entry 1: invalid OCCURS \"100\"");
        assertFailure("05 A OCCURS +1.", "entry 1: invalid OCCURS \"+1\"");
        assertFailure("05 A OCCURS \u0661.", "entry 1: invalid OCCURS \"?\"");
    }

    @Test
    void rejectsMissingAndInvalidRedefinesOperands() {
        assertFailure("05 A REDEFINES.", "entry 1: invalid REDEFINES \"\"");
        assertFailure("05 A REDEFINES filler.", "entry 1: invalid REDEFINES \"filler\"");
        assertFailure("05 A REDEFINES PIC.", "entry 1: invalid REDEFINES \"PIC\"");
        assertFailure("05 A REDEFINES 9B.", "entry 1: invalid REDEFINES \"9B\"");
    }

    @Test
    void rejectsDuplicateClausesBeforeTheirOperands() {
        assertFailure("05 A PIC X PICTURE X.", "entry 1: unexpected word \"PICTURE\"");
        assertFailure("05 A PICTURE X PIC X.", "entry 1: unexpected word \"PIC\"");
        assertFailure("05 A OCCURS 2 occurs.", "entry 1: unexpected word \"occurs\"");
        assertFailure("05 A REDEFINES B REDEFINES C.", "entry 1: unexpected word \"REDEFINES\"");
    }

    @Test
    void rejectsMisplacedOptionalClauseWords() {
        assertFailure("05 A PIC X IS.", "entry 1: unexpected word \"IS\"");
        assertFailure("05 A TIMES.", "entry 1: unexpected word \"TIMES\"");
    }

    @Test
    void sanitizesSupplementaryCodePointsAsOneCharacter() {
        assertFailure("05 A \uD83D\uDE00.", "entry 1: unexpected word \"?\"");
    }

    @Test
    void validatesClausesInSourceOrder() {
        assertFailure("05 A OCCURS 0 PIC X(0).", "entry 1: invalid OCCURS \"0\"");
    }

    @Test
    void validatesEntriesInSourceOrder() {
        assertFailure("05 A PIC X(0). 00 B.", "entry 1: invalid PIC \"X(0)\"");
    }

    @Test
    void returnsSyntaxValidEntriesWithoutStructuralValidation() {
        assertEquals(
                List.of(
                        new Entry(1, 5, "A", new Picture(false, false, 1, 0, 1), 0, null),
                        new Entry(2, 10, "B", null, 0, null),
                        new Entry(3, 7, "A", null, 99, "MISSING")),
                Parser.parse("05 A PIC X. 10 B. 07 A REDEFINES MISSING OCCURS 99."));
    }

    @Test
    void returnsMultipleRootsWithoutModelValidation() {
        assertEquals(
                List.of(
                        new Entry(1, 1, "R", null, 0, null),
                        new Entry(2, 1, "R", null, 0, null)),
                Parser.parse("01 R. 01 R."));
    }

    @Test
    void reportsSyntaxErrorsBeforeModelValidation() {
        assertFailure("05 R. 05 A PIC X(0).", "entry 2: invalid PIC \"X(0)\"");
    }

    private void assertFailure(String text, String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> Parser.parse(text));
        assertEquals(expectedMessage, failure.getMessage());
    }
}
