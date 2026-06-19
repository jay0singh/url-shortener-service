package com.project.urlshortener.controller;

import com.project.urlshortener.exception.InvalidShortCodeException;
import com.project.urlshortener.service.UrlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private final UrlService service;

    public RedirectController(UrlService service) {
        this.service = service;
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {

        if (shortCode == null || shortCode.isBlank()) {
            log.warn("Received empty shortCode");
            throw new InvalidShortCodeException("shortCode cannot be empty");
        }

        log.info("Redirect request received for shortCode={}", shortCode);

        String url = service.redirectUrl(shortCode);

        try {
            URI uri = URI.create(url);

            log.info("Redirecting shortCode={} to url={}", shortCode, url);

            return ResponseEntity
                    .status(HttpStatus.FOUND) // 302
                    .location(uri)
                    .build();

        } catch (Exception e) {
            log.error("Invalid URL stored for shortCode={} url={}", shortCode, url);
            throw new RuntimeException("Invalid redirect URL");
        }
    }
}