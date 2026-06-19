package com.project.urlshortener.service;

import com.project.urlshortener.dto.UrlStatsDTO;

public interface UrlService {


    UrlCreationResult createShortUrl(String longUrl, Integer ttlDays);

    String redirectUrl(String shortCode);

    void deactivateUrl(String shortCode);

    UrlStatsDTO getStats(String shortCode);
}
