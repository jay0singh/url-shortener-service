package com.project.urlshortener.scheduler;

import com.project.urlshortener.repository.URLMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class UrlCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UrlCleanupScheduler.class);

    private final URLMappingRepository repository;

    public UrlCleanupScheduler(URLMappingRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deactivateExpiredUrls() {
        int count = repository.deactivateExpired(LocalDateTime.now());
        log.info("Deactivated {} expired URL mapping(s)", count);
    }
}