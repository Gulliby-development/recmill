package io.github.gulliby.recmill.syntax;

import io.github.gulliby.recmill.core.Text;

public record Picture(boolean numeric, boolean signed, int whole, int scale, int length) {
    public static Picture parse(String word) {
        String text = Text.upper(word);
        if (text.isEmpty()) {
            return null;
        }
        if (text.charAt(0) == 'X') {
            return parseAlpha(text);
        }
        return parseNumeric(text);
    }

    public String text() {
        if (!numeric) {
            return "X(" + length + ")";
        }

        StringBuilder result = new StringBuilder();
        if (signed) {
            result.append('S');
        }
        if (whole > 0) {
            result.append("9(").append(whole).append(')');
        }
        if (scale > 0) {
            result.append("V9(").append(scale).append(')');
        }
        return result.toString();
    }

    private static Picture parseAlpha(String text) {
        int position = 0;
        int length = 0;
        int[] next = new int[1];
        while (position < text.length()) {
            if (text.charAt(position) != 'X') {
                return null;
            }
            position++;
            int count = itemCount(text, position, next);
            if (count < 0 || count > 255 - length) {
                return null;
            }
            length += count;
            position = next[0];
        }
        if (length == 0) {
            return null;
        }
        return new Picture(false, false, length, 0, length);
    }

    private static Picture parseNumeric(String text) {
        int position = 0;
        boolean signed = text.charAt(0) == 'S';
        if (signed) {
            position++;
        }

        boolean afterV = false;
        boolean sawV = false;
        int whole = 0;
        int scale = 0;
        int total = 0;
        int[] next = new int[1];
        while (position < text.length()) {
            char item = text.charAt(position);
            if (item == 'V') {
                if (sawV) {
                    return null;
                }
                sawV = true;
                afterV = true;
                position++;
                continue;
            }
            if (item != '9') {
                return null;
            }

            position++;
            int count = itemCount(text, position, next);
            if (count < 0 || count > 18 - total) {
                return null;
            }
            total += count;
            if (afterV) {
                scale += count;
            } else {
                whole += count;
            }
            position = next[0];
        }
        if (total == 0) {
            return null;
        }
        return new Picture(true, signed, whole, scale, total);
    }

    private static int itemCount(String text, int position, int[] next) {
        if (position >= text.length() || text.charAt(position) != '(') {
            next[0] = position;
            return 1;
        }

        int index = position + 1;
        if (index >= text.length() || text.charAt(index) < '1' || text.charAt(index) > '9') {
            return -1;
        }

        int value = 0;
        int digits = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character < '0' || character > '9') {
                break;
            }
            if (digits == 3) {
                return -1;
            }
            value = value * 10 + character - '0';
            digits++;
            index++;
        }
        if (index >= text.length() || text.charAt(index) != ')') {
            return -1;
        }
        next[0] = index + 1;
        return value;
    }
}
