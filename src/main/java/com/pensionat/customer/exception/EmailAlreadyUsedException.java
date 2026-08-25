package com.pensionat.customer.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Email address " + email + " is already used by another customer");
    }
}
