package io.github.gulliby.recmill.syntax;

import io.github.gulliby.recmill.core.Failure;

import java.util.ArrayList;
import java.util.List;

public final class Words {
    private Words() {
    }

    public static List<List<String>> entries(String text) {
        List<List<String>> entries = new ArrayList<>();
        List<String> entry = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean comment = false;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (comment) {
                if (character == '\r' || character == '\n') {
                    comment = false;
                    finishWord(word, entry, entries);
                }
                continue;
            }

            if (character == '*' && index + 1 < text.length() && text.charAt(index + 1) == '>') {
                comment = true;
                index++;
            } else if (isSeparator(character)) {
                finishWord(word, entry, entries);
            } else {
                word.append(character);
            }
        }

        finishWord(word, entry, entries);
        if (!entry.isEmpty()) {
            throw new Failure("entry " + (entries.size() + 1) + ": missing period at the end");
        }
        if (entries.isEmpty()) {
            throw new Failure("layout is empty");
        }
        return entries;
    }

    private static void finishWord(StringBuilder word, List<String> entry, List<List<String>> entries) {
        if (word.length() == 0) {
            return;
        }

        String value = word.toString();
        word.setLength(0);
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
            if (!value.isEmpty()) {
                entry.add(value);
            }
            entries.add(new ArrayList<>(entry));
            entry.clear();
        } else {
            entry.add(value);
        }
    }

    private static boolean isSeparator(char character) {
        return character == ' ' || character == '\t' || character == '\r' || character == '\n';
    }
}
