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

public final class DecodeCommand {
    public static final String USAGE = "recmill decode LAYOUT [NAME...]";

    private DecodeCommand() {
    }

    public static int run(List<String> args, Io io) {
        Options.Parsed parsed = Options.parse(args, List.of());
        if (parsed.args().isEmpty()) {
            throw new Failure("usage: " + USAGE);
        }

        List<String> operands = parsed.args();
        Layout layout = Layout.build(operands.get(0));
        List<Slot> slots = Selection.select(layout, operands.subList(1, operands.size()));
        List<List<Decoder.Value>> records = Decoder.decode(layout, slots, Records.read(io));

        StringBuilder output = new StringBuilder();
        for (int recordIndex = 0; recordIndex < records.size(); recordIndex++) {
            if (recordIndex > 0) {
                output.append('\n');
            }
            for (Decoder.Value value : records.get(recordIndex)) {
                output.append(value.path()).append('=').append(value.text()).append('\n');
            }
        }

        io.out(output.toString());
        return 0;
    }
}
