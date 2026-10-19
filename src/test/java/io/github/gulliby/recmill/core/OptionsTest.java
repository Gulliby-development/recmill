package io.github.gulliby.recmill.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import io.github.gulliby.recmill.core.Options.Spec;
import org.junit.jupiter.api.Test;

class OptionsTest {
    private static List<Options.Spec> specs() {
        return List.of(Spec.flag("--groups"),Spec.integer("--max",1,1000,20));
    }

    @Test
    void parseUsesDefaultsWhenNoOptionsAreProvided() {
        Options.Parsed parsed = Options.parse(List.of(), specs());

        assertEquals(Map.of("--groups", 0, "--max", 20), parsed.values());
        assertEquals(List.of(), parsed.args());
    }

    @Test
    void parseKeepsOrdinaryOperandsInOrder() {
        Options.Parsed parsed = Options.parse(List.of("A", "-", "-x", "B"), specs());

        assertEquals(Map.of("--groups", 0, "--max", 20), parsed.values());
        assertEquals(List.of("A", "-", "-x", "B"), parsed.args());
    }

    @Test
    void parseConsumesOptionsBetweenOperands() {
        Options.Parsed parsed = Options.parse(List.of("A", "--groups", "--max=5", "B"), specs());

        assertEquals(Map.of("--groups", 1, "--max", 5), parsed.values());
        assertEquals(List.of("A", "B"), parsed.args());
    }

    @Test
    void parseUsesLastOccurrenceOfRepeatedOptions() {
        Options.Parsed parsed = Options.parse(
                List.of("--groups", "--groups", "--max", "4", "--max=7"), specs());

        assertEquals(Map.of("--groups", 1, "--max", 7), parsed.values());
        assertEquals(List.of(), parsed.args());
    }

    @Test
    void parseValidatesOnlyTheLastRawIntegerValue() {
        Options.Parsed parsed = Options.parse(List.of("--max=bad", "--max=5"), specs());

        assertEquals(Map.of("--groups", 0, "--max", 5), parsed.values());
        assertEquals(List.of(), parsed.args());
    }

    @Test
    void parseSanitizesUnknownOptionAssignments() {
        assertFailure(List.of("--wat=é"), "unknown option \"--wat=?\"");
    }

    @Test
    void parseDoesNotTreatDoubleDashAsAnOptionTerminator() {
        assertFailure(List.of("--"), "unknown option \"--\"");
    }

    @Test
    void parseDoesNotProvideImplicitHelp() {
        assertFailure(List.of("--help"), "unknown option \"--help\"");
    }

    @Test
    void parseRejectsAssignedValuesForFlags() {
        assertFailure(List.of("--groups=1"), "--groups takes no value");
    }

    @Test
    void parseRejectsEmptyAssignmentForFlags() {
        assertFailure(List.of("--groups="), "--groups takes no value");
    }

    @Test
    void parseRequiresAValueForIntegerOptions() {
        assertFailure(List.of("--max"), "--max needs a value");
    }

    @Test
    void parseConsumesAnOptionLikeArgumentAsTheIntegerValue() {
        assertFailure(List.of("--max", "--groups"),
                "invalid --max \"--groups\" (expected an integer from 1 to 1000)");
    }

    @Test
    void parseSplitsAssignmentsAtTheFirstEqualsSign() {
        assertFailure(List.of("--max=5=6"),
                "invalid --max \"5=6\" (expected an integer from 1 to 1000)");
    }

    @Test
    void parseRejectsAnEmptyIntegerAssignment() {
        assertFailure(List.of("--max="), "invalid --max \"\" (expected an integer from 1 to 1000)");
    }

    @Test
    void parseValidatesIntegerOptionsInSpecOrder() {
        List<Spec> orderedSpecs = List.of(Spec.integer("--a", 1, 9, 1), Spec.integer("--b", 1, 9, 1));

        assertFailure(List.of("--b=no", "--a=bad"), orderedSpecs,
                "invalid --a \"bad\" (expected an integer from 1 to 9)");
    }

    @Test
    void parseReportsConsumptionErrorsBeforeDelayedIntegerValidation() {
        List<Spec> orderedSpecs = List.of(Spec.integer("--a", 1, 9, 1), Spec.integer("--b", 1, 9, 1));

        assertFailure(List.of("--a=bad", "--unknown"), orderedSpecs, "unknown option \"--unknown\"");
    }

    @Test
    void parseReportsMissingValuesBeforeDelayedIntegerValidation() {
        List<Spec> orderedSpecs = List.of(Spec.integer("--a", 1, 9, 1), Spec.integer("--b", 1, 9, 1));

        assertFailure(List.of("--a=bad", "--b"), orderedSpecs, "--b needs a value");
    }

    @Test
    void parseStopsAtTheFirstLeftToRightConsumptionFailure() {
        List<Spec> orderedSpecs = List.of(Spec.integer("--a", 1, 9, 1), Spec.integer("--b", 1, 9, 1));

        assertFailure(List.of("--unknown", "--b"), orderedSpecs, "unknown option \"--unknown\"");
    }

    @Test
    void integerAcceptsCanonicalValuesAtBounds() {
        assertEquals(0, Options.integer("n", "0", 0, 999999));
        assertEquals(1, Options.integer("n", "1", 1, 999999));
        assertEquals(999999, Options.integer("n", "999999", 1, 999999));
    }

    @Test
    void integerReportsValuesOutsideInclusiveBounds() {
        assertIntegerFailure("column", "99", 1, 15,
                "invalid column \"99\" (expected an integer from 1 to 15)");
        assertIntegerFailure("n", "0", 1, 9,
                "invalid n \"0\" (expected an integer from 1 to 9)");
    }

    @Test
    void integerRejectsNoncanonicalSyntaxWithExactDiagnostics() {
        assertIntegerFailure("n", "", 0, 999999,
                "invalid n \"\" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", "00", 0, 999999,
                "invalid n \"00\" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", "01", 0, 999999,
                "invalid n \"01\" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", "+1", 0, 999999,
                "invalid n \"+1\" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", "-1", 0, 999999,
                "invalid n \"-1\" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", " 1", 0, 999999,
                "invalid n \" 1\" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", "1 ", 0, 999999,
                "invalid n \"1 \" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", "1.0", 0, 999999,
                "invalid n \"1.0\" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", "1e2", 0, 999999,
                "invalid n \"1e2\" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", "١", 0, 999999,
                "invalid n \"?\" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", "1000000", 0, 999999,
                "invalid n \"1000000\" (expected an integer from 0 to 999999)");
        assertIntegerFailure("n", "\n", 0, 999999,
                "invalid n \"?\" (expected an integer from 0 to 999999)");
    }

    private static void assertIntegerFailure(String name, String text, int lo, int hi,
            String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> Options.integer(name, text, lo, hi));
        assertEquals(expectedMessage, failure.getMessage());
    }

    private static void assertFailure(List<String> argv, String expectedMessage) {
        assertFailure(argv, specs(), expectedMessage);
    }

    private static void assertFailure(List<String> argv, List<Spec> specs, String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> Options.parse(argv, specs));
        assertEquals(expectedMessage, failure.getMessage());
    }
}
