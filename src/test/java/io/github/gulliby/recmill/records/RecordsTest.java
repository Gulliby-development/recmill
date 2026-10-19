package io.github.gulliby.recmill.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.model.Slot;
import io.github.gulliby.recmill.syntax.Picture;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.records.Records.Line;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordsTest {
    private static final Slot AMT_SLOT =
            new Slot("AMT", 11, 5, new Picture(true, true, 3, 2, 5), false, List.of());
    private static final Slot BODY_QTY_SLOT =
            new Slot("BODY.QTY(2)", 7, 2, new Picture(true, false, 2, 0, 2), false, List.of());
    private static final Slot A_SLOT =
            new Slot("A", 1, 1, new Picture(false, false, 1, 0, 1), false, List.of());
    @Test
    void skipsInteriorEmptyLinesAndPreservesPhysicalLineNumbers() {
        MemoryIo io = new MemoryIo("A\n\nB\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of(new Line(1, "A"), new Line(3, "B")), Records.read(io));
        assertNoOutput(io);
    }

    @Test
    void skipsBomAndNormalizesCrLfWithoutRenumberingRecords() {
        MemoryIo io = new MemoryIo("\uFEFF\r\nAB\r\n\r\nCD".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of(new Line(2, "AB"), new Line(4, "CD")), Records.read(io));
        assertNoOutput(io);
    }

    @Test
    void retainsSpaceOnlyRecords() {
        MemoryIo io = new MemoryIo(" \n".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of(new Line(1," ")), Records.read(io));
        assertNoOutput(io);
    }

    @Test
    void rejectsEmptyInput() {
        MemoryIo io = new MemoryIo("".getBytes(StandardCharsets.UTF_8));

        Failure failure = assertThrows(Failure.class, () -> Records.read(io));

        assertEquals("<stdin>: no input", failure.getMessage());
        assertNoOutput(io);
    }

    @Test
    void rejectsInputContainingOnlyEmptyLines() {
        MemoryIo io = new MemoryIo("\n\r\n".getBytes(StandardCharsets.UTF_8));

        Failure failure = assertThrows(Failure.class, () -> Records.read(io));

        assertEquals("<stdin>: no input", failure.getMessage());
        assertNoOutput(io);
    }

    @Test
    void translatesMalformedUtf8WithoutWritingOutput() {
        MemoryIo io = new MemoryIo(new byte[] {0x41, 0x42, (byte) 0xFF, 0x0A});

        Failure failure = assertThrows(Failure.class, () -> Records.read(io));

        assertEquals("<stdin>: input is not valid UTF-8", failure.getMessage());
        assertNoOutput(io);
    }

    @Test
    void translatesReadFailureWithoutWritingOutput() {
        MemoryIo io = new MemoryIo(new byte[] {0x41});
        io.readFailure = true;

        Failure failure = assertThrows(Failure.class, () -> Records.read(io));

        assertEquals("<stdin>: cannot read the input", failure.getMessage());
        assertNoOutput(io);
    }

    @Test
    void acceptsPrintableAsciiEndpointsAtExactWidth() {
        assertNull(Records.problem(" ~", 2));
    }

    @Test
    void acceptsExactWidthRecord() {
        assertNull(Records.problem("AB", 2));
    }

    @Test
    void reportsShortRecordLength() {
        assertEquals("record has 1 characters (expected 2)", Records.problem("A", 2));
    }

    @Test
    void reportsLongRecordLength() {
        assertEquals("record has 3 characters (expected 2)", Records.problem("ABC", 2));
    }

    @Test
    void reportsAsciiProblemBeforeLengthMismatch() {
        assertEquals(
                "column 2 is not a printable ASCII character",
                Records.problem("A\tB", 2));
    }

    @Test
    void reportsFirstInvalidCodePointBeforeLaterInvalidCharacter() {
        assertEquals(
                "column 2 is not a printable ASCII character",
                Records.problem("A\u00E9\t", 3));
    }

    @Test
    void rejectsDelAsNonPrintableAscii() {
        assertEquals(
                "column 1 is not a printable ASCII character",
                Records.problem("\u007F", 1));
    }

    @Test
    void formatsFieldDiagnosticWithInclusiveColumnSpan() {
        assertEquals(
                "AMT (columns 11-15): invalid value \"0x15{\"",
                Records.fieldProblem(
                        AMT_SLOT,
                        "0x15{"));
    }

    @Test
    void sanitizesControlsAndNonAsciiInOccurrenceDiagnostic() {
        assertEquals(
                "BODY.QTY(2) (columns 7-8): invalid value \"??\"",
                Records.fieldProblem(
                        BODY_QTY_SLOT,
                        "\t\u00E9"));
    }

    @Test
    void sanitizesSupplementaryCodePointAsOneQuestionMark() {
        assertEquals(
                "A (columns 1-1): invalid value \"?\"",
                Records.fieldProblem(
                        A_SLOT,
                        "\uD83D\uDE00"));
    }

    private static void assertNoOutput(MemoryIo io) {
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    private static final class MemoryIo implements Io {
        private final byte[] input;
        private int outCalls;
        private int errCalls;
        private boolean readFailure;

        private MemoryIo(byte[] input) {
            this.input = input;
        }

        @Override
        public byte[] readStdin() throws IOException {
            if (readFailure) {
                throw new IOException("read failed");
            }
            return input;
        }

        @Override
        public void out(String text) {
            outCalls++;
        }

        @Override
        public void err(String text) {
            errCalls++;
        }
    }
}
