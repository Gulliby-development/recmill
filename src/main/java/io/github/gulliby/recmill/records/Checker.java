package io.github.gulliby.recmill.records;

import io.github.gulliby.recmill.codec.Cells;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;

import java.util.ArrayList;
import java.util.List;

public final class Checker {
    private Checker() {
    }

    public record Report(List<String> problems, int records, int invalid) {
        public Report {
            problems = List.copyOf(problems);
        }
    }

    public static Report check(Layout layout, List<Slot> slots, List<Records.Line> lines) {
        List<String> problems = new ArrayList<>();
        List<Slot> fields = Selection.fields(slots);
        int invalid = 0;

        for (Records.Line line : lines) {
            String recordProblem = Records.problem(line.text(), layout.length());
            boolean recordInvalid = false;
            if (recordProblem != null) {
                problems.add("line " + line.number() + ": " + recordProblem);
                recordInvalid = true;
            } else {
                for (Slot slot : fields) {
                    String raw = line.text().substring(slot.start() - 1, slot.end());
                    if (Cells.decode(slot, raw) == null) {
                        problems.add("line " + line.number() + ": " + Records.fieldProblem(slot, raw));
                        recordInvalid = true;
                    }
                }
            }
            if (recordInvalid) {
                invalid++;
            }
        }

        return new Report(problems, lines.size(), invalid);
    }
}
