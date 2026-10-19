package io.github.gulliby.recmill.commands;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.core.Options;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;
import io.github.gulliby.recmill.records.Decoder;
import io.github.gulliby.recmill.records.Records;

import java.util.List;

public final class CsvCommand {
    public static final String USAGE = "recmill csv [--no-header] LAYOUT [NAME...]";

    private CsvCommand() {
    }

    public static int run(List<String> args, Io io) {
        Options.Parsed parsed = Options.parse(args, List.of(Options.Spec.flag("--no-header")));
        if (parsed.args().isEmpty()) {
            throw new Failure("usage: " + USAGE);
        }

        List<String> operands = parsed.args();
        Layout layout = Layout.build(operands.get(0));
        List<Slot> slots = Selection.select(layout, operands.subList(1, operands.size()));
        List<Slot> fields = Selection.fields(slots);
        List<List<Decoder.Value>> records = Decoder.decode(layout, slots, Records.read(io));

        StringBuilder output = new StringBuilder();
        if (parsed.values().get("--no-header") == 0) {
            appendRow(output, fields.stream().map(Slot::path).toList());
        }
        for (List<Decoder.Value> record : records) {
            appendRow(output, record.stream().map(Decoder.Value::text).toList());
        }

        io.out(output.toString());
        return 0;
    }

    private static void appendRow(StringBuilder output, List<String> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            output.append(csvCell(cells.get(index)));
        }
        output.append('\n');
    }

    private static String csvCell(String value) {
        boolean quote = value.startsWith(" ") || value.indexOf(',') >= 0 || value.indexOf('"') >= 0;
        if (!quote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
