package com.pensionat.customer.controller;

import com.pensionat.customer.TestcontainersConfiguration;
import com.pensionat.customer.client.BookingClient;
import com.pensionat.customer.exception.BookingServiceUnavailableException;
import com.pensionat.customer.model.Customer;
import com.pensionat.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerDeleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @MockitoBean
    private BookingClient bookingClient;

    private Long id;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
        Customer c = new Customer();
        c.setFirstName("Anna");
        c.setLastName("Svensson");
        id = customerRepository.save(c).getId();
    }

    @Test
    void withoutActiveBookingsReturns204AndDeletes() throws Exception {
        when(bookingClient.countActiveBookings(id)).thenReturn(0L);

        mockMvc.perform(delete("/api/customers/" + id).with(jwt()))
                .andExpect(status().isNoContent());

        assertThat(customerRepository.findById(id)).isEmpty();
    }

    @Test
    void withActiveBookingsReturns409AndKeepsCustomer() throws Exception {
        when(bookingClient.countActiveBookings(id)).thenReturn(2L);

        mockMvc.perform(delete("/api/customers/" + id).with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_HAS_ACTIVE_BOOKINGS"))
                .andExpect(jsonPath("$.activeBookingCount").value(2));

        assertThat(customerRepository.findById(id)).isPresent();
    }

    @Test
    void bookingServiceDownReturns503AndKeepsCustomer() throws Exception {
        when(bookingClient.countActiveBookings(id))
                .thenThrow(new BookingServiceUnavailableException("down", null));

        mockMvc.perform(delete("/api/customers/" + id).with(jwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.errorCode").value("BOOKING_SERVICE_UNAVAILABLE"));

        assertThat(customerRepository.findById(id)).isPresent();
    }

    @Test
    void unknownCustomerReturns404WithoutCallingBookingService() throws Exception {
        mockMvc.perform(delete("/api/customers/999999").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));

        verify(bookingClient, never()).countActiveBookings(any());
    }
}
