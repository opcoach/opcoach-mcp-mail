package org.opcoach.mailmcp.config;

import java.util.Optional;

public interface SecretStore {

    Optional<String> readPassword(String profile);

    default void writePassword(String profile, char[] password) {
        throw new UnsupportedOperationException("Secret writes are not supported by this backend.");
    }

    default boolean deletePassword(String profile) {
        return false;
    }

    default boolean supportsDurableStorage() {
        return false;
    }
}
