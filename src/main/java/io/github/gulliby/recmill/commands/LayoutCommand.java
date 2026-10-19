package io.github.gulliby.recmill.commands;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.core.Options;
import io.github.gulliby.recmill.core.Text;
import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Slot;

import java.util.ArrayList;
import java.util.List;

public final class LayoutCommand {
    public static final String USAGE = "recmill layout [--groups] LAYOUT";

    private LayoutCommand() {
    }

    public static int run(List<String> args, Io io) {
        Options.Parsed parsed = Options.parse(args, List.of(Options.Spec.flag("--groups")));
        if (parsed.args().size() != 1) {
            throw new Failure("usage: " + USAGE);
        }

        Layout layout = Layout.build(parsed.args().get(0));
        boolean includeGroups = parsed.values().get("--groups") == 1;
        List<List<String>> rows = new ArrayList<>();
        for (Slot slot : layout.slots()) {
            if (slot.group() && !includeGroups) {
                continue;
            }
            rows.add(List.of(
                    slot.path(),
                    Integer.toString(slot.start()),
                    Integer.toString(slot.end()),
                    slot.group() ? "-" : slot.pic().text()));
        }

        StringBuilder report = new StringBuilder();
        for (String line : Text.renderTable(List.of("path", "from", "to", "pic"), rows)) {
            report.append(line).append('\n');
        }
        report.append(Text.line("length", Integer.toString(layout.length())));
        io.out(report.toString());
        return 0;
    }
}
