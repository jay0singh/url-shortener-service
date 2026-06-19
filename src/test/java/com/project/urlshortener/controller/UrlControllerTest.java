package com.project.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.urlshortener.exception.InvalidUrlException;
import com.project.urlshortener.service.UrlCreationResult;
import com.project.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import com.project.urlshortener.dto.UrlStatsDTO;
import com.project.urlshortener.exception.InvalidShortCodeException;
import com.project.urlshortener.exception.UrlNotFoundException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
@TestPropertySource(properties = {
        "app.base-url=http://localhost:8090",
        "app.cors.allowed-origins=*"
})
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlService urlService;

    @Test
    void shorten_validRequest_returns200WithShortUrl() throws Exception {
        when(urlService.createShortUrl(eq("https://example.com"), eq(null)))
                .thenReturn(new UrlCreationResult("abc1", null));

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8090/abc1"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void shorten_withTtlDays_returns200WithExpiresAt() throws Exception {
        LocalDateTime expiry = LocalDateTime.of(2026, 6, 16, 12, 0, 0);
        when(urlService.createShortUrl(eq("https://example.com"), eq(7)))
                .thenReturn(new UrlCreationResult("abc1", expiry));

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://example.com", "ttlDays", 7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8090/abc1"))
                .andExpect(jsonPath("$.expiresAt").value("2026-06-16T12:00:00"));
    }

    @Test
    void shorten_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("url")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shorten_ttlDaysZero_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://example.com", "ttlDays", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shorten_ttlDaysOver365_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://example.com", "ttlDays", 366))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shorten_invalidUrl_returns400() throws Exception {
        when(urlService.createShortUrl(any(), any()))
                .thenThrow(new InvalidUrlException("Invalid URL"));

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "not-a-url"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid URL"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deactivate_validShortCode_returns204() throws Exception {
        doNothing().when(urlService).deactivateUrl("abc1");

        mockMvc.perform(delete("/api/v1/shorten/abc1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivate_notFound_returns404() throws Exception {
        doThrow(new UrlNotFoundException("URL not found")).when(urlService).deactivateUrl("abc1");

        mockMvc.perform(delete("/api/v1/shorten/abc1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("URL not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deactivate_invalidShortCode_returns400() throws Exception {
        doThrow(new InvalidShortCodeException("Invalid shortCode")).when(urlService).deactivateUrl("abc1");

        mockMvc.perform(delete("/api/v1/shorten/abc1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid shortCode"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void stats_validShortCode_returns200WithStats() throws Exception {
        UrlStatsDTO dto = new UrlStatsDTO();
        dto.setShortCode("abc1");
        dto.setLongUrl("https://example.com");
        dto.setHitCount(10L);
        when(urlService.getStats("abc1")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/stats/abc1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc1"))
                .andExpect(jsonPath("$.longUrl").value("https://example.com"))
                .andExpect(jsonPath("$.hitCount").value(10));
    }

    @Test
    void stats_notFound_returns404() throws Exception {
        when(urlService.getStats("abc1")).thenThrow(new UrlNotFoundException("URL not found"));

        mockMvc.perform(get("/api/v1/stats/abc1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shorten_anyOrigin_returnsCorsHeader() throws Exception {
        when(urlService.createShortUrl(any(), any()))
                .thenReturn(new UrlCreationResult("abc1", null));

        mockMvc.perform(post("/api/v1/shorten")
                        .header("Origin", "http://any-domain.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://example.com"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
}