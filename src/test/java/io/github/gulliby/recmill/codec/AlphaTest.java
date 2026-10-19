package io.github.gulliby.recmill.codec;

import static io.github.gulliby.recmill.codec.Alpha.decode;
import static io.github.gulliby.recmill.codec.Alpha.encode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AlphaTest {
    @Test
    void decodeTrimsTrailingSpacesOnly() {
        assertEquals(" A B", decode(" A B  "));
    }

    @Test
    void decodeOfAllSpacesIsEmpty() {
        assertEquals("", decode("   "));
    }

    @Test
    void decodeOfEmptyRawFieldIsEmpty() {
        assertEquals("", decode(""));
    }

    @Test
    void decodeAcceptsPrintableAsciiEndpoints() {
        assertEquals(" ~", decode(" ~"));
    }

    @Test
    void decodeRejectsControlCharacters() {
        assertNull(decode("A\t"));
    }

    @Test
    void decodeRejectsDel() {
        assertNull(decode("\u007f"));
    }

    @Test
    void decodeRejectsNonAscii() {
        assertNull(decode("\u00e9"));
    }

    @Test
    void encodePadsEmptyValue() {
        assertEquals("   ", encode("", 3));
    }

    @Test
    void encodePadsOnTheRight() {
        assertEquals("A  ", encode("A ", 3));
    }

    @Test
    void encodeAcceptsValueAtExactWidth() {
        assertEquals(" ~", encode(" ~", 2));
    }

    @Test
    void encodeRejectsValueLongerThanWidth() {
        assertNull(encode("ABC", 2));
    }

    @Test
    void encodeRejectsControlCharacters() {
        assertNull(encode("A\n", 3));
    }

    @Test
    void encodeRejectsNonAscii() {
        assertNull(encode("\u00e9", 2));
    }
}
