package io.github.gulliby.recmill.records;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.core.Lines;
import io.github.gulliby.recmill.core.Text;
import io.github.gulliby.recmill.model.Slot;

import java.util.ArrayList;
import java.util.List;

public final class Records {
    private Records() {
    }

    public record Line(int number, String text) {
    }

    public static List<Line> read(Io io) {
        List<String> input = Lines.read(io);
        List<Line> records = new ArrayList<>();
        for (int index = 0; index < input.size(); index++) {
            String text = input.get(index);
            if (!text.isEmpty()) {
                records.add(new Line(index + 1, text));
            }
        }
        if (records.isEmpty()) {
            throw new Failure("<stdin>: no input");
        }
        return List.copyOf(records);
    }

    public static String problem(String text, int length) {
        int column = 1;
        for (int offset = 0; offset < text.length(); column++) {
            int character = text.codePointAt(offset);
            if (character < ' ' || character > '~') {
                return "column " + column + " is not a printable ASCII character";
            }
            offset += Character.charCount(character);
        }
        if (text.length() != length) {
            return "record has " + text.length() + " characters (expected " + length + ")";
        }
        return null;
    }

    public static String fieldProblem(Slot slot, String raw) {
        return slot.path() + " (columns " + slot.start() + "-" + slot.end()
                + "): invalid value " + Text.show(raw);
    }
}
