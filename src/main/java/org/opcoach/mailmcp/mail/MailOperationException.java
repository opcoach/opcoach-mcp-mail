package org.opcoach.mailmcp.mail;

public class MailOperationException extends RuntimeException {

    public MailOperationException(String message) {
        super(message);
    }

    public MailOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
