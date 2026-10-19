package io.github.gulliby.recmill;

import io.github.gulliby.recmill.commands.CsvCommand;
import io.github.gulliby.recmill.commands.DecodeCommand;
import io.github.gulliby.recmill.commands.DiffCommand;
import io.github.gulliby.recmill.commands.EncodeCommand;
import io.github.gulliby.recmill.commands.FindCommand;
import io.github.gulliby.recmill.commands.LayoutCommand;
import io.github.gulliby.recmill.commands.ProfileCommand;
import io.github.gulliby.recmill.commands.ValidateCommand;
import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Io;
import io.github.gulliby.recmill.core.Text;

import java.util.List;

public final class Cli {
    public static final String USAGE = String.join("\n",
            LayoutCommand.USAGE,
            FindCommand.USAGE,
            DecodeCommand.USAGE,
            EncodeCommand.USAGE,
            CsvCommand.USAGE,
            ValidateCommand.USAGE,
            ProfileCommand.USAGE,
            DiffCommand.USAGE);

    private Cli() {
    }

    public static int run(List<String> argv, Io io) {
        try {
            if (argv.isEmpty()) {
                throw new Failure(
                        "missing command (layout, find, decode, encode, csv, validate, profile or diff)");
            }

            String command = argv.get(0);
            if (argv.size() == 1 && command.equals("--help")) {
                io.out(USAGE + "\n");
                return 0;
            }

            List<String> args = argv.subList(1, argv.size());
            return switch (command) {
                case "layout" -> LayoutCommand.run(args, io);
                case "find" -> FindCommand.run(args, io);
                case "decode" -> DecodeCommand.run(args, io);
                case "encode" -> EncodeCommand.run(args, io);
                case "csv" -> CsvCommand.run(args, io);
                case "validate" -> ValidateCommand.run(args, io);
                case "profile" -> ProfileCommand.run(args, io);
                case "diff" -> DiffCommand.run(args, io);
                default -> throw new Failure("unknown command " + Text.show(command));
            };
        } catch (Failure failure) {
            io.err("recmill: " + failure.getMessage() + "\n");
            return 2;
        }
    }
}
