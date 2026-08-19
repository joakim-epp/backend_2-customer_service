package com.pensionat.customer.dto;

public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String address,
        boolean deleted
) {}
