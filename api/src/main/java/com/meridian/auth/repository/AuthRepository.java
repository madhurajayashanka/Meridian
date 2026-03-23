package com.meridian.auth.repository;

import com.meridian.auth.entity.RefreshToken;
import com.meridian.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthRepository extends JpaRepository<RefreshToken, UUID> {

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash AND rt.revokedAt IS NULL AND rt.expiresAt > :now")
    Optional<RefreshToken> findValidToken(String tokenHash, LocalDateTime now);

    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    void deleteAllByUserId(UUID userId);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
    long countActiveTokensByUserId(UUID userId);
}
