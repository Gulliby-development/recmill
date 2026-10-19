package io.github.gulliby.recmill.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvCommandTest {
    private static final String VIEW_LAYOUT = "01 R. 05 G. 10 N PIC 9 OCCURS 2. 05 T REDEFINES G PIC X(2). 05 FILLER PIC 9. 05 Z PIC X.";
    private static final String TWO_FIELD_LAYOUT = "01 R. 05 A PIC 9. 05 B PIC 9.";

    @Test
    void exportsDefaultViewOccurrencesAndSkipsGroupAndInvalidNumericFiller() {
        // Scenario: input "12xQ", views [].
        MemoryIo decodeIo = new MemoryIo(utf8("12xQ"));
        assertEquals(0, DecodeCommand.run(List.of(VIEW_LAYOUT), decodeIo));
        assertSuccess(decodeIo, "G.N(1)=1\nG.N(2)=2\nZ=Q\n");

        MemoryIo io = new MemoryIo(utf8("12xQ"));
        assertEquals(0, CsvCommand.run(List.of(VIEW_LAYOUT), io));
        assertSuccess(io, "G.N(1),G.N(2),Z\n1,2,Q\n");
    }

    @Test
    void exportsLowercaseAlternateViewAndSkipsInactiveNumericFields() {
        // Scenario: input "ABxQ", views ["t"].
        MemoryIo decodeIo = new MemoryIo(utf8("ABxQ"));
        assertEquals(0, DecodeCommand.run(List.of(VIEW_LAYOUT, "t"), decodeIo));
        assertSuccess(decodeIo, "T=AB\nZ=Q\n");

        MemoryIo io = new MemoryIo(utf8("ABxQ"));
        assertEquals(0, CsvCommand.run(List.of(VIEW_LAYOUT, "t"), io));
        assertSuccess(io, "T,Z\nAB,Q\n");
    }

    @Test
    void repeatedViewOperandsDoNotDuplicateCsvFields() {
        // Scenario: input "ABxQ", views ["T","T"].
        MemoryIo decodeIo = new MemoryIo(utf8("ABxQ"));
        assertEquals(0, DecodeCommand.run(List.of(VIEW_LAYOUT, "T", "T"), decodeIo));
        assertSuccess(decodeIo, "T=AB\nZ=Q\n");

        MemoryIo io = new MemoryIo(utf8("ABxQ"));
        assertEquals(0, CsvCommand.run(List.of(VIEW_LAYOUT, "T", "T"), io));
        assertSuccess(io, "T,Z\nAB,Q\n");
    }

    @Test
    void reportsNonPrintableCharacterBeforeRecordLengthAndSuppressesHeader() {
        assertFailure(
                "line 1: column 2 is not a printable ASCII character",
                "1\tX");
    }

    @Test
    void reportsShortRecordAndSuppressesHeader() {
        assertFailure("line 1: record has 1 characters (expected 2)", "1");
    }

    @Test
    void reportsLongRecordAndSuppressesHeader() {
        assertFailure("line 1: record has 3 characters (expected 2)", "123");
    }

    @Test
    void reportsFirstInvalidFieldAndSuppressesHeader() {
        assertFailure("line 1: A (columns 1-1): invalid value \"x\"", "xy");
    }

    @Test
    void reportsFirstLateFailureWithPhysicalLineNumberAndSuppressesHeader() {
        assertFailure("line 3: B (columns 2-2): invalid value \"x\"", "12\n\n3x\nxy");
    }

    @Test
    void reportsNonAsciiCharacterAndSuppressesHeader() {
        assertFailure("line 1: column 2 is not a printable ASCII character", "1\u00E9");
    }

    @Test
    void quotesCommaAndDoubleQuoteCellsAndHeaderValues() {
        MemoryIo io = new MemoryIo(utf8("A,B 1\n\"Q\" 2"));

        assertEquals(0, CsvCommand.run(List.of("01 R. 05 A PIC X(4). 05 N PIC 9."), io));

        assertSuccess(io, "A,N\n\"A,B\",1\n\"\"\"Q\"\"\",2\n");
    }

    @Test
    void quotesLeadingSpaceButNotInternalSpaceOrEmptyCells() {
        MemoryIo io = new MemoryIo(utf8(" A  3\nA B 4\n    5"));

        assertEquals(0, CsvCommand.run(List.of("01 R. 05 A PIC X(4). 05 N PIC 9."), io));

        assertSuccess(io, "A,N\n\" A\",3\nA B,4\n,5\n");
    }

    @Test
    void noHeaderFlagBeforeLayoutSuppressesHeader() {
        // Arguments ["--no-header",L], stdin "AB  1".
        MemoryIo io = new MemoryIo(utf8("AB  1"));

        assertEquals(0, CsvCommand.run(List.of("--no-header", "01 R. 05 A PIC X(4). 05 N PIC 9."), io));

        assertSuccess(io, "AB,1\n");
    }

    @Test
    void repeatedNoHeaderFlagAfterLayoutSuppressesHeader() {
        // Arguments [L,"--no-header","--no-header"], stdin "AB  1".
        MemoryIo io = new MemoryIo(utf8("AB  1"));

        assertEquals(0, CsvCommand.run(
                List.of("01 R. 05 A PIC X(4). 05 N PIC 9.", "--no-header", "--no-header"), io));

        assertSuccess(io, "AB,1\n");
    }

    @Test
    void validatesCsvOptionsBeforeArityAndDoesNotReadOnArgumentFailures() {
        // AC-005: CSV `[]`.
        assertArgumentFailure("usage: recmill csv [--no-header] LAYOUT [NAME...]",
                List.of(), new byte[0]);
        // AC-005: CSV `["--no-header=1"]`.
        assertArgumentFailure("--no-header takes no value",
                List.of("--no-header=1"), new byte[0]);
    }

    @Test
    void rejectsInvalidLayoutAndViewsBeforeReadingInput() {
        assertArgumentFailure("layout is empty", List.of(""), utf8("A"));
        assertArgumentFailure("\"A\" is not a redefining entry",
                List.of("01 R. 05 A PIC X.", "A"), utf8("A"));
        // AC-005: Either adapter `["01 R. 05 FILLER PIC X."]`.
        assertArgumentFailure("no named field is selected",
                List.of("01 R. 05 FILLER PIC X."), utf8("A"));
        // AC-005 Either adapter ["01 R. 05 A PIC X. 05 P REDEFINES A PIC X. 05 Q REDEFINES A PIC X.","Q","P"].
        assertArgumentFailure("\"P\" and \"Q\" redefine the same entry",
                List.of("01 R. 05 A PIC X. 05 P REDEFINES A PIC X. "
                        + "05 Q REDEFINES A PIC X.", "Q", "P"), utf8("A"));
    }

    @Test
    void stripsLiteralUtf8BomAndNormalizesCrLfAndEmptyLines() {
        MemoryIo io = new MemoryIo(new byte[] {
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x41, 0x42,
                0x0D, 0x0A, 0x0D, 0x0A, 0x43, 0x44
        });

        assertEquals(0, CsvCommand.run(List.of("01 R. 05 A PIC X(2)."), io));

        assertSuccess(io, "A\nAB\nCD\n");
    }

    @Test
    void rejectsEmptyInputAndOnlyEmptyLinesWithoutWritingCsv() {
        assertInputFailure("<stdin>: no input", new byte[0]);
        assertInputFailure("<stdin>: no input", new byte[] {0x0A, 0x0A});
    }

    @Test
    void translatesReadFailureWithoutWritingCsv() {
        MemoryIo io = new MemoryIo(new byte[] {0x41, 0x42});
        io.readFailure = true;

        Failure failure = assertThrows(
                Failure.class,
                () -> CsvCommand.run(List.of("01 R. 05 A PIC X(2)."), io));

        assertEquals("<stdin>: cannot read the input", failure.getMessage());
        assertEquals(1, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals("", io.output);
        assertEquals(0, io.errCalls);
    }

    @Test
    void checksDecodeArgumentPrecedenceBeforeReadingInput() {
        // AC-005: Decode [].
        assertDecodeArgumentFailure(
                "usage: recmill decode LAYOUT [NAME...]", List.of(), new byte[0]);
        // AC-005: Decode ["--help"].
        assertDecodeArgumentFailure(
                "unknown option \"--help\"", List.of("--help"), new byte[0]);
        // AC-005: Decode [""].
        assertDecodeArgumentFailure("layout is empty", List.of(""), utf8("A"));
    }

    @Test
    void checksDecodeViewsBeforeReadingInput() {
        // AC-005: Either adapter [L, "A"].
        assertDecodeArgumentFailure(
                "\"A\" is not a redefining entry",
                List.of("01 R. 05 A PIC X.", "A"), utf8("A"));
        // AC-005: Either adapter ["01 R. 05 FILLER PIC X."].
        assertDecodeArgumentFailure(
                "no named field is selected",
                List.of("01 R. 05 FILLER PIC X."), utf8("A"));
        // AC-005: the conflicting views are rejected in layout order.
        assertDecodeArgumentFailure(
                "\"P\" and \"Q\" redefine the same entry",
                List.of("01 R. 05 A PIC X. 05 P REDEFINES A PIC X. "
                        + "05 Q REDEFINES A PIC X.", "Q", "P"), utf8("A"));
    }

    @Test
    void decodesLiteralBomAndCrLfInputToOrderedRecords() {
        // AC-006 decode output: A=AB\n\nA=CD\n.
        MemoryIo io = new MemoryIo(new byte[] {
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x41, 0x42,
                0x0D, 0x0A, 0x0D, 0x0A, 0x43, 0x44
        });

        assertEquals(0, DecodeCommand.run(List.of("01 R. 05 A PIC X(2)."), io));

        assertSuccess(io, "A=AB\n\nA=CD\n");
    }

    @Test
    void rejectsEmptyDecodeInputAndOnlyEmptyLines() {
        assertDecodeInputFailure("<stdin>: no input", new byte[0]);
        assertDecodeInputFailure("<stdin>: no input", new byte[] {0x0A, 0x0A});
    }

    @Test
    void translatesDecodeReadFailureWithoutWritingOutput() {
        MemoryIo io = new MemoryIo(new byte[] {0x41, 0x42});
        io.readFailure = true;

        Failure failure = assertThrows(
                Failure.class,
                () -> DecodeCommand.run(List.of("01 R. 05 A PIC X(2)."), io));

        assertEquals("<stdin>: cannot read the input", failure.getMessage());
        assertEquals(1, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals("", io.output);
        assertEquals(0, io.errCalls);
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

    private static void assertArgumentFailure(
            String expectedMessage, List<String> args, byte[] input) {
        MemoryIo io = new MemoryIo(input);

        Failure failure = assertThrows(Failure.class, () -> CsvCommand.run(args, io));

        assertEquals(expectedMessage, failure.getMessage());
        assertEquals(0, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals("", io.output);
        assertEquals(0, io.errCalls);
    }

    private static void assertDecodeArgumentFailure(
            String expectedMessage, List<String> args, byte[] input) {
        MemoryIo io = new MemoryIo(input);

        Failure failure = assertThrows(Failure.class, () -> DecodeCommand.run(args, io));

        assertEquals(expectedMessage, failure.getMessage());
        assertEquals(0, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals("", io.output);
        assertEquals(0, io.errCalls);
    }

    private static void assertDecodeInputFailure(String expectedMessage, byte[] input) {
        MemoryIo io = new MemoryIo(input);

        Failure failure = assertThrows(
                Failure.class,
                () -> DecodeCommand.run(List.of("01 R. 05 A PIC X(2)."), io));

        assertEquals(expectedMessage, failure.getMessage());
        assertEquals(1, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals("", io.output);
        assertEquals(0, io.errCalls);
    }

    private static void assertInputFailure(String expectedMessage, byte[] input) {
        MemoryIo io = new MemoryIo(input);

        Failure failure = assertThrows(
                Failure.class,
                () -> CsvCommand.run(List.of("01 R. 05 A PIC X(2)."), io));

        assertEquals(expectedMessage, failure.getMessage());
        assertEquals(1, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals("", io.output);
        assertEquals(0, io.errCalls);
    }

    private static void assertFailure(String expectedMessage, String input) {
        MemoryIo io = new MemoryIo(utf8(input));

        Failure failure = assertThrows(
                Failure.class,
                () -> CsvCommand.run(List.of(TWO_FIELD_LAYOUT), io));

        assertEquals(expectedMessage, failure.getMessage());
        assertEquals(1, io.readCalls);
        assertEquals(0, io.outCalls);
        assertEquals("", io.output);
        assertEquals(0, io.errCalls);
    }

    private static final class MemoryIo implements Io {
        private final byte[] input;
        private int readCalls;
        private boolean readFailure;
        private int outCalls;
        private int errCalls;
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
