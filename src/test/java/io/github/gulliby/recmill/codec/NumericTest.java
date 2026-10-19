package io.github.gulliby.recmill.codec;

import static io.github.gulliby.recmill.codec.Numeric.decode;
import static io.github.gulliby.recmill.codec.Numeric.encode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.gulliby.recmill.syntax.Picture;
import org.junit.jupiter.api.Test;

class NumericTest {
    @Test
    void decodesEverySignedOverpunchCharacter() {
        Picture picture = Picture.parse("S9");

        assertEquals("0", decode("{", picture));
        assertEquals("1", decode("A", picture));
        assertEquals("2", decode("B", picture));
        assertEquals("3", decode("C", picture));
        assertEquals("4", decode("D", picture));
        assertEquals("5", decode("E", picture));
        assertEquals("6", decode("F", picture));
        assertEquals("7", decode("G", picture));
        assertEquals("8", decode("H", picture));
        assertEquals("9", decode("I", picture));
        assertEquals("0", decode("}", picture));
        assertEquals("-1", decode("J", picture));
        assertEquals("-2", decode("K", picture));
        assertEquals("-3", decode("L", picture));
        assertEquals("-4", decode("M", picture));
        assertEquals("-5", decode("N", picture));
        assertEquals("-6", decode("O", picture));
        assertEquals("-7", decode("P", picture));
        assertEquals("-8", decode("Q", picture));
        assertEquals("-9", decode("R", picture));
    }

    @Test
    void decodesNumericFieldFormatAndSizingBoundaries() {
        assertEquals("1.50", decode("0015{", Picture.parse("S9(3)V99")));
        assertEquals("-12.00", decode("0120}", Picture.parse("S9(3)V99")));
        assertEquals("0.00", decode("0000}", Picture.parse("S9(3)V99")));
        assertEquals("0.05", decode("05", Picture.parse("V99")));
        assertEquals("0", decode("000", Picture.parse("999V")));
        assertEquals("123456789012345678", decode("123456789012345678", Picture.parse("9(18)")));

        assertNull(decode("", Picture.parse("9")));
        assertNull(decode("1", Picture.parse("99")));
        assertNull(decode("123", Picture.parse("99")));
        assertNull(decode(" A", Picture.parse("S99")));
        assertNull(decode("A1", Picture.parse("S99")));
        assertNull(decode("a", Picture.parse("S9")));
        assertNull(decode("{", Picture.parse("9")));
        assertNull(decode("}", Picture.parse("9")));
        assertNull(decode("\u0661", Picture.parse("9")));
    }

    @Test
    void encodesNumericSizingAndCanonicalizationBoundaries() {
        assertEquals("0007", encode("7", Picture.parse("9(4)")));
        assertEquals("00150", encode("1.5", Picture.parse("S9(3)V99")));
        assertEquals("0125}", encode("-12.5", Picture.parse("S9(3)V99")));
        assertEquals("00J", encode("-0.01", Picture.parse("S9V99")));
        assertEquals("000", encode("-0.00", Picture.parse("S9V99")));
        assertEquals("0", encode("-0", Picture.parse("S9")));
        assertEquals("05", encode("000.05", Picture.parse("V99")));
        assertEquals("00", encode("0", Picture.parse("V99")));
        assertNull(encode("1", Picture.parse("V99")));
        assertNull(encode("1000", Picture.parse("9(3)")));
        assertEquals("12", encode("00012", Picture.parse("99")));
        assertNull(encode("1.500", Picture.parse("9V99")));
        assertNull(encode("1.0", Picture.parse("9")));
        assertEquals("1", encode("1", Picture.parse("9V")));
        assertEquals("123456789012345678", encode("123456789012345678", Picture.parse("9(18)")));
        assertNull(encode("1234567890123456789", Picture.parse("9(18)")));
        assertNull(encode("-0", Picture.parse("9")));
        assertNull(encode("-1", Picture.parse("9")));
    }

    @Test
    void rejectsMalformedDecimalText() {
        Picture picture = Picture.parse("S9(3)V99");

        assertNull(encode("", picture));
        assertNull(encode("1.", picture));
        assertNull(encode(".5", picture));
        assertNull(encode("+3", picture));
        assertNull(encode("1e3", picture));
        assertNull(encode(" 1", picture));
        assertNull(encode("1 ", picture));
        assertNull(encode("-", picture));
        assertNull(encode("--1", picture));
        assertNull(encode("1.2.3", picture));
        assertNull(encode("\u0661", picture));
    }

    @Test
    void decodeThenEncodeUsesCanonicalRawFields() {
        Picture signedScaled = Picture.parse("S9(3)V99");
        // "1.50" then "00150"
        assertEquals("1.50", decode("0015{", signedScaled));
        assertEquals("00150", encode(decode("0015{", signedScaled), signedScaled));

        // "0.00" then "00000"
        assertEquals("0.00", decode("0000}", signedScaled));
        assertEquals("00000", encode(decode("0000}", signedScaled), signedScaled));

        // "-12.00" then "0120}"
        assertEquals("-12.00", decode("0120}", signedScaled));
        assertEquals("0120}", encode(decode("0120}", signedScaled), signedScaled));

        Picture wholeZero = Picture.parse("V99");
        // "0.05" then "05"
        assertEquals("0.05", decode("05", wholeZero));
        assertEquals("05", encode(decode("05", wholeZero), wholeZero));
    }
}
