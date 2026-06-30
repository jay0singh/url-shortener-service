package com.project.urlshortener.service.impl;

import com.project.urlshortener.cache.UrlResolutionCache;
import com.project.urlshortener.dto.UrlStatsDTO;
import com.project.urlshortener.entity.UrlMapping;
import com.project.urlshortener.exception.InvalidShortCodeException;
import com.project.urlshortener.exception.InvalidUrlException;
import com.project.urlshortener.exception.UrlExpiredException;
import com.project.urlshortener.exception.UrlNotFoundException;
import com.project.urlshortener.repository.URLMappingRepository;
import com.project.urlshortener.service.UrlCreationResult;
import com.project.urlshortener.service.UrlService;
import com.project.urlshortener.utils.Base62Encoder;
import com.project.urlshortener.utils.UrlHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;

@Service
public class UrlServiceImpl implements UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlServiceImpl.class);

    private final URLMappingRepository repository;
    private final Base62Encoder encoder;
    private final UrlHasher urlHasher;
    private final UrlResolutionCache urlResolutionCache;

    public UrlServiceImpl(URLMappingRepository repository,
            Base62Encoder encoder,
            UrlHasher urlHasher,
            UrlResolutionCache urlResolutionCache) {
        this.repository = repository;
        this.encoder = encoder;
        this.urlHasher = urlHasher;
        this.urlResolutionCache = urlResolutionCache;
    }

    @Override
    @Transactional
    public UrlCreationResult createShortUrl(String longUrl, Integer ttlDays) {

        log.info("Starting URL shortening for: {}", longUrl);

        // Normalize URL
        if (!longUrl.startsWith("http://") && !longUrl.startsWith("https://")) {
            log.debug("Protocol missing, prepending https://");
            longUrl = "https://" + longUrl;
        }

        // Validate URL
        try {
            URI uri = new URI(longUrl);
            if (uri.getHost() == null || !uri.getHost().contains(".")) {
                log.error("Invalid URL detected: {}", longUrl);
                throw new InvalidUrlException("Invalid URL");
            }
        } catch (URISyntaxException e) {
            log.error("Invalid URL format: {}", longUrl, e);
            throw new InvalidUrlException("Invalid URL");
        }

        String hash = urlHasher.hash(longUrl);
        log.debug("Generated hash for URL: {}", hash);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = ttlDays != null ? now.plusDays(ttlDays) : null;

        // Check for existing active, non-expired mapping
        UrlMapping entity = repository.findActiveByHash(hash, now).orElse(null);

        if (entity != null) {
            log.info("Duplicate URL found. Returning existing shortCode for id={}", entity.getId());
            return new UrlCreationResult(encoder.encode(entity.getId()), entity.getExpiresAt());
        }

        // Reactivate an existing inactive (expired or deactivated) record for this URL
        UrlMapping inactive = repository.findByUrlHash(hash).orElse(null);
        if (inactive != null) {
            inactive.setIsActive(true);
            inactive.setExpiresAt(expiresAt);
            inactive.setHitCount(0L);
            repository.save(inactive);
            log.info("Reactivated existing URL mapping id={}", inactive.getId());
            urlResolutionCache.evict(encoder.encode(inactive.getId()));
            return new UrlCreationResult(encoder.encode(inactive.getId()), inactive.getExpiresAt());
        }

        // Create new entry
        try {
            UrlMapping newEntity = new UrlMapping();
            newEntity.setLongUrl(longUrl);
            newEntity.setUrlHash(hash);
            newEntity.setExpiresAt(expiresAt);

            newEntity = repository.save(newEntity);

            log.info("New URL mapping created with id={}", newEntity.getId());

            return new UrlCreationResult(encoder.encode(newEntity.getId()), newEntity.getExpiresAt());
        } catch (DataIntegrityViolationException e) {
            if (!isUrlHashUniqueViolation(e)) {
                log.error("Insert failed for non-hash constraint reason: {}", longUrl, e);
                throw new InvalidUrlException("URL could not be processed");
            }
            // Concurrent request saved the same URL first — return the existing entry
            log.warn("Concurrent insert detected for hash={}, falling back to existing record", hash);
            return repository.findActiveByHash(hash, LocalDateTime.now())
                    .map(existing -> new UrlCreationResult(encoder.encode(existing.getId()), existing.getExpiresAt()))
                    .orElseThrow(() -> new InvalidUrlException("URL processing failed"));
        }
    }

    private boolean isUrlHashUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        return cause instanceof ConstraintViolationException cve
                && cve.getConstraintName() != null
                && cve.getConstraintName().toLowerCase().contains("url_hash");
    }

    @Override
    @Transactional
    public String redirectUrl(String shortCode) {

        log.info("Redirect requested for shortCode={}", shortCode);

        UrlResolutionCache.CachedUrl cached = urlResolutionCache.resolve(shortCode);

        if (cached.expiresAt() != null && cached.expiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Expired URL requested for shortCode={}", shortCode);
            throw new UrlExpiredException("This short URL has expired");
        }

        repository.recordHit(cached.id(), LocalDateTime.now());
        log.info("Redirecting shortCode={} to {}", shortCode, cached.longUrl());

        return cached.longUrl();
    }

    @Override
    public UrlStatsDTO getStats(String shortCode) {

        log.info("Stats requested for shortCode={}", shortCode);

        long id;

        try {
            id = encoder.decode(shortCode);
        } catch (Exception e) {
            log.error("Invalid shortCode received: {}", shortCode);
            throw new InvalidShortCodeException("Invalid shortCode");
        }

        UrlMapping entity = repository.findById(id)
                .orElseThrow(() -> {
                    log.error("No URL found for id={}", id);
                    return new UrlNotFoundException("URL not found");
                });

        if (!entity.getIsActive()) {
            throw new UrlNotFoundException("URL not found");
        }

        UrlStatsDTO stats = new UrlStatsDTO();
        stats.setShortCode(shortCode);
        stats.setLongUrl(entity.getLongUrl());
        stats.setHitCount(entity.getHitCount());
        stats.setCreatedAt(entity.getCreatedAt());
        stats.setLastAccessedAt(entity.getLastAccessedAt());
        stats.setExpiresAt(entity.getExpiresAt());

        return stats;
    }

    @Override
    @Transactional
    public void deactivateUrl(String shortCode) {

        log.info("Deactivation requested for shortCode={}", shortCode);

        long id;

        try {
            id = encoder.decode(shortCode);
        } catch (Exception e) {
            log.error("Invalid shortCode received : {}", shortCode);
            throw new InvalidShortCodeException("Invalid shortCode");
        }

        UrlMapping entity = repository.findById(id)
                .orElseThrow(() -> {
                    log.error("No URL found for id ={}", id);
                    return new UrlNotFoundException("URL not found");
                });

        if (!entity.getIsActive()) {
            log.warn("Deactivation requested for already-inactive id={}", id);
            throw new UrlNotFoundException("URL not found");
        }

        entity.setIsActive(false);
        repository.save(entity);
        urlResolutionCache.evict(shortCode);

        log.info("Deactivated URL mapping id={}", id);
    }
}