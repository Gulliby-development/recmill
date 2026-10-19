package io.github.gulliby.recmill.records;

import io.github.gulliby.recmill.codec.Cells;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;
import io.github.gulliby.recmill.syntax.Picture;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Profiler {
    private Profiler() {
    }

    public record Profile(List<List<String>> rows, int records, int skipped) {
        public Profile {
            List<List<String>> copiedRows = new ArrayList<>(rows.size());
            for (List<String> row : rows) {
                copiedRows.add(List.copyOf(row));
            }
            rows = List.copyOf(copiedRows);
        }
    }

    public static Profile profile(Layout layout, List<Slot> slots, List<Records.Line> lines) {
        List<Slot> fields = Selection.fields(slots);
        List<FieldStats> statistics = new ArrayList<>(fields.size());
        for (Slot field : fields) {
            statistics.add(new FieldStats(field));
        }

        int skipped = 0;
        for (Records.Line line : lines) {
            if (Records.problem(line.text(), layout.length()) != null) {
                skipped++;
                continue;
            }
            for (FieldStats fieldStats : statistics) {
                Slot field = fieldStats.field;
                int start = field.start() - 1;
                String raw = line.text().substring(start, start + field.length());
                if (isBlank(raw)) {
                    fieldStats.blank++;
                    continue;
                }

                String value = Cells.decode(field, raw);
                if (value == null) {
                    fieldStats.bad++;
                    continue;
                }
                fieldStats.add(value);
            }
        }

        List<List<String>> rows = new ArrayList<>(statistics.size());
        for (FieldStats fieldStats : statistics) {
            rows.add(fieldStats.row());
        }
        return new Profile(rows, lines.size(), skipped);
    }

    private static boolean isBlank(String raw) {
        for (int index = 0; index < raw.length(); index++) {
            if (raw.charAt(index) != ' ') {
                return false;
            }
        }
        return true;
    }

    private static final class FieldStats {
        private final Slot field;
        private final Set<String> distinct = new HashSet<>();
        private int blank;
        private int bad;
        private String minimum;
        private String maximum;
        private BigDecimal numericMinimum;
        private BigDecimal numericMaximum;
        private BigDecimal sum = BigDecimal.ZERO;

        private FieldStats(Slot field) {
            this.field = field;
        }

        private void add(String value) {
            distinct.add(value);
            if (field.pic().numeric()) {
                BigDecimal number = new BigDecimal(value);
                if (numericMinimum == null || number.compareTo(numericMinimum) < 0) {
                    numericMinimum = number;
                    minimum = value;
                }
                if (numericMaximum == null || number.compareTo(numericMaximum) > 0) {
                    numericMaximum = number;
                    maximum = value;
                }
                sum = sum.add(number);
            } else {
                if (minimum == null || value.compareTo(minimum) < 0) {
                    minimum = value;
                }
                if (maximum == null || value.compareTo(maximum) > 0) {
                    maximum = value;
                }
            }
        }

        private List<String> row() {
            Picture picture = field.pic();
            String renderedSum = "-";
            if (picture.numeric() && numericMinimum != null) {
                renderedSum = sum.setScale(picture.scale()).toPlainString();
            }
            return List.of(
                    field.path(),
                    picture.text(),
                    Integer.toString(blank),
                    Integer.toString(bad),
                    Integer.toString(distinct.size()),
                    minimum == null ? "-" : minimum,
                    maximum == null ? "-" : maximum,
                    renderedSum);
        }
    }
}
