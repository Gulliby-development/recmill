package io.github.gulliby.recmill.codec;

import io.github.gulliby.recmill.syntax.Picture;

public final class Numeric {
    private Numeric() {
    }

    public static String decode(String raw, Picture pic) {
        if (raw == null || pic == null || !pic.numeric() || raw.length() != pic.length()) {
            return null;
        }

        StringBuilder digits = new StringBuilder(raw.length());
        boolean negative = false;
        int finalIndex = raw.length() - 1;
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (index == finalIndex && !isAsciiDigit(character)) {
                if (!pic.signed()) {
                    return null;
                }
                int overpunch = overpunchDigit(character);
                if (overpunch < 0) {
                    return null;
                }
                digits.append((char) ('0' + overpunch));
                negative = isNegativeOverpunch(character);
            } else {
                if (!isAsciiDigit(character)) {
                    return null;
                }
                digits.append(character);
            }
        }

        boolean zero = true;
        for (int index = 0; index < digits.length(); index++) {
            if (digits.charAt(index) != '0') {
                zero = false;
                break;
            }
        }

        String whole;
        String fraction;
        if (pic.whole() == 0) {
            whole = "0";
            fraction = digits.toString();
        } else {
            whole = digits.substring(0, pic.whole());
            int firstSignificant = 0;
            while (firstSignificant < whole.length() - 1 && whole.charAt(firstSignificant) == '0') {
                firstSignificant++;
            }
            whole = whole.substring(firstSignificant);
            fraction = digits.substring(pic.whole());
        }

        StringBuilder value = new StringBuilder(raw.length() + 2);
        if (negative && !zero) {
            value.append('-');
        }
        value.append(whole);
        if (pic.scale() > 0) {
            value.append('.').append(fraction);
        }
        return value.toString();
    }

    public static String encode(String value, Picture pic) {
        if (value == null || pic == null || !pic.numeric() || value.isEmpty()) {
            return null;
        }

        int position = 0;
        boolean negative = value.charAt(0) == '-';
        if (negative) {
            if (!pic.signed()) {
                return null;
            }
            position++;
        }

        int wholeStart = position;
        while (position < value.length() && isAsciiDigit(value.charAt(position))) {
            position++;
        }
        if (position == wholeStart) {
            return null;
        }
        int wholeEnd = position;

        int fractionStart = -1;
        int fractionEnd = -1;
        if (position < value.length() && value.charAt(position) == '.') {
            position++;
            fractionStart = position;
            while (position < value.length() && isAsciiDigit(value.charAt(position))) {
                position++;
            }
            if (position == fractionStart) {
                return null;
            }
            fractionEnd = position;
        }
        if (position != value.length()) {
            return null;
        }

        int firstSignificant = wholeStart;
        while (firstSignificant < wholeEnd && value.charAt(firstSignificant) == '0') {
            firstSignificant++;
        }
        int significantWholeLength = wholeEnd - firstSignificant;
        if (significantWholeLength > pic.whole()) {
            return null;
        }
        if (fractionStart >= 0 && fractionEnd - fractionStart > pic.scale()) {
            return null;
        }

        StringBuilder raw = new StringBuilder(pic.length());
        for (int index = significantWholeLength; index < pic.whole(); index++) {
            raw.append('0');
        }
        raw.append(value, firstSignificant, wholeEnd);

        boolean zero = significantWholeLength == 0;
        if (fractionStart >= 0) {
            int fractionLength = fractionEnd - fractionStart;
            for (int index = fractionStart; index < fractionEnd; index++) {
                char digit = value.charAt(index);
                raw.append(digit);
                if (digit != '0') {
                    zero = false;
                }
            }
            for (int index = fractionLength; index < pic.scale(); index++) {
                raw.append('0');
            }
        } else {
            for (int index = 0; index < pic.scale(); index++) {
                raw.append('0');
            }
        }

        if (negative && !zero) {
            int last = raw.length() - 1;
            raw.setCharAt(last, negativeOverpunch(raw.charAt(last)));
        }
        return raw.toString();
    }

    private static boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static int overpunchDigit(char character) {
        if (character == '{' || character == '}') {
            return 0;
        }
        if (character >= 'A' && character <= 'I') {
            return character - 'A' + 1;
        }
        if (character >= 'J' && character <= 'R') {
            return character - 'J' + 1;
        }
        return -1;
    }

    private static boolean isNegativeOverpunch(char character) {
        return character == '}' || character >= 'J' && character <= 'R';
    }

    private static char negativeOverpunch(char digit) {
        if (digit == '0') {
            return '}';
        }
        return (char) ('J' + digit - '1');
    }
}
