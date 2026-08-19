package com.pensionat.customer.service;

import com.pensionat.customer.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final long expiryMinutes;

    public AuthService(AppUserRepository appUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       @Value("${app.jwt.issuer}") String issuer,
                       @Value("${app.jwt.audience}") String audience,
                       @Value("${app.jwt.expiry-minutes}") long expiryMinutes) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.audience = audience;
        this.expiryMinutes = expiryMinutes;
    }

    public String login(String username, String password) {
        var user = appUserRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Wrong username or password"));

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
