package io.github.gulliby.recmill.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileCommandTest {
    private static final String BASIC_LAYOUT = "01 R. 05 N PIC 9.";
    private static final String OCCURS_LAYOUT =
            "01 R. 05 G. 10 N PIC 9 OCCURS 2. 05 T REDEFINES G PIC X(2). 05 FILLER PIC X.";
    private static final String SAMPLE_LAYOUT =
            "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99.";

    @Test
    void exposesExactUsage() {
        assertEquals("recmill profile LAYOUT [NAME...]", ProfileCommand.USAGE);
    }

    @Test
    void reportsDefaultViewFieldsInLayoutOrderAndExcludesGroupsAndFillers() {
        MemoryIo io = new MemoryIo("12!".getBytes(StandardCharsets.UTF_8));

        int status = ProfileCommand.run(List.of(OCCURS_LAYOUT), io);

        assertEquals(0, status);
        assertEquals(List.of(
                "field   pic   blank  bad  distinct  min  max  sum\n"
                        + "G.N(1)  9(1)  0      0    1         1    1    1\n"
                        + "G.N(2)  9(1)  0      0    1         2    2    2\n"
                        + "records     1\n"
                        + "skipped     0\n"),
                io.outputs);
        assertEquals(List.of(), io.errors);
        assertEquals(1, io.readCalls);
        assertEquals(1, io.outputs.size());
        assertEquals(0, io.errors.size());
        assertAsciiLf(io.outputs.get(0));
    }

    @Test
    void reportsSelectedRedefiningViewOnly() {
        MemoryIo io = new MemoryIo("12!".getBytes(StandardCharsets.UTF_8));

        int status = ProfileCommand.run(List.of(OCCURS_LAYOUT, "t"), io);

        assertEquals(0, status);
        assertEquals(List.of(
                "field  pic   blank  bad  distinct  min  max  sum\n"
                        + "T      X(2)  0      0    1         12   12   -\n"
                        + "records     1\n"
                        + "skipped     0\n"),
                io.outputs);
        assertEquals(List.of(), io.errors);
        assertEquals(1, io.readCalls);
        assertAsciiLf(io.outputs.get(0));
    }

    @Test
    void writesTheExactExampleReportOnce() {
        MemoryIo io = new MemoryIo(("0001ALICE 0015{\n"
                + "0002BOB   0120}\n"
                + "0003ALICE 00000\n"
                + "0004\n").getBytes(StandardCharsets.UTF_8));

        int status = ProfileCommand.run(List.of(SAMPLE_LAYOUT), io);

        assertEquals(0, status);
        assertEquals(List.of(
                "field  pic         blank  bad  distinct  min     max   sum\n"
                        + "ID     9(4)        0      0    3         1       3     6\n"
                        + "NAME   X(6)        0      0    2         ALICE   BOB   -\n"
                        + "AMT    S9(3)V9(2)  0      0    3         -12.00  1.50  -10.50\n"
                        + "records     4\n"
                        + "skipped     1\n"),
                io.outputs);
        assertEquals(List.of(), io.errors);
        assertEquals(1, io.readCalls);
        assertAsciiLf(io.outputs.get(0));
    }

    @Test
    void rejectsMissingLayoutBeforeReadingInput() {
        assertFailure(List.of(), new byte[0], false,
                "usage: recmill profile LAYOUT [NAME...]", 0);
    }

    @Test
    void rejectsHelpAsAnUnknownOptionBeforeCheckingArity() {
        assertFailure(List.of("--help"), new byte[0], false,
                "unknown option \"--help\"", 0);
    }

    @Test
    void parsesOptionsBeforeCheckingForTheLayoutOperand() {
        assertFailure(List.of("", "--bad"), new byte[0], false,
                "unknown option \"--bad\"", 0);
    }

    @Test
    void rejectsAnEmptyLayoutBeforeReadingInput() {
        assertFailure(List.of(""), new byte[0], false,
                "layout is empty", 0);
    }

    @Test
    void rejectsAnUnknownViewBeforeReadingInput() {
        assertFailure(List.of(BASIC_LAYOUT, "NOPE"), new byte[0], false,
                "\"NOPE\" is not a redefining entry", 0);
    }

    @Test
    void rejectsLayoutsWithoutNamedFieldsBeforeReadingInput() {
        assertFailure(List.of("01 R. 05 FILLER PIC X."), new byte[0], false,
                "no named field is selected", 0);
    }

    @Test
    void rejectsInputContainingOnlyEmptyLines() {
        assertFailure(List.of(BASIC_LAYOUT), "\n\n".getBytes(StandardCharsets.UTF_8), false,
                "<stdin>: no input", 1);
    }

    @Test
    void translatesReadFailureWithoutOutput() {
        assertFailure(List.of(BASIC_LAYOUT), new byte[] {'1'}, true,
                "<stdin>: cannot read the input", 1);
    }

    @Test
    void normalizesBomCrLfAndEmptyLinesThroughTheSharedRecordReader() {
        MemoryIo io = new MemoryIo(new byte[] {
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '1', '\r', '\n',
                '\r', '\n', '2'
        });

        int status = ProfileCommand.run(List.of(BASIC_LAYOUT), io);

        assertEquals(0, status);
        assertEquals(List.of(
                "field  pic   blank  bad  distinct  min  max  sum\n"
                        + "N      9(1)  0      0    2         1    2    3\n"
                        + "records     2\n"
                        + "skipped     0\n"),
                io.outputs);
        assertEquals(List.of(), io.errors);
        assertEquals(1, io.readCalls);
        assertAsciiLf(io.outputs.get(0));
    }

    @Test
    void reportsUnusableNumericInputAndSkipsOnlyInvalidRecords() {
        MemoryIo io = new MemoryIo("x\n12\n".getBytes(StandardCharsets.UTF_8));

        int status = ProfileCommand.run(List.of(BASIC_LAYOUT), io);

        assertEquals(0, status);
        assertEquals(List.of(
                "field  pic   blank  bad  distinct  min  max  sum\n"
                        + "N      9(1)  0      1    0         -    -    -\n"
                        + "records     2\n"
                        + "skipped     1\n"),
                io.outputs);
        assertEquals(List.of(), io.errors);
        assertEquals(1, io.readCalls);
        assertAsciiLf(io.outputs.get(0));
    }

    private static void assertFailure(
            List<String> args, byte[] input, boolean readFailure, String message, int expectedReadCalls) {
        MemoryIo io = new MemoryIo(input);
        io.readFailure = readFailure;

        Failure failure = assertThrows(Failure.class, () -> ProfileCommand.run(args, io));

        assertEquals(message, failure.getMessage());
        assertEquals(expectedReadCalls, io.readCalls);
        assertEquals(List.of(), io.outputs);
        assertEquals(List.of(), io.errors);
    }

    private static void assertAsciiLf(String report) {
        assertTrue(report.chars().allMatch(character -> character <= 0x7F));
        assertFalse(report.contains("\r"));
        assertTrue(report.endsWith("\n"));
    }

    private static final class MemoryIo implements Io {
        private final byte[] input;
        private final List<String> outputs = new java.util.ArrayList<>();
        private final List<String> errors = new java.util.ArrayList<>();
        private int readCalls;
        private boolean readFailure;

        private MemoryIo(byte[] input) {
            this.input = input;
        }

        @Override
        public byte[] readStdin() throws IOException {
            readCalls++;
            if (readFailure) {
                throw new IOException("read failed");
            }
            return input;
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
