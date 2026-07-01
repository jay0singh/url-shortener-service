package com.project.urlshortener.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.urlshortener.dto.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final int shortenLimit;
    private final int redirectLimit;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.rate-limit.shorten:10}") int shortenLimit,
            @Value("${app.rate-limit.redirect:60}") int redirectLimit,
            ObjectMapper objectMapper) {
        this.shortenLimit = shortenLimit;
        this.redirectLimit = redirectLimit;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        BucketConfig config = resolveConfig(request.getRequestURI(), request.getMethod());
        if (config == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip = extractClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip + ":" + config.key(), k -> newBucket(config.limit()));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            writeTooManyRequests(response);
        }
    }

    private BucketConfig resolveConfig(String path, String method) {
        if ("POST".equalsIgnoreCase(method) && "/api/v1/shorten".equals(path)) {
            return new BucketConfig("shorten", shortenLimit);
        }
        if ("GET".equalsIgnoreCase(method) && path.matches("/[a-zA-Z0-9]+")) {
            return new BucketConfig("redirect", redirectLimit);
        }
        return null;
    }

    private Bucket newBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Rate limit exceeded. Please try again later.");
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    // visible for testing
    void reset() {
        buckets.clear();
    }

    private record BucketConfig(String key, int limit) {}
}
