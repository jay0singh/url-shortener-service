package com.project.urlshortener.controller;

import com.project.urlshortener.dto.UrlRequestDTO;
import com.project.urlshortener.dto.UrlResponseDTO;
import com.project.urlshortener.dto.UrlStatsDTO;
import com.project.urlshortener.service.UrlCreationResult;
import com.project.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UrlController {

    private static final Logger log = LoggerFactory.getLogger(UrlController.class);

    private final UrlService service;
    private final String baseUrl;

    public UrlController(UrlService service, @Value("${app.base-url}") String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/shorten")
    public UrlResponseDTO shorten(@Valid @RequestBody UrlRequestDTO request) {

        log.info("Received request to shorten URL: {}", request.getUrl());

        UrlCreationResult result = service.createShortUrl(request.getUrl(), request.getTtlDays());

        String shortUrl = baseUrl + "/" + result.shortCode();

        log.info("Generated short URL: {}", shortUrl);

        UrlResponseDTO response = new UrlResponseDTO();
        response.setShortUrl(shortUrl);
        response.setExpiresAt(result.expiresAt());

        return response;
    }

    @DeleteMapping("/shorten/{shortCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String shortCode) {
        log.info("Received deactivation request for shortCode={}", shortCode);
        service.deactivateUrl(shortCode);
    }

    @GetMapping("/stats/{shortCode}")
    public UrlStatsDTO stats(@PathVariable String shortCode) {
        log.info("Received stats request for shortCode={}", shortCode);
        return service.getStats(shortCode);
    }
}