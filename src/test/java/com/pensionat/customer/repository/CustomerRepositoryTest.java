package com.pensionat.customer.repository;

import com.pensionat.customer.TestcontainersConfiguration;
import com.pensionat.customer.model.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void savedCustomerCanBeRetrieved() {
        Customer c = new Customer();
        c.setFirstName("Anna");
        c.setLastName("Svensson");
        c.setEmail("anna@example.com");

        Long id = customerRepository.save(c).getId();

        assertThat(customerRepository.findById(id))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getFirstName()).isEqualTo("Anna");
                    assertThat(saved.getLastName()).isEqualTo("Svensson");
                });
    }

    @Test
    void customerWithoutEmailCanBeSaved() {
        Customer c = new Customer();
        c.setFirstName("Bo");
        c.setLastName("Nilsson");

        assertThat(customerRepository.save(c).getId()).isNotNull();
    }
}
