package com.pensionat.customer.controller;

import com.pensionat.customer.TestcontainersConfiguration;
import com.pensionat.customer.model.Customer;
import com.pensionat.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void clear() {
        customerRepository.deleteAll();
    }

    private Long saveCustomer(String firstName, String lastName) {
        Customer c = new Customer();
        c.setFirstName(firstName);
        c.setLastName(lastName);
        return customerRepository.save(c).getId();
    }

    @Test
    void createCustomerReturns201AndPersists() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Anna", "lastName": "Svensson", "email": "anna@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(customerRepository.findAll())
                .singleElement()
                .satisfies(c -> assertThat(c.getFirstName()).isEqualTo("Anna"));
    }

    @Test
    void blankFirstNameReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "", "lastName": "Svensson"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("firstName"));
    }

    @Test
    void invalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Anna", "lastName": "Svensson", "email": "not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/customers/999999").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void batchFetchesMultipleCustomers() throws Exception {
        Long id1 = saveCustomer("Anna", "Svensson");
        Long id2 = saveCustomer("Bo", "Nilsson");

        mockMvc.perform(get("/api/customers").param("ids", id1 + "," + id2 + ",999999").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void emptyIdsReturns400() throws Exception {
        mockMvc.perform(get("/api/customers").param("ids", "").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void blankIdInListReturns400() throws Exception {
        mockMvc.perform(get("/api/customers").param("ids", "1,,2").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void negativeIdReturns400() throws Exception {
        mockMvc.perform(get("/api/customers").param("ids", "-1").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void moreThan100IdsReturns400() throws Exception {
        String ids = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(get("/api/customers").param("ids", ids).with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void putReplacesAllFields() throws Exception {
        Long id = saveCustomer("Anna", "Svensson");

        mockMvc.perform(put("/api/customers/" + id)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Anna", "lastName": "Andersson"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Andersson"));
    }
}
