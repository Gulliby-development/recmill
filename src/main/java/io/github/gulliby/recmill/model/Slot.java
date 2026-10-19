package io.github.gulliby.recmill.model;

import io.github.gulliby.recmill.syntax.Picture;

import java.util.List;

public record Slot(String path, int start, int length, Picture pic, boolean filler, List<Node> chain) {
    public Slot {
        chain = List.copyOf(chain);
    }

    public boolean group() {
        return pic == null;
    }

    public int end() {
        return start + length - 1;
    }
}
