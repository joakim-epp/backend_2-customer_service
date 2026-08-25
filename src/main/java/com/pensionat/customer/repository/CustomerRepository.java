package com.pensionat.customer.repository;

import com.pensionat.customer.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByDeletedAtIsNull();

    Optional<Customer> findByIdAndDeletedAtIsNull(Long id);

    /** Batch lookup deliberately includes soft-deleted customers, see CustomerService.findByIds. */
    List<Customer> findByIdIn(List<Long> ids);

    Optional<Customer> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
}
