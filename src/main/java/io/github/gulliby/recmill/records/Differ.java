package io.github.gulliby.recmill.records;

import io.github.gulliby.recmill.model.Layout;
import io.github.gulliby.recmill.model.Selection;
import io.github.gulliby.recmill.model.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Differ {
    private Differ() {
    }

    public record Diff(List<String> lines, int removed, int changed, int added, int same, String record) {
        public Diff {
            lines = List.copyOf(lines);
        }
    }

    public static Diff diff(Layout a, Layout b) {
        List<Slot> first = Selection.fields(a.slots());
        List<Slot> second = Selection.fields(b.slots());
        Map<String, Slot> firstByPath = byPath(first);
        Map<String, Slot> secondByPath = byPath(second);
        List<String> lines = new ArrayList<>();
        List<String> removals = new ArrayList<>();
        List<String> changes = new ArrayList<>();
        List<String> additions = new ArrayList<>();
        int removed = 0;
        int changed = 0;
        int added = 0;
        int same = 0;

        for (Slot slot : first) {
            Slot match = secondByPath.get(slot.path());
            if (match == null) {
                removed++;
                removals.add("- " + slot.path() + " from " + slot.start() + " length " + slot.length()
                        + " pic " + slot.pic().text());
                continue;
            }

            List<String> differences = new ArrayList<>();
            if (slot.start() != match.start()) {
                differences.add("from " + slot.start() + " -> " + match.start());
            }
            if (slot.length() != match.length()) {
                differences.add("length " + slot.length() + " -> " + match.length());
            }
            String firstPicture = slot.pic().text();
            String secondPicture = match.pic().text();
            if (!firstPicture.equals(secondPicture)) {
                differences.add("pic " + firstPicture + " -> " + secondPicture);
            }
            if (differences.isEmpty()) {
                same++;
            } else {
                changed++;
                changes.add("~ " + slot.path() + " " + String.join(", ", differences));
            }
        }

        for (Slot slot : second) {
            if (!firstByPath.containsKey(slot.path())) {
                added++;
                additions.add("+ " + slot.path() + " from " + slot.start() + " length " + slot.length()
                        + " pic " + slot.pic().text());
            }
        }

        lines.addAll(removals);
        lines.addAll(changes);
        lines.addAll(additions);

        String record = a.length() == b.length()
                ? Integer.toString(a.length())
                : a.length() + " -> " + b.length();
        return new Diff(lines, removed, changed, added, same, record);
    }

    private static Map<String, Slot> byPath(List<Slot> slots) {
        Map<String, Slot> slotsByPath = new HashMap<>();
        for (Slot slot : slots) {
            slotsByPath.put(slot.path(), slot);
        }
        return slotsByPath;
    }
}
