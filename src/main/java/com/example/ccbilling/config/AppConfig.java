package com.example.ccbilling.config;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Random;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    /** Business clock (UTC). Business "today" is the UTC date. Tests replace it with a controllable clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Spring Security delegating encoder: BCrypt by default, hashes carry their algorithm id. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public Random cardNumberRandom() {
        return new SecureRandom();
    }
}
