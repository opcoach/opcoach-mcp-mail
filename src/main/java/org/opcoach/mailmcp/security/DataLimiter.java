package org.opcoach.mailmcp.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class DataLimiter {

    private DataLimiter() {
    }

    public static String truncateUtf8(String value, int maxBytes) {
        if (value == null) {
            return "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        int length = Math.max(0, maxBytes);
        while (length > 0 && (bytes[length] & 0xC0) == 0x80) {
            length--;
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_8) + "\n[content truncated]";
    }

    public static byte[] readAtMost(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("Content is too large for the configured limit.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
