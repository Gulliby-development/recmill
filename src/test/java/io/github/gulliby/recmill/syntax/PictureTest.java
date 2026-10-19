package io.github.gulliby.recmill.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


import org.junit.jupiter.api.Test;

class PictureTest {
    @Test
    void parsesSingleAlphanumericItem() {
        assertEquals(new Picture(false, false, 1, 0, 1), Picture.parse("X"));
    }

    @Test
    void parsesLowercaseAlphanumericItem() {
        assertEquals(new Picture(false, false, 1, 0, 1), Picture.parse("x"));
    }

    @Test
    void parsesRepeatedAlphanumericItems() {
        assertEquals(new Picture(false, false, 3, 0, 3), Picture.parse("xxx"));
    }

    @Test
    void parsesCountedAndSingleAlphanumericItems() {
        assertEquals(new Picture(false, false, 4, 0, 4), Picture.parse("X(3)X"));
    }

    @Test
    void parsesMixedCaseCountedAlphanumericItems() {
        assertEquals(new Picture(false, false, 5, 0, 5), Picture.parse("x(2)X(3)"));
    }

    @Test
    void parsesMaximumAlphanumericCount() {
        assertEquals(new Picture(false, false, 255, 0, 255), Picture.parse("X(255)"));
    }

    @Test
    void parsesAlphanumericAggregateMaximum() {
        assertEquals(new Picture(false, false, 255, 0, 255), Picture.parse("X(254)X"));
    }

    @Test
    void parsesNumericPictureExamples() {
        assertEquals(new Picture(true, false, 1, 0, 1), Picture.parse("9"));
        assertEquals(new Picture(true, true, 5, 0, 5), Picture.parse("S9(5)"));
        assertEquals(new Picture(true, false, 3, 2, 5), Picture.parse("9(3)V99"));
        assertEquals(new Picture(true, true, 3, 2, 5), Picture.parse("s9(3)v99"));
        assertEquals(new Picture(true, false, 0, 2, 2), Picture.parse("V99"));
        assertEquals(new Picture(true, true, 0, 2, 2), Picture.parse("SV99"));
        assertEquals(new Picture(true, false, 2, 0, 2), Picture.parse("99V"));
        assertEquals(new Picture(true, true, 2, 0, 2), Picture.parse("S99V"));
        assertEquals(new Picture(true, false, 18, 0, 18), Picture.parse("9(18)"));
        assertEquals(new Picture(true, true, 9, 9, 18), Picture.parse("S9(9)V9(9)"));
        assertEquals(new Picture(true, false, 0, 18, 18), Picture.parse("V9(18)"));
    }

    @Test
    void rejectsInvalidKindsAndMisplacedSigns() {
        assertEquals(null, Picture.parse(""));
        assertEquals(null, Picture.parse("S"));
        assertEquals(null, Picture.parse("V"));
        assertEquals(null, Picture.parse("SV"));
        assertEquals(null, Picture.parse("XS"));
        assertEquals(null, Picture.parse("X9"));
        assertEquals(null, Picture.parse("9X"));
        assertEquals(null, Picture.parse("SX"));
        assertEquals(null, Picture.parse("XV"));
        assertEquals(null, Picture.parse("9V9V9"));
        assertEquals(null, Picture.parse("VV9"));
        assertEquals(null, Picture.parse("9S"));
        assertEquals(null, Picture.parse("SS9"));
        assertEquals(null, Picture.parse("9S9"));
    }

    @Test
    void rejectsMalformedAndExcessiveCounts() {
        assertNull(Picture.parse("X(0)"));
        assertNull(Picture.parse("9(0)"));
        assertNull(Picture.parse("X(01)"));
        assertNull(Picture.parse("9(01)"));
        assertNull(Picture.parse("X()"));
        assertNull(Picture.parse("X(3"));
        assertNull(Picture.parse("X3)"));
        assertNull(Picture.parse("X((3))"));
        assertNull(Picture.parse("X(1)(2)"));
        assertNull(Picture.parse("X(+3)"));
        assertNull(Picture.parse("X(-3)"));
        assertNull(Picture.parse("X(1.0)"));
        assertNull(Picture.parse("X( 3)"));
        assertNull(Picture.parse("V(2)99"));
        assertNull(Picture.parse("9V(1)"));
        assertNull(Picture.parse("X(1000)"));
        assertNull(Picture.parse("9(1000)"));
        assertNull(Picture.parse("X(999999999999999999999)"));
    }

    @Test
    void rejectsExcessiveTotalsAndForbiddenCharacters() {
        assertNull(Picture.parse("x(256)"));
        assertNull(Picture.parse("X(255)X"));
        assertNull(Picture.parse("X(999)"));
        assertNull(Picture.parse("9(19)"));
        assertNull(Picture.parse("9(18)9"));
        assertNull(Picture.parse("9(9)V9(10)"));
        assertNull(Picture.parse("9(999)"));
        assertNull(Picture.parse(" X"));
        assertNull(Picture.parse("X "));
        assertNull(Picture.parse("9 9"));
        assertNull(Picture.parse("X\t"));
        assertNull(Picture.parse("9\n"));
        assertNull(Picture.parse("X."));
        assertNull(Picture.parse("\uFF38"));
        assertNull(Picture.parse("\uFF19"));
        assertNull(Picture.parse("X(\u0663)"));
    }

    @Test
    void rendersCanonicalTextAndRoundTrips() {
        assertCanonical("X", new Picture(false, false, 1, 0, 1), "X(1)");
        assertCanonical("x(2)X(3)", new Picture(false, false, 5, 0, 5), "X(5)");
        assertCanonical("9999", new Picture(true, false, 4, 0, 4), "9(4)");
        assertCanonical("s9(3)v99", new Picture(true, true, 3, 2, 5), "S9(3)V9(2)");
        assertCanonical("V99", new Picture(true, false, 0, 2, 2), "V9(2)");
        assertCanonical("SV99", new Picture(true, true, 0, 2, 2), "SV9(2)");
        assertCanonical("99V", new Picture(true, false, 2, 0, 2), "9(2)");
        assertCanonical("S99V", new Picture(true, true, 2, 0, 2), "S9(2)");
    }

    private void assertCanonical(String input, Picture expected, String expectedText) {
        Picture parsed = Picture.parse(input);
        assertEquals(expected, parsed);
        assertEquals(expectedText, parsed.text());
        assertEquals(expectedText, expected.text());
        assertEquals(expected, Picture.parse(expectedText));
    }
}
