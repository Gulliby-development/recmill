package io.github.gulliby.recmill.core;

import java.io.IOException;

public interface Io {
    byte[] readStdin() throws IOException;

    void out(String text);

    void err(String text);
}
