package com.newshub.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads test fixtures from src/test/resources/fixtures. */
public final class Fixtures {

    private Fixtures() {
    }

    public static byte[] bytes(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) throw new IllegalStateException("Missing fixture " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String text(String name) {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }
}
