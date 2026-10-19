package io.github.gulliby.recmill.records;

import io.github.gulliby.recmill.codec.Cells;
import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;

import java.util.ArrayList;
import java.util.List;

public final class Decoder {
    private Decoder() {
    }

    public record Value(String path, String text) {
    }

    public static List<List<Value>> decode(Layout layout, List<Slot> slots, List<Records.Line> lines) {
        List<Slot> fields = Selection.fields(slots);
        List<List<Value>> decoded = new ArrayList<>(lines.size());

        for (Records.Line line : lines) {
            String recordProblem = Records.problem(line.text(), layout.length());
            if (recordProblem != null) {
                throw new Failure("line " + line.number() + ": " + recordProblem);
            }

            List<Value> values = new ArrayList<>(fields.size());
            for (Slot field : fields) {
                String raw = line.text().substring(field.start() - 1, field.end());
                String value = Cells.decode(field, raw);
                if (value == null) {
                    throw new Failure("line " + line.number() + ": " + Records.fieldProblem(field, raw));
                }
                values.add(new Value(field.path(), value));
            }
            decoded.add(List.copyOf(values));
        }

        return List.copyOf(decoded);
    }
}
