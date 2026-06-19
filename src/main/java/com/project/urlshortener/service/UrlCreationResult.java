package com.project.urlshortener.service;

import java.time.LocalDateTime;

public record UrlCreationResult(String shortCode, LocalDateTime expiresAt) {}