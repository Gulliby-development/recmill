package io.github.gulliby.recmill.core;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    private Text() {
    }

    public static String show(String text) {
        StringBuilder result = new StringBuilder(text.length() + 2);
        result.append('"');
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (codePoint >= ' ' && codePoint <= '~') {
                result.appendCodePoint(codePoint);
            } else {
                result.append('?');
            }
            offset += Character.charCount(codePoint);
        }
        result.append('"');
        return result.toString();
    }

    public static String upper(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character >= 'a' && character <= 'z') {
                character = (char) (character - ('a' - 'A'));
            }
            result.append(character);
        }
        return result.toString();
    }

    public static String line(String key, String value) {
        StringBuilder result = new StringBuilder(key);
        for (int index = key.length(); index < 10; index++) {
            result.append(' ');
        }
        return result.append("  ").append(value).append('\n').toString();
    }

    public static List<String> renderTable(List<String> header, List<List<String>> rows) {
        int[] widths = new int[header.size()];
        for (int column = 0; column < header.size(); column++) {
            widths[column] = header.get(column).length();
        }
        for (List<String> row : rows) {
            for (int column = 0; column < widths.length; column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        }

        List<String> rendered = new ArrayList<>(rows.size() + 1);
        rendered.add(renderRow(header, widths));
        for (List<String> row : rows) {
            rendered.add(renderRow(row, widths));
        }
        return rendered;
    }

    private static String renderRow(List<String> row, int[] widths) {
        StringBuilder rendered = new StringBuilder();
        for (int column = 0; column < row.size(); column++) {
            String cell = row.get(column);
            rendered.append(cell);
            if (column + 1 < row.size()) {
                for (int padding = cell.length(); padding < widths[column]; padding++) {
                    rendered.append(' ');
                }
                rendered.append("  ");
            }
        }
        int end = rendered.length();
        while (end > 0 && rendered.charAt(end - 1) == ' ') {
            end--;
        }
        return rendered.substring(0, end);
    }
}
