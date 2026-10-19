package io.github.gulliby.recmill.records;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.recmill.model.Layout;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfilerTest {
    private static final Layout LAYOUT =
            Layout.build("01 R. 05 N PIC 9(2). 05 T PIC X(2).");

    @Test
    void profilesMixedValidBlankAndBadFieldsIndependently() {
        Profiler.Profile profile = profile(List.of(
                new Records.Line(1, "01A "),
                new Records.Line(2, "  B "),
                new Records.Line(3, "x2  "),
                new Records.Line(4, "02A ")));

        assertEquals(4, profile.records());
        assertEquals(0, profile.skipped());
        assertEquals("records=4, skipped=0", totals(profile));
        assertEquals(List.of(
                List.of("N", "9(2)", "1", "1", "2", "1", "2", "3"),
                List.of("T", "X(2)", "1", "0", "2", "A", "B", "-")),
                profile.rows());
    }

    @Test
    void marksFieldsWithoutUsableValuesAndRendersMissingStatistics() {
        Profiler.Profile profile = profile(List.of(
                new Records.Line(1, "    "),
                new Records.Line(2, "x2  ")));

        assertEquals(2, profile.records());
        assertEquals(0, profile.skipped());
        assertEquals("records=2, skipped=0", totals(profile));
        assertEquals(List.of(
                List.of("N", "9(2)", "1", "1", "0", "-", "-", "-"),
                List.of("T", "X(2)", "2", "0", "0", "-", "-", "-")),
                profile.rows());
    }

    @Test
    void skipsEveryRecordWithARecordLevelProblem() {
        // AC-002 literal input: ["01","01\t ","01\u00e9 ","01ABC"]
        List<Records.Line> lines = List.of(
                new Records.Line(1, "01"),
                new Records.Line(2, "01\t "),
                new Records.Line(3, "01\u00e9 "),
                new Records.Line(4, "01ABC"));
        assertEquals(List.of("01", "01\t ", "01\u00e9 ", "01ABC"),
                List.of(lines.get(0).text(), lines.get(1).text(), lines.get(2).text(), lines.get(3).text()));
        Profiler.Profile profile = profile(lines);

        assertEquals(4, profile.records());
        assertEquals(4, profile.skipped());
        assertEquals("records=4, skipped=4", totals(profile));
        // Expected summary: blank=bad=distinct=0 and min=max=sum="-".
        assertEquals(List.of(
                List.of("N", "9(2)", "0", "0", "0", "-", "-", "-"),
                List.of("T", "X(2)", "0", "0", "0", "-", "-", "-")),
                profile.rows());
    }

    @Test
    void countsZeroAsAUsableNumericValue() {
        Profiler.Profile profile = profile(List.of(new Records.Line(1, "00  ")));

        assertEquals(1, profile.records());
        assertEquals(0, profile.skipped());
        assertEquals("records=1, skipped=0", totals(profile));
        // Expected summary: distinct=1, min=max=sum="0", blank=bad=0; the text field has blank=1.
        assertEquals(List.of(
                List.of("N", "9(2)", "0", "0", "1", "0", "0", "0"),
                List.of("T", "X(2)", "1", "0", "0", "-", "-", "-")),
                profile.rows());
    }

    // PIC S9(3)V99, ["0015{","00150","0120}","00000","0000}"]: distinct=3, min="-12.00", max="1.50", sum="-9.00".
    @Test
    void comparesSignedNumericValuesAndSumsDecodedDuplicatesExactly() {
        Layout layout = Layout.build("01 R. 05 N PIC S9(3)V99.");
        Profiler.Profile profile = Profiler.profile(layout, layout.slots(), List.of(
                new Records.Line(1, "0015{"),
                new Records.Line(2, "00150"),
                new Records.Line(3, "0120}"),
                new Records.Line(4, "00000"),
                new Records.Line(5, "0000}")));

        assertEquals(5, profile.records());
        assertEquals(0, profile.skipped());
        assertEquals(List.of(List.of("N", "S9(3)V9(2)", "0", "0", "3",
                "-12.00", "1.50", "-9.00")), profile.rows());
    }

    // PIC 9(2), ["02","10"]: numeric order gives min="2", max="10", sum="12".
    @Test
    void comparesNumericValuesByMagnitudeRatherThanText() {
        Layout layout = Layout.build("01 R. 05 N PIC 9(2).");
        Profiler.Profile profile = Profiler.profile(layout, layout.slots(), List.of(
                new Records.Line(1, "02"),
                new Records.Line(2, "10")));

        assertEquals(2, profile.records());
        assertEquals(0, profile.skipped());
        assertEquals(List.of(List.of("N", "9(2)", "0", "0", "2", "2", "10", "12")),
                profile.rows());
    }

    // PIC 9(18), ["999999999999999999","999999999999999999"]: sum="1999999999999999998".
    @Test
    void sumsRepeatedEighteenDigitValuesWithoutPrecisionLoss() {
        Layout layout = Layout.build("01 R. 05 N PIC 9(18).");
        Profiler.Profile profile = Profiler.profile(layout, layout.slots(), List.of(
                new Records.Line(1, "999999999999999999"),
                new Records.Line(2, "999999999999999999")));

        assertEquals(2, profile.records());
        assertEquals(0, profile.skipped());
        assertEquals(List.of(List.of("N", "9(18)", "0", "0", "1",
                "999999999999999999", "999999999999999999", "1999999999999999998")),
                profile.rows());
    }

    // PIC V99, ["05","10"]: min="0.05", max="0.10", sum="0.15".
    @Test
    void formatsNumbersWhenPictureHasNoWholeDigits() {
        Layout layout = Layout.build("01 R. 05 N PIC V99.");
        Profiler.Profile profile = Profiler.profile(layout, layout.slots(), List.of(
                new Records.Line(1, "05"),
                new Records.Line(2, "10")));

        assertEquals(2, profile.records());
        assertEquals(0, profile.skipped());
        assertEquals(List.of(List.of("N", "V9(2)", "0", "0", "2",
                "0.05", "0.10", "0.15")), profile.rows());
    }

    // PIC S9V99, ["10}","100"]: min="-1.00", max="1.00", sum="0.00".
    @Test
    void preservesPictureScaleWhenSignedValuesCancel() {
        Layout layout = Layout.build("01 R. 05 N PIC S9V99.");
        Profiler.Profile profile = Profiler.profile(layout, layout.slots(), List.of(
                new Records.Line(1, "10}"),
                new Records.Line(2, "100")));

        assertEquals(2, profile.records());
        assertEquals(0, profile.skipped());
        assertEquals(List.of(List.of("N", "S9(1)V9(2)", "0", "0", "2",
                "-1.00", "1.00", "0.00")), profile.rows());
    }

    // PIC X(3), ["a  ","Z  "," A ","a  "]: row min=" A", max="a".
    @Test
    void comparesTextExtremaByCharacterOrderAndKeepsLeadingSpaces() {
        Layout layout = Layout.build("01 R. 05 T PIC X(3).");
        Profiler.Profile profile = Profiler.profile(layout, layout.slots(), List.of(
                new Records.Line(1, "a  "),
                new Records.Line(2, "Z  "),
                new Records.Line(3, " A "),
                new Records.Line(4, "a  ")));

        assertEquals(4, profile.records());
        assertEquals(0, profile.skipped());
        assertEquals(List.of(List.of("T", "X(3)", "0", "0", "3", " A", "a", "-")),
                profile.rows());
    }

    private static String totals(Profiler.Profile profile) {
        return "records=" + profile.records() + ", skipped=" + profile.skipped();
    }

    private static Profiler.Profile profile(List<Records.Line> lines) {
        return Profiler.profile(LAYOUT, LAYOUT.slots(), lines);
    }
}
