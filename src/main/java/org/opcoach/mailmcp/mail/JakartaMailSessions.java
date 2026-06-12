package org.opcoach.mailmcp.mail;

import org.opcoach.mailmcp.config.ConnectionSecurity;
import org.opcoach.mailmcp.config.MailEndpoint;

import java.util.Properties;

final class JakartaMailSessions {

    private JakartaMailSessions() {
    }

    static String smtpProtocol(MailEndpoint endpoint) {
        return endpoint.security() == ConnectionSecurity.SSL_TLS ? "smtps" : "smtp";
    }

    static String imapProtocol(MailEndpoint endpoint) {
        return endpoint.security() == ConnectionSecurity.SSL_TLS ? "imaps" : "imap";
    }

    static Properties smtpProperties(MailEndpoint endpoint) {
        String protocol = smtpProtocol(endpoint);
        Properties properties = baseProperties(protocol, endpoint);
        properties.put("mail." + protocol + ".auth", "true");
        properties.put("mail." + protocol + ".connectiontimeout", "10000");
        properties.put("mail." + protocol + ".timeout", "10000");
        if (endpoint.security() == ConnectionSecurity.STARTTLS) {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
        }
        if (endpoint.security() == ConnectionSecurity.SSL_TLS) {
            properties.put("mail.smtps.ssl.enable", "true");
        }
        return properties;
    }

    static Properties imapProperties(MailEndpoint endpoint) {
        String protocol = imapProtocol(endpoint);
        Properties properties = baseProperties(protocol, endpoint);
        properties.put("mail." + protocol + ".connectiontimeout", "10000");
        properties.put("mail." + protocol + ".timeout", "10000");
        if (endpoint.security() == ConnectionSecurity.STARTTLS) {
            properties.put("mail.imap.starttls.enable", "true");
            properties.put("mail.imap.starttls.required", "true");
        }
        if (endpoint.security() == ConnectionSecurity.SSL_TLS) {
            properties.put("mail.imaps.ssl.enable", "true");
        }
        return properties;
    }

    private static Properties baseProperties(String protocol, MailEndpoint endpoint) {
        Properties properties = new Properties();
        properties.put("mail.transport.protocol", protocol);
        properties.put("mail.store.protocol", protocol);
        properties.put("mail." + protocol + ".host", endpoint.host());
        properties.put("mail." + protocol + ".port", Integer.toString(endpoint.port()));
        properties.put("mail.mime.charset", "UTF-8");
        return properties;
    }
}
