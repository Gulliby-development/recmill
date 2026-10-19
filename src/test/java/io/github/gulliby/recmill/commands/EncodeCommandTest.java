package io.github.gulliby.recmill.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class EncodeCommandTest {
    private static final String LAYOUT_A =
            "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99.";
    private static final String L = "01 R. 05 A PIC X. 05 P REDEFINES A PIC X.";

    @Test
    void exposesExactUsage() {
        assertEquals("recmill encode LAYOUT [NAME...]", EncodeCommand.USAGE);
    }

    @Test
    void writesWorkedExampleInOneLfTerminatedCall() {
        MemoryIo io = new MemoryIo(
                "ID=7\nNAME=EVE\nAMT=-12.5\n\nNAME=ZED\n".getBytes(StandardCharsets.UTF_8));

        int result = EncodeCommand.run(List.of(LAYOUT_A), io);

        assertEquals(0, result);
        assertEquals(1, io.readCalls);
        assertEquals(1, io.outCalls);
        assertEquals("0007EVE   0125}\n0000ZED   00000\n", io.output);
        assertEquals(0, io.errCalls);
    }

    @Test
    void readsBomCrLfAndMissingFinalLfThroughLines() {
        String inputText = "\uFEFFID=7\r\n\r\nNAME=ZED";
        byte[] input = inputText.getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(
                new byte[] {
                    (byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
                    'I', 'D', '=', '7', '\r', '\n', '\r', '\n',
                    'N', 'A', 'M', 'E', '=', 'Z', 'E', 'D'
                },
                input);
        MemoryIo io = new MemoryIo(input);

        int result = EncodeCommand.run(List.of(LAYOUT_A), io);

        assertEquals(0, result);
        assertEquals(1, io.readCalls);
        assertEquals(1, io.outCalls);
        assertEquals("0007      00000\n0000ZED   00000\n", io.output);
        assertEquals(0, io.errCalls);
    }

    @Test
    void doesNotWriteEarlierRecordsWhenALaterAssignmentFails() {
        MemoryIo io = new MemoryIo(
                "ID=7\n\nID=12345\n".getBytes(StandardCharsets.UTF_8));

        Failure failure = assertThrows(
                Failure.class, () -> EncodeCommand.run(List.of(LAYOUT_A), io));

        assertEquals("line 3: invalid value \"12345\" for ID (PIC 9(4))", failure.getMessage());
        assertEquals(1, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    @Test
    void rejectsInputWithoutAnyAssignmentBlock() {
        MemoryIo io = new MemoryIo("\n\n".getBytes(StandardCharsets.UTF_8));

        Failure failure = assertThrows(
                Failure.class, () -> EncodeCommand.run(List.of(LAYOUT_A), io));

        assertEquals("<stdin>: no input", failure.getMessage());
        assertEquals(1, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    @Test
    void parsesOptionsBeforeCheckingForRequiredLayout() {
        MemoryIo io = new MemoryIo(new byte[0]);

        Failure failure = assertThrows(
                Failure.class, () -> EncodeCommand.run(List.of("--bad"), io));

        assertEquals("unknown option \"--bad\"", failure.getMessage());
        assertEquals(0, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    @Test
    void rejectsMissingLayoutWithUsageBeforeReadingInput() {
        MemoryIo io = new MemoryIo(new byte[0]);

        Failure failure = assertThrows(Failure.class, () -> EncodeCommand.run(List.of(), io));

        assertEquals("usage: recmill encode LAYOUT [NAME...]", failure.getMessage());
        assertEquals(0, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    @Test
    void rejectsInvalidLayoutBeforeReadingInput() {
        MemoryIo io = new MemoryIo(new byte[0]);

        Failure failure = assertThrows(
                Failure.class, () -> EncodeCommand.run(List.of(""), io));

        assertEquals("layout is empty", failure.getMessage());
        assertEquals(0, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    @Test
    void rejectsUnknownViewBeforeReadingInput() {
        // AC-006 args: [L,"NO"]
        MemoryIo io = new MemoryIo(new byte[0]);

        Failure failure = assertThrows(
                Failure.class, () -> EncodeCommand.run(List.of(L, "NO"), io));

        assertEquals("\"NO\" is not a redefining entry", failure.getMessage());
        assertEquals(0, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    @Test
    void acceptsLowercaseViewOperandAndWritesSelectedField() {
        // AC-006 args: [L,"p"], stdin P=X\n
        MemoryIo io = new MemoryIo("P=X\n".getBytes(StandardCharsets.UTF_8));

        int result = EncodeCommand.run(List.of(L, "p"), io);

        assertEquals(0, result);
        assertEquals(1, io.readCalls);
        assertEquals(1, io.outCalls);
        assertEquals("X\n", io.output);
        assertEquals(0, io.errCalls);
    }

    @Test
    void translatesReadFailureWithoutWritingOutput() {
        // AC-006 args: [L], read throws IOException
        MemoryIo io = new MemoryIo(new byte[] {'A'});
        io.readFailure = true;

        Failure failure = assertThrows(
                Failure.class, () -> EncodeCommand.run(List.of(L), io));

        assertEquals("<stdin>: cannot read the input", failure.getMessage());
        assertEquals(1, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    private static final class MemoryIo implements Io {
        private final byte[] input;
        private int readCalls;
        private int outCalls;
        private int errCalls;
        private String output;
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
            outCalls++;
            output = text;
        }

        @Override
        public void err(String text) {
            errCalls++;
        }
    }
}
