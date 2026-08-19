package com.pensionat.customer.service;

import com.pensionat.customer.dto.CustomerRequest;
import com.pensionat.customer.dto.CustomerResponse;
import com.pensionat.customer.exception.CustomerNotFoundException;
import com.pensionat.customer.model.Customer;
import com.pensionat.customer.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void findByIdMapsToResponse() {
        Customer anna = new Customer();
        anna.setId(1L);
        anna.setFirstName("Anna");
        anna.setLastName("Svensson");
        when(customerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(anna));

        CustomerResponse response = customerService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.firstName()).isEqualTo("Anna");
    }

    @Test
    void findByIdUnknownIdThrowsNotFound() {
        when(customerRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findById(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void updateUnknownIdThrowsNotFound() {
        when(customerRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        CustomerRequest request = new CustomerRequest("Anna", "Svensson", null, null, null);

        assertThatThrownBy(() -> customerService.update(99L, request))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void createSavesAndReturnsResponse() {
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer c = invocation.getArgument(0);
                    c.setId(7L);
                    return c;
                });

        CustomerResponse response = customerService.create(
                new CustomerRequest("Bo", "Nilsson", "bo@example.com", null, null));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("bo@example.com");
    }
}
