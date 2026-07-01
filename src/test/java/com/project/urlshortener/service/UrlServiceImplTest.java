package com.project.urlshortener.service;

import com.project.urlshortener.cache.UrlResolutionCache;
import com.project.urlshortener.entity.UrlMapping;
import com.project.urlshortener.exception.InvalidShortCodeException;
import com.project.urlshortener.exception.InvalidUrlException;
import com.project.urlshortener.exception.UrlExpiredException;
import com.project.urlshortener.exception.UrlNotFoundException;
import com.project.urlshortener.repository.URLMappingRepository;
import com.project.urlshortener.service.impl.UrlServiceImpl;
import com.project.urlshortener.utils.Base62Encoder;
import com.project.urlshortener.utils.UrlHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlServiceImplTest {

    @Mock
    private URLMappingRepository repository;

    @Mock
    private Base62Encoder encoder;

    @Mock
    private UrlHasher urlHasher;

    @Mock
    private UrlResolutionCache urlResolutionCache;

    @InjectMocks
    private UrlServiceImpl service;

    private static final String LONG_URL = "https://example.com/some/path";
    private static final String HASH = "abc123hash";
    private static final String SHORT_CODE = "abc1";
    private static final long ENTITY_ID = 1L;

    // --- createShortUrl ---

    @Test
    void createShortUrl_newUrl_savesEntityAndReturnsShortCode() {
        when(urlHasher.hash(LONG_URL)).thenReturn(HASH);
        when(encoder.encode(ENTITY_ID)).thenReturn(SHORT_CODE);
        when(repository.findActiveByHash(eq(HASH), any())).thenReturn(Optional.empty());
        UrlMapping saved = mappingWithId(LONG_URL, null);
        when(repository.save(any())).thenReturn(saved);

        UrlCreationResult result = service.createShortUrl(LONG_URL, null);

        assertThat(result.shortCode()).isEqualTo(SHORT_CODE);
        assertThat(result.expiresAt()).isNull();
        verify(repository).save(any());
    }

    @Test
    void createShortUrl_duplicateUrl_returnsExistingShortCode() {
        when(urlHasher.hash(LONG_URL)).thenReturn(HASH);
        when(encoder.encode(ENTITY_ID)).thenReturn(SHORT_CODE);
        UrlMapping existing = mappingWithId(LONG_URL, null);
        when(repository.findActiveByHash(eq(HASH), any())).thenReturn(Optional.of(existing));

        UrlCreationResult result = service.createShortUrl(LONG_URL, null);

        assertThat(result.shortCode()).isEqualTo(SHORT_CODE);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createShortUrl_withTtlDays_setsExpiresAt() {
        when(urlHasher.hash(LONG_URL)).thenReturn(HASH);
        when(encoder.encode(ENTITY_ID)).thenReturn(SHORT_CODE);
        when(repository.findActiveByHash(eq(HASH), any())).thenReturn(Optional.empty());
        UrlMapping saved = mappingWithId(LONG_URL, LocalDateTime.now().plusDays(7));
        when(repository.save(any())).thenReturn(saved);

        UrlCreationResult result = service.createShortUrl(LONG_URL, 7);

        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    void createShortUrl_missingProtocol_prependsHttpsBeforeHashing() {
        String bare = "example.com/some/path";
        String normalized = "https://" + bare;
        when(urlHasher.hash(normalized)).thenReturn(HASH);
        when(encoder.encode(ENTITY_ID)).thenReturn(SHORT_CODE);
        when(repository.findActiveByHash(eq(HASH), any())).thenReturn(Optional.empty());
        UrlMapping saved = mappingWithId(normalized, null);
        when(repository.save(any())).thenReturn(saved);

        UrlCreationResult result = service.createShortUrl(bare, null);

        assertThat(result.shortCode()).isEqualTo(SHORT_CODE);
    }

    @Test
    void createShortUrl_invalidUrl_throwsInvalidUrlException() {
        assertThatThrownBy(() -> service.createShortUrl("https://notavalidurl", null))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void createShortUrl_malformedUri_throwsInvalidUrlException() {
        assertThatThrownBy(() -> service.createShortUrl("https://[invalid", null))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void createShortUrl_concurrentInsert_fallsBackToExistingRecord() {
        when(urlHasher.hash(LONG_URL)).thenReturn(HASH);
        when(encoder.encode(ENTITY_ID)).thenReturn(SHORT_CODE);
        when(repository.findActiveByHash(eq(HASH), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mappingWithId(LONG_URL, null)));
        when(repository.findByUrlHash(HASH)).thenReturn(Optional.empty());
        ConstraintViolationException cause =
                new ConstraintViolationException("duplicate", null, "uk_url_mapping_url_hash");
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate", cause));

        UrlCreationResult result = service.createShortUrl(LONG_URL, null);

        assertThat(result.shortCode()).isEqualTo(SHORT_CODE);
    }


    // --- redirectUrl ---

    @Test
    void redirectUrl_validShortCode_returnsLongUrl() {
        when(urlResolutionCache.resolve(SHORT_CODE))
                .thenReturn(new UrlResolutionCache.CachedUrl(ENTITY_ID, LONG_URL, null));

        String result = service.redirectUrl(SHORT_CODE);

        assertThat(result).isEqualTo(LONG_URL);
    }

    @Test
    void redirectUrl_validShortCode_recordsHit() {
        when(urlResolutionCache.resolve(SHORT_CODE))
                .thenReturn(new UrlResolutionCache.CachedUrl(ENTITY_ID, LONG_URL, null));

        service.redirectUrl(SHORT_CODE);

        verify(repository).recordHit(eq(ENTITY_ID), any());
    }

    @Test
    void redirectUrl_expiredUrl_throwsUrlExpiredException() {
        when(urlResolutionCache.resolve(SHORT_CODE))
                .thenReturn(new UrlResolutionCache.CachedUrl(ENTITY_ID, LONG_URL, LocalDateTime.now().minusHours(1)));

        assertThatThrownBy(() -> service.redirectUrl(SHORT_CODE))
                .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void redirectUrl_invalidShortCode_throwsInvalidShortCodeException() {
        when(urlResolutionCache.resolve(SHORT_CODE))
                .thenThrow(new InvalidShortCodeException("Invalid shortCode"));

        assertThatThrownBy(() -> service.redirectUrl(SHORT_CODE))
                .isInstanceOf(InvalidShortCodeException.class);
    }

    @Test
    void redirectUrl_notFound_throwsUrlNotFoundException() {
        when(urlResolutionCache.resolve(SHORT_CODE))
                .thenThrow(new UrlNotFoundException("URL not found"));

        assertThatThrownBy(() -> service.redirectUrl(SHORT_CODE))
                .isInstanceOf(UrlNotFoundException.class);
    }

    // --- getStats ---

    @Test
    void getStats_validShortCode_returnsStats() {
        when(encoder.decode(SHORT_CODE)).thenReturn(ENTITY_ID);
        UrlMapping mapping = mappingWithId(LONG_URL, null);
        mapping.setHitCount(5L);
        when(repository.findById(ENTITY_ID)).thenReturn(Optional.of(mapping));

        com.project.urlshortener.dto.UrlStatsDTO stats = service.getStats(SHORT_CODE);

        assertThat(stats.getShortCode()).isEqualTo(SHORT_CODE);
        assertThat(stats.getLongUrl()).isEqualTo(LONG_URL);
        assertThat(stats.getHitCount()).isEqualTo(5L);
    }

    @Test
    void getStats_invalidShortCode_throwsInvalidShortCodeException() {
        when(encoder.decode(SHORT_CODE)).thenThrow(new IllegalArgumentException("bad char"));

        assertThatThrownBy(() -> service.getStats(SHORT_CODE))
                .isInstanceOf(InvalidShortCodeException.class);
    }

    @Test
    void getStats_notFound_throwsUrlNotFoundException() {
        when(encoder.decode(SHORT_CODE)).thenReturn(ENTITY_ID);
        when(repository.findById(ENTITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStats(SHORT_CODE))
                .isInstanceOf(UrlNotFoundException.class);
    }

    // --- deactivateUrl ---

    @Test
    void deactivateUrl_validShortCode_setsInactiveAndEvictsCache() {
        when(encoder.decode(SHORT_CODE)).thenReturn(ENTITY_ID);
        UrlMapping active = mappingWithId(LONG_URL, null);
        when(repository.findById(ENTITY_ID)).thenReturn(Optional.of(active));

        service.deactivateUrl(SHORT_CODE);

        assertThat(active.getIsActive()).isFalse();
        verify(repository).save(active);
        verify(urlResolutionCache).evict(SHORT_CODE);
    }

    @Test
    void deactivateUrl_invalidShortCode_throwsInvalidShortCodeException() {
        when(encoder.decode(SHORT_CODE)).thenThrow(new IllegalArgumentException("bad char"));

        assertThatThrownBy(() -> service.deactivateUrl(SHORT_CODE))
                .isInstanceOf(InvalidShortCodeException.class);
    }

    @Test
    void deactivateUrl_notFound_throwsUrlNotFoundException() {
        when(encoder.decode(SHORT_CODE)).thenReturn(ENTITY_ID);
        when(repository.findById(ENTITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateUrl(SHORT_CODE))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void deactivateUrl_alreadyInactive_throwsUrlNotFoundException() {
        when(encoder.decode(SHORT_CODE)).thenReturn(ENTITY_ID);
        UrlMapping inactive = mappingWithId(LONG_URL, null);
        inactive.setIsActive(false);
        when(repository.findById(ENTITY_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.deactivateUrl(SHORT_CODE))
                .isInstanceOf(UrlNotFoundException.class);
    }

    // --- helpers ---

    private UrlMapping mappingWithId(String longUrl, LocalDateTime expiresAt) {
        UrlMapping m = new UrlMapping();
        m.setId(UrlServiceImplTest.ENTITY_ID);
        m.setLongUrl(longUrl);
        m.setUrlHash(HASH);
        m.setIsActive(true);
        m.setExpiresAt(expiresAt);
        return m;
    }
}