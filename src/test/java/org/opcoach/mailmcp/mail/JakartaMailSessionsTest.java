package org.opcoach.mailmcp.mail;

import org.junit.jupiter.api.Test;
import org.opcoach.mailmcp.config.ConnectionSecurity;
import org.opcoach.mailmcp.config.MailEndpoint;

import javax.net.ssl.SSLSocketFactory;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JakartaMailSessionsTest {

    @Test
    void imapsEnablesTlsAndHostnameVerification() {
        Properties properties = JakartaMailSessions.imapProperties(
                new MailEndpoint("imap.example.com", 993, ConnectionSecurity.SSL_TLS)
        );

        assertEquals("true", properties.getProperty("mail.imaps.ssl.enable"));
        assertEquals("true", properties.getProperty("mail.imaps.ssl.checkserveridentity"));
        assertOptionalPlatformSocketFactory(properties, "mail.imaps.ssl.socketFactory");
    }

    @Test
    void imapStartTlsRequiresTlsAndVerifiesHostname() {
        Properties properties = JakartaMailSessions.imapProperties(
                new MailEndpoint("imap.example.com", 143, ConnectionSecurity.STARTTLS)
        );

        assertEquals("true", properties.getProperty("mail.imap.starttls.enable"));
        assertEquals("true", properties.getProperty("mail.imap.starttls.required"));
        assertEquals("true", properties.getProperty("mail.imap.ssl.checkserveridentity"));
        assertOptionalPlatformSocketFactory(properties, "mail.imap.ssl.socketFactory");
    }

    @Test
    void smtpTlsVerifiesHostname() {
        Properties properties = JakartaMailSessions.smtpProperties(
                new MailEndpoint("smtp.example.com", 465, ConnectionSecurity.SSL_TLS)
        );

        assertEquals("true", properties.getProperty("mail.smtps.ssl.enable"));
        assertEquals("true", properties.getProperty("mail.smtps.ssl.checkserveridentity"));
        assertOptionalPlatformSocketFactory(properties, "mail.smtps.ssl.socketFactory");
    }

    @Test
    void unencryptedConnectionDoesNotConfigureTls() {
        Properties properties = JakartaMailSessions.imapProperties(
                new MailEndpoint("127.0.0.1", 1143, ConnectionSecurity.NONE)
        );

        assertFalse(properties.containsKey("mail.imap.ssl.checkserveridentity"));
        assertFalse(properties.containsKey("mail.imap.ssl.socketFactory"));
    }

    private static void assertOptionalPlatformSocketFactory(Properties properties, String key) {
        if (properties.containsKey(key)) {
            assertInstanceOf(SSLSocketFactory.class, properties.get(key));
        } else {
            assertTrue(PlatformSslContext.windowsAndJavaSocketFactory().isEmpty());
        }
    }
}
