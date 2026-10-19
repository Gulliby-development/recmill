package io.github.gulliby.recmill.codec;

public final class Alpha {
    private Alpha() {
    }

    public static String decode(String raw) {
        if (raw == null || !isPrintableAscii(raw)) {
            return null;
        }

        int end = raw.length();
        while (end > 0 && raw.charAt(end - 1) == ' ') {
            end--;
        }
        return raw.substring(0, end);
    }

    public static String encode(String value, int length) {
        if (value == null || length < 0 || value.length() > length || !isPrintableAscii(value)) {
            return null;
        }

        StringBuilder result = new StringBuilder(length);
        result.append(value);
        for (int index = value.length(); index < length; index++) {
            result.append(' ');
        }
        return result.toString();
    }

    private static boolean isPrintableAscii(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < ' ' || character > '~') {
                return false;
            }
        }
        return true;
    }
}
