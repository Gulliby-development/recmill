package io.github.gulliby.recmill.records;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.recmill.commands.DiffCommand;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.model.Layout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DifferTest {
    @Test
    void reportsRemovalsChangesAndAdditionsInTheirRequiredOrder() {
        Differ.Diff diff = Differ.diff(
                Layout.build("01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99."),
                Layout.build("01 ORDER. 05 ID PIC 9(5). 05 NAME PIC X(6). 05 NOTE PIC X(4). 05 FILLER PIC X(2)."));

        assertEquals(List.of(
                "- AMT from 11 length 5 pic S9(3)V9(2)",
                "~ ID length 4 -> 5, pic 9(4) -> 9(5)",
                "~ NAME from 5 -> 6",
                "+ NOTE from 12 length 4 pic X(4)"), diff.lines());
        assertEquals(1, diff.removed());
        assertEquals(2, diff.changed());
        assertEquals(1, diff.added());
        assertEquals(0, diff.same());
        assertEquals("15 -> 17", diff.record());
    }

    @Test
    void treatsEquivalentPicturesAsEqualAndExcludesTheRootGroup() {
        Differ.Diff diff = Differ.diff(
                Layout.build("01 R. 05 A PIC XX."),
                Layout.build("01 S. 05 A PIC X(2)."));

        assertEquals(List.of(), diff.lines());
        assertEquals(0, diff.removed());
        assertEquals(0, diff.changed());
        assertEquals(0, diff.added());
        assertEquals(1, diff.same());
        assertEquals("2", diff.record());
    }

    @Test
    void reportsRecordLengthOnlyDifferencesWithoutInventingFieldChanges() {
        Differ.Diff diff = Differ.diff(
                Layout.build("01 R. 05 A PIC X."),
                Layout.build("01 R. 05 A PIC X. 05 FILLER PIC X."));

        assertEquals(List.of(), diff.lines());
        assertEquals(0, diff.removed());
        assertEquals(0, diff.changed());
        assertEquals(0, diff.added());
        assertEquals(1, diff.same());
        assertEquals("1 -> 2", diff.record());
    }

    @Test
    void supportsFillerOnlyLayoutsWithoutNamedFields() {
        Differ.Diff diff = Differ.diff(
                Layout.build("01 R. 05 FILLER PIC X."),
                Layout.build("01 R. 05 FILLER PIC X."));

        assertEquals(List.of(), diff.lines());
        assertEquals(0, diff.removed());
        assertEquals(0, diff.changed());
        assertEquals(0, diff.added());
        assertEquals(0, diff.same());
        assertEquals("1", diff.record());
    }

    @Test
    void comparesInactiveRedefiningViewsByPath() {
        Differ.Diff diff = Differ.diff(
                Layout.build("01 R. 05 A PIC X(2). 05 B REDEFINES A PIC X(2)."),
                Layout.build("01 R. 05 A PIC X(2). 05 B REDEFINES A PIC 99."));

        assertEquals(List.of("~ B pic X(2) -> 9(2)"), diff.lines());
        assertEquals(0, diff.removed());
        assertEquals(1, diff.changed());
        assertEquals(0, diff.added());
        assertEquals(1, diff.same());
        assertEquals("2", diff.record());
    }

    @Test
    void reportsEveryChangedAttributeInStartLengthPictureOrder() {
        Differ.Diff diff = Differ.diff(
                Layout.build("01 R. 05 FILLER PIC X. 05 A PIC X."),
                Layout.build("01 R. 05 FILLER PIC XX. 05 A PIC 99."));

        assertEquals(List.of("~ A from 2 -> 3, length 1 -> 2, pic X(1) -> 9(2)"), diff.lines());
        assertEquals(0, diff.removed());
        assertEquals(1, diff.changed());
        assertEquals(0, diff.added());
        assertEquals(0, diff.same());
        assertEquals("2 -> 4", diff.record());
    }

    @Test
    void preservesLayoutOrderWithinEachDifferenceCategory() {
        Differ.Diff diff = Differ.diff(
                Layout.build("01 R. 05 Z PIC X. 05 Y PIC X. 05 A PIC X. 05 B PIC X."),
                Layout.build("01 R. 05 Q PIC X. 05 P PIC X. 05 B PIC 9. 05 A PIC 9."));

        assertEquals(List.of(
                "- Z from 1 length 1 pic X(1)",
                "- Y from 2 length 1 pic X(1)",
                "~ A from 3 -> 4, pic X(1) -> 9(1)",
                "~ B from 4 -> 3, pic X(1) -> 9(1)",
                "+ Q from 1 length 1 pic X(1)",
                "+ P from 2 length 1 pic X(1)"), diff.lines());
        assertEquals(2, diff.removed());
        assertEquals(2, diff.changed());
        assertEquals(2, diff.added());
        assertEquals(0, diff.same());
        assertEquals("4", diff.record());
    }

    @Test
    void matchesExpandedOccurrencePathsAndExcludesGroups() {
        Differ.Diff diff = Differ.diff(
                Layout.build("01 R. 05 G. 10 A PIC X OCCURS 1."),
                Layout.build("01 R. 05 G. 10 A PIC X OCCURS 2."));

        assertEquals(List.of("+ G.A(2) from 2 length 1 pic X(1)"), diff.lines());
        assertEquals(0, diff.removed());
        assertEquals(0, diff.changed());
        assertEquals(1, diff.added());
        assertEquals(1, diff.same());
        assertEquals("1 -> 2", diff.record());
    }

    @Test
    void commandRendersOrderedCategoriesAndReturnsDifferenceStatus() {
        TestIo io = new TestIo();

        int exit = DiffCommand.run(List.of(
                "01 R. 05 Z PIC X. 05 Y PIC X. 05 A PIC X. 05 B PIC X.",
                "01 R. 05 Q PIC X. 05 P PIC X. 05 B PIC 9. 05 A PIC 9."), io);

        assertEquals(1, exit);
        assertEquals(List.of(
                "- Z from 1 length 1 pic X(1)\n"
                        + "- Y from 2 length 1 pic X(1)\n"
                        + "~ A from 3 -> 4, pic X(1) -> 9(1)\n"
                        + "~ B from 4 -> 3, pic X(1) -> 9(1)\n"
                        + "+ Q from 1 length 1 pic X(1)\n"
                        + "+ P from 2 length 1 pic X(1)\n"
                        + "removed     2\nchanged     2\nadded       2\nsame        0\nrecord      4\n"), io.outputs);
        assertEquals(List.of(), io.errors);
        assertEquals(0, io.stdinReads);
    }

    @Test
    void commandRendersOccurrenceAdditionAndReturnsDifferenceStatus() {
        TestIo io = new TestIo();

        int exit = DiffCommand.run(List.of(
                "01 R. 05 G. 10 A PIC X OCCURS 1.",
                "01 R. 05 G. 10 A PIC X OCCURS 2."), io);

        assertEquals(1, exit);
        assertEquals(List.of(
                "+ G.A(2) from 2 length 1 pic X(1)\n"
                        + "removed     0\nchanged     0\nadded       1\nsame        1\nrecord      1 -> 2\n"),
                io.outputs);
        assertEquals(List.of(), io.errors);
        assertEquals(0, io.stdinReads);
    }

    private static final class TestIo implements Io {
        private final List<String> outputs = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private int stdinReads;

        @Override
        public byte[] readStdin() throws IOException {
            stdinReads++;
            return new byte[0];
        }

        @Override
        public void out(String text) {
            outputs.add(text);
        }

        @Override
        public void err(String text) {
            errors.add(text);
        }
    }
}
