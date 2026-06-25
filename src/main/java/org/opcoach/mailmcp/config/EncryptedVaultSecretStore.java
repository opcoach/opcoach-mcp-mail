package org.opcoach.mailmcp.config;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class EncryptedVaultSecretStore implements SecretStore {

    public static final String VAULT_PASSWORD_ENV = "MAIL_MCP_VAULT_PASSWORD";
    public static final String VAULT_PASSWORD_STDIN_ENV = "MAIL_MCP_VAULT_PASSWORD_STDIN";
    public static final String VAULT_FILE_ENV = "MAIL_MCP_VAULT_FILE";

    private static final String VAULT_FILE = "secrets.enc";
    private static final String VERSION = "1";
    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int ITERATIONS = 310_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final Path vaultPath;
    private final Map<String, String> env;
    private final char[] fixedMasterPassword;
    private final SecureRandom random;

    public EncryptedVaultSecretStore() {
        this(defaultVaultPath(), System.getenv(), null, new SecureRandom());
    }

    EncryptedVaultSecretStore(Path vaultPath, Map<String, String> env, char[] fixedMasterPassword, SecureRandom random) {
        this.vaultPath = vaultPath;
        this.env = Map.copyOf(env);
        this.fixedMasterPassword = fixedMasterPassword == null ? null : Arrays.copyOf(fixedMasterPassword, fixedMasterPassword.length);
        this.random = random;
    }

    public static Path defaultVaultPath() {
        String configured = System.getenv(VAULT_FILE_ENV);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return ConfigurationPaths.defaultHomeDir().resolve(VAULT_FILE);
    }

    public EncryptedVaultSecretStore withMasterPassword(char[] masterPassword) {
        return new EncryptedVaultSecretStore(vaultPath, env, masterPassword, random);
    }

    public Path vaultPath() {
        return vaultPath;
    }

    @Override
    public Optional<String> readPassword(String profile) {
        if (!Files.exists(vaultPath)) {
            return Optional.empty();
        }
        Properties secrets = readSecrets();
        String encoded = secrets.getProperty(profileKey(profile));
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
    }

    @Override
    public void writePassword(String profile, char[] password) {
        if (password == null || password.length == 0) {
            throw new ConfigurationException("Empty mail password: no secret was written.");
        }
        Properties secrets = Files.exists(vaultPath) ? readSecrets() : new Properties();
        byte[] passwordBytes = utf8(password);
        try {
            secrets.setProperty(profileKey(profile), Base64.getEncoder().encodeToString(passwordBytes));
            writeSecrets(secrets);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    @Override
    public boolean deletePassword(String profile) {
        if (!Files.exists(vaultPath)) {
            return false;
        }
        Properties secrets = readSecrets();
        Object removed = secrets.remove(profileKey(profile));
        if (removed == null) {
            return false;
        }
        if (secrets.isEmpty()) {
            try {
                Files.deleteIfExists(vaultPath);
            } catch (IOException exception) {
                throw new ConfigurationException("Unable to delete encrypted vault: " + vaultPath, exception);
            }
        } else {
            writeSecrets(secrets);
        }
        return true;
    }

    @Override
    public boolean supportsDurableStorage() {
        return true;
    }

    private Properties readSecrets() {
        try {
            Properties envelope = new Properties();
            try (var input = Files.newInputStream(vaultPath)) {
                envelope.load(input);
            }
            validateEnvelope(envelope);
            byte[] salt = decode(envelope, "salt");
            byte[] iv = decode(envelope, "iv");
            byte[] cipherText = decode(envelope, "ciphertext");
            char[] masterPassword = masterPassword();
            try {
                byte[] plainText = decrypt(masterPassword, salt, iv, cipherText);
                try {
                    Properties secrets = new Properties();
                    secrets.load(new ByteArrayInputStream(plainText));
                    return secrets;
                } finally {
                    Arrays.fill(plainText, (byte) 0);
                }
            } finally {
                Arrays.fill(masterPassword, '\0');
            }
        } catch (AEADBadTagException exception) {
            throw new ConfigurationException("Unable to decrypt the local password vault. Check the vault password.", exception);
        } catch (IOException | GeneralSecurityException exception) {
            throw new ConfigurationException("Unable to read encrypted password vault: " + vaultPath, exception);
        }
    }

    private void writeSecrets(Properties secrets) {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(IV_BYTES);
        char[] masterPassword = masterPassword();
        try {
            byte[] plainText = plainText(secrets);
            try {
                byte[] cipherText = encrypt(masterPassword, salt, iv, plainText);
                Properties envelope = new Properties();
                envelope.setProperty("version", VERSION);
                envelope.setProperty("kdf", KDF);
                envelope.setProperty("iterations", Integer.toString(ITERATIONS));
                envelope.setProperty("cipher", CIPHER);
                envelope.setProperty("salt", Base64.getEncoder().encodeToString(salt));
                envelope.setProperty("iv", Base64.getEncoder().encodeToString(iv));
                envelope.setProperty("ciphertext", Base64.getEncoder().encodeToString(cipherText));
                writeEnvelope(envelope);
            } finally {
                Arrays.fill(plainText, (byte) 0);
            }
        } catch (IOException | GeneralSecurityException exception) {
            throw new ConfigurationException("Unable to write encrypted password vault: " + vaultPath, exception);
        } finally {
            Arrays.fill(masterPassword, '\0');
        }
    }

    private byte[] encrypt(char[] masterPassword, byte[] salt, byte[] iv, byte[] plainText) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, key(masterPassword, salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(plainText);
    }

    private byte[] decrypt(char[] masterPassword, byte[] salt, byte[] iv, byte[] cipherText) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, key(masterPassword, salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(cipherText);
    }

    private SecretKeySpec key(char[] masterPassword, byte[] salt) throws GeneralSecurityException {
        KeySpec spec = new PBEKeySpec(masterPassword, salt, ITERATIONS, KEY_BITS);
        byte[] keyBytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
        try {
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
            if (spec instanceof PBEKeySpec pbeKeySpec) {
                pbeKeySpec.clearPassword();
            }
        }
    }

    private byte[] plainText(Properties secrets) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        secrets.store(output, "Encrypted opcoach-mcp-mail secrets.");
        return output.toByteArray();
    }

    private void writeEnvelope(Properties envelope) throws IOException {
        Path absolute = vaultPath.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, "secrets-", ".tmp");
        try (var output = Files.newOutputStream(temporary)) {
            envelope.store(output, "Encrypted opcoach-mcp-mail password vault.");
        }
        restrictOwnerReadWrite(temporary);
        try {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
        restrictOwnerReadWrite(absolute);
    }

    private char[] masterPassword() {
        if (fixedMasterPassword != null && fixedMasterPassword.length > 0) {
            return Arrays.copyOf(fixedMasterPassword, fixedMasterPassword.length);
        }
        String configured = env.get(VAULT_PASSWORD_ENV);
        if (configured != null && !configured.isBlank()) {
            return configured.toCharArray();
        }
        if ("1".equals(env.get(VAULT_PASSWORD_STDIN_ENV))) {
            return readMasterPasswordFromStdin();
        }
        throw new ConfigurationException("""
                Missing vault password.
                Set MAIL_MCP_VAULT_PASSWORD, start through a script that prompts for it,
                or use the local setup UI through an SSH tunnel.
                """);
    }

    private char[] readMasterPasswordFromStdin() {
        try {
            Console console = System.console();
            if (console != null) {
                char[] password = console.readPassword("Vault password: ");
                return password == null ? new char[0] : password;
            }
            StringBuilder builder = new StringBuilder();
            int value;
            while ((value = System.in.read()) != -1 && value != '\n' && value != '\r') {
                builder.append((char) value);
            }
            return builder.toString().toCharArray();
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to read vault password from stdin.", exception);
        }
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }

    private static void validateEnvelope(Properties envelope) {
        require(envelope, "version", VERSION);
        require(envelope, "kdf", KDF);
        require(envelope, "cipher", CIPHER);
        require(envelope, "iterations", Integer.toString(ITERATIONS));
    }

    private static void require(Properties properties, String key, String expected) {
        String value = properties.getProperty(key);
        if (!expected.equals(value)) {
            throw new ConfigurationException("Unsupported encrypted vault " + key + ": " + value);
        }
    }

    private static byte[] decode(Properties envelope, String key) {
        String value = envelope.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing encrypted vault field: " + key);
        }
        return Base64.getDecoder().decode(value);
    }

    private static byte[] utf8(char[] value) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return bytes;
    }

    private static String profileKey(String profile) {
        String normalized = profile == null || profile.isBlank() ? "default" : profile.trim();
        String encodedProfile = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
        return "profile." + encodedProfile;
    }

    private static void restrictOwnerReadWrite(Path path) {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and some volumes do not support POSIX permissions.
        }
    }
}
