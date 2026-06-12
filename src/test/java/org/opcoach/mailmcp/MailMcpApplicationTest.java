package org.opcoach.mailmcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MailMcpApplicationTest {

    @Test
    void helpDoesNotRequireConfiguration() {
        int exitCode = new MailMcpApplication.MailMcpApplicationRunner().run(new String[]{"--help"});

        assertEquals(0, exitCode);
    }

    @Test
    void missingConfigurationReturnsActionableError() {
        int exitCode = new MailMcpApplication.MailMcpApplicationRunner().run(new String[]{"--stdio"});

        assertEquals(2, exitCode);
    }
}
