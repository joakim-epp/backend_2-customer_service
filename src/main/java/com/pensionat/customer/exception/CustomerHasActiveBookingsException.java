package com.pensionat.customer.exception;

public class CustomerHasActiveBookingsException extends RuntimeException {
    private final long activeBookingCount;

    public CustomerHasActiveBookingsException(long activeBookingCount) {
        super("Customer has " + activeBookingCount + " active bookings");
        this.activeBookingCount = activeBookingCount;
    }

    public long getActiveBookingCount() {
        return activeBookingCount;
    }
}
