package io.github.gulliby.recmill.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayoutCommandTest {
    private static final String A =
            "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99.";
    private static final String B = "01 REC. 05 KIND PIC X. 05 BODY. 10 CODE PIC X(3). 10 QTY PIC 9(2) OCCURS 3 TIMES. 05 TEXT REDEFINES BODY PIC X(8). 05 TAIL PIC X(2).";

    @Test
    void exposesExactUsage() {
        assertEquals("recmill layout [--groups] LAYOUT", LayoutCommand.USAGE);
    }

    @Test
    void listsOrdinaryLayoutFieldsInTableOrder() {
        TestIo io = new TestIo();

        int exit = LayoutCommand.run(List.of(A), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "path  from  to  pic\nID    1     4   9(4)\nNAME  5     10  X(6)\nAMT   11    15  S9(3)V9(2)\nlength      15\n");
    }

    @Test
    void listsGroupsOverlaysAndEveryOccurrenceWhenRequested() {
        TestIo io = new TestIo();

        int exit = LayoutCommand.run(List.of("--groups", B), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "path         from  to  pic\nKIND         1     1   X(1)\nBODY         2     10  -\nBODY.CODE    2     4   X(3)\nBODY.QTY(1)  5     6   9(2)\nBODY.QTY(2)  7     8   9(2)\nBODY.QTY(3)  9     10  9(2)\nTEXT         2     9   X(8)\nTAIL         11    12  X(2)\nlength      12\n");
    }

    @Test
    void omitsGroupsByDefaultButIncludesAllViewsAndFillers() {
        TestIo io = new TestIo();

        int exit = LayoutCommand.run(List.of(B), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "path         from  to  pic\nKIND         1     1   X(1)\nBODY.CODE    2     4   X(3)\nBODY.QTY(1)  5     6   9(2)\nBODY.QTY(2)  7     8   9(2)\nBODY.QTY(3)  9     10  9(2)\nTEXT         2     9   X(8)\nTAIL         11    12  X(2)\nlength      12\n");
    }

    @Test
    void listsFillerOnlyLayoutWithoutRequiringNamedFields() {
        TestIo io = new TestIo();

        int exit = LayoutCommand.run(List.of("01 R. 05 FILLER PIC X."), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "path    from  to  pic\nFILLER  1     1   X(1)\nlength      1\n");
    }

    @Test
    void listsTheComparisonLayoutIncludingItsFiller() {
        TestIo io = new TestIo();

        int exit = LayoutCommand.run(List.of("01 ORDER. 05 ID PIC 9(5). 05 NAME PIC X(6). 05 NOTE PIC X(4). 05 FILLER PIC X(2)."), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "path    from  to  pic\nID      1     5   9(5)\nNAME    6     11  X(6)\nNOTE    12    15  X(4)\nFILLER  16    17  X(2)\nlength      17\n");
    }

    @Test
    void reportsUsageForMissingLayoutOperandWithoutIo() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> LayoutCommand.run(List.of(), io));

        assertEquals("usage: recmill layout [--groups] LAYOUT", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void rejectsFlagValuesBeforeCheckingLayoutOperand() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class,
                () -> LayoutCommand.run(List.of("", "--groups=1"), io));

        assertEquals("--groups takes no value", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void rejectsHelpAsAnUnknownOptionBeforeCheckingLayoutOperand() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> LayoutCommand.run(List.of("--help"), io));

        assertEquals("unknown option \"--help\"", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void reportsEmptyLayoutWithoutIo() {
        TestIo io = new TestIo();

        Failure failure = assertThrows(Failure.class, () -> LayoutCommand.run(List.of(""), io));

        assertEquals("layout is empty", failure.getMessage());
        assertNoIo(io);
    }

    @Test
    void acceptsRepeatedGroupsFlagAfterLayout() {
        TestIo io = new TestIo();

        int exit = LayoutCommand.run(List.of("01 R. 05 G. 10 A PIC X.", "--groups", "--groups"), io);

        assertEquals(0, exit);
        assertSuccessfulOutput(io, "path  from  to  pic\n"
                + "G     1     1   -\n"
                + "G.A   1     1   X(1)\n"
                + "length      1\n");
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
