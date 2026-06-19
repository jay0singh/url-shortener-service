package com.project.urlshortener.cache;

import com.project.urlshortener.entity.UrlMapping;
import com.project.urlshortener.exception.InvalidShortCodeException;
import com.project.urlshortener.exception.UrlNotFoundException;
import com.project.urlshortener.repository.URLMappingRepository;
import com.project.urlshortener.utils.Base62Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UrlResolutionCache {

    private static final Logger log = LoggerFactory.getLogger(UrlResolutionCache.class);

    private final URLMappingRepository repository;
    private final Base62Encoder encoder;

    public UrlResolutionCache(URLMappingRepository repository, Base62Encoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @Cacheable(value = "redirects", key = "#shortCode")
    public CachedUrl resolve(String shortCode) {
        long id;
        try {
            id = encoder.decode(shortCode);
        } catch (Exception e) {
            throw new InvalidShortCodeException("Invalid shortCode");
        }

        UrlMapping entity = repository.findById(id)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        if (!entity.getIsActive()) {
            throw new UrlNotFoundException("URL not found");
        }

        log.debug("Cache miss for shortCode={}, loaded from DB", shortCode);
        return new CachedUrl(entity.getId(), entity.getLongUrl(), entity.getExpiresAt());
    }

    @CacheEvict(value = "redirects", key = "#shortCode")
    public void evict(String shortCode) {
        log.debug("Evicted cache entry for shortCode={}", shortCode);
    }

    public record CachedUrl(long id, String longUrl, LocalDateTime expiresAt) {}
}
