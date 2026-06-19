package com.project.urlshortener.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.urlshortener.service.UrlCreationResult;
import com.project.urlshortener.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@TestPropertySource(properties = {
        "app.base-url=http://localhost:8090",
        "app.cors.allowed-origins=*",
        "app.rate-limit.shorten=2",
        "app.rate-limit.redirect=2"
})
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @MockBean
    private UrlService urlService;

    @BeforeEach
    void resetBuckets() {
        rateLimitFilter.reset();
    }

    @Test
    void shorten_withinLimit_returns200() throws Exception {
        when(urlService.createShortUrl(any(), any())).thenReturn(new UrlCreationResult("abc1", null));

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    void shorten_exceedsLimit_returns429() throws Exception {
        when(urlService.createShortUrl(any(), any())).thenReturn(new UrlCreationResult("abc1", null));

        String body = objectMapper.writeValueAsString(Map.of("url", "https://example.com"));

        // consume the 2-request allowance
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        // third request must be rate-limited
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please try again later."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void redirect_exceedsLimit_returns429() throws Exception {
        when(urlService.redirectUrl("abc1")).thenReturn("https://example.com");

        // consume the 2-request allowance
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/abc1")).andExpect(status().isFound());
        }

        // third request must be rate-limited
        mockMvc.perform(get("/abc1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please try again later."));
    }

    @Test
    void rateLimits_areIsolatedPerEndpoint() throws Exception {
        when(urlService.createShortUrl(any(), any())).thenReturn(new UrlCreationResult("abc1", null));
        when(urlService.redirectUrl("abc1")).thenReturn("https://example.com");

        // exhaust the shorten bucket
        String body = objectMapper.writeValueAsString(Map.of("url", "https://example.com"));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());

        // redirect bucket should still be available
        mockMvc.perform(get("/abc1")).andExpect(status().isFound());
    }

    @Test
    void xForwardedFor_usedAsClientIp() throws Exception {
        when(urlService.createShortUrl(any(), any())).thenReturn(new UrlCreationResult("abc1", null));

        String body = objectMapper.writeValueAsString(Map.of("url", "https://example.com"));

        // Two different IPs each get their own bucket (both within limit)
        mockMvc.perform(post("/api/v1/shorten")
                        .header("X-Forwarded-For", "10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/shorten")
                        .header("X-Forwarded-For", "10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // 10.0.0.1 is now at the limit
        mockMvc.perform(post("/api/v1/shorten")
                        .header("X-Forwarded-For", "10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());

        // 10.0.0.2 still has its own fresh bucket
        mockMvc.perform(post("/api/v1/shorten")
                        .header("X-Forwarded-For", "10.0.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
