package com.example.ccbilling.config;

import com.example.ccbilling.domain.Administrator;
import com.example.ccbilling.repository.AdministratorRepository;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Provisions the initial administrator on startup. Credentials come from BOOTSTRAP_ADMIN_USERNAME /
 * BOOTSTRAP_ADMIN_PASSWORD (never hard-coded). In the local H2 profile only, a random one-time password is
 * generated and printed once if nothing is configured and no administrator exists.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    static final int MIN_PASSWORD_LENGTH = 12;

    private final AdministratorRepository administrators;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String fullName;
    private final boolean generateIfMissing;

    public AdminBootstrap(AdministratorRepository administrators, PasswordEncoder passwordEncoder,
                          @Value("${app.bootstrap.admin.username:}") String username,
                          @Value("${app.bootstrap.admin.password:}") String password,
                          @Value("${app.bootstrap.admin.full-name:Local Administrator}") String fullName,
                          @Value("${app.bootstrap.admin.generate-password-if-missing:false}") boolean generateIfMissing) {
        this.administrators = administrators;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.fullName = fullName;
        this.generateIfMissing = generateIfMissing;
    }

    @Override
    public void run(ApplicationArguments args) {
        String name = username;
        String pass = password;
        boolean generated = false;

        if (name.isEmpty() && pass.isEmpty() && generateIfMissing && administrators.count() == 0) {
            name = "admin";
            pass = randomPassword();
            generated = true;
        }
        if (name.isEmpty() || pass.isEmpty()) {
            log.info("No initial administrator configured (set BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD).");
            return;
        }
        if (administrators.existsByUsername(name)) {
            log.info("Administrator '{}' already exists; bootstrap skipped.", name);
            return;
        }
        if (pass.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        administrators.save(new Administrator(name, fullName, passwordEncoder.encode(pass)));
        if (generated) {
            log.warn("""

                    ================================================================
                     LOCAL DEV ONLY - generated one-time administrator credentials
                       username: {}
                       password: {}
                     Set BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD to choose your own.
                    ================================================================""", name, pass);
        } else {
            log.info("Initial administrator '{}' created.", name);
        }
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
