package io.github.gulliby.recmill.commands;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.core.Lines;
import io.github.gulliby.recmill.core.Options;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;
import io.github.gulliby.recmill.records.Encoder;

import java.util.List;

public final class EncodeCommand {
    public static final String USAGE = "recmill encode LAYOUT [NAME...]";

    private EncodeCommand() {
    }

    public static int run(List<String> args, Io io) {
        List<String> operands = Options.parse(args, List.of()).args();
        if (operands.isEmpty()) {
            throw new Failure("usage: " + USAGE);
        }

        Layout layout = Layout.build(operands.get(0));
        List<Slot> slots = Selection.select(layout, operands.subList(1, operands.size()));
        List<String> records = Encoder.encode(layout, slots, Lines.read(io));

        StringBuilder output = new StringBuilder();
        for (String record : records) {
            output.append(record).append('\n');
        }
        io.out(output.toString());
        return 0;
    }
}
