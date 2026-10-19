package io.github.gulliby.recmill.model;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Text;
import io.github.gulliby.recmill.syntax.Entry;
import io.github.gulliby.recmill.syntax.Parser;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record Layout(Node root, List<Slot> slots) {
    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;

    public Layout {
        slots = List.copyOf(slots);
    }

    public int length() {
        return root.unit();
    }

    public static Layout build(String text) {
        List<Entry> entries = Parser.parse(text);
        List<Node> nodes = buildHierarchy(entries);
        Node root = nodes.get(0);

        validateStructure(nodes, root);
        SizeData sizes = calculateSizes(nodes);

        for (Node node : nodes) {
            Node base = node.base();
            if (base != null && sizes.totals().get(node).compareTo(sizes.totals().get(base)) > 0) {
                throw new Failure("entry " + node.entry().index() + ": " + Text.show(node.name())
                        + " (" + sizes.totals().get(node) + " characters) is longer than "
                        + Text.show(base.name()) + " (" + sizes.totals().get(base) + " characters)");
            }
        }

        BigInteger recordLength = sizes.units().get(root);
        if (recordLength.compareTo(BigInteger.valueOf(4096)) > 0) {
            throw new Failure("layout is " + recordLength + " characters (above 4096)");
        }

        BigInteger fieldCount = sizes.leaves().get(root);
        if (fieldCount.compareTo(BigInteger.valueOf(2000)) > 0) {
            throw new Failure("layout has " + fieldCount + " fields (above 2000)");
        }

        for (Node node : nodes) {
            node.setSizes(sizes.units().get(node).intValueExact(), sizes.totals().get(node).intValueExact());
        }
        return new Layout(root, emitSlots(root));
    }

    private static List<Node> buildHierarchy(List<Entry> entries) {
        List<Node> nodes = new ArrayList<>(entries.size());
        List<Node> open = new ArrayList<>();
        Node root = null;

        for (Entry entry : entries) {
            if (nodes.isEmpty()) {
                if (entry.level() != 1) {
                    throw new Failure("entry 1: the layout must start with level 01");
                }
                root = new Node(entry);
                nodes.add(root);
                open.add(root);
                continue;
            }

            if (entry.level() == 1) {
                throw new Failure("entry " + entry.index() + ": only one level 01 entry is allowed");
            }

            Node previous = open.get(open.size() - 1);
            Node parent;
            if (entry.level() > previous.entry().level()) {
                parent = previous;
            } else {
                int matchingOpen = -1;
                for (int index = open.size() - 1; index >= 0; index--) {
                    if (open.get(index).entry().level() == entry.level()) {
                        matchingOpen = index;
                        break;
                    }
                }
                if (matchingOpen < 0) {
                    throw new Failure("entry " + entry.index() + ": level "
                            + twoDigits(entry.level()) + " does not match an enclosing level");
                }
                Node matched = open.get(matchingOpen);
                parent = matched.parent();
                open.subList(matchingOpen, open.size()).clear();
            }

            Node node = new Node(entry);
            node.attachTo(parent);
            nodes.add(node);
            open.add(node);
        }
        return nodes;
    }

    private static void validateStructure(List<Node> nodes, Node root) {
        Map<Node, Set<String>> siblingNames = new IdentityHashMap<>();
        Map<Node, Node> nearestBase = new IdentityHashMap<>();

        for (Node node : nodes) {
            Entry entry = node.entry();
            if (node == root && !node.group()) {
                throw new Failure("entry " + entry.index() + ": level 01 must be a group");
            }
            if (node.group() && entry.pic() != null) {
                throw new Failure("entry " + entry.index() + ": group " + Text.show(node.name())
                        + " cannot have PIC");
            }
            if (!node.group() && entry.pic() == null) {
                throw new Failure("entry " + entry.index() + ": " + Text.show(node.name()) + " needs PIC");
            }

            Node parent = node.parent();
            if (parent != null && !node.name().equals("FILLER")) {
                Set<String> names = siblingNames.computeIfAbsent(parent, ignored -> new HashSet<>());
                if (!names.add(node.name())) {
                    throw new Failure("entry " + entry.index() + ": duplicate name " + Text.show(node.name()));
                }
            }

            if (entry.redefines() != null) {
                Node base = parent == null ? null : nearestBase.get(parent);
                if (base == null || !base.name().equals(entry.redefines())) {
                    throw new Failure("entry " + entry.index() + ": invalid REDEFINES target "
                            + Text.show(entry.redefines()));
                }
                if (base.entry().occurs() != 0) {
                    throw new Failure("entry " + entry.index() + ": REDEFINES target "
                            + Text.show(entry.redefines()) + " has OCCURS");
                }
                if (entry.occurs() != 0) {
                    throw new Failure("entry " + entry.index() + ": REDEFINES cannot have OCCURS");
                }
                node.setBase(base);
            } else if (parent != null) {
                nearestBase.put(parent, node);
            }
        }
    }

    private static SizeData calculateSizes(List<Node> nodes) {
        Map<Node, BigInteger> units = new IdentityHashMap<>();
        Map<Node, BigInteger> totals = new IdentityHashMap<>();
        Map<Node, BigInteger> leaves = new IdentityHashMap<>();

        for (int index = nodes.size() - 1; index >= 0; index--) {
            Node node = nodes.get(index);
            BigInteger unit;
            BigInteger leafCount;
            if (node.group()) {
                unit = ZERO;
                leafCount = ZERO;
                for (Node child : node.children()) {
                    if (child.base() == null) {
                        unit = unit.add(totals.get(child));
                    }
                    leafCount = leafCount.add(leaves.get(child).multiply(occurrenceCount(child)));
                }
            } else {
                unit = BigInteger.valueOf(node.entry().pic().length());
                leafCount = ONE;
            }
            units.put(node, unit);
            totals.put(node, unit.multiply(occurrenceCount(node)));
            leaves.put(node, leafCount);
        }

        return new SizeData(units, totals, leaves);
    }

    private static BigInteger occurrenceCount(Node node) {
        return node.entry().occurs() == 0 ? ONE : BigInteger.valueOf(node.entry().occurs());
    }

    private static List<Slot> emitSlots(Node root) {
        List<Slot> slots = new ArrayList<>();
        int cursor = 1;
        Map<Node, Integer> baseStarts = new IdentityHashMap<>();
        for (Node child : root.children()) {
            int start;
            if (child.base() == null) {
                start = cursor;
                baseStarts.put(child, start);
            } else {
                start = baseStarts.get(child.base());
            }
            expand(child, start, List.of(), List.of(), slots);
            if (child.base() == null) {
                cursor += child.total();
            }
        }
        return slots;
    }

    private static void expand(Node node, int firstStart, List<String> parentPath,
                               List<Node> parentChain, List<Slot> slots) {
        Entry entry = node.entry();
        int occurrences = entry.occurs() == 0 ? 1 : entry.occurs();
        for (int occurrence = 1; occurrence <= occurrences; occurrence++) {
            int start = firstStart + (occurrence - 1) * node.unit();
            String component = node.name();
            if (entry.occurs() != 0) {
                component += "(" + occurrence + ")";
            }

            List<String> pathParts = new ArrayList<>(parentPath);
            pathParts.add(component);
            List<Node> chain = new ArrayList<>(parentChain);
            chain.add(node);
            String path = String.join(".", pathParts);
            slots.add(new Slot(path, start, node.unit(), entry.pic(),
                    node.name().equals("FILLER"), chain));

            if (!node.group()) {
                continue;
            }

            int cursor = start;
            Map<Node, Integer> childBaseStarts = new IdentityHashMap<>();
            for (Node child : node.children()) {
                int childStart;
                if (child.base() == null) {
                    childStart = cursor;
                    childBaseStarts.put(child, childStart);
                } else {
                    childStart = childBaseStarts.get(child.base());
                }
                expand(child, childStart, pathParts, chain, slots);
                if (child.base() == null) {
                    cursor += child.total();
                }
            }
        }
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private record SizeData(Map<Node, BigInteger> units, Map<Node, BigInteger> totals,
                            Map<Node, BigInteger> leaves) {
    }
}
