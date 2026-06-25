package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedVaultSecretStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void storesPasswordsInEncryptedVault() throws Exception {
        Path vault = tempDir.resolve("secrets.enc");
        EncryptedVaultSecretStore store = store(vault, "vault-secret");

        store.writePassword("default", "mail-secret".toCharArray());

        assertEquals("mail-secret", store.readPassword("default").orElseThrow());
        String vaultContent = Files.readString(vault);
        assertFalse(vaultContent.contains("mail-secret"));
        assertFalse(vaultContent.contains("default"));
        assertTrue(vaultContent.contains("ciphertext="));
    }

    @Test
    void rejectsWrongVaultPassword() {
        Path vault = tempDir.resolve("secrets.enc");
        store(vault, "vault-secret").writePassword("default", "mail-secret".toCharArray());

        ConfigurationException exception = assertThrows(
                ConfigurationException.class,
                () -> store(vault, "wrong-secret").readPassword("default")
        );

        assertTrue(exception.getMessage().contains("Unable to decrypt"));
    }

    @Test
    void deletesOnlySelectedProfilePassword() throws Exception {
        Path vault = tempDir.resolve("secrets.enc");
        EncryptedVaultSecretStore store = store(vault, "vault-secret");
        store.writePassword("default", "first-secret".toCharArray());
        store.writePassword("olivier", "second-secret".toCharArray());

        assertEquals(true, store.deletePassword("default"));

        assertEquals("second-secret", store.readPassword("olivier").orElseThrow());
        assertEquals(true, store.readPassword("default").isEmpty());

        assertEquals(true, store.deletePassword("olivier"));
        assertTrue(Files.notExists(vault));
    }

    private static EncryptedVaultSecretStore store(Path vault, String masterPassword) {
        return new EncryptedVaultSecretStore(
                vault,
                Map.of(),
                masterPassword.toCharArray(),
                new SecureRandom()
        );
    }
}
