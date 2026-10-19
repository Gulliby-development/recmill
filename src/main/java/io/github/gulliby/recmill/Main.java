package io.github.gulliby.recmill;

import io.github.gulliby.recmill.core.Io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Io io = new Io() {
            @Override
            public byte[] readStdin() throws IOException {
                return System.in.readAllBytes();
            }

            @Override
            public void out(String text) {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                System.out.write(bytes, 0, bytes.length);
                System.out.flush();
            }

            @Override
            public void err(String text) {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                System.err.write(bytes, 0, bytes.length);
                System.err.flush();
            }
        };

        int status = Cli.run(Arrays.asList(args), io);
        System.out.flush();
        System.err.flush();
        System.exit(status);
    }
}
