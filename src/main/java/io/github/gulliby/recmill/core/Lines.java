package io.github.gulliby.recmill.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Lines {
    private Lines() {
    }

    public static List<String> read(Io io) {
        byte[] bytes;
        try {
            bytes = io.readStdin();
        } catch (IOException exception) {
            throw new Failure("<stdin>: cannot read the input");
        }

        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new Failure("<stdin>: input is not valid UTF-8");
        }

        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        if (text.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                lines.add(withoutOneTrailingCarriageReturn(text.substring(start, index)));
                start = index + 1;
            }
        }
        if (start < text.length()) {
            lines.add(withoutOneTrailingCarriageReturn(text.substring(start)));
        }
        return lines;
    }

    private static String withoutOneTrailingCarriageReturn(String line) {
        if (line.endsWith("\r")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }
}
