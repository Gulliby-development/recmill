package io.github.gulliby.recmill.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.core.Failure;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionTest {
    private static final String VIEW_LAYOUT =
            "01 R. 05 A PIC X(2). 05 P REDEFINES A PIC X. 05 Q REDEFINES A PIC X(2). 05 FILLER PIC X. 05 Z PIC X.";

    @Test
    void defaultsToBasesAndKeepsFillersInSelectedSlotsOnly() {
        Layout layout = Layout.build(VIEW_LAYOUT);

        assertSelection(layout, List.of(), List.of("A", "FILLER", "Z"), List.of("A", "Z"));
    }

    @Test
    void choosesNamedViewWithoutChangingLayoutOrder() {
        Layout layout = Layout.build(VIEW_LAYOUT);

        assertSelection(layout, List.of("p"), List.of("P", "FILLER", "Z"), List.of("P", "Z"));
    }

    @Test
    void repeatedViewOperandsAreIdempotent() {
        Layout layout = Layout.build(VIEW_LAYOUT);

        assertSelection(layout, List.of("P", "p"), List.of("P", "FILLER", "Z"), List.of("P", "Z"));
    }

    @Test
    void reportsConflictingViewsInLayoutOrderRatherThanOperandOrder() {
        Layout layout = Layout.build(VIEW_LAYOUT);

        assertSelectionFailure(layout, List.of("Q", "P"),
                "\"P\" and \"Q\" redefine the same entry");
    }

    @Test
    void rejectsBaseNamesAsViewOperands() {
        Layout layout = Layout.build(VIEW_LAYOUT);

        assertSelectionFailure(layout, List.of("A"), "\"A\" is not a redefining entry");
    }

    @Test
    void sanitizesUnknownViewOperands() {
        Layout layout = Layout.build(VIEW_LAYOUT);

        assertSelectionFailure(layout, List.of("bad\t"), "\"bad?\" is not a redefining entry");
    }

    @Test
    void validatesViewOperandsInOperandOrder() {
        Layout layout = Layout.build(VIEW_LAYOUT);

        assertSelectionFailure(layout, List.of("missing", "A"),
                "\"missing\" is not a redefining entry");
    }

    @Test
    void selectsEveryMatchingViewNameAcrossRuns() {
        Layout layout = Layout.build(
                "01 R. 05 G. 10 A PIC X. 10 P REDEFINES A PIC X. 05 H REDEFINES G PIC X. 05 J. 10 A PIC X. 10 P REDEFINES A PIC X.");

        assertSelection(layout, List.of("P"),
                List.of("G", "G.P", "J", "J.P"), List.of("G.P", "J.P"));
    }

    @Test
    void chosenOuterViewDeactivatesItsDescendantsButNotOtherBranches() {
        Layout layout = Layout.build(
                "01 R. 05 G. 10 A PIC X. 10 P REDEFINES A PIC X. 05 H REDEFINES G PIC X. 05 J. 10 A PIC X. 10 P REDEFINES A PIC X.");

        assertSelection(layout, List.of("H", "P"),
                List.of("H", "J", "J.P"), List.of("H", "J.P"));
    }

    @Test
    void defaultsToBasesInEachNestedViewRun() {
        Layout layout = Layout.build(
                "01 R. 05 G. 10 A PIC X. 10 P REDEFINES A PIC X. 05 H REDEFINES G PIC X. 05 J. 10 A PIC X. 10 P REDEFINES A PIC X.");

        assertSelection(layout, List.of(),
                List.of("G", "G.A", "J", "J.A"), List.of("G.A", "J.A"));
    }

    @Test
    void rejectsSelectionsContainingOnlyFillerFields() {
        // Case: 01 R. 05 FILLER PIC X. ; names=[]
        Layout layout = Layout.build("01 R. 05 FILLER PIC X.");

        assertSelectionFailure(layout, List.of(), "no named field is selected");
    }

    @Test
    void rejectsSelectionsWhoseChosenViewContainsOnlyGroupsAndFillers() {
        // Case: 01 R. 05 A PIC X. 05 P REDEFINES A. 10 FILLER PIC X. ; names=["P"]
        Layout layout = Layout.build("01 R. 05 A PIC X. 05 P REDEFINES A. 10 FILLER PIC X.");

        assertSelectionFailure(layout, List.of("P"), "no named field is selected");
    }

    @Test
    void selectsViewsUnderEveryOccurrenceOfAnAncestor() {
        // Case: 01 R. 05 G OCCURS 2. 10 A PIC X. 10 P REDEFINES A PIC X. ; names=["p"]
        Layout layout = Layout.build("01 R. 05 G OCCURS 2. 10 A PIC X. 10 P REDEFINES A PIC X.");

        List<Slot> selected = Selection.select(layout, List.of("p"));
        List<Slot> fields = Selection.fields(selected);

        assertEquals(List.of("G(1)", "G(1).P", "G(2)", "G(2).P"), paths(selected));
        assertEquals(List.of("G(1).P", "G(2).P"), paths(fields));
        assertEquals(List.of(1, 2), starts(fields));
    }

    private static void assertSelection(Layout layout, List<String> names,
                                        List<String> expectedSlots, List<String> expectedFields) {
        List<Slot> selected = Selection.select(layout, names);

        assertEquals(expectedSlots, paths(selected));
        assertEquals(expectedFields, paths(Selection.fields(selected)));
    }

    private static void assertSelectionFailure(Layout layout, List<String> names, String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> Selection.select(layout, names));

        assertEquals(expectedMessage, failure.getMessage());
    }

    private static List<String> paths(List<Slot> slots) {
        return slots.stream().map(Slot::path).toList();
    }

    private static List<Integer> starts(List<Slot> slots) {
        return slots.stream().map(Slot::start).toList();
    }
}
