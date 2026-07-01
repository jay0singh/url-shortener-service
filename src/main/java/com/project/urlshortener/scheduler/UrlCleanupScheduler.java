package com.project.urlshortener.scheduler;

import com.project.urlshortener.cache.UrlResolutionCache;
import com.project.urlshortener.repository.URLMappingRepository;
import com.project.urlshortener.utils.Base62Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class UrlCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UrlCleanupScheduler.class);

    private final URLMappingRepository repository;
    private final UrlResolutionCache urlResolutionCache;
    private final Base62Encoder encoder;

    public UrlCleanupScheduler(URLMappingRepository repository,
                               UrlResolutionCache urlResolutionCache,
                               Base62Encoder encoder) {
        this.repository = repository;
        this.urlResolutionCache = urlResolutionCache;
        this.encoder = encoder;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deactivateExpiredUrls() {
        LocalDateTime now = LocalDateTime.now();

        List<Long> expiredIds = repository.findExpiredIds(now);
        if (expiredIds.isEmpty()) {
            return;
        }

        expiredIds.forEach(id -> urlResolutionCache.evict(encoder.encode(id)));

        int count = repository.deactivateExpired(now);
        log.info("Deactivated {} expired URL mapping(s)", count);
    }
}