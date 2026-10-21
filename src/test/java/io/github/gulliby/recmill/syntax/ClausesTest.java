package io.github.gulliby.recmill.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.recmill.Cli;
import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ClausesTest {
    private static void assertFailure(String layout, String message) {
        Failure failure = assertThrows(Failure.class, () -> Parser.parse(layout), layout);
        assertEquals(message, failure.getMessage(), layout);
    }

    private static String[] run(String stdin, String... args) {
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Io io = new Io() {
            @Override
            public byte[] readStdin() {
                return stdin.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public void out(String text) {
                out.append(text);
            }

            @Override
            public void err(String text) {
                err.append(text);
            }
        };
        int status = Cli.run(List.of(args), io);
        return new String[] {String.valueOf(status), out.toString(), err.toString()};
    }

    private static void assertCliError(String layout, String message) {
        String[] result = run("", "layout", layout);
        assertEquals("2", result[0], layout);
        assertEquals("", result[1], layout);
        assertEquals("recmill: " + message + "\n", result[2], layout);
    }

    private static void assertSameAs(String expectedLayout, String... layouts) {
        List<Entry> expected = Parser.parse(expectedLayout);
        for (String layout : layouts) {
            assertEquals(expected, Parser.parse(layout), layout);
        }
    }

    @Test
    void usageDisplayIsAcceptedAndIgnored() {
        assertSameAs(
                "01 R. 05 A PIC X(3).",
                "01 R. 05 A PIC X(3) USAGE IS DISPLAY.",
                "01 R. 05 A PIC X(3) USAGE DISPLAY.",
                "01 R. 05 A PIC X(3) DISPLAY.",
                "01 R. 05 A PIC X(3) usage display.",
                "01 R. 05 A PIC X(3) Usage Is Display.");
        assertSameAs(
                "01 R. 05 B PIC X. 05 A PIC X(3) OCCURS 2 REDEFINES B.",
                "01 R. 05 B PIC X. 05 A USAGE DISPLAY PIC X(3) OCCURS 2 REDEFINES B.",
                "01 R. 05 B PIC X. 05 A PIC X(3) DISPLAY OCCURS 2 REDEFINES B.",
                "01 R. 05 B PIC X. 05 A PIC X(3) OCCURS 2 USAGE IS DISPLAY REDEFINES B.",
                "01 R. 05 B PIC X. 05 A PIC X(3) OCCURS 2 REDEFINES B DISPLAY.");

        String[] spellings = {"USAGE IS DISPLAY", "USAGE DISPLAY", "DISPLAY"};
        Random random = new Random(1234);
        for (int round = 0; round < 150; round++) {
            List<String> clauses = new ArrayList<>(List.of("PIC X(3)", "OCCURS 2", "REDEFINES B"));
            java.util.Collections.shuffle(clauses, random);
            String plain = "01 R. 05 B PIC X. 05 A " + String.join(" ", clauses) + ".";
            String spelling = spellings[random.nextInt(spellings.length)];
            spelling = random.nextBoolean() ? spelling.toLowerCase() : spelling;
            clauses.add(random.nextInt(clauses.size() + 1), spelling);
            String withUsage = "01 R. 05 B PIC X. 05 A " + String.join(" ", clauses) + ".";
            assertEquals(Parser.parse(plain), Parser.parse(withUsage), withUsage);
        }

        String[] result = run(
                "",
                "layout",
                "01 ORDER. 05 ID PIC 9(4) USAGE IS DISPLAY. 05 NAME PIC X(6) VALUE 'N/A'. "
                        + "05 AMT PIC S9(3)V99 DISPLAY VALUE ZEROS.");
        assertEquals("0", result[0]);
        assertEquals(
                "path  from  to  pic\nID    1     4   9(4)\nNAME  5     10  X(6)\nAMT   11    15  S9(3)V9(2)\nlength      15\n",
                result[1]);
        assertEquals("", result[2]);
    }

    @Test
    void usageMayComeBeforeThePicture() {
        assertSameAs("01 R. 05 A PIC X(3).", "01 R. 05 A DISPLAY PIC X(3).");
    }

    @Test
    void usageOtherThanDisplayAndRepeatedUsageAreRejected() {
        assertFailure("01 R. 05 A PIC X USAGE COMP.", "entry 2: invalid USAGE \"COMP\"");
        assertFailure("01 R. 05 A PIC X USAGE.", "entry 2: invalid USAGE \"\"");
        assertFailure("01 R. 05 A PIC X DISPLAY DISPLAY.", "entry 2: unexpected word \"DISPLAY\"");
        assertFailure("01 R. 05 A PIC X USAGE DISPLAY DISPLAY.", "entry 2: unexpected word \"DISPLAY\"");
        assertCliError("01 R. 05 A PIC X USAGE COMP.", "entry 2: invalid USAGE \"COMP\"");
        assertCliError("01 R. 05 A PIC X DISPLAY DISPLAY.", "entry 2: unexpected word \"DISPLAY\"");
    }

    @Test
    void valueClauseAcceptsNumbersFigurativesAndLiterals() {
        assertSameAs(
                "01 R. 05 A PIC X.",
                "01 R. 05 A PIC X VALUE IS 'a b'.",
                "01 R. 05 A PIC X VALUE SPACE.",
                "01 R. 05 A PIC X VALUE SPACES.",
                "01 R. 05 A VALUE ZERO PIC X.",
                "01 R. 05 A PIC X VALUE zeros.",
                "01 R. 05 A PIC X VALUE ZEROES.",
                "01 R. 05 A PIC X VALUE -12.50.",
                "01 R. 05 A PIC X VALUE +1.5.",
                "01 R. 05 A PIC X VALUE 0.",
                "01 R. 05 A PIC X value is 'abc'.",
                "01 R. 05 A PIC X VALUE \"N/A\".",
                "01 R. 05 A PIC X DISPLAY VALUE ZEROS.");

        String[] figuratives = {"SPACE", "SPACES", "ZERO", "ZEROS", "ZEROES"};
        String letters = "ab .*>c";
        Random random = new Random(4321);
        for (int round = 0; round < 150; round++) {
            String operand;
            switch (random.nextInt(3)) {
                case 0 -> {
                    String sign = new String[] {"", "+", "-"}[random.nextInt(3)];
                    String fraction = random.nextBoolean() ? "." + random.nextInt(1000) : "";
                    operand = sign + random.nextInt(100000) + fraction;
                }
                case 1 -> {
                    String word = figuratives[random.nextInt(figuratives.length)];
                    operand = random.nextBoolean() ? word.toLowerCase() : word;
                }
                default -> {
                    char quote = random.nextBoolean() ? '\'' : '"';
                    StringBuilder literal = new StringBuilder().append(quote);
                    int length = random.nextInt(8);
                    for (int i = 0; i < length; i++) {
                        if (random.nextInt(5) == 0) {
                            literal.append(quote).append(quote);
                        } else {
                            literal.append(letters.charAt(random.nextInt(letters.length())));
                        }
                    }
                    operand = literal.append(quote).toString();
                }
            }
            String is = random.nextBoolean() ? "IS " : "";
            String value = "VALUE " + is + operand;
            String layout = random.nextBoolean()
                    ? "01 R. 05 A PIC X " + value + "."
                    : "01 R. 05 A " + value + " PIC X.";
            assertEquals(Parser.parse("01 R. 05 A PIC X."), Parser.parse(layout), layout);
        }

        String clauses =
                "01 ORDER. 05 ID PIC 9(4) USAGE DISPLAY. 05 NAME PIC X(6) VALUE IS 'A B. C'. "
                        + "05 AMT PIC S9(3)V99 VALUE -12.50.";
        String plainLayout = "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99.";
        String[] decoded = run("0001ALICE 0015{\n", "decode", clauses);
        assertEquals("0", decoded[0]);
        assertEquals("ID=1\nNAME=ALICE\nAMT=1.50\n", decoded[1]);
        assertEquals("", decoded[2]);
        String[] encodedWith = run(decoded[1], "encode", clauses);
        String[] encodedPlain = run(decoded[1], "encode", plainLayout);
        assertEquals(encodedPlain[0], encodedWith[0]);
        assertEquals(encodedPlain[1], encodedWith[1]);
        assertEquals(encodedPlain[2], encodedWith[2]);
    }

    @Test
    void invalidValueOperandsAreRejected() {
        assertFailure("01 R. 05 A PIC X VALUE 1.2.3.", "entry 2: invalid VALUE \"1.2.3\"");
        assertFailure("01 R. 05 A PIC X VALUE +.", "entry 2: invalid VALUE \"+\"");
        assertFailure("01 R. 05 A PIC X VALUE.", "entry 2: invalid VALUE \"\"");
        assertFailure("01 R. 05 A PIC X VALUE 1 VALUE 2.", "entry 2: unexpected word \"VALUE\"");
        assertFailure("01 R. 05 A PIC X VALUE 'x'y.", "entry 2: invalid VALUE \"'x'y\"");
        assertCliError("01 R. 05 A PIC X VALUE 1.2.3.", "entry 2: invalid VALUE \"1.2.3\"");
        assertCliError("01 R. 05 A PIC X VALUE.", "entry 2: invalid VALUE \"\"");
        assertCliError("01 R. 05 A PIC X VALUE 1 VALUE 2.", "entry 2: unexpected word \"VALUE\"");
        assertCliError("01 R. 05 A PIC X VALUE 'x'y.", "entry 2: invalid VALUE \"'x'y\"");
    }

    @Test
    void literalKeepsSpacesPeriodsAndDoubledQuotes() {
        assertEquals(
                List.of(List.of("05", "A", "VALUE", "'it''s. ok'")),
                Words.entries("05 A VALUE 'it''s. ok'."));
        assertEquals(
                List.of(List.of("05", "A", "VALUE", "\"x *> y\"")),
                Words.entries("05 A VALUE \"x *> y\"."));
        assertEquals(
                List.of(List.of("01", "R"), List.of("05", "A", "VALUE", "'N/A'")),
                Words.entries("01 R. 05 A VALUE 'N/A'."));
        assertEquals(
                List.of(List.of("05", "A", "VALUE", "'a\tb'")),
                Words.entries("05 A VALUE 'a\tb'."));
        assertEquals(
                List.of(List.of("05", "A", "VALUE", "'a\nb'")),
                Words.entries("05 A VALUE 'a\nb'."));
    }

    @Test
    void unterminatedLiteralNamesTheEntry() {
        assertFailure("01 R. 05 A PIC X VALUE 'abc.", "entry 2: unterminated literal");
        assertFailure("05 A VALUE \"abc.", "entry 1: unterminated literal");
        assertCliError("01 R. 05 A PIC X VALUE 'abc.", "entry 2: unterminated literal");
    }

    @Test
    void quoteInsideAWordIsAnOrdinaryCharacter() {
        assertFailure("01 R'x.", "entry 1: invalid name \"R'x\"");
        Picture x = Parser.parse("01 R. 05 A PIC X.").get(1).pic();
        for (String name : List.of("VALUE", "USAGE", "DISPLAY")) {
            List<Entry> entries = Parser.parse("01 R. 05 " + name + " PIC X.");
            assertEquals(2, entries.size(), name);
            assertEquals(name, entries.get(1).name());
            assertEquals(x, entries.get(1).pic(), name);
        }
    }
}
