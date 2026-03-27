package com.meridian.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT Utility for token generation and validation.
 * Uses RS256 asymmetric signing with RSA-2048 keypair.
 * Property 5: Token security invariants — RS256 algorithm and bcrypt prefix $2b$12$
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.jwt.access-token-expiry-minutes:15}")
    private int accessTokenExpiryMinutes;

    @Value("${app.jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    @Value("${JWT_SECRET_KEY}")
    private String secretKey;

    @Value("${JWT_PUBLIC_KEY}")
    private String publicKey;

    private PrivateKey privateKeyObj;
    private PublicKey publicKeyObj;

    /**
     * Initialize keys from PEM format strings.
     * PEM format: -----BEGIN PRIVATE KEY----- ... -----END PRIVATE KEY-----
     */
    private void initializeKeys() {
        if (privateKeyObj == null) {
            try {
                // Parse private key (PKCS8 format)
                String privateKeyPEM = secretKey
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
                byte[] decodedKey = java.util.Base64.getDecoder().decode(privateKeyPEM);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                privateKeyObj = kf.generatePrivate(spec);

                // Parse public key (X.509 format)
                String publicKeyPEM = publicKey
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
                byte[] decodedPublicKey = java.util.Base64.getDecoder().decode(publicKeyPEM);
                X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(decodedPublicKey);
                publicKeyObj = kf.generatePublic(publicKeySpec);
            } catch (Exception e) {
                log.error("Error initializing JWT keys", e);
                throw new RuntimeException("Failed to initialize JWT keys", e);
            }
        }
    }

    /**
     * Generate JWT access token (15-minute expiry by default).
     * Property 2: Login returns valid tokens with correct expiry
     */
    public String generateAccessToken(UUID userId, String email) {
        initializeKeys();
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", "access");

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (long) accessTokenExpiryMinutes * 60 * 1000);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(privateKeyObj, SignatureAlgorithm.RS256)
                .compact();
    }

    /**
     * Generate JWT refresh token (7-day expiry by default).
     * Property 2: Login returns valid tokens with correct expiry
     * Property 4: Token refresh round-trip
     */
    public String generateRefreshToken(UUID userId) {
        initializeKeys();
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("jti", UUID.randomUUID().toString()); // JWT ID for revocation tracking

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (long) refreshTokenExpiryDays * 24 * 60 * 60 * 1000);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(privateKeyObj, SignatureAlgorithm.RS256)
                .compact();
    }

    /**
     * Validate and extract claims from JWT token.
     * Returns null if token is invalid or expired.
     */
    public Claims validateToken(String token) {
        initializeKeys();
        
        try {
            return Jwts.parser()
                    .verifyWith(publicKeyObj)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract user ID from token claims.
     */
    public UUID extractUserId(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return null;
        }
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in token: {}", claims.getSubject());
            return null;
        }
    }

    /**
     * Extract email from token claims.
     */
    public String extractEmail(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("email", String.class);
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return true;
        }
        return claims.getExpiration().before(new Date());
    }

    /**
     * Check if token is a refresh token.
     */
    public boolean isRefreshToken(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return false;
        }
        return "refresh".equals(claims.get("type", String.class));
    }

    /**
     * Check if token is an access token.
     */
    public boolean isAccessToken(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return false;
        }
        return "access".equals(claims.get("type", String.class));
    }

    /**
     * Get JTI (JWT ID) from token for revocation tracking.
     */
    public String getJti(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("jti", String.class);
    }
}
