package com.e2d.odin.application.port;

/** La lectura agotó su tiempo: se reintenta una sola vez. */
public class ReaderTimedOutException extends ReaderFailedException {

    public ReaderTimedOutException(String message, Throwable cause) {
        super(message, cause);
    }
}
