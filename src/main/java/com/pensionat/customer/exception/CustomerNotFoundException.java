package com.pensionat.customer.exception;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long customerId) {
        super("Customer with id " + customerId + " was not found");
    }
}
