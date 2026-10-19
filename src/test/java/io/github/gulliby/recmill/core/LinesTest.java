package io.github.gulliby.recmill.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LinesTest {
    @Test
    void failurePreservesMessageAndIsRuntimeException() {
        Failure failure = new Failure("problem");

        assertEquals("problem", failure.getMessage());
        assertInstanceOf(RuntimeException.class, failure);
    }

    @Test
    void inMemoryIoImplementsAllMethodsAndAllowsReadFailure() throws IOException {
        byte[] input = new byte[] {65, 66};
        MemoryIo io = new MemoryIo(input);

        assertArrayEquals(input, io.readStdin());
        io.out("output");
        io.err("error");
        assertEquals("output", io.output);
        assertEquals("error", io.error);

        MemoryIo failingIo = new MemoryIo(input);
        failingIo.readFailure = true;
        assertThrows(IOException.class, failingIo::readStdin);
    }

    @ParameterizedTest
    @MethodSource("lineCases")
    void readsLiteralUtf8LineBoundaries(String input, List<String> expected) {
        MemoryIo io = new MemoryIo(input.getBytes(StandardCharsets.UTF_8));

        assertEquals(expected, Lines.read(io));
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    private static Stream<Arguments> lineCases() {
        return Stream.of(
                Arguments.of("", List.of()),
                Arguments.of("\uFEFF", List.of()),
                Arguments.of("A", List.of("A")),
                Arguments.of("A\n", List.of("A")),
                Arguments.of("\n", List.of("")),
                Arguments.of("A\n\nB\n", List.of("A", "", "B")),
                Arguments.of("A\n\n", List.of("A", "")),
                Arguments.of("\uFEFFA\r\nB\r", List.of("A", "B")),
                Arguments.of("\uFEFF\uFEFFA", List.of("\uFEFFA")),
                Arguments.of("A\r\r\nB\rC", List.of("A\r", "B\rC")),
                Arguments.of("é\uD83D\uDE00\n", List.of("é\uD83D\uDE00")),
                Arguments.of(" \n", List.of(" ")));
    }

    @ParameterizedTest
    @MethodSource("malformedByteCases")
    void rejectsMalformedUtf8WithoutReturningLinesOrWritingOutput(String hexBytes, byte[] input) {
        MemoryIo io = new MemoryIo(input);

        Failure failure = assertThrows(Failure.class, () -> Lines.read(io));

        assertEquals("<stdin>: input is not valid UTF-8", failure.getMessage(), hexBytes);
        assertNoOutput(io);
    }

    private static Stream<Arguments> malformedByteCases() {
        return Stream.of(
                Arguments.of("41 42 FF 0A", new byte[] {0x41, 0x42, (byte) 0xFF, 0x0A}),
                Arguments.of("C3", new byte[] {(byte) 0xC3}),
                Arguments.of("C0 AF", new byte[] {(byte) 0xC0, (byte) 0xAF}),
                Arguments.of("ED A0 80", new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80}));
    }

    @Test
    void translatesReadFailureWithoutReturningLinesOrWritingOutput() {
        MemoryIo io = new MemoryIo(new byte[] {65});
        io.readFailure = true;

        Failure failure = assertThrows(Failure.class, () -> Lines.read(io));

        assertEquals("<stdin>: cannot read the input", failure.getMessage());
        assertNoOutput(io);
    }

    private static void assertNoOutput(MemoryIo io) {
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    private static final class MemoryIo implements Io {
        private final byte[] input;
        private String output;
        private String error;
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
            output = text;
        }

        @Override
        public void err(String text) {
            errCalls++;
            error = text;
        }
    }
}
