package io.github.gulliby.recmill.records;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckerTest {
    private static final String TWO_NUMERIC_FIELDS = "01 R. 05 A PIC 9. 05 B PIC 9.";

    @Test
    void reportsNoRecordsForAnEmptyDirectList() {
        assertEquals(
                new Checker.Report(List.of(), 0, 0),
                check(TWO_NUMERIC_FIELDS, List.of(), List.of()));
    }

    @Test
    void countsAValidRecordWithoutProblems() {
        assertEquals(
                new Checker.Report(List.of(), 1, 0),
                check(TWO_NUMERIC_FIELDS, List.of(), List.of(new Records.Line(1, "12"))));
    }

    @Test
    void reportsEveryFieldProblemInRecordAndLayoutOrder() {
        assertEquals(
                new Checker.Report(
                        List.of(
                                "line 2: A (columns 1-1): invalid value \"x\"",
                                "line 2: B (columns 2-2): invalid value \"y\"",
                                "line 4: B (columns 2-2): invalid value \"z\""),
                        3,
                        2),
                check(
                        TWO_NUMERIC_FIELDS,
                        List.of(),
                        List.of(
                                new Records.Line(2, "xy"),
                                new Records.Line(4, "1z"),
                                new Records.Line(5, "12"))));
    }

    @Test
    void reportsShortRecordAndSuppressesFieldInspection() {
        assertEquals(
                new Checker.Report(
                        List.of("line 3: record has 1 characters (expected 2)"),
                        1,
                        1),
                check(TWO_NUMERIC_FIELDS, List.of(), List.of(new Records.Line(3, "x"))));
    }

    @Test
    void reportsLongRecordAndSuppressesFieldInspection() {
        assertEquals(
                new Checker.Report(
                        List.of("line 3: record has 3 characters (expected 2)"),
                        1,
                        1),
                check(TWO_NUMERIC_FIELDS, List.of(), List.of(new Records.Line(3, "xy0"))));
    }

    @Test
    void reportsNonPrintableAsciiBeforeRecordWidth() {
        assertEquals(
                new Checker.Report(
                        List.of("line 3: column 1 is not a printable ASCII character"),
                        1,
                        1),
                check(TWO_NUMERIC_FIELDS, List.of(), List.of(new Records.Line(3, "\t"))));
    }

    @Test
    void reportsNonAsciiCharacterAtItsColumn() {
        assertEquals(
                new Checker.Report(
                        List.of("line 3: column 2 is not a printable ASCII character"),
                        1,
                        1),
                check(TWO_NUMERIC_FIELDS, List.of(), List.of(new Records.Line(3, "1\u00E9"))));
    }

    @Test
    void checksOccurrencesButIgnoresTheirGroupAndFiller() {
        assertEquals(
                new Checker.Report(
                        List.of(
                                "line 1: G.A(1) (columns 1-1): invalid value \"x\"",
                                "line 1: G.A(2) (columns 2-2): invalid value \"y\""),
                        1,
                        1),
                check(
                        "01 R. 05 G. 10 A PIC 9 OCCURS 2. 05 FILLER PIC 9.",
                        List.of(),
                        List.of(new Records.Line(1, "xyZ"))));
    }

    @Test
    void checksTheDefaultBaseView() {
        assertEquals(
                new Checker.Report(
                        List.of("line 1: A (columns 1-1): invalid value \"x\""),
                        1,
                        1),
                check(
                        "01 R. 05 A PIC 9. 05 T REDEFINES A PIC X.",
                        List.of(),
                        List.of(new Records.Line(1, "x"))));
    }

    @Test
    void checksOnlyTheSelectedAlternateView() {
        assertEquals(
                new Checker.Report(List.of(), 1, 0),
                check(
                        "01 R. 05 A PIC 9. 05 T REDEFINES A PIC X.",
                        List.of("t"),
                        List.of(new Records.Line(1, "x"))));
    }

    private static Checker.Report check(
            String layoutText, List<String> views, List<Records.Line> lines) {
        Layout layout = Layout.build(layoutText);
        return Checker.check(layout, Selection.select(layout, views), lines);
    }
}
