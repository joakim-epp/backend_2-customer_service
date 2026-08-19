package com.pensionat.customer.controller;

import com.pensionat.customer.dto.CustomerRequest;
import com.pensionat.customer.dto.CustomerResponse;
import com.pensionat.customer.exception.InvalidRequestException;
import com.pensionat.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final int MAX_IDS = 100;

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<CustomerResponse> list(@RequestParam(required = false) String ids) {
        return ids == null ? customerService.findAll() : customerService.findByIds(parseIds(ids));
    }

    @GetMapping("/{id}")
    public CustomerResponse findById(@PathVariable Long id) {
        return customerService.findById(validateId(id));
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(validateId(id), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(validateId(id));
        return ResponseEntity.noContent().build();
    }

    private Long validateId(Long id) {
        if (id <= 0) {
            throw new InvalidRequestException("Id must be a positive number");
        }
        return id;
    }

    private List<Long> parseIds(String ids) {
        String[] parts = ids.split(",", -1);
        if (parts.length > MAX_IDS) {
            throw new InvalidRequestException("At most " + MAX_IDS + " ids per request");
        }
        List<Long> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new InvalidRequestException("The ids parameter must not contain empty values");
            }
            try {
                long value = Long.parseLong(trimmed);
                if (value <= 0) {
                    throw new InvalidRequestException("Id must be a positive number: " + trimmed);
                }
                result.add(value);
            } catch (NumberFormatException e) {
                throw new InvalidRequestException("Invalid id: " + trimmed);
            }
        }
        return result;
    }
}
