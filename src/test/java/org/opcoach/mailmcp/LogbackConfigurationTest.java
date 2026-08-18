package org.opcoach.mailmcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.status.Status;

class LogbackConfigurationTest {

    @TempDir
    Path tempDirectory;

    @Test
    void boundsAndRotatesApplicationLogs() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/logback.xml")) {
            assertNotNull(input);
            String configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(configuration.contains("SizeAndTimeBasedRollingPolicy"));
            assertTrue(configuration.contains("<maxFileSize>50MB</maxFileSize>"));
            assertTrue(configuration.contains("<maxHistory>10</maxHistory>"));
            assertTrue(configuration.contains("<totalSizeCap>450MB</totalSizeCap>"));
            assertTrue(configuration.contains("${MAIL_MCP_LOG_LEVEL:-INFO}"));
            assertTrue(configuration.contains("${user.home}/.opcoach-mcp-mail/logs/opcoach-mcp-mail.log"));
        }

        LoggerContext context = new LoggerContext();
        context.putProperty("MAIL_MCP_LOG_FILE", tempDirectory.resolve("mail-mcp.log").toString());
        try (InputStream input = getClass().getResourceAsStream("/logback.xml")) {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(input);

            assertFalse(context.getStatusManager().getCopyOfStatusList().stream()
                    .anyMatch(status -> status.getLevel() >= Status.ERROR));
        } finally {
            context.stop();
        }
    }
}
