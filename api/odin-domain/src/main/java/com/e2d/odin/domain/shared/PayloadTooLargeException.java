package com.e2d.odin.domain.shared;

public class PayloadTooLargeException extends DomainException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
