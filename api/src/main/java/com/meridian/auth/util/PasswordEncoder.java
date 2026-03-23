package com.meridian.auth.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password hashing utility using BCrypt with cost factor 12.
 * Property 5: Token security invariants — passwords hashed with $2b$12$ prefix
 */
@Slf4j
@Component
public class PasswordEncoder {

    private static final int BCRYPT_STRENGTH = 12;
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    /**
     * Encode a plain text password using BCrypt with cost factor 12.
     * Requirement 1.7: Passwords stored using bcrypt with cost factor 12
     */
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * Verify a plain text password against a BCrypt hash.
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }

    /**
     * Verify that the hash was generated with BCrypt cost factor 12.
     * Property 5: Password hashes should begin with $2b$12$
     */
    public boolean isValidBcryptHash(String hash) {
        return hash != null && hash.startsWith("$2b$12$");
    }
}
