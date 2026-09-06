package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsDpapiSecretStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndReadsPasswordThroughDpapi() throws Exception {
        WindowsDpapiSecretStore store = store();

        store.writePassword("chrystele", "mail-secret".toCharArray());

        assertEquals("mail-secret", store.readPassword("chrystele").orElseThrow());
        Path secretFile;
        try (var files = Files.list(tempDir)) {
            secretFile = files.findFirst().orElseThrow();
        }
        String stored = Files.readString(secretFile);
        assertFalse(stored.contains("mail-secret"));
        assertTrue(store.supportsDurableStorage());
    }

    @Test
    void returnsEmptyForUnknownProfileAndDeletesStoredPassword() {
        WindowsDpapiSecretStore store = store();

        assertTrue(store.readPassword("unknown").isEmpty());
        store.writePassword("chrystele", "mail-secret".toCharArray());
        assertTrue(store.deletePassword("chrystele"));
        assertTrue(store.readPassword("chrystele").isEmpty());
        assertFalse(store.deletePassword("chrystele"));
    }

    private WindowsDpapiSecretStore store() {
        return new WindowsDpapiSecretStore(tempDir, (operation, input) -> {
            byte[] value = Base64.getDecoder().decode(input);
            if (operation == WindowsDpapiSecretStore.Operation.PROTECT) {
                for (int index = 0; index < value.length; index++) {
                    value[index] ^= 0x5A;
                }
            } else {
                for (int index = 0; index < value.length; index++) {
                    value[index] ^= 0x5A;
                }
            }
            return Base64.getEncoder().encodeToString(value);
        });
    }
}
