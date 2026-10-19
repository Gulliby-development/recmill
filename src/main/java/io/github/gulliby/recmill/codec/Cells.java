package io.github.gulliby.recmill.codec;

import io.github.gulliby.recmill.model.Slot;

public final class Cells {
    private Cells() {
    }

    public static String decode(Slot slot, String raw) {
        if (slot.pic().numeric()) {
            return Numeric.decode(raw, slot.pic());
        }
        return Alpha.decode(raw);
    }

    public static String encode(Slot slot, String value) {
        if (slot.pic().numeric()) {
            return Numeric.encode(value, slot.pic());
        }
        return Alpha.encode(value, slot.length());
    }

    public static String blank(Slot slot) {
        char blank = slot.pic().numeric() ? '0' : ' ';
        return String.valueOf(blank).repeat(slot.length());
    }
}
