package org.opcoach.mailmcp.config;

import java.util.Optional;

public interface SecretStore {

    Optional<String> readPassword(String profile);

    default void writePassword(String profile, char[] password) {
        throw new UnsupportedOperationException("Écriture de secret non supportée par ce backend.");
    }
}
