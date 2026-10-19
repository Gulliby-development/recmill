package io.github.gulliby.recmill.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FindCommandTest {
    private static final String B =
            "01 REC. 05 KIND PIC X. 05 BODY. 10 CODE PIC X(3). 10 QTY PIC 9(2) OCCURS 3 TIMES. 05 TEXT REDEFINES BODY PIC X(8). 05 TAIL PIC X(2).";
    private static final String F = "01 R. 05 FILLER PIC X(2). 05 A PIC 9.";
    private static final String D = "01 ORDER. 05 ID PIC 9(5). 05 NAME PIC X(6). 05 NOTE PIC X(4). 05 FILLER PIC X(2).";

    @Test
    void findsAllOverlappingViewsAtAnInclusiveColumn() {
        TestIo io = new TestIo();

        int exit = FindCommand.run(List.of(B, "7"), io);

        assertEquals("recmill find LAYOUT COLUMN", FindCommand.USAGE);
        assertEquals(0, exit);
        assertSuccessfulOutput(io, "column      7\nfield       BODY.QTY(2) 7-8 9(2)\nfield       TEXT 2-9 X(8)\n");
    }

    @Test
    void includesFillerAtItsFirstColumn() {
        TestIo io = new TestIo();

        int exit = FindCommand.run(List.of(F, "1"), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "column      1\n"
                + "field       FILLER 1-2 X(2)\n");
    }

    @Test
    void includesFillerAtItsLastColumn() {
        TestIo io = new TestIo();

        int exit = FindCommand.run(List.of(F, "2"), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "column      2\n"
                + "field       FILLER 1-2 X(2)\n");
    }

    @Test
    void findsFieldAtFinalRecordColumn() {
        TestIo io = new TestIo();

        int exit = FindCommand.run(List.of(F, "3"), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "column      3\nfield       A 3-3 9(1)\n");

        TestIo comparisonIo = new TestIo();
        int comparisonExit = FindCommand.run(List.of(D, "12"), comparisonIo);
        assertEquals(0, comparisonExit);
        assertSuccessfulOutput(comparisonIo, "column      12\nfield       NOTE 12-15 X(4)\n");
    }

    @Test
    void excludesShorterOverlayAtTheBaseEndColumn() {
        TestIo io = new TestIo();

        int exit = FindCommand.run(List.of(B, "10"), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "column      10\nfield       BODY.QTY(3) 9-10 9(2)\n");
    }

    @Test
    void reportsUsageWhenColumnIsMissingWithoutIo() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> FindCommand.run(List.of(
                "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99."), io));

        assertEquals("usage: recmill find LAYOUT COLUMN", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void rejectsGroupsOptionBeforeCheckingArity() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> FindCommand.run(List.of("--groups"), io));

        assertEquals("unknown option \"--groups\"", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void rejectsColumnAboveLayoutLength() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> FindCommand.run(List.of(
                "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99.", "99"), io));

        assertEquals("invalid column \"99\" (expected an integer from 1 to 15)", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void rejectsZeroColumnBelowOneBasedRange() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> FindCommand.run(List.of(
                "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99.", "0"), io));

        assertEquals("invalid column \"0\" (expected an integer from 1 to 15)", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void rejectsLeadingZeroColumnSpelling() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> FindCommand.run(List.of(
                "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99.", "01"), io));

        assertEquals("invalid column \"01\" (expected an integer from 1 to 15)", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void validatesLayoutBeforeInvalidColumn() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> FindCommand.run(List.of("", "0"), io));

        assertEquals("layout is empty", failure.getMessage());
        assertNoIo(io);
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
