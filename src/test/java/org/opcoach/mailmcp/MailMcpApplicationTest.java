package org.opcoach.mailmcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.opcoach.mailmcp.config.ConfigurationPaths;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MailMcpApplicationTest {

    @Test
    void helpDoesNotRequireConfiguration() {
        int exitCode = new MailMcpApplication.MailMcpApplicationRunner().run(new String[]{"--help"});

        assertEquals(0, exitCode);
    }

    @Test
    @Timeout(5)
    void missingConfigurationReturnsActionableError() {
        String previousConfig = System.getProperty(ConfigurationPaths.CONFIG_PROPERTY);
        try {
            System.setProperty(ConfigurationPaths.CONFIG_PROPERTY, missingConfig.toString());
            int exitCode = new MailMcpApplication.MailMcpApplicationRunner().run(new String[]{"--stdio"});

            assertEquals(2, exitCode);
        } finally {
            restoreConfigProperty(previousConfig);
        }
    }

    @TempDir
    Path tempDir;

    private Path missingConfig;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        missingConfig = tempDir.resolve("missing.properties");
    }

    private static void restoreConfigProperty(String previousConfig) {
        if (previousConfig == null) {
            System.clearProperty(ConfigurationPaths.CONFIG_PROPERTY);
        } else {
            System.setProperty(ConfigurationPaths.CONFIG_PROPERTY, previousConfig);
        }
    }
}
