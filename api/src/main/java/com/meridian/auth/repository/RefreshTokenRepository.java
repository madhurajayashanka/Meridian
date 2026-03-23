package com.meridian.auth.repository;

import com.meridian.auth.entity.RefreshToken;
import com.meridian.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = ?1 AND rt.revokedAt IS NULL AND rt.expiresAt > ?2")
    Optional<RefreshToken> findValidToken(String tokenHash, LocalDateTime now);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user = ?1 AND rt.revokedAt IS NULL")
    List<RefreshToken> findAllValidTokensByUser(User user);

    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < ?1")
    void deleteExpiredTokens(LocalDateTime now);
}
