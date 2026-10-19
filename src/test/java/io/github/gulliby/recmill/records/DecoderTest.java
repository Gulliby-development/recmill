package io.github.gulliby.recmill.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecoderTest {
    private static final String DECIMAL_LAYOUT =
            "01 R. 05 ID PIC 9(2). 05 NAME PIC X(3). 05 AMT PIC S9V99.";
    private static final String OCCURS_LAYOUT =
            "01 R. 05 G. 10 N PIC 9 OCCURS 2. 05 T REDEFINES G PIC X(2). "
                    + "05 FILLER PIC 9. 05 Z PIC X.";
    private static final String TWO_DIGIT_LAYOUT = "01 R. 05 A PIC 9. 05 B PIC 9.";

    @Test
    void decodesSignedDecimalsAndMultipleRecordsInFieldOrder() {
        assertEquals(
                List.of(
                        List.of(
                                new Decoder.Value("ID", "1"),
                                new Decoder.Value("NAME", "A"),
                                new Decoder.Value("AMT", "1.50")),
                        List.of(
                                new Decoder.Value("ID", "2"),
                                new Decoder.Value("NAME", "B"),
                                new Decoder.Value("AMT", "-2.00"))),
                decode(
                        DECIMAL_LAYOUT,
                        List.of(),
                        List.of(
                                new Records.Line(1, "01A  15{"),
                                new Records.Line(2, "02B  20}"))));
    }

    @Test
    void decodesEmptyAlphaAndNegativeZeroWithoutASeparatorValue() {
        assertEquals(
                List.of(
                        List.of(
                                new Decoder.Value("ID", "0"),
                                new Decoder.Value("NAME", ""),
                                new Decoder.Value("AMT", "0.00"))),
                decode(
                        DECIMAL_LAYOUT,
                        List.of(),
                        List.of(new Records.Line(1, "00   00}"))));
    }

    @Test
    void decodesDefaultViewOccurrencesAndSkipsGroupAndInvalidNumericFiller() {
        assertEquals(
                List.of(
                        List.of(
                                new Decoder.Value("G.N(1)", "1"),
                                new Decoder.Value("G.N(2)", "2"),
                                new Decoder.Value("Z", "Q"))),
                decode(
                        OCCURS_LAYOUT,
                        List.of(),
                        List.of(new Records.Line(1, "12xQ"))));
    }

    @Test
    void decodesLowercaseAlternateViewAndSkipsInactiveNumericFields() {
        assertEquals(
                List.of(
                        List.of(
                                new Decoder.Value("T", "AB"),
                                new Decoder.Value("Z", "Q"))),
                decode(
                        OCCURS_LAYOUT,
                        List.of("t"),
                        List.of(new Records.Line(1, "ABxQ"))));
    }

    @Test
    void acceptsRepeatedViewNamesWithoutDuplicatingValues() {
        assertEquals(
                List.of(
                        List.of(
                                new Decoder.Value("T", "AB"),
                                new Decoder.Value("Z", "Q"))),
                decode(
                        OCCURS_LAYOUT,
                        List.of("T", "T"),
                        List.of(new Records.Line(1, "ABxQ"))));
    }

    @Test
    void reportsNonPrintableCharacterBeforeRecordLengthMismatch() {
        assertFailure(
                "line 1: column 2 is not a printable ASCII character",
                TWO_DIGIT_LAYOUT,
                List.of(new Records.Line(1, "1\tX")));
    }

    @Test
    void reportsShortRecordBeforeSlicingFields() {
        assertFailure(
                "line 1: record has 1 characters (expected 2)",
                TWO_DIGIT_LAYOUT,
                List.of(new Records.Line(1, "1")));
    }

    @Test
    void reportsLongRecordBeforeSlicingFields() {
        assertFailure(
                "line 1: record has 3 characters (expected 2)",
                TWO_DIGIT_LAYOUT,
                List.of(new Records.Line(1, "123")));
    }

    @Test
    void reportsFirstInvalidFieldInFieldOrder() {
        assertFailure(
                "line 1: A (columns 1-1): invalid value \"x\"",
                TWO_DIGIT_LAYOUT,
                List.of(new Records.Line(1, "xy")));
    }

    @Test
    void reportsFirstFailureWithPhysicalLineNumberAfterEmptyLine() {
        assertFailure(
                "line 3: B (columns 2-2): invalid value \"x\"",
                TWO_DIGIT_LAYOUT,
                List.of(
                        new Records.Line(1, "12"),
                        new Records.Line(3, "3x"),
                        new Records.Line(4, "xy")));
    }

    @Test
    void rejectsNonAsciiRecordCharactersBeforeFieldDecoding() {
        assertFailure(
                "line 1: column 2 is not a printable ASCII character",
                TWO_DIGIT_LAYOUT,
                List.of(new Records.Line(1, "1\u00E9")));
    }

    private static List<List<Decoder.Value>> decode(
            String layoutText, List<String> views, List<Records.Line> lines) {
        Layout layout = Layout.build(layoutText);
        List<Slot> slots = Selection.select(layout, views);
        return Decoder.decode(layout, slots, lines);
    }

    private static void assertFailure(
            String expected, String layoutText, List<Records.Line> lines) {
        Layout layout = Layout.build(layoutText);
        List<Slot> slots = Selection.select(layout, List.of());

        Failure failure = assertThrows(
                Failure.class, () -> Decoder.decode(layout, slots, lines));

        assertEquals(expected, failure.getMessage());
    }
}
