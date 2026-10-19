package io.github.gulliby.recmill.commands;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.core.Options;
import io.github.gulliby.recmill.core.Text;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.records.Differ;

import java.util.List;

public final class DiffCommand {
    public static final String USAGE = "recmill diff LAYOUT1 LAYOUT2";

    private DiffCommand() {
    }

    public static int run(List<String> args, Io io) {
        Options.Parsed parsed = Options.parse(args, List.of());
        if (parsed.args().size() != 2) {
            throw new Failure("usage: " + USAGE);
        }

        Layout first;
        try {
            first = Layout.build(parsed.args().get(0));
        } catch (Failure failure) {
            throw new Failure("LAYOUT1: " + failure.getMessage());
        }

        Layout second;
        try {
            second = Layout.build(parsed.args().get(1));
        } catch (Failure failure) {
            throw new Failure("LAYOUT2: " + failure.getMessage());
        }

        Differ.Diff difference = Differ.diff(first, second);
        StringBuilder report = new StringBuilder();
        for (String line : difference.lines()) {
            report.append(line).append('\n');
        }
        report.append(Text.line("removed", Integer.toString(difference.removed())));
        report.append(Text.line("changed", Integer.toString(difference.changed())));
        report.append(Text.line("added", Integer.toString(difference.added())));
        report.append(Text.line("same", Integer.toString(difference.same())));
        report.append(Text.line("record", difference.record()));

        io.out(report.toString());
        return difference.removed() > 0
                || difference.changed() > 0
                || difference.added() > 0
                || first.length() != second.length() ? 1 : 0;
    }
}
