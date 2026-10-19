package io.github.gulliby.recmill.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.records.Differ;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffCommandTest {
    private static final String A =
            "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99.";
    private static final String B =
            "01 REC. 05 KIND PIC X. 05 BODY. 10 CODE PIC X(3). 10 QTY PIC 9(2) OCCURS 3 TIMES. 05 TEXT REDEFINES BODY PIC X(8). 05 TAIL PIC X(2).";
    private static final String D =
            "01 ORDER. 05 ID PIC 9(5). 05 NAME PIC X(6). 05 NOTE PIC X(4). 05 FILLER PIC X(2).";

    @Test
    void reportsRemovalsChangesAndAdditionsInOneCompleteOutput() {
        List<String> lines = List.of(
                "- AMT from 11 length 5 pic S9(3)V9(2)",
                "~ ID length 4 -> 5, pic 9(4) -> 9(5)",
                "~ NAME from 5 -> 6",
                "+ NOTE from 12 length 4 pic X(4)");
        Differ.Diff difference = Differ.diff(Layout.build(A), Layout.build(D));
        assertEquals(lines, difference.lines());
        assertEquals(1, difference.removed());
        assertEquals(2, difference.changed());
        assertEquals(1, difference.added());
        assertEquals(0, difference.same());
        assertEquals("15 -> 17", difference.record());

        TestIo io = new TestIo();

        int exit = DiffCommand.run(List.of(A, D), io);

        String expectedSummary = "removed     1\nchanged     2\nadded       1\nsame        0\nrecord      15 -> 17\n";
        String expected = "- AMT from 11 length 5 pic S9(3)V9(2)\n"
                + "~ ID length 4 -> 5, pic 9(4) -> 9(5)\n"
                + "~ NAME from 5 -> 6\n"
                + "+ NOTE from 12 length 4 pic X(4)\n"
                + expectedSummary;

        assertEquals("recmill diff LAYOUT1 LAYOUT2", DiffCommand.USAGE);
        assertEquals(1, exit);
        assertSuccessfulOutput(io, expected);
    }

    @Test
    void reportsUsageWhenSecondLayoutIsMissingWithoutIo() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> DiffCommand.run(List.of(A), io));

        assertEquals("usage: recmill diff LAYOUT1 LAYOUT2", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void rejectsUnsupportedOptionBeforeCheckingArityOrBuildingLayouts() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> DiffCommand.run(List.of("--groups"), io));

        assertEquals("unknown option \"--groups\"", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void reportsLayoutAndFindUsageWhenArgumentsAreMissing() {
        TestIo layoutIo = new TestIo();
        Failure layoutFailure = assertThrows(Failure.class, () -> LayoutCommand.run(List.of(), layoutIo));
        assertEquals("usage: recmill layout [--groups] LAYOUT", layoutFailure.getMessage());
        assertNoIo(layoutIo);

        TestIo findIo = new TestIo();
        Failure findFailure = assertThrows(Failure.class, () -> FindCommand.run(List.of(A), findIo));
        assertEquals("usage: recmill find LAYOUT COLUMN", findFailure.getMessage());
        assertNoIo(findIo);
    }

    @Test
    void rejectsUnsupportedOptionsBeforeArityOrLayoutValidation() {
        TestIo diffIo = new TestIo();
        Failure diffFailure = assertThrows(Failure.class,
                () -> DiffCommand.run(List.of("--groups"), diffIo));
        assertEquals("unknown option \"--groups\"", diffFailure.getMessage());
        assertNoIo(diffIo);

        TestIo layoutValueIo = new TestIo();
        Failure layoutValueFailure = assertThrows(Failure.class,
                () -> LayoutCommand.run(List.of("", "--groups=1"), layoutValueIo));
        assertEquals("--groups takes no value", layoutValueFailure.getMessage());
        assertNoIo(layoutValueIo);

        TestIo helpIo = new TestIo();
        Failure helpFailure = assertThrows(Failure.class,
                () -> LayoutCommand.run(List.of("--help"), helpIo));
        assertEquals("unknown option \"--help\"", helpFailure.getMessage());
        assertNoIo(helpIo);

        TestIo findIo = new TestIo();
        Failure findFailure = assertThrows(Failure.class, () -> FindCommand.run(List.of("--groups"), findIo));
        assertEquals("unknown option \"--groups\"", findFailure.getMessage());
        assertNoIo(findIo);

        TestIo validLayoutIo = new TestIo();
        Failure validLayoutFailure = assertThrows(Failure.class,
                () -> DiffCommand.run(List.of("--groups", B), validLayoutIo));
        assertEquals("unknown option \"--groups\"", validLayoutFailure.getMessage());
        assertNoIo(validLayoutIo);
    }

    @Test
    void rejectsFindColumnsOutsideTheOneBasedLayoutLengthAndInvalidSpelling() {
        TestIo tooLargeIo = new TestIo();
        Failure tooLarge = assertThrows(Failure.class, () -> FindCommand.run(List.of(A, "99"), tooLargeIo));
        assertEquals("invalid column \"99\" (expected an integer from 1 to 15)", tooLarge.getMessage());
        assertNoIo(tooLargeIo);

        TestIo zeroIo = new TestIo();
        Failure zero = assertThrows(Failure.class, () -> FindCommand.run(List.of(A, "0"), zeroIo));
        assertEquals("invalid column \"0\" (expected an integer from 1 to 15)", zero.getMessage());
        assertNoIo(zeroIo);

        TestIo leadingZeroIo = new TestIo();
        Failure leadingZero = assertThrows(Failure.class, () -> FindCommand.run(List.of(A, "01"), leadingZeroIo));
        assertEquals("invalid column \"01\" (expected an integer from 1 to 15)", leadingZero.getMessage());
        assertNoIo(leadingZeroIo);
    }

    @Test
    void validatesLayoutBeforeFindColumnAndRejectsAnEmptyLayout() {
        TestIo findIo = new TestIo();
        Failure findFailure = assertThrows(Failure.class, () -> FindCommand.run(List.of("", "0"), findIo));
        assertEquals("layout is empty", findFailure.getMessage());
        assertNoIo(findIo);

        TestIo layoutIo = new TestIo();
        Failure layoutFailure = assertThrows(Failure.class, () -> LayoutCommand.run(List.of(""), layoutIo));
        assertEquals("layout is empty", layoutFailure.getMessage());
        assertNoIo(layoutIo);
    }

    @Test
    void acceptsRepeatedGroupsFlagsWithOneCompleteLayoutReport() {
        TestIo io = new TestIo();

        int exit = LayoutCommand.run(List.of("01 R. 05 A PIC X.", "--groups", "--groups"), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "path  from  to  pic\n"
                + "A     1     1   X(1)\n"
                + "length      1\n");

        TestIo singleFlagIo = new TestIo();
        int singleFlagExit = LayoutCommand.run(List.of("01 R. 05 A PIC X.", "--groups"), singleFlagIo);

        assertEquals(0, singleFlagExit);
        assertEquals(singleFlagIo.outputs, io.outputs);
        assertEquals(singleFlagIo.errors, io.errors);
        assertEquals(singleFlagIo.stdinReads, io.stdinReads);
    }

    @Test
    void comparesCanonicalPicturesAndExcludesTheRootName() {
        // AC-006: `"01 R. 05 A PIC XX."` versus `"01 S. 05 A PIC X(2)."`.
        assertComparison(
                "01 R. 05 A PIC XX.",
                "01 S. 05 A PIC X(2).",
                List.of(), 0, 0, 0, 1, "2", 0,
                "removed     0\nchanged     0\nadded       0\nsame        1\nrecord      2\n");
    }

    @Test
    void reportsRecordLengthOnlyDifferencesAsExitOne() {
        // AC-006: `"01 R. 05 A PIC X."` versus `"01 R. 05 A PIC X. 05 FILLER PIC X."`.
        assertComparison(
                "01 R. 05 A PIC X.",
                "01 R. 05 A PIC X. 05 FILLER PIC X.",
                List.of(), 0, 0, 0, 1, "1 -> 2", 1,
                "removed     0\nchanged     0\nadded       0\nsame        1\nrecord      1 -> 2\n");
    }

    @Test
    void allowsFillerOnlyLayoutsWithoutNamedFields() {
        // AC-006: `"01 R. 05 FILLER PIC X."` versus `"01 R. 05 FILLER PIC X."`.
        assertComparison(
                "01 R. 05 FILLER PIC X.",
                "01 R. 05 FILLER PIC X.",
                List.of(), 0, 0, 0, 0, "1", 0,
                "removed     0\nchanged     0\nadded       0\nsame        0\nrecord      1\n");
    }

    @Test
    void comparesInactiveRedefinersAlongsideTheirBase() {
        // AC-006: `"01 R. 05 A PIC X(2). 05 B REDEFINES A PIC XX."` versus `"01 R. 05 A PIC X(2). 05 B REDEFINES A PIC 99."`.
        assertComparison(
                "01 R. 05 A PIC X(2). 05 B REDEFINES A PIC XX.",
                "01 R. 05 A PIC X(2). 05 B REDEFINES A PIC 99.",
                List.of("~ B pic X(2) -> 9(2)"), 0, 1, 0, 1, "2", 1,
                "~ B pic X(2) -> 9(2)\nremoved     0\nchanged     1\nadded       0\nsame        1\nrecord      2\n");
    }

    @Test
    void reportsChangedStartLengthAndPictureInThatOrder() {
        // AC-006: `"01 R. 05 FILLER PIC X. 05 A PIC X."` versus `"01 R. 05 FILLER PIC XX. 05 A PIC 99."`.
        assertComparison(
                "01 R. 05 FILLER PIC X. 05 A PIC X.",
                "01 R. 05 FILLER PIC XX. 05 A PIC 99.",
                List.of("~ A from 2 -> 3, length 1 -> 2, pic X(1) -> 9(2)"),
                0, 1, 0, 0, "2 -> 4", 1,
                "~ A from 2 -> 3, length 1 -> 2, pic X(1) -> 9(2)\n"
                        + "removed     0\nchanged     1\nadded       0\nsame        0\nrecord      2 -> 4\n");
    }

    @Test
    void prefixesOnlyTheFirstLayoutBuildFailure() {
        // AC-008 input: `["", "01 R. 05 A."]`.
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class,
                () -> DiffCommand.run(List.of("", "01 R. 05 A."), io));

        assertEquals("LAYOUT1: layout is empty", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void prefixesTheSecondLayoutBuildFailure() {
        // AC-008 input: `[A, "01 R. 05 A."]`.
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class,
                () -> DiffCommand.run(List.of(A, "01 R. 05 A."), io));

        assertEquals("LAYOUT2: entry 2: \"A\" needs PIC", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void checksArityBeforeBuildingEitherLayout() {
        // AC-008 input: `["", "", "extra"]`.
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class,
                () -> DiffCommand.run(List.of("", "", "extra"), io));

        assertEquals("usage: recmill diff LAYOUT1 LAYOUT2", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void checksOptionsBeforeArityOrBuildingEitherLayout() {
        // AC-008 input: `["", "", "--bad"]`.
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class,
                () -> DiffCommand.run(List.of("", "", "--bad"), io));

        assertEquals("unknown option \"--bad\"", failure.getMessage());
        assertNoIo(io);
    }

    private static void assertComparison(String firstLayout, String secondLayout,
                                         List<String> expectedLines, int expectedRemoved,
                                         int expectedChanged, int expectedAdded, int expectedSame,
                                         String expectedRecord, int expectedExit, String expectedReport) {
        Differ.Diff difference = Differ.diff(Layout.build(firstLayout), Layout.build(secondLayout));
        assertEquals(expectedLines, difference.lines());
        assertEquals(expectedRemoved, difference.removed());
        assertEquals(expectedChanged, difference.changed());
        assertEquals(expectedAdded, difference.added());
        assertEquals(expectedSame, difference.same());
        assertEquals(expectedRecord, difference.record());

        TestIo io = new TestIo();
        int exit = DiffCommand.run(List.of(firstLayout, secondLayout), io);

        assertEquals(expectedExit, exit);
        assertSuccessfulOutput(io, expectedReport);
    }

    private static void assertSuccessfulOutput(TestIo io, String expected) {
        assertEquals(List.of(expected), io.outputs);
        assertEquals(1, io.outputs.size());
        assertEquals(List.of(), io.errors);
        assertEquals(0, io.stdinReads);
        assertTrue(expected.endsWith("\n"));
        assertFalse(expected.endsWith("\r\n"));
        assertTrue(expected.chars().allMatch(character -> character <= 0x7f));
    }

    private static void assertNoIo(TestIo io) {
        assertEquals(List.of(), io.outputs);
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
