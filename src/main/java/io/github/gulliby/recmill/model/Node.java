package io.github.gulliby.recmill.model;

import io.github.gulliby.recmill.syntax.Entry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Node {
    private final Entry entry;
    private final List<Node> children = new ArrayList<>();
    private final List<Node> childrenView = Collections.unmodifiableList(children);
    private final List<Node> redefiners = new ArrayList<>();
    private final List<Node> redefinersView = Collections.unmodifiableList(redefiners);
    private Node parent;
    private Node base;
    private int unit;
    private int total;

    Node(Entry entry) {
        this.entry = entry;
    }

    public Entry entry() {
        return entry;
    }

    public Node parent() {
        return parent;
    }

    public List<Node> children() {
        return childrenView;
    }

    public Node base() {
        return base;
    }

    public List<Node> redefiners() {
        return redefinersView;
    }

    public int unit() {
        return unit;
    }

    public int total() {
        return total;
    }

    public boolean group() {
        return !children.isEmpty();
    }

    public String name() {
        return entry.name();
    }

    void attachTo(Node newParent) {
        parent = newParent;
        newParent.children.add(this);
    }

    void setBase(Node newBase) {
        base = newBase;
        newBase.redefiners.add(this);
    }

    void setSizes(int newUnit, int newTotal) {
        unit = newUnit;
        total = newTotal;
    }
}
