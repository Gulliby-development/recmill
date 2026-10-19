package io.github.gulliby.recmill;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.recmill.core.Io;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MainTest {
    private static final String LAYOUT_A =
            "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99.";
    private static final String LAYOUT_B =
            "01 REC. 05 KIND PIC X. 05 BODY. 10 CODE PIC X(3). 10 QTY PIC 9(2) OCCURS 3 TIMES. 05 TEXT REDEFINES BODY PIC X(8). 05 TAIL PIC X(2).";
    private static final String PROCESS_COMMAND =
            "java -cp target/classes io.github.gulliby.recmill.Main decode \"01 R. 05 A PIC X(2).\"; stdin bytes 41 42 FF 0A";
    private static final String LAYOUT_D =
            "01 ORDER. 05 ID PIC 9(5). 05 NAME PIC X(6). 05 NOTE PIC X(4). 05 FILLER PIC X(2).";
    private static final String FENCE = String.valueOf((char) 96).repeat(3);
    private static final String USAGE = """
            recmill layout [--groups] LAYOUT
            recmill find LAYOUT COLUMN
            recmill decode LAYOUT [NAME...]
            recmill encode LAYOUT [NAME...]
            recmill csv [--no-header] LAYOUT [NAME...]
            recmill validate [--max MAX] LAYOUT [NAME...]
            recmill profile LAYOUT [NAME...]
            recmill diff LAYOUT1 LAYOUT2""";
    private static final String LAYOUT_OUTPUT = """
            path  from  to  pic
            ID    1     4   9(4)
            NAME  5     10  X(6)
            AMT   11    15  S9(3)V9(2)
            length      15""";
    private static final String DECODE_OUTPUT = """
            ID=1
            NAME=ALICE
            AMT=1.50

            ID=2
            NAME=BOB
            AMT=-12.00""";
    private static final String DIFF_OUTPUT = """
            - AMT from 11 length 5 pic S9(3)V9(2)
            ~ ID length 4 -> 5, pic 9(4) -> 9(5)
            ~ NAME from 5 -> 6
            + NOTE from 12 length 4 pic X(4)
            removed     1
            changed     2
            added       1
            same        0
            record      15 -> 17""";

    @Test
    void rejectsMalformedUtf8OnRealStandardInput() throws Exception {
        String executableName = System.getProperty("os.name").startsWith("Windows")
                ? "java.exe"
                : "java";
        Path executable = Path.of(System.getProperty("java.home"), "bin", executableName);
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Process process = new ProcessBuilder(
                        executable.toString(),
                        "-cp",
                        "target/classes",
                        "io.github.gulliby.recmill.Main",
                        "decode",
                        "01 R. 05 A PIC X(2).")
                .directory(workingDirectory.toFile())
                .start();

        try {
            process.getOutputStream().write(new byte[] {0x41, 0x42, (byte) 0xFF, 0x0A});
            process.getOutputStream().close();

            assertTrue(
                    process.waitFor(30, TimeUnit.SECONDS),
                    PROCESS_COMMAND + " child process did not exit within 30 seconds");
            assertEquals(2, process.exitValue());
            assertArrayEquals(new byte[0], process.getInputStream().readAllBytes());
            assertArrayEquals(
                    "recmill: <stdin>: input is not valid UTF-8\n".getBytes(StandardCharsets.UTF_8),
                    process.getErrorStream().readAllBytes(),
                    "exit 2; stdout zero bytes; stderr exactly UTF-8 bytes of \"recmill: <stdin>: input is not valid UTF-8\\n\"");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(30, TimeUnit.SECONDS);
            }
            process.getOutputStream().close();
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }

    @Test
    void documentsUsageAndExamplesThatMatchCliOutput() throws IOException {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r", "\n");

        assertTrue(readme.contains("## Install"));
        assertTrue(readme.contains("## Usage"));
        assertTrue(readme.contains("## Layouts"));
        assertTrue(readme.contains("## Commands"));
        assertTrue(readme.contains("## Exit codes"));
        assertTrue(readme.contains("## Development"));
        assertTrue(readme.contains("Java 21"));
        assertTrue(readme.contains("Maven"));
        assertTrue(readme.contains("mvn -B verify"));
        assertTrue(readme.contains("mvn -B -q verify"));
        assertTrue(readme.contains("java -cp target/classes io.github.gulliby.recmill.Main COMMAND"));
        assertTrue(readme.contains("\"" + LAYOUT_A + "\""));
        assertTrue(readme.contains("\"" + LAYOUT_D + "\""));
        assertTrue(readme.contains("0001ALICE 0015{" + String.valueOf((char) 96)
                + " followed by LF and "
                + String.valueOf((char) 96) + "0002BOB   0120}" + String.valueOf((char) 96)
                + " followed by LF"));

        assertEquals(4, countTextCodeBlocks(readme));
        assertTrue(readme.contains(FENCE + "text\n" + USAGE + "\n" + FENCE));
        assertTrue(readme.contains(FENCE + "text\n" + LAYOUT_OUTPUT + "\n" + FENCE));
        assertTrue(readme.contains(FENCE + "text\n" + DECODE_OUTPUT + "\n" + FENCE));
        assertTrue(readme.contains(FENCE + "text\n" + DIFF_OUTPUT + "\n" + FENCE));

        assertCli(
                List.of("layout", LAYOUT_A),
                new byte[0],
                0,
                LAYOUT_OUTPUT + "\n",
                0);
        assertCli(
                List.of("decode", LAYOUT_A),
                "0001ALICE 0015{\n0002BOB   0120}\n".getBytes(StandardCharsets.UTF_8),
                0,
                DECODE_OUTPUT + "\n",
                1);
        assertCli(
                List.of("diff", LAYOUT_A, LAYOUT_D),
                new byte[0],
                1,
                DIFF_OUTPUT + "\n",
                0);
        assertCli(
                List.of("find", LAYOUT_B, "7"),
                new byte[0],
                0,
                "column      7\nfield       BODY.QTY(2) 7-8 9(2)\nfield       TEXT 2-9 X(8)\n",
                0);
    }

    private static int countTextCodeBlocks(String text) {
        Matcher matcher = Pattern.compile(FENCE + "text\n").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void assertCli(
            List<String> arguments,
            byte[] input,
            int expectedStatus,
            String expectedOutput,
            int expectedReads) {
        MemoryIo io = new MemoryIo(input);

        assertEquals(expectedStatus, Cli.run(arguments, io));
        assertEquals(expectedOutput, io.output);
        assertEquals("", io.error);
        assertEquals(1, io.outCalls);
        assertEquals(0, io.errCalls);
        assertEquals(expectedReads, io.readCalls);
    }

    private static final class MemoryIo implements Io {
        private final byte[] input;
        private int readCalls;
        private int outCalls;
        private int errCalls;
        private String output = "";
        private String error = "";

        private MemoryIo(byte[] input) {
            this.input = input;
        }

        @Override
        public byte[] readStdin() throws IOException {
            readCalls++;
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
