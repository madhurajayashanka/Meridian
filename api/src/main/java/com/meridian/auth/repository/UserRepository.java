package com.meridian.auth.repository;

import com.meridian.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(?1) AND u.deletedAt IS NULL")
    Optional<User> findByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u WHERE u.id = ?1 AND u.deletedAt IS NULL")
    Optional<User> findByIdActive(UUID id);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(?1) AND u.deletedAt IS NULL")
    boolean existsByEmailIgnoreCase(String email);
}
