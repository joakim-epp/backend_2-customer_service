package com.pensionat.customer.client;

import com.pensionat.customer.exception.BookingServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BookingClient {

    private final RestClient restClient;
    private final String baseUrl;

    public BookingClient(RestClient restClient, @Value("${booking.service.url}") String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    public long countActiveBookings(Long customerId) {
        try {
            BookingCountResponse response = restClient.get()
                    .uri(baseUrl + "/api/bookings/count?customerId={id}&status=ACTIVE", customerId)
                    .retrieve()
                    .body(BookingCountResponse.class);

            if (response == null || response.count() == null) {
                throw new BookingServiceUnavailableException(
                        "Booking service responded without a count", null);
            }
            return response.count();
        } catch (RestClientException e) {
            throw new BookingServiceUnavailableException("Could not reach the booking service", e);
        }
    }
}
