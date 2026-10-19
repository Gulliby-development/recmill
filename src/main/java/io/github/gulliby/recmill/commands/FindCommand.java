package io.github.gulliby.recmill.commands;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.core.Options;
import io.github.gulliby.recmill.core.Text;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Slot;

import java.util.ArrayList;
import java.util.List;

public final class FindCommand {
    public static final String USAGE = "recmill find LAYOUT COLUMN";

    private FindCommand() {
    }

    public static int run(List<String> args, Io io) {
        Options.Parsed parsed = Options.parse(args, List.of());
        if (parsed.args().size() != 2) {
            throw new Failure("usage: " + USAGE);
        }

        Layout layout = Layout.build(parsed.args().get(0));
        int column = Options.integer("column", parsed.args().get(1), 1, layout.length());

        StringBuilder report = new StringBuilder(Text.line("column", Integer.toString(column)));
        for (Slot slot : layout.slots()) {
            if (!slot.group() && slot.start() <= column && column <= slot.end()) {
                report.append(Text.line("field", slot.path() + " " + slot.start() + "-"
                        + slot.end() + " " + slot.pic().text()));
            }
        }
        io.out(report.toString());
        return 0;
    }
}
