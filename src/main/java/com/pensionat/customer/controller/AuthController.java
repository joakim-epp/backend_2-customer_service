package com.pensionat.customer.controller;

import com.pensionat.customer.dto.LoginRequest;
import com.pensionat.customer.dto.LoginResponse;
import com.pensionat.customer.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(new LoginResponse(
                authService.login(request.username(), request.password())));
    }
}
