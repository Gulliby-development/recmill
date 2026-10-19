package io.github.gulliby.recmill.codec;

import static io.github.gulliby.recmill.codec.Cells.blank;
import static io.github.gulliby.recmill.codec.Cells.decode;
import static io.github.gulliby.recmill.codec.Cells.encode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.gulliby.recmill.model.Slot;
import io.github.gulliby.recmill.syntax.Picture;
import java.util.List;
import org.junit.jupiter.api.Test;

class CellsTest {
    @Test
    void decodesAlphaAfterTrimmingTrailingSpaces() {
        assertEquals(" A", decode(slot("X(4)"), " A  "));
    }

    @Test
    void encodesAlphaWithRightPadding() {
        assertEquals("A   ", encode(slot("X(4)"), "A"));
    }

    @Test
    void rejectsAlphaOverflow() {
        assertNull(encode(slot("X(2)"), "ABC"));
    }

    @Test
    void rejectsInvalidAlphaInput() {
        assertNull(decode(slot("X(2)"), "A\t"));
    }

    @Test
    void decodesUnsignedNumericValue() {
        assertEquals("7", decode(slot("9(3)"), "007"));
    }

    @Test
    void encodesUnsignedNumericWithLeadingZeros() {
        assertEquals("007", encode(slot("9(3)"), "7"));
    }

    @Test
    void rejectsOverpunchForUnsignedNumeric() {
        assertNull(decode(slot("9(3)"), "00J"));
    }

    @Test
    void decodesPositiveSignedScaledOverpunch() {
        assertEquals("1.50", decode(slot("S9(3)V99"), "0015{"));
    }

    @Test
    void encodesCanonicalPositiveSignedScaledValue() {
        assertEquals("00150", encode(slot("S9(3)V99"), "1.50"));
    }

    @Test
    void encodesNegativeSignedScaledValue() {
        assertEquals("0125}", encode(slot("S9(3)V99"), "-12.5"));
    }

    @Test
    void decodesNegativeZeroWithoutNegativeSign() {
        assertEquals("0.00", decode(slot("S9(3)V99"), "0000}"));
    }

    @Test
    void rejectsUnsignedNumericOverflow() {
        assertNull(encode(slot("9(2)"), "123"));
    }

    @Test
    void blanksNamedAlphaSlotWithSpaces() {
        assertEquals("   ", blank(slot("X(3)")));
    }

    @Test
    void blanksAlphaFillerWithSpaces() {
        Slot filler = new Slot("FILLER", 1, 2, Picture.parse("X(2)"), true, List.of());
        assertEquals("  ", blank(filler));
    }

    @Test
    void blanksNamedNumericSlotWithZeros() {
        assertEquals("000", blank(slot("9(3)")));
    }

    @Test
    void blanksSignedScaledNumericFillerWithPlainZeros() {
        Slot filler = new Slot("FILLER", 1, 5, Picture.parse("S9(3)V99"), true, List.of());
        assertEquals("00000", blank(filler));
    }

    private static Slot slot(String pictureText) {
        Picture picture = Picture.parse(pictureText);
        return new Slot("FIELD", 1, picture.length(), picture, false, List.of());
    }
}
