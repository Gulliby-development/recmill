package io.github.gulliby.recmill.records;

import io.github.gulliby.recmill.codec.Cells;
import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Text;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Encoder {
    private Encoder() {
    }

    public static List<String> encode(Layout layout, List<Slot> slots, List<String> lines) {
        Map<String, Slot> fields = new HashMap<>();
        for (Slot slot : Selection.fields(slots)) {
            fields.put(slot.path(), slot);
        }

        List<String> records = new ArrayList<>();
        Map<String, String> assignments = new HashMap<>();
        boolean inBlock = false;

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isEmpty()) {
                if (inBlock) {
                    records.add(render(layout, slots, assignments));
                    assignments.clear();
                    inBlock = false;
                }
                continue;
            }

            inBlock = true;
            int equals = line.indexOf('=');
            if (equals < 0) {
                throw new Failure("line " + (index + 1) + ": expected PATH=VALUE");
            }

            String path = line.substring(0, equals);
            String value = line.substring(equals + 1);
            Slot slot = fields.get(path);
            if (slot == null) {
                throw new Failure("line " + (index + 1) + ": unknown field " + Text.show(path));
            }
            if (assignments.containsKey(path)) {
                throw new Failure("line " + (index + 1) + ": duplicate field " + Text.show(path));
            }

            String encoded = Cells.encode(slot, value);
            if (encoded == null) {
                throw new Failure("line " + (index + 1) + ": invalid value " + Text.show(value)
                        + " for " + path + " (PIC " + slot.pic().text() + ")");
            }
            assignments.put(path, encoded);
        }

        if (inBlock) {
            records.add(render(layout, slots, assignments));
        }
        if (records.isEmpty()) {
            throw new Failure("<stdin>: no input");
        }
        return List.copyOf(records);
    }

    private static String render(Layout layout, List<Slot> slots, Map<String, String> assignments) {
        char[] record = new char[layout.length()];
        Arrays.fill(record, ' ');
        for (Slot slot : slots) {
            if (!slot.group()) {
                String value = assignments.get(slot.path());
                if (value == null) {
                    value = Cells.blank(slot);
                }
                value.getChars(0, slot.length(), record, slot.start() - 1);
            }
        }
        return new String(record);
    }
}
