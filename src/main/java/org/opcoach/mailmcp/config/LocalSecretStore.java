package org.opcoach.mailmcp.config;

import java.util.Locale;
import java.util.Optional;

public final class LocalSecretStore implements SecretStore {

    private final SecretStore delegate;

    private LocalSecretStore(SecretStore delegate) {
        this.delegate = delegate;
    }

    public static LocalSecretStore system() {
        return system(null);
    }

    public static LocalSecretStore system(char[] vaultPassword) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            return new LocalSecretStore(new KeychainSecretStore(osName));
        }
        if (osName.contains("linux")) {
            EncryptedVaultSecretStore store = new EncryptedVaultSecretStore();
            if (vaultPassword != null && vaultPassword.length > 0) {
                store = store.withMasterPassword(vaultPassword);
            }
            return new LocalSecretStore(store);
        }
        return new LocalSecretStore(new UnsupportedSecretStore());
    }

    public static boolean systemUsesEncryptedVault() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    @Override
    public Optional<String> readPassword(String profile) {
        return delegate.readPassword(profile);
    }

    @Override
    public void writePassword(String profile, char[] password) {
        delegate.writePassword(profile, password);
    }

    @Override
    public boolean deletePassword(String profile) {
        return delegate.deletePassword(profile);
    }

    @Override
    public boolean supportsDurableStorage() {
        return delegate.supportsDurableStorage();
    }

    private static final class UnsupportedSecretStore implements SecretStore {

        @Override
        public Optional<String> readPassword(String profile) {
            return Optional.empty();
        }

        @Override
        public void writePassword(String profile, char[] password) {
            throw new ConfigurationException("""
                    No durable local secret store is supported on this platform yet.
                    Use MAIL_MCP_PASSWORD temporarily.
                    """);
        }
    }
}
