package io.github.gulliby.recmill.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Options {
    private Options() {
    }

    public record Spec(String name, boolean flag, int lo, int hi, int fallback) {
        public static Spec flag(String name) {
            return new Spec(name, true, 0, 0, 0);
        }

        public static Spec integer(String name, int lo, int hi, int fallback) {
            return new Spec(name, false, lo, hi, fallback);
        }
    }

    public record Parsed(Map<String, Integer> values, List<String> args) {
    }

    public static Parsed parse(List<String> argv, List<Spec> specs) {
        Map<String, Spec> byName = new LinkedHashMap<>();
        for (Spec spec : specs) {
            byName.put(spec.name(), spec);
        }

        Set<String> provided = new LinkedHashSet<>();
        Map<String, String> rawValues = new LinkedHashMap<>();
        List<String> args = new ArrayList<>();

        for (int index = 0; index < argv.size(); index++) {
            String argument = argv.get(index);
            if (!argument.startsWith("--")) {
                args.add(argument);
                continue;
            }

            int equals = argument.indexOf('=');
            String name = equals < 0 ? argument : argument.substring(0, equals);
            Spec spec = byName.get(name);
            if (spec == null) {
                throw new Failure("unknown option " + Text.show(argument));
            }
            if (spec.flag()) {
                if (equals >= 0) {
                    throw new Failure(name + " takes no value");
                }
                provided.add(name);
                continue;
            }

            String value;
            if (equals >= 0) {
                value = argument.substring(equals + 1);
            } else {
                if (index + 1 >= argv.size()) {
                    throw new Failure(name + " needs a value");
                }
                value = argv.get(++index);
            }
            provided.add(name);
            rawValues.put(name, value);
        }

        Map<String, Integer> values = new LinkedHashMap<>();
        for (Spec spec : specs) {
            if (spec.flag()) {
                values.put(spec.name(), provided.contains(spec.name()) ? 1 : 0);
            } else if (provided.contains(spec.name())) {
                values.put(spec.name(), integer(spec.name(), rawValues.get(spec.name()), spec.lo(), spec.hi()));
            } else {
                values.put(spec.name(), spec.fallback());
            }
        }
        return new Parsed(values, args);
    }

    public static int integer(String name, String text, int lo, int hi) {
        boolean valid = !text.isEmpty()
                && text.length() <= 6
                && (text.length() == 1 || text.charAt(0) != '0');
        for (int index = 0; valid && index < text.length(); index++) {
            char character = text.charAt(index);
            valid = character >= '0' && character <= '9';
        }

        if (valid) {
            int value = Integer.parseInt(text);
            if (value >= lo && value <= hi) {
                return value;
            }
        }
        throw new Failure("invalid " + name + " " + Text.show(text)
                + " (expected an integer from " + lo + " to " + hi + ")");
    }
}
