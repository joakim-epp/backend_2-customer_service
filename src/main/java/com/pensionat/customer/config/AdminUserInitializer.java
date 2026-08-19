package com.pensionat.customer.config;

import com.pensionat.customer.model.AppUser;
import com.pensionat.customer.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminUserInitializer(AppUserRepository appUserRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin.username}") String username,
                                @Value("${app.admin.password}") String password) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (appUserRepository.findByUsername(username).isPresent()) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        appUserRepository.save(admin);
    }
}
