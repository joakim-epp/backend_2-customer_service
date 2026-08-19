package com.pensionat.customer.client;

import com.pensionat.customer.exception.BookingServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BookingClientTest {

    private static final String BASE_URL = "http://booking-service:8081";
    private static final String COUNT_URL = BASE_URL + "/api/bookings/count?customerId=5&status=ACTIVE";

    private MockRestServiceServer server;
    private BookingClient bookingClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        bookingClient = new BookingClient(builder.build(), BASE_URL);
    }

    @Test
    void validResponseReturnsCount() {
        server.expect(requestTo(COUNT_URL))
                .andRespond(withSuccess("{\"count\": 2}", MediaType.APPLICATION_JSON));

        assertThat(bookingClient.countActiveBookings(5L)).isEqualTo(2L);
    }

    @Test
    void zeroBookingsReturnsZero() {
        server.expect(requestTo(COUNT_URL))
                .andRespond(withSuccess("{\"count\": 0}", MediaType.APPLICATION_JSON));

        assertThat(bookingClient.countActiveBookings(5L)).isZero();
    }

    @Test
    void serverErrorThrowsUnavailable() {
        server.expect(requestTo(COUNT_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> bookingClient.countActiveBookings(5L))
                .isInstanceOf(BookingServiceUnavailableException.class);
    }

    @Test
    void clientErrorThrowsUnavailable() {
        server.expect(requestTo(COUNT_URL)).andRespond(withBadRequest());

        assertThatThrownBy(() -> bookingClient.countActiveBookings(5L))
                .isInstanceOf(BookingServiceUnavailableException.class);
    }

    @Test
    void unparsableBodyThrowsUnavailable() {
        server.expect(requestTo(COUNT_URL))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> bookingClient.countActiveBookings(5L))
                .isInstanceOf(BookingServiceUnavailableException.class);
    }

    @Test
    void missingCountFieldThrowsUnavailable() {
        server.expect(requestTo(COUNT_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> bookingClient.countActiveBookings(5L))
                .isInstanceOf(BookingServiceUnavailableException.class);
    }
}
