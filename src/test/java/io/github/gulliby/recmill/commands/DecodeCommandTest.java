package io.github.gulliby.recmill.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecodeCommandTest {
    private static final String DECIMAL_LAYOUT =
            "01 R. 05 ID PIC 9(2). 05 NAME PIC X(3). 05 AMT PIC S9V99.";
    private static final String OCCURS_LAYOUT =
            "01 R. 05 G. 10 N PIC 9 OCCURS 2. 05 T REDEFINES G PIC X(2). "
                    + "05 FILLER PIC 9. 05 Z PIC X.";
    private static final String TWO_FIELD_LAYOUT = "01 R. 05 A PIC 9. 05 B PIC 9.";
    private static final String SINGLE_FIELD_LAYOUT = "01 R. 05 A PIC X.";
    private static final String FILLER_LAYOUT = "01 R. 05 FILLER PIC X.";
    private static final String CONFLICT_LAYOUT =
            "01 R. 05 A PIC X. 05 P REDEFINES A PIC X. 05 Q REDEFINES A PIC X.";

    @Test
    void rendersSignedDecimalsAndMultipleRecordsInFieldOrder() {
        MemoryIo io = new MemoryIo(utf8("01A  15{\n02B  20}\n"));

        assertEquals(0, DecodeCommand.run(List.of(DECIMAL_LAYOUT), io));

        assertSuccess(io, "ID=1\nNAME=A\nAMT=1.50\n\nID=2\nNAME=B\nAMT=-2.00\n");
    }

    @Test
    void rendersEmptyAlphaAndNegativeZero() {
        MemoryIo io = new MemoryIo(utf8("00   00}"));

        assertEquals(0, DecodeCommand.run(List.of(DECIMAL_LAYOUT), io));

        assertSuccess(io, "ID=0\nNAME=\nAMT=0.00\n");
    }

    @Test
    void rendersDefaultViewOccurrencesAndSkipsGroupAndInvalidNumericFiller() {
        MemoryIo io = new MemoryIo(utf8("12xQ"));

        assertEquals(0, DecodeCommand.run(List.of(OCCURS_LAYOUT), io));

        assertSuccess(io, "G.N(1)=1\nG.N(2)=2\nZ=Q\n");
    }

    @Test
    void rendersLowercaseAlternateViewAndSkipsInactiveNumericFields() {
        MemoryIo io = new MemoryIo(utf8("ABxQ"));

        assertEquals(0, DecodeCommand.run(List.of(OCCURS_LAYOUT, "t"), io));

        assertSuccess(io, "T=AB\nZ=Q\n");
    }

    @Test
    void repeatedViewOperandsDoNotDuplicateValues() {
        MemoryIo io = new MemoryIo(utf8("ABxQ"));

        assertEquals(0, DecodeCommand.run(List.of(OCCURS_LAYOUT, "T", "T"), io));

        assertSuccess(io, "T=AB\nZ=Q\n");
    }

    @Test
    void reportsNonPrintableCharacterBeforeRecordLengthWithoutOutput() {
        assertFailure(
                "line 1: column 2 is not a printable ASCII character",
                List.of(TWO_FIELD_LAYOUT),
                utf8("1\tX"),
                1);
    }

    @Test
    void reportsShortRecordWithoutOutput() {
        assertFailure(
                "line 1: record has 1 characters (expected 2)",
                List.of(TWO_FIELD_LAYOUT),
                utf8("1"),
                1);
    }

    @Test
    void reportsLongRecordWithoutOutput() {
        assertFailure(
                "line 1: record has 3 characters (expected 2)",
                List.of(TWO_FIELD_LAYOUT),
                utf8("123"),
                1);
    }

    @Test
    void reportsFirstInvalidFieldWithoutOutput() {
        assertFailure(
                "line 1: A (columns 1-1): invalid value \"x\"",
                List.of(TWO_FIELD_LAYOUT),
                utf8("xy"),
                1);
    }

    @Test
    void reportsFirstLateFailureWithPhysicalLineNumberWithoutOutput() {
        assertFailure(
                "line 3: B (columns 2-2): invalid value \"x\"",
                List.of(TWO_FIELD_LAYOUT),
                utf8("12\n\n3x\nxy"),
                1);
    }

    @Test
    void reportsNonAsciiCharacterWithoutOutput() {
        assertFailure(
                "line 1: column 2 is not a printable ASCII character",
                List.of(TWO_FIELD_LAYOUT),
                utf8("1\u00E9"),
                1);
    }

    @Test
    void rejectsMissingLayoutOperandBeforeReadingInput() {
        assertFailure(
                "usage: recmill decode LAYOUT [NAME...]",
                List.of(),
                utf8("A"),
                0);
    }

    @Test
    void rejectsHelpAsAnUnknownOptionBeforeReadingInput() {
        assertFailure(
                "unknown option \"--help\"",
                List.of("--help"),
                utf8("A"),
                0);
    }

    @Test
    void rejectsEmptyLayoutBeforeReadingInput() {
        assertFailure("layout is empty", List.of(""), utf8("A"), 0);
    }

    @Test
    void rejectsUnknownViewBeforeReadingInput() {
        assertFailure(
                "\"A\" is not a redefining entry",
                List.of(SINGLE_FIELD_LAYOUT, "A"),
                utf8("A"),
                0);
    }

    @Test
    void rejectsLayoutWithOnlyFillerBeforeReadingInput() {
        assertFailure(
                "no named field is selected",
                List.of(FILLER_LAYOUT),
                utf8("A"),
                0);
    }

    @Test
    void reportsConflictingViewsInLayoutOrderBeforeReadingInput() {
        assertFailure(
                "\"P\" and \"Q\" redefine the same entry",
                List.of(CONFLICT_LAYOUT, "Q", "P"),
                utf8("A"),
                0);
    }

    @Test
    void normalizesBomCrLfAndEmptyRecordSeparatorsFromBytes() {
        MemoryIo io = new MemoryIo(
                new byte[] {
                    (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x41, 0x42,
                    0x0D, 0x0A, 0x0D, 0x0A, 0x43, 0x44
                });

        assertEquals(
                0,
                DecodeCommand.run(List.of("01 R. 05 A PIC X(2)."), io));

        assertSuccess(io, "A=AB\n\nA=CD\n");
    }

    @Test
    void rejectsEmptyByteInputWithoutOutput() {
        assertFailure(
                "<stdin>: no input",
                List.of("01 R. 05 A PIC X(2)."),
                new byte[] {},
                1);
    }

    @Test
    void rejectsInputContainingOnlyEmptyLinesWithoutOutput() {
        assertFailure(
                "<stdin>: no input",
                List.of("01 R. 05 A PIC X(2)."),
                utf8("\n\n"),
                1);
    }

    @Test
    void translatesReadFailureWithoutOutput() {
        MemoryIo io = new MemoryIo(utf8("AB"));
        io.readFailure = true;

        Failure failure = assertThrows(
                Failure.class,
                () -> DecodeCommand.run(List.of("01 R. 05 A PIC X(2)."), io));

        assertEquals("<stdin>: cannot read the input", failure.getMessage());
        assertNoOutput(io, 1);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void assertSuccess(MemoryIo io, String expected) {
        assertEquals(1, io.readCalls);
        assertEquals(1, io.outCalls);
        assertEquals(expected, io.output);
        assertEquals(0, io.errCalls);
    }

    private static void assertFailure(
            String expectedMessage, List<String> args, byte[] input, int expectedReadCalls) {
        MemoryIo io = new MemoryIo(input);

        Failure failure = assertThrows(Failure.class, () -> DecodeCommand.run(args, io));

        assertEquals(expectedMessage, failure.getMessage());
        assertNoOutput(io, expectedReadCalls);
    }

    private static void assertNoOutput(MemoryIo io, int expectedReadCalls) {
        assertEquals(expectedReadCalls, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
        assertEquals("", io.output);
    }

    private static final class MemoryIo implements Io {
        private final byte[] input;
        private int readCalls;
        private int outCalls;
        private int errCalls;
        private boolean readFailure;
        private String output = "";

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
        }
    }
}
