package com.project.urlshortener.repository;

import com.project.urlshortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface URLMappingRepository extends JpaRepository<UrlMapping, Long> {

    @Query("SELECT u FROM UrlMapping u WHERE u.urlHash = :hash AND u.isActive = true AND (u.expiresAt IS NULL OR u.expiresAt > :now)")
    Optional<UrlMapping> findActiveByHash(@Param("hash") String hash, @Param("now") LocalDateTime now);

    @Query("SELECT u FROM UrlMapping u WHERE u.id = :id AND u.isActive = true AND (u.expiresAt IS NULL OR u.expiresAt > :now)")
    Optional<UrlMapping> findActiveById(@Param("id") Long id, @Param("now") LocalDateTime now);

    Optional<UrlMapping> findByUrlHash(String urlHash);

    @Query("SELECT u.id FROM UrlMapping u WHERE u.isActive = true AND u.expiresAt IS NOT NULL AND u.expiresAt <= :now")
    List<Long> findExpiredIds(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UrlMapping u SET u.isActive = false WHERE u.isActive = true AND u.expiresAt IS NOT NULL AND u.expiresAt <= :now")
    int deactivateExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UrlMapping u SET u.hitCount = u.hitCount + 1, u.lastAccessedAt = :now WHERE u.id = :id")
    void recordHit(@Param("id") Long id, @Param("now") LocalDateTime now);
}
