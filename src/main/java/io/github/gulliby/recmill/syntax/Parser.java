package io.github.gulliby.recmill.syntax;

import io.github.gulliby.recmill.core.Failure;
import io.github.gulliby.recmill.core.Text;

import java.util.ArrayList;
import java.util.List;

public final class Parser {
    private static final String[] RESERVED_NAMES = {
            "PIC", "PICTURE", "IS", "OCCURS", "TIMES", "REDEFINES"
    };

    private Parser() {
    }

    public static List<Entry> parse(String text) {
        List<List<String>> words = Words.entries(text);
        List<Entry> entries = new ArrayList<>(words.size());
        for (int index = 0; index < words.size(); index++) {
            entries.add(parseEntry(index + 1, words.get(index)));
        }
        return entries;
    }

    private static Entry parseEntry(int index, List<String> words) {
        String levelWord = wordAt(words, 0);
        int level = parseLevel(levelWord);
        if (level < 0) {
            throw new Failure("entry " + index + ": invalid level " + Text.show(levelWord));
        }

        String nameWord = wordAt(words, 1);
        if (!isName(nameWord)) {
            throw new Failure("entry " + index + ": invalid name " + Text.show(nameWord));
        }
        String name = Text.upper(nameWord);

        Picture pic = null;
        int occurs = 0;
        String redefines = null;
        boolean sawPic = false;
        boolean sawOccurs = false;
        boolean sawRedefines = false;

        int position = 2;
        while (position < words.size()) {
            String keyword = words.get(position);
            String normalized = Text.upper(keyword);
            if (normalized.equals("PIC") || normalized.equals("PICTURE")) {
                if (sawPic) {
                    throw unexpected(index, keyword);
                }
                sawPic = true;
                position++;

                if (position < words.size() && Text.upper(words.get(position)).equals("IS")) {
                    position++;
                }
                String pictureWord = wordAt(words, position);
                pic = Picture.parse(pictureWord);
                if (pic == null) {
                    throw invalidOperand(index, "PIC", pictureWord);
                }
                position++;
            } else if (normalized.equals("OCCURS")) {
                if (sawOccurs) {
                    throw unexpected(index, keyword);
                }
                sawOccurs = true;
                position++;

                String countWord = wordAt(words, position);
                occurs = parseOccurs(countWord);
                if (occurs == 0) {
                    throw invalidOperand(index, "OCCURS", countWord);
                }
                position++;
                if (position < words.size() && Text.upper(words.get(position)).equals("TIMES")) {
                    position++;
                }
            } else if (normalized.equals("REDEFINES")) {
                if (sawRedefines) {
                    throw unexpected(index, keyword);
                }
                sawRedefines = true;
                position++;

                String targetWord = wordAt(words, position);
                if (!isName(targetWord) || Text.upper(targetWord).equals("FILLER")) {
                    throw invalidOperand(index, "REDEFINES", targetWord);
                }
                redefines = Text.upper(targetWord);
                position++;
            } else {
                throw unexpected(index, keyword);
            }
        }

        return new Entry(index, level, name, pic, occurs, redefines);
    }

    private static String wordAt(List<String> words, int position) {
        return position < words.size() ? words.get(position) : "";
    }

    private static int parseLevel(String text) {
        if (text.length() < 1 || text.length() > 2) {
            return -1;
        }

        int value = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < '0' || character > '9') {
                return -1;
            }
            value = value * 10 + character - '0';
        }
        return value >= 1 && value <= 49 ? value : -1;
    }

    private static int parseOccurs(String text) {
        if (text.isEmpty() || text.length() > 2 || text.charAt(0) == '0') {
            return 0;
        }

        int value = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < '0' || character > '9') {
                return 0;
            }
            value = value * 10 + character - '0';
        }
        return value >= 1 && value <= 99 ? value : 0;
    }

    private static boolean isName(String text) {
        if (text.isEmpty() || text.length() > 30 || !isLetter(text.charAt(0))) {
            return false;
        }
        for (int index = 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (!isLetter(character) && (character < '0' || character > '9')
                    && character != '-' && character != '_') {
                return false;
            }
        }

        String normalized = Text.upper(text);
        for (String reserved : RESERVED_NAMES) {
            if (normalized.equals(reserved)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLetter(char character) {
        return (character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z');
    }

    private static Failure invalidOperand(int index, String kind, String word) {
        return new Failure("entry " + index + ": invalid " + kind + " " + Text.show(word));
    }

    private static Failure unexpected(int index, String word) {
        return new Failure("entry " + index + ": unexpected word " + Text.show(word));
    }
}
