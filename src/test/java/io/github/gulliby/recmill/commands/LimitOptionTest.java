package io.github.gulliby.recmill.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LimitOptionTest {
    private static final String LAYOUT = "01 R. 05 ID PIC 9(2). 05 NAME PIC X(3).";
    private static final String THREE_RECORDS = "01AAA\n02BBB\n03CCC\n";

    @Test
    void decodeStopsAfterTheFirstRecords() {
        MemoryIo io = new MemoryIo(THREE_RECORDS);

        assertEquals(0, DecodeCommand.run(List.of("--limit", "2", LAYOUT), io));

        assertEquals("ID=1\nNAME=AAA\n\nID=2\nNAME=BBB\n", io.output);
    }

    @Test
    void decodeAcceptsTheEqualsSpellingAndTheLastValueWins() {
        MemoryIo io = new MemoryIo(THREE_RECORDS);

        assertEquals(0, DecodeCommand.run(List.of("--limit=3", "--limit=1", LAYOUT), io));

        assertEquals("ID=1\nNAME=AAA\n", io.output);
    }

    @Test
    void decodeWithoutTheOptionAndWithALargeLimitReadEverything() {
        String all = "ID=1\nNAME=AAA\n\nID=2\nNAME=BBB\n\nID=3\nNAME=CCC\n";
        MemoryIo plain = new MemoryIo(THREE_RECORDS);
        MemoryIo large = new MemoryIo(THREE_RECORDS);

        assertEquals(0, DecodeCommand.run(List.of(LAYOUT), plain));
        assertEquals(0, DecodeCommand.run(List.of("--limit", "999999", LAYOUT), large));

        assertEquals(all, plain.output);
        assertEquals(all, large.output);
    }

    @Test
    void emptyLinesDoNotCountAsRecords() {
        MemoryIo io = new MemoryIo("\n01AAA\n\n02BBB\n03CCC\n");

        assertEquals(0, DecodeCommand.run(List.of("--limit", "2", LAYOUT), io));

        assertEquals("ID=1\nNAME=AAA\n\nID=2\nNAME=BBB\n", io.output);
    }

    @Test
    void aBadRecordAfterTheLimitIsNotAnError() {
        MemoryIo io = new MemoryIo("01AAA\n02B\n");

        assertEquals(0, DecodeCommand.run(List.of("--limit", "1", LAYOUT), io));

        assertEquals("ID=1\nNAME=AAA\n", io.output);
    }

    @Test
    void aBadRecordInsideTheLimitStillFails() {
        MemoryIo io = new MemoryIo("01AAA\n02B\n");

        Failure failure =
                assertThrows(Failure.class, () -> DecodeCommand.run(List.of("--limit", "2", LAYOUT), io));

        assertEquals("line 2: record has 3 characters (expected 5)", failure.getMessage());
    }

    @Test
    void csvStopsAfterTheFirstRecordsAndKeepsTheHeader() {
        MemoryIo io = new MemoryIo(THREE_RECORDS);

        assertEquals(0, CsvCommand.run(List.of("--limit", "2", LAYOUT), io));

        assertEquals("ID,NAME\n1,AAA\n2,BBB\n", io.output);
    }

    @Test
    void csvLimitWorksTogetherWithNoHeader() {
        MemoryIo io = new MemoryIo(THREE_RECORDS);

        assertEquals(0, CsvCommand.run(List.of("--no-header", "--limit=1", LAYOUT), io));

        assertEquals("1,AAA\n", io.output);
    }

    @Test
    void rejectsEveryBadValueBeforeReadingInput() {
        for (String value : List.of("0", "00", "01", "-1", "+1", "1.5", "x", "", "1000000", "1234567")) {
            MemoryIo io = new MemoryIo(THREE_RECORDS);

            Failure failure =
                    assertThrows(
                            Failure.class, () -> DecodeCommand.run(List.of("--limit=" + value, LAYOUT), io));

            assertEquals(
                    "invalid --limit \"" + value + "\" (expected an integer from 1 to 999999)",
                    failure.getMessage());
            assertEquals(0, io.readCalls);
        }
    }

    @Test
    void needsAValueAndTheOtherCommandsDoNotTakeIt() {
        MemoryIo io = new MemoryIo(THREE_RECORDS);

        assertEquals(
                "--limit needs a value",
                assertThrows(Failure.class, () -> CsvCommand.run(List.of(LAYOUT, "--limit"), io))
                        .getMessage());
        assertEquals(
                "unknown option \"--limit\"",
                assertThrows(Failure.class, () -> ValidateCommand.run(List.of("--limit", "1", LAYOUT), io))
                        .getMessage());
        assertEquals(0, io.readCalls);
    }

    @Test
    void theUsageLinesNameTheOption() {
        assertEquals("recmill decode [--limit N] LAYOUT [NAME...]", DecodeCommand.USAGE);
        assertEquals("recmill csv [--no-header] [--limit N] LAYOUT [NAME...]", CsvCommand.USAGE);
    }

    private static final class MemoryIo implements Io {
        private final byte[] input;
        private final List<String> errors = new ArrayList<>();
        private int readCalls;
        private String output = "";

        private MemoryIo(String text) {
            this.input = text.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] readStdin() {
            readCalls++;
            return input;
        }

        @Override
        public void out(String text) {
            output = text;
        }

        @Override
        public void err(String text) {
            errors.add(text);
        }
    }
}
