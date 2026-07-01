package com.project.urlshortener.controller;

import com.project.urlshortener.config.SecurityConfig;
import com.project.urlshortener.exception.InvalidShortCodeException;
import com.project.urlshortener.exception.UrlExpiredException;
import com.project.urlshortener.exception.UrlNotFoundException;
import com.project.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RedirectController.class)
@Import(SecurityConfig.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlService urlService;

    @Test
    void redirect_validShortCode_returns302WithLocationHeader() throws Exception {
        when(urlService.redirectUrl("abc1")).thenReturn("https://example.com");

        mockMvc.perform(get("/abc1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void redirect_expiredShortCode_returns410() throws Exception {
        when(urlService.redirectUrl("abc1")).thenThrow(new UrlExpiredException("This short URL has expired"));

        mockMvc.perform(get("/abc1"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("This short URL has expired"))
                .andExpect(jsonPath("$.status").value(410));
    }

    @Test
    void redirect_notFoundShortCode_returns404() throws Exception {
        when(urlService.redirectUrl("abc1")).thenThrow(new UrlNotFoundException("URL not found"));

        mockMvc.perform(get("/abc1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("URL not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void redirect_invalidShortCode_returns400() throws Exception {
        when(urlService.redirectUrl("abc1")).thenThrow(new InvalidShortCodeException("Invalid shortCode"));

        mockMvc.perform(get("/abc1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid shortCode"))
                .andExpect(jsonPath("$.status").value(400));
    }
}
