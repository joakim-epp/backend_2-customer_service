package com.pensionat.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must be at most 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must be at most 50 characters")
        String lastName,

        @Email(message = "Email address is not valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @Size(max = 30, message = "Phone number must be at most 30 characters")
        String phone,

        @Size(max = 200, message = "Address must be at most 200 characters")
        String address
) {}
