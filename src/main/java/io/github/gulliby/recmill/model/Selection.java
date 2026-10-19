package io.github.gulliby.recmill.model;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Selection {
    private Selection() {
    }

    public static List<Slot> select(Layout layout, List<String> names) {
        Set<String> selectedNames = new HashSet<>();
        for (String name : names) {
            String normalized = Text.upper(name);
            if (!hasRedefiner(layout.root(), normalized)) {
                throw new Failure(Text.show(name) + " is not a redefining entry");
            }
            selectedNames.add(normalized);
        }

        List<Node> nodes = new ArrayList<>();
        collectNodes(layout.root(), nodes);
        Map<Node, Boolean> active = new IdentityHashMap<>();
        for (Node node : nodes) {
            active.put(node, true);
        }

        for (Node base : nodes) {
            List<Node> redefiners = base.redefiners();
            if (redefiners.isEmpty()) {
                continue;
            }

            Node chosen = null;
            for (Node redefiner : redefiners) {
                if (selectedNames.contains(redefiner.name())) {
                    if (chosen == null) {
                        chosen = redefiner;
                    } else {
                        throw new Failure(Text.show(chosen.name()) + " and "
                                + Text.show(redefiner.name()) + " redefine the same entry");
                    }
                }
            }

            active.put(base, chosen == null);
            for (Node redefiner : redefiners) {
                active.put(redefiner, redefiner == chosen);
            }
        }

        List<Slot> selectedSlots = new ArrayList<>();
        for (Slot slot : layout.slots()) {
            boolean slotIsActive = true;
            for (Node node : slot.chain()) {
                if (!active.get(node)) {
                    slotIsActive = false;
                    break;
                }
            }
            if (slotIsActive) {
                selectedSlots.add(slot);
            }
        }

        if (fields(selectedSlots).isEmpty()) {
            throw new Failure("no named field is selected");
        }
        return selectedSlots;
    }

    public static List<Slot> fields(List<Slot> slots) {
        List<Slot> fields = new ArrayList<>();
        for (Slot slot : slots) {
            if (!slot.group() && !slot.filler()) {
                fields.add(slot);
            }
        }
        return fields;
    }

    private static boolean hasRedefiner(Node node, String name) {
        if (node.base() != null && node.name().equals(name)) {
            return true;
        }
        for (Node child : node.children()) {
            if (hasRedefiner(child, name)) {
                return true;
            }
        }
        return false;
    }

    private static void collectNodes(Node node, List<Node> nodes) {
        for (Node child : node.children()) {
            nodes.add(child);
            collectNodes(child, nodes);
        }
    }
}
