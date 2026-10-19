package io.github.gulliby.recmill.commands;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.core.Options;
import io.github.gulliby.recmill.core.Text;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;
import io.github.gulliby.recmill.records.Profiler;
import io.github.gulliby.recmill.records.Records;

import java.util.List;

public final class ProfileCommand {
    public static final String USAGE = "recmill profile LAYOUT [NAME...]";

    private ProfileCommand() {
    }

    public static int run(List<String> args, Io io) {
        Options.Parsed parsed = Options.parse(args, List.of());
        List<String> operands = parsed.args();
        if (operands.isEmpty()) {
            throw new Failure("usage: " + USAGE);
        }

        Layout layout = Layout.build(operands.get(0));
        List<Slot> slots = Selection.select(layout, operands.subList(1, operands.size()));
        List<Records.Line> lines = Records.read(io);
        Profiler.Profile profile = Profiler.profile(layout, slots, lines);

        List<String> report = Text.renderTable(
                List.of("field", "pic", "blank", "bad", "distinct", "min", "max", "sum"),
                profile.rows());
        StringBuilder output = new StringBuilder();
        for (String row : report) {
            output.append(row).append('\n');
        }
        output.append(Text.line("records", Integer.toString(profile.records())));
        output.append(Text.line("skipped", Integer.toString(profile.skipped())));
        io.out(output.toString());
        return 0;
    }
}
