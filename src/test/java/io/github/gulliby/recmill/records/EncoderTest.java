package io.github.gulliby.recmill.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;
import java.util.List;
import org.junit.jupiter.api.Test;

class EncoderTest {
    private static final Layout LAYOUT = Layout.build("01 R. 05 A PIC X(3). 05 N PIC 9(2).");
    private static final List<Slot> SLOTS = Selection.select(LAYOUT, List.of());
    private static final Layout DIAGNOSTIC_LAYOUT =
            Layout.build("01 R. 05 ID PIC 9(4). 05 NAME PIC X(6).");
    private static final List<Slot> DIAGNOSTIC_SLOTS =
            Selection.select(DIAGNOSTIC_LAYOUT, List.of());

    @Test
    void separatesAssignmentBlocksAndResetsOmittedFields() {
        assertEquals(
                List.of("X  07", "Y  00"),
                Encoder.encode(LAYOUT, SLOTS, List.of("A=X", "N=7", "", "A=Y")));
    }

    @Test
    void ignoresLeadingTrailingAndRepeatedEmptySeparators() {
        assertEquals(
                List.of("X  00", "Y  00"),
                Encoder.encode(LAYOUT, SLOTS, List.of("", "A=X", "", "", "A=Y", "")));
    }

    @Test
    void splitsAssignmentAtOnlyTheFirstEqualsSign() {
        assertEquals(
                List.of("a=b00"),
                Encoder.encode(LAYOUT, SLOTS, List.of("A=a=b")));
    }

    @Test
    void acceptsAnEmptyAlphanumericValue() {
        assertEquals(
                List.of("   00"),
                Encoder.encode(LAYOUT, SLOTS, List.of("A=")));
    }

    @Test
    void rejectsAnEmptyLineListAsNoInput() {
        assertNoInput(List.of());
    }

    @Test
    void rejectsOneEmptyLineAsNoInput() {
        assertNoInput(List.of(""));
    }

    @Test
    void rejectsMultipleEmptyLinesAsNoInput() {
        assertNoInput(List.of("", ""));
    }

    @Test
    void doesNotTreatAWhitespaceOnlyLineAsASeparator() {
        Failure failure = assertThrows(
                Failure.class,
                () -> Encoder.encode(LAYOUT, SLOTS, List.of(" ")));

        assertEquals("line 1: expected PATH=VALUE", failure.getMessage());
    }

    @Test
    void reportsMissingEqualsAtItsPhysicalLine() {
        assertFailure(List.of("ID=1", "NAME"), "line 2: expected PATH=VALUE");
    }

    @Test
    void reportsUnknownFieldBeforeCheckingItsValue() {
        assertFailure(List.of("ID=1", "AGE=2"), "line 2: unknown field \"AGE\"");
    }

    @Test
    void keepsSelectedPathsCaseSensitive() {
        assertFailure(List.of("id=1"), "line 1: unknown field \"id\"");
    }

    @Test
    void doesNotTrimAssignmentPaths() {
        assertFailure(List.of(" ID=1"), "line 1: unknown field \" ID\"");
    }

    @Test
    void rejectsDuplicateAssignmentInTheSameBlock() {
        assertFailure(List.of("ID=1", "ID=2"), "line 2: duplicate field \"ID\"");
    }

    @Test
    void rejectsNumericValueThatDoesNotFit() {
        assertFailure(List.of("ID=12345"),
                "line 1: invalid value \"12345\" for ID (PIC 9(4))");
    }

    @Test
    void rejectsEmptyNumericValue() {
        assertFailure(List.of("ID="), "line 1: invalid value \"\" for ID (PIC 9(4))");
    }

    @Test
    void countsPhysicalLinesAcrossEmptySeparators() {
        assertFailure(List.of("", "ID=1", "", "NAME"), "line 4: expected PATH=VALUE");
    }

    @Test
    void detectsDuplicateBeforeValidatingTheRepeatedValue() {
        assertFailure(List.of("ID=1", "ID=bad"), "line 2: duplicate field \"ID\"");
    }

    @Test
    void reportsAnEarlierUnknownPathBeforeALaterMissingEquals() {
        assertFailure(List.of("AGE=bad", "NAME"), "line 1: unknown field \"AGE\"");
    }

    @Test
    void reportsAnEarlierInvalidValueBeforeALaterDuplicate() {
        assertFailure(List.of("ID=bad", "ID=2"),
                "line 1: invalid value \"bad\" for ID (PIC 9(4))");
    }

    @Test
    void sanitizesNonAsciiValueInInvalidValueDiagnostic() {
        Failure failure = assertThrows(
                Failure.class,
                () -> Encoder.encode(DIAGNOSTIC_LAYOUT, DIAGNOSTIC_SLOTS, List.of("NAME=\u00e9")));

        assertEquals("line 1: invalid value \"?\" for NAME (PIC X(6))", failure.getMessage());
    }

    @Test
    void sanitizesNonAsciiPathInUnknownFieldDiagnostic() {
        Failure failure = assertThrows(
                Failure.class,
                () -> Encoder.encode(DIAGNOSTIC_LAYOUT, DIAGNOSTIC_SLOTS, List.of("\u00e9=1")));

        assertEquals("line 1: unknown field \"?\"", failure.getMessage());
    }

    // Layout `01 R. 05 A PIC X. 05 FILLER PIC 9(2). 05 FILLER PIC X.`, lines `["A=Z"]`.
    @Test
    void writesNamedAndFillerSlotsUsingTheirDefaultBlanks() {
        Layout layout = Layout.build(
                "01 R. 05 A PIC X. 05 FILLER PIC 9(2). 05 FILLER PIC X.");
        List<Slot> slots = Selection.select(layout, List.of());

        assertEquals(List.of("Z00 "), Encoder.encode(layout, slots, List.of("A=Z")));
    }

    // Same layout, `["FILLER=1"]`.
    @Test
    void doesNotMakeFillerSlotsAssignable() {
        Layout layout = Layout.build(
                "01 R. 05 A PIC X. 05 FILLER PIC 9(2). 05 FILLER PIC X.");
        List<Slot> slots = Selection.select(layout, List.of());

        Failure failure = assertThrows(
                Failure.class, () -> Encoder.encode(layout, slots, List.of("FILLER=1")));

        assertEquals("line 1: unknown field \"FILLER\"", failure.getMessage());
    }

    // Layout `01 R. 05 G OCCURS 2. 10 A PIC X OCCURS 2.`, lines `["G(1).A(2)=B","G(2).A(1)=C"]`.
    @Test
    void writesNestedOccurrencesAtTheirOwnColumns() {
        Layout layout = Layout.build("01 R. 05 G OCCURS 2. 10 A PIC X OCCURS 2.");
        List<Slot> slots = Selection.select(layout, List.of());

        assertEquals(
                List.of(" BC "),
                Encoder.encode(layout, slots, List.of("G(1).A(2)=B", "G(2).A(1)=C")));
    }

    // Same layout, `["G(1)=X"]`.
    @Test
    void doesNotAllowGroupPathsAsAssignments() {
        Layout layout = Layout.build("01 R. 05 G OCCURS 2. 10 A PIC X OCCURS 2.");
        List<Slot> slots = Selection.select(layout, List.of());

        Failure failure = assertThrows(
                Failure.class, () -> Encoder.encode(layout, slots, List.of("G(1)=X")));

        assertEquals("line 1: unknown field \"G(1)\"", failure.getMessage());
    }

    // Layout `01 R. 05 BASE PIC X(3). 05 ALT REDEFINES BASE PIC 9. 05 TAIL PIC X.`, view `ALT`, lines `["TAIL=Z"]`.
    @Test
    void leavesColumnsUncoveredByAShorterSelectedViewAsSpaces() {
        Layout layout = Layout.build(
                "01 R. 05 BASE PIC X(3). 05 ALT REDEFINES BASE PIC 9. 05 TAIL PIC X.");
        List<Slot> slots = Selection.select(layout, List.of("ALT"));

        assertEquals(List.of("0  Z"), Encoder.encode(layout, slots, List.of("TAIL=Z")));
    }

    // Same layout and view, `["BASE=X"]`.
    @Test
    void doesNotAllowInactiveFieldsAsAssignments() {
        Layout layout = Layout.build(
                "01 R. 05 BASE PIC X(3). 05 ALT REDEFINES BASE PIC 9. 05 TAIL PIC X.");
        List<Slot> slots = Selection.select(layout, List.of("ALT"));

        Failure failure = assertThrows(
                Failure.class, () -> Encoder.encode(layout, slots, List.of("BASE=X")));

        assertEquals("line 1: unknown field \"BASE\"", failure.getMessage());
    }

    private static void assertFailure(List<String> lines, String expectedMessage) {
        Failure failure = assertThrows(
                Failure.class,
                () -> Encoder.encode(DIAGNOSTIC_LAYOUT, DIAGNOSTIC_SLOTS, lines));

        assertEquals(expectedMessage, failure.getMessage());
    }

    private static void assertNoInput(List<String> lines) {
        Failure failure = assertThrows(
                Failure.class,
                () -> Encoder.encode(LAYOUT, SLOTS, lines));

        assertEquals("<stdin>: no input", failure.getMessage());
    }
}
