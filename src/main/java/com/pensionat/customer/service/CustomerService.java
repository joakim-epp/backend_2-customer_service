package com.pensionat.customer.service;

import com.pensionat.customer.client.BookingClient;
import com.pensionat.customer.dto.CustomerRequest;
import com.pensionat.customer.dto.CustomerResponse;
import com.pensionat.customer.exception.CustomerHasActiveBookingsException;
import com.pensionat.customer.exception.CustomerNotFoundException;
import com.pensionat.customer.model.Customer;
import com.pensionat.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final BookingClient bookingClient;

    public CustomerService(CustomerRepository customerRepository, BookingClient bookingClient) {
        this.customerRepository = customerRepository;
        this.bookingClient = bookingClient;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll() {
        return customerRepository.findByDeletedAtIsNull().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return customerRepository.findByIdAndDeletedAtIsNull(id)
                .map(this::toResponse)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    /**
     * Batch lookup includes soft-deleted customers, flagged with deleted=true. The booking service
     * uses this to render names in its booking list, and a booking may legitimately point at a
     * customer that was deleted afterwards. Existence checks use findById instead, which hides
     * deleted customers so no new booking can be created for one.
     */
    @Transactional(readOnly = true)
    public List<CustomerResponse> findByIds(List<Long> ids) {
        return customerRepository.findByIdIn(ids).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer c = new Customer();
        apply(request, c);
        return toResponse(customerRepository.save(c));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer c = customerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        apply(request, c);
        return toResponse(customerRepository.save(c));
    }

    /**
     * Deliberately not @Transactional: the HTTP call below may take up to four seconds, and a
     * transaction spanning it would hold a pooled connection open the whole time.
     * SimpleJpaRepository.save is itself transactional, so the soft delete still runs in its own
     * short transaction.
     */
    public void delete(Long id) {
        if (customerRepository.findByIdAndDeletedAtIsNull(id).isEmpty()) {
            throw new CustomerNotFoundException(id);
        }

        long activeBookings = bookingClient.countActiveBookings(id);
        if (activeBookings > 0) {
            throw new CustomerHasActiveBookingsException(activeBookings);
        }

        // Re-read rather than reusing the entity fetched above: it is up to four seconds stale by
        // now, and saving it would silently undo any edit made in that window.
        customerRepository.findByIdAndDeletedAtIsNull(id).ifPresent(c -> {
            c.setDeletedAt(Instant.now());
            customerRepository.save(c);
        });
    }

    private void apply(CustomerRequest request, Customer c) {
        c.setFirstName(request.firstName());
        c.setLastName(request.lastName());
        c.setEmail(request.email());
        c.setPhone(request.phone());
        c.setAddress(request.address());
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(),
                c.getEmail(), c.getPhone(), c.getAddress(), c.isDeleted());
    }
}
