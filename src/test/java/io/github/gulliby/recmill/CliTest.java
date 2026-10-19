package io.github.gulliby.recmill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.gulliby.recmill.core.Io;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

class CliTest {
    private static final String LAYOUT_A =
            "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99.";
    private static final String LAYOUT_B =
            "01 REC. 05 KIND PIC X. 05 BODY. 10 CODE PIC X(3). 10 QTY PIC 9(2) OCCURS 3 TIMES. 05 TEXT REDEFINES BODY PIC X(8). 05 TAIL PIC X(2).";
    private static final String LAYOUT_D =
            "01 ORDER. 05 ID PIC 9(5). 05 NAME PIC X(6). 05 NOTE PIC X(4). 05 FILLER PIC X(2).";
    private static final String EXPECTED_USAGE =
            "recmill layout [--groups] LAYOUT\n"
                    + "recmill find LAYOUT COLUMN\n"
                    + "recmill decode LAYOUT [NAME...]\n"
                    + "recmill encode LAYOUT [NAME...]\n"
                    + "recmill csv [--no-header] LAYOUT [NAME...]\n"
                    + "recmill validate [--max MAX] LAYOUT [NAME...]\n"
                    + "recmill profile LAYOUT [NAME...]\n"
                    + "recmill diff LAYOUT1 LAYOUT2";

    @Test
    void usageMatchesTheEightLiteralCommandLines() {
        assertEquals(EXPECTED_USAGE, Cli.USAGE);
        assertFalse(Cli.USAGE.endsWith("\n"));
    }

    @Test
    void loneHelpPrintsUsageAndDoesNotReadStdin() {
        MemoryIo io = new MemoryIo();

        int status = Cli.run(List.of("--help"), io);

        assertEquals(0, status);
        assertEquals(EXPECTED_USAGE + "\n", io.output);
        assertEquals(1, io.outCalls);
        assertEquals("", io.error);
        assertEquals(0, io.errCalls);
        assertEquals(0, io.readCalls);
    }

    @Test
    void missingCommandIsReportedWithoutOutputOrReadingStdin() {
        assertFailure(
                List.of(),
                "recmill: missing command (layout, find, decode, encode, csv, validate, profile or diff)\n");
    }

    @Test
    void unknownCommandIsReportedWithoutOutputOrReadingStdin() {
        assertFailure(List.of("frob"), "recmill: unknown command \"frob\"\n");
    }

    @Test
    void helpWithAnotherArgumentIsAnUnknownCommand() {
        assertFailure(
                List.of("--help", "layout"),
                "recmill: unknown command \"--help\"\n");
    }

    @Test
    void unknownCommandTextIsSanitized() {
        assertFailure(
                List.of("fr\u00f6b\n"),
                "recmill: unknown command \"fr?b?\"\n");
    }

    @Test
    void layoutCommandPrintsTheRequestedLayoutReport() {
        MemoryIo io = new MemoryIo();

        int status = Cli.run(List.of("layout", LAYOUT_A), io);

        assertEquals(0, status);
        assertSuccess(
                io,
                "path  from  to  pic\n"
                        + "ID    1     4   9(4)\n"
                        + "NAME  5     10  X(6)\n"
                        + "AMT   11    15  S9(3)V9(2)\n"
                        + "length      15\n",
                0);
    }

    @Test
    void findCommandPrintsEveryMatchingViewAtTheRequestedColumn() {
        MemoryIo io = new MemoryIo();

        int status = Cli.run(List.of("find", LAYOUT_B, "7"), io);

        assertEquals(0, status);
        assertSuccess(
                io,
                "column      7\n"
                        + "field       BODY.QTY(2) 7-8 9(2)\n"
                        + "field       TEXT 2-9 X(8)\n",
                0);
    }

    @Test
    void decodeCommandPrintsEveryRecordAndSeparatesRecords() {
        MemoryIo io = new MemoryIo("0001ALICE 0015{\n0002BOB   0120}\n");

        int status = Cli.run(List.of("decode", LAYOUT_A), io);

        assertEquals(0, status);
        assertSuccess(
                io,
                "ID=1\n"
                        + "NAME=ALICE\n"
                        + "AMT=1.50\n"
                        + "\n"
                        + "ID=2\n"
                        + "NAME=BOB\n"
                        + "AMT=-12.00\n",
                1);
    }

    @Test
    void encodeCommandBuildsRecordsFromAssignmentBlocks() {
        MemoryIo io = new MemoryIo("ID=7\nNAME=EVE\nAMT=-12.5\n\nNAME=ZED\n");

        int status = Cli.run(List.of("encode", LAYOUT_A), io);

        assertEquals(0, status);
        assertSuccess(io, "0007EVE   0125}\n0000ZED   00000\n", 1);
    }

    @Test
    void csvCommandQuotesCellsAndPrintsTheHeader() {
        MemoryIo io = new MemoryIo("0001A,B   0015{\n0002\"Q\"   0120}\n");

        int status = Cli.run(List.of("csv", LAYOUT_A), io);

        assertEquals(0, status);
        assertSuccess(
                io,
                "ID,NAME,AMT\n"
                        + "1,\"A,B\",1.50\n"
                        + "2,\"\"\"Q\"\"\",-12.00\n",
                1);
    }

    @Test
    void validateCommandPrintsTheCompleteInvalidReportAndReturnsOne() {
        MemoryIo io = new MemoryIo(
                "0001ALICE 0015{\n0002BOB   01x0}\n0003\n");

        int status = Cli.run(List.of("validate", LAYOUT_A), io);

        assertEquals(1, status);
        assertSuccess(
                io,
                "line 2: AMT (columns 11-15): invalid value \"01x0}\"\n"
                        + "line 3: record has 4 characters (expected 15)\n"
                        + "records     3\n"
                        + "invalid     2\n"
                        + "problems    2\n",
                1);
    }

    @Test
    void profileCommandPrintsTheRequestedFieldStatistics() {
        MemoryIo io = new MemoryIo(
                "0001ALICE 0015{\n"
                        + "0002BOB   0120}\n"
                        + "0003ALICE 00000\n"
                        + "0004\n");

        int status = Cli.run(List.of("profile", LAYOUT_A), io);

        assertEquals(0, status);
        assertSuccess(
                io,
                "field  pic         blank  bad  distinct  min     max   sum\n"
                        + "ID     9(4)        0      0    3         1       3     6\n"
                        + "NAME   X(6)        0      0    2         ALICE   BOB   -\n"
                        + "AMT    S9(3)V9(2)  0      0    3         -12.00  1.50  -10.50\n"
                        + "records     4\n"
                        + "skipped     1\n",
                1);
    }

    @Test
    void diffCommandPrintsTheDifferencesAndReturnsOne() {
        MemoryIo io = new MemoryIo();

        int status = Cli.run(List.of("diff", LAYOUT_A, LAYOUT_D), io);

        assertEquals(1, status);
        assertSuccess(
                io,
                "- AMT from 11 length 5 pic S9(3)V9(2)\n"
                        + "~ ID length 4 -> 5, pic 9(4) -> 9(5)\n"
                        + "~ NAME from 5 -> 6\n"
                        + "+ NOTE from 12 length 4 pic X(4)\n"
                        + "removed     1\n"
                        + "changed     2\n"
                        + "added       1\n"
                        + "same        0\n"
                        + "record      15 -> 17\n",
                0);
    }

    @ParameterizedTest(name = "worked error: {0}")
    @MethodSource("workedErrors")
    void reportsEveryWorkedErrorWithoutPartialOutput(
            String scenario,
            List<String> args,
            String input,
            String message,
            int expectedReadCalls) {
        MemoryIo io = input == null ? new MemoryIo() : new MemoryIo(input);

        int status = Cli.run(args, io);

        assertEquals(2, status);
        assertEquals("", io.output);
        assertEquals(0, io.outCalls);
        assertEquals("recmill: MESSAGE\n".replace("MESSAGE", message), io.error);
        assertEquals(1, io.errCalls);
        assertEquals(expectedReadCalls, io.readCalls);
    }

    private static Stream<Arguments> workedErrors() {
        return Stream.of(
                Arguments.of("layout arity", List.of("layout"), null,
                        "usage: recmill layout [--groups] LAYOUT", 0),
                Arguments.of("flag value", List.of("layout", LAYOUT_A, "--groups=1"), null,
                        "--groups takes no value", 0),
                Arguments.of("column range", List.of("find", LAYOUT_A, "99"), null,
                        "invalid column \"99\" (expected an integer from 1 to 15)", 0),
                Arguments.of("empty layout", List.of("layout", ""), null,
                        "layout is empty", 0),
                Arguments.of("unterminated entry", List.of("layout", "01 R. 05 A PIC X"), null,
                        "entry 2: missing period at the end", 0),
                Arguments.of("invalid level", List.of("layout", "01 R. 5x A PIC X."), null,
                        "entry 2: invalid level \"5x\"", 0),
                Arguments.of("reserved name", List.of("layout", "01 R. 05 PIC X."), null,
                        "entry 2: invalid name \"PIC\"", 0),
                Arguments.of("invalid picture", List.of("layout", "01 R. 05 A PIC X(0)."), null,
                        "entry 2: invalid PIC \"X(0)\"", 0),
                Arguments.of("invalid occurs", List.of("layout", "01 R. 05 A PIC X OCCURS 100."), null,
                        "entry 2: invalid OCCURS \"100\"", 0),
                Arguments.of("duplicate clause", List.of("layout", "01 R. 05 A PIC X PIC X."), null,
                        "entry 2: unexpected word \"PIC\"", 0),
                Arguments.of("root level", List.of("layout", "05 R. 05 A PIC X."), null,
                        "entry 1: the layout must start with level 01", 0),
                Arguments.of("unmatched level", List.of("layout", "01 R. 05 A. 10 B PIC X. 07 C PIC X."), null,
                        "entry 4: level 07 does not match an enclosing level", 0),
                Arguments.of("duplicate name", List.of("layout", "01 R. 05 A PIC X. 05 A PIC X."), null,
                        "entry 3: duplicate name \"A\"", 0),
                Arguments.of("group picture", List.of("layout", "01 R. 05 A PIC X. 10 B PIC X."), null,
                        "entry 2: group \"A\" cannot have PIC", 0),
                Arguments.of("missing picture", List.of("layout", "01 R. 05 A."), null,
                        "entry 2: \"A\" needs PIC", 0),
                Arguments.of("invalid redefining target",
                        List.of("layout", "01 R. 05 A PIC X. 05 B REDEFINES C PIC X."), null,
                        "entry 3: invalid REDEFINES target \"C\"", 0),
                Arguments.of("long redefiner",
                        List.of("layout", "01 R. 05 A PIC X. 05 B REDEFINES A PIC X(2)."), null,
                        "entry 3: \"B\" (2 characters) is longer than \"A\" (1 characters)", 0),
                Arguments.of("length limit",
                        List.of("layout", "01 R. 05 A OCCURS 99 TIMES. 10 B OCCURS 99 TIMES. 15 C PIC X."),
                        null, "layout is 9801 characters (above 4096)", 0),
                Arguments.of("field limit",
                        List.of("layout", "01 R. 05 A OCCURS 50 TIMES. 10 B OCCURS 50 TIMES. 15 C PIC X."),
                        null, "layout has 2500 fields (above 2000)", 0),
                Arguments.of(
                        "invalid view",
                        List.of("decode",
                                "01 REC. 05 KIND PIC X. 05 BODY. 10 CODE PIC X(3). "
                                        + "10 QTY PIC 9(2) OCCURS 3 TIMES. "
                                        + "05 TEXT REDEFINES BODY PIC X(8). 05 TAIL PIC X(2).",
                                "CODE"),
                        null,
                        "\"CODE\" is not a redefining entry", 0),
                Arguments.of("conflicting views",
                        List.of("decode",
                                "01 R. 05 A PIC X. 05 P REDEFINES A PIC X. 05 Q REDEFINES A PIC X.",
                                "P", "Q"),
                        null, "\"P\" and \"Q\" redefine the same entry", 0),
                Arguments.of("no named field",
                        List.of("decode", "01 R. 05 FILLER PIC X."), null,
                        "no named field is selected", 0),
                Arguments.of(
                        "diff A \"01 R. 05 A.\"; D is 01 ORDER. 05 ID PIC 9(5). 05 NAME PIC X(6). "
                                + "05 NOTE PIC X(4). 05 FILLER PIC X(2).",
                        List.of("diff", LAYOUT_A, "01 R. 05 A."), null,
                        "LAYOUT2: entry 2: \"A\" needs PIC", 0),
                Arguments.of("no records", List.of("decode", LAYOUT_A), "\n\n",
                        "<stdin>: no input", 1),
                Arguments.of("record length", List.of("decode", LAYOUT_A), "0001ALICE 0015{x",
                        "line 1: record has 16 characters (expected 15)", 1),
                Arguments.of(
                        "decode A; stdin \"0001A\\tICE 0015{\"",
                        List.of("decode", LAYOUT_A), "0001A\tICE 0015{",
                        "line 1: column 6 is not a printable ASCII character", 1),
                Arguments.of("numeric field",
                        List.of("decode", LAYOUT_A), "0001ALICE 0x15{",
                        "line 1: AMT (columns 11-15): invalid value \"0x15{\"", 1),
                Arguments.of("value overflow", List.of("encode", LAYOUT_A), "ID=12345",
                        "line 1: invalid value \"12345\" for ID (PIC 9(4))", 1),
                Arguments.of("encode A; stdin \"ID=1\\nID=2\"",
                        List.of("encode", LAYOUT_A), "ID=1\nID=2",
                        "line 2: duplicate field \"ID\"", 1),
                Arguments.of("encode A; stdin \"ID=1\\nAGE=2\"",
                        List.of("encode", LAYOUT_A), "ID=1\nAGE=2",
                        "line 2: unknown field \"AGE\"", 1),
                Arguments.of("encode A; stdin \"ID=1\\nNAME\"",
                        List.of("encode", LAYOUT_A), "ID=1\nNAME",
                        "line 2: expected PATH=VALUE", 1));
    }

    @ParameterizedTest(name = "precedence: {0}")
    @MethodSource("precedenceErrors")
    void reportsPrecedenceAndReadFailuresWithoutPartialOutput(
            String scenario,
            List<String> args,
            String input,
            String message,
            int expectedReadCalls,
            boolean readFailure) {
        MemoryIo io = input == null ? new MemoryIo() : new MemoryIo(input);
        io.readFailure = readFailure;

        int status = Cli.run(args, io);

        assertEquals(2, status);
        assertEquals("", io.output);
        assertEquals(0, io.outCalls);
        assertEquals("recmill: " + message + "\n", io.error);
        assertEquals(1, io.errCalls);
        assertEquals(expectedReadCalls, io.readCalls);
    }

    private static Stream<Arguments> precedenceErrors() {
        return Stream.of(
                Arguments.of(
                        "options before arity",
                        List.of("layout", "--bogus"),
                        null,
                        "unknown option \"--bogus\"",
                        0,
                        false),
                Arguments.of(
                        "integer validation before arity",
                        List.of("validate", "--max=0"),
                        null,
                        "invalid --max \"0\" (expected an integer from 1 to 1000)",
                        0,
                        false),
                Arguments.of(
                        "left-to-right operands",
                        List.of("find", "", "0"),
                        null,
                        "layout is empty",
                        0,
                        false),
                Arguments.of(
                        "syntax before levels",
                        List.of("layout", "05 R. 05 A PIC X(0)."),
                        null,
                        "entry 2: invalid PIC \"X(0)\"",
                        0,
                        false),
                Arguments.of(
                        "levels before structure",
                        List.of("layout", "05 R. 05 A."),
                        null,
                        "entry 1: the layout must start with level 01",
                        0,
                        false),
                Arguments.of(
                        "structure before lengths",
                        List.of(
                                "layout",
                                "01 R. 05 A PIC X. 05 B REDEFINES A PIC X(2). 05 C."),
                        null,
                        "entry 4: \"C\" needs PIC",
                        0,
                        false),
                Arguments.of(
                        "selection before input",
                        List.of("decode", LAYOUT_B, "CODE"),
                        null,
                        "\"CODE\" is not a redefining entry",
                        0,
                        false),
                Arguments.of(
                        "decode A; stdin \"0001ALICE 0015{\\n0002BOB   01x0}\\n\"",
                        List.of("decode", LAYOUT_A),
                        "0001ALICE 0015{\n0002BOB   01x0}\n",
                        "line 2: AMT (columns 11-15): invalid value \"01x0}\"",
                        1,
                        false),
                Arguments.of(
                        "read failure",
                        List.of("decode", LAYOUT_A),
                        null,
                        "<stdin>: cannot read the input",
                        1,
                        true));
    }

    private static void assertFailure(List<String> args, String expectedError) {
        MemoryIo io = new MemoryIo();

        int status = Cli.run(args, io);

        assertEquals(2, status);
        assertEquals("", io.output);
        assertEquals(0, io.outCalls);
        assertEquals(expectedError, io.error);
        assertEquals(1, io.errCalls);
        assertEquals(0, io.readCalls);
    }

    private static void assertSuccess(
            MemoryIo io, String expectedOutput, int expectedReadCalls) {
        assertEquals(expectedOutput, io.output);
        assertEquals(1, io.outCalls);
        assertEquals("", io.error);
        assertEquals(0, io.errCalls);
        assertEquals(expectedReadCalls, io.readCalls);
    }

    private static final class MemoryIo implements Io {
        private final byte[] input;
        private int readCalls;
        private int outCalls;
        private int errCalls;
        private boolean readFailure;
        private String output = "";
        private String error = "";

        private MemoryIo() {
            this(new byte[0]);
        }

        private MemoryIo(String input) {
            this(input.getBytes(StandardCharsets.UTF_8));
        }

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
            output += text;
        }

        @Override
        public void err(String text) {
            errCalls++;
            error += text;
        }
    }
}
