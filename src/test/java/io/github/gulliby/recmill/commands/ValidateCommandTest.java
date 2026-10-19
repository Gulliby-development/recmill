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

class ValidateCommandTest {
    private static final String TWENTY_ONE_OCCURRENCES = "01 R. 05 A PIC 9 OCCURS 21.";

    @Test
    void exposesTheExactUsage() {
        assertEquals(
                "recmill validate [--max MAX] LAYOUT [NAME...]",
                ValidateCommand.USAGE);
    }

    // AC-004 fixtures: \`[LAYOUT]\`, \`12\n\`; \`[LAYOUT]\`, \`\uFEFF12\r\n\r\nxy\`.
    @Test
    void printsTheSummaryForAValidRecordInOneWrite() {
        MemoryIo io = new MemoryIo("12\n".getBytes(StandardCharsets.UTF_8));

        int exitCode = ValidateCommand.run(List.of("01 R. 05 A PIC 9. 05 B PIC 9."), io);

        assertEquals(0, exitCode);
        assertSingleReport(
                io,
                "records     1\ninvalid     0\nproblems    0\n");
    }

    @Test
    void capsPrintedProblemsButKeepsCompleteTotals() {
        MemoryIo io = new MemoryIo("xy\n1z\n12\n".getBytes(StandardCharsets.UTF_8));

        int exitCode = ValidateCommand.run(
                List.of("--max=1", "01 R. 05 A PIC 9. 05 B PIC 9."), io);

        assertEquals(1, exitCode);
        assertSingleReport(
                io,
                "line 1: A (columns 1-1): invalid value \"x\"\nrecords     3\ninvalid     2\nproblems    3\n");
    }

    @Test
    void normalizesBomCrLfAndInteriorEmptyLinesAndPreservesPhysicalNumbers() {
        MemoryIo io =
                new MemoryIo(
                        "\uFEFF12\r\n\r\nxy".getBytes(StandardCharsets.UTF_8));

        int exitCode = ValidateCommand.run(List.of("01 R. 05 A PIC 9. 05 B PIC 9."), io);

        assertEquals(1, exitCode);
        assertSingleReport(
                io,
                "line 3: A (columns 1-1): invalid value \"x\"\nline 3: B (columns 2-2): invalid value \"y\"\nrecords     2\ninvalid     1\nproblems    2\n");
    }

    @Test
    void defaultLimitPrintsTwentyOfTwentyOneProblems() {
        MemoryIo io =
                new MemoryIo(
                        "xxxxxxxxxxxxxxxxxxxxx\n".getBytes(StandardCharsets.UTF_8));

        int exitCode = ValidateCommand.run(List.of(TWENTY_ONE_OCCURRENCES), io);

        assertEquals(1, exitCode);
        assertEquals(1, io.outCalls);
        assertEquals(0, io.errCalls);
        assertEquals(1, io.readCalls);
        assertEquals(24, io.output.split("\n", -1).length);
        assertTrue(io.output.startsWith(
                "line 1: A(1) (columns 1-1): invalid value \"x\"\n"));
        assertTrue(io.output.contains(
                "line 1: A(20) (columns 20-20): invalid value \"x\"\n"));
        assertFalse(io.output.contains("A(21)"));
        assertTrue(io.output.endsWith("records     1\ninvalid     1\nproblems    21\n"));
        assertAsciiLf(io.output);
    }

    @Test
    void maximumLimitPrintsAllTwentyOneProblems() {
        MemoryIo io =
                new MemoryIo(
                        "xxxxxxxxxxxxxxxxxxxxx\n".getBytes(StandardCharsets.UTF_8));

        int exitCode =
                ValidateCommand.run(List.of("--max=1000", TWENTY_ONE_OCCURRENCES), io);

        assertEquals(1, exitCode);
        assertEquals(1, io.outCalls);
        assertEquals(0, io.errCalls);
        assertEquals(1, io.readCalls);
        assertEquals(25, io.output.split("\n", -1).length);
        assertTrue(io.output.startsWith(
                "line 1: A(1) (columns 1-1): invalid value \"x\"\n"));
        assertTrue(io.output.contains(
                "line 1: A(21) (columns 21-21): invalid value \"x\""));
        assertTrue(io.output.endsWith("records     1\ninvalid     1\nproblems    21\n"));
        assertAsciiLf(io.output);
    }

    @Test
    void repeatedLimitUsesTheLastValue() {
        MemoryIo io =
                new MemoryIo(
                        "xxxxxxxxxxxxxxxxxxxxx\n".getBytes(StandardCharsets.UTF_8));

        int exitCode =
                ValidateCommand.run(
                        List.of("--max=1", "--max", "2", TWENTY_ONE_OCCURRENCES), io);

        assertEquals(1, exitCode);
        assertEquals(1, io.outCalls);
        assertEquals(0, io.errCalls);
        assertEquals(1, io.readCalls);
        assertEquals(6, io.output.split("\n", -1).length);
        assertTrue(io.output.startsWith(
                "line 1: A(1) (columns 1-1): invalid value \"x\"\n"));
        assertTrue(io.output.contains(
                "line 1: A(2) (columns 2-2): invalid value \"x\"\n"));
        assertFalse(io.output.contains("A(3)"));
        assertTrue(io.output.endsWith("records     1\ninvalid     1\nproblems    21\n"));
        assertAsciiLf(io.output);
    }

    @Test
    void overwrittenInvalidLimitIsNotValidated() {
        MemoryIo io =
                new MemoryIo(
                        "xxxxxxxxxxxxxxxxxxxxx\n".getBytes(StandardCharsets.UTF_8));

        int exitCode =
                ValidateCommand.run(
                        List.of("--max=bad", "--max=1", TWENTY_ONE_OCCURRENCES), io);

        assertEquals(1, exitCode);
        assertEquals(1, io.outCalls);
        assertEquals(0, io.errCalls);
        assertEquals(1, io.readCalls);
        assertEquals(5, io.output.split("\n", -1).length);
        assertTrue(io.output.startsWith(
                "line 1: A(1) (columns 1-1): invalid value \"x\"\n"));
        assertFalse(io.output.contains("A(2)"));
        assertTrue(io.output.endsWith("records     1\ninvalid     1\nproblems    21\n"));
        assertAsciiLf(io.output);
    }

    @Test
    void rejectsMissingLayoutAndOptionArgumentsBeforeReadingInput() {
        assertFailure(
                List.of(),
                "usage: recmill validate [--max MAX] LAYOUT [NAME...]",
                0);
        assertFailure(List.of("--max"), "--max needs a value", 0);
        assertFailure(List.of("--help"), "unknown option \"--help\"", 0);
    }

    @Test
    void rejectsInvalidMaxValuesBeforeReadingInput() {
        assertFailure(
                List.of("--max=0", "01 R. 05 A PIC 9."),
                "invalid --max \"0\" (expected an integer from 1 to 1000)",
                0);
        assertFailure(
                List.of("--max=1001", "01 R. 05 A PIC 9."),
                "invalid --max \"1001\" (expected an integer from 1 to 1000)",
                0);
        assertFailure(
                List.of("--max=01", "01 R. 05 A PIC 9."),
                "invalid --max \"01\" (expected an integer from 1 to 1000)",
                0);
    }

    @Test
    void scansAllOptionsBeforeValidatingTheLastIntegerValue() {
        assertFailure(
                List.of("--max=bad", "--wat"),
                "unknown option \"--wat\"",
                0);
    }

    @Test
    void rejectsInvalidLayoutsAndViewsBeforeReadingInput() {
        assertFailure(List.of(""), "layout is empty", 0);
        assertFailure(
                List.of("01 R. 05 A PIC 9.", "BOGUS"),
                "\"BOGUS\" is not a redefining entry",
                0);
        assertFailure(
                List.of("01 R. 05 FILLER PIC X."),
                "no named field is selected",
                0);
    }

    @Test
    void rejectsInputWithNoRecordsWithoutWritingAReport() {
        MemoryIo io = new MemoryIo("\n\r\n".getBytes(StandardCharsets.UTF_8));

        Failure failure =
                assertThrows(
                        Failure.class,
                        () -> ValidateCommand.run(List.of("01 R. 05 A PIC 9."), io));

        assertEquals("<stdin>: no input", failure.getMessage());
        assertEquals(1, io.readCalls);
        assertNoWrites(io);
    }

    @Test
    void translatesStdinReadFailureWithoutWritingAReport() {
        MemoryIo io = new MemoryIo("1\n".getBytes(StandardCharsets.UTF_8));
        io.readFailure = true;

        Failure failure =
                assertThrows(
                        Failure.class,
                        () -> ValidateCommand.run(List.of("01 R. 05 A PIC 9."), io));

        assertEquals("<stdin>: cannot read the input", failure.getMessage());
        assertEquals(1, io.readCalls);
        assertNoWrites(io);
    }

    private static void assertFailure(
            List<String> args, String expectedMessage, int expectedReadCalls) {
        MemoryIo io = new MemoryIo("1\n".getBytes(StandardCharsets.UTF_8));

        Failure failure = assertThrows(Failure.class, () -> ValidateCommand.run(args, io));

        assertEquals(expectedMessage, failure.getMessage());
        assertEquals(expectedReadCalls, io.readCalls);
        assertNoWrites(io);
    }

    private static void assertNoWrites(MemoryIo io) {
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    private static void assertSingleReport(MemoryIo io, String expected) {
        assertEquals(1, io.outCalls);
        assertEquals(expected, io.output);
        assertEquals(0, io.errCalls);
        assertEquals(1, io.readCalls);
        assertAsciiLf(io.output);
    }

    private static void assertAsciiLf(String text) {
        assertFalse(text.contains("\r"));
        assertTrue(
                text.chars().allMatch(
                        character -> character == '\n'
                                || (character >= 0x20 && character <= 0x7E)));
    }

    private static final class MemoryIo implements Io {
        private final byte[] input;
        private int readCalls;
        private int outCalls;
        private int errCalls;
        private boolean readFailure;
        private String output = "";
        private String error = "";

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
            outCalls++;
            output = text;
        }

        @Override
        public void err(String text) {
            errCalls++;
            error = text;
        }
    }
}
