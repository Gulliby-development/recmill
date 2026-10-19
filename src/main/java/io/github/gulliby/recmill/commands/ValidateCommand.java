package io.github.gulliby.recmill.commands;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.core.Options;
import io.github.gulliby.recmill.core.Text;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;
import io.github.gulliby.recmill.records.Checker;
import io.github.gulliby.recmill.records.Records;

import java.util.ArrayList;
import java.util.List;

public final class ValidateCommand {
    public static final String USAGE = "recmill validate [--max MAX] LAYOUT [NAME...]";

    private static final List<Options.Spec> OPTIONS =
            List.of(Options.Spec.integer("--max", 1, 1000, 20));

    private ValidateCommand() {
    }

    public static int run(List<String> args, Io io) {
        Options.Parsed parsed = Options.parse(args, OPTIONS);
        if (parsed.args().isEmpty()) {
            throw new Failure("usage: " + USAGE);
        }

        Layout layout = Layout.build(parsed.args().get(0));
        List<Slot> slots = Selection.select(layout, parsed.args().subList(1, parsed.args().size()));
        List<Records.Line> lines = Records.read(io);
        Checker.Report report = Checker.check(layout, slots, lines);

        int max = parsed.values().get("--max");
        StringBuilder output = new StringBuilder();
        int printed = Math.min(max, report.problems().size());
        for (int index = 0; index < printed; index++) {
            output.append(report.problems().get(index)).append('\n');
        }
        output.append(Text.line("records", Integer.toString(report.records())));
        output.append(Text.line("invalid", Integer.toString(report.invalid())));
        output.append(Text.line("problems", Integer.toString(report.problems().size())));

        io.out(output.toString());
        return report.invalid() > 0 ? 1 : 0;
    }
}
