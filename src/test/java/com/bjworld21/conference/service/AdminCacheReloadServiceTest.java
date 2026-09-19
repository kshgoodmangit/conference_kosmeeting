package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.AdminCacheReloadResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCacheReloadServiceTest {

    @Test
    void reloadsEveryRegisteredAdminCache() {
        AdminReloadableCache firstCache = mock(AdminReloadableCache.class);
        AdminReloadableCache secondCache = mock(AdminReloadableCache.class);
        AdminCacheReloadResult firstResult = new AdminCacheReloadResult(
                "first", 2, LocalDateTime.of(2026, 8, 27, 10, 30));
        AdminCacheReloadResult secondResult = new AdminCacheReloadResult(
                "second", 5, LocalDateTime.of(2026, 8, 27, 10, 31));
        when(firstCache.reloadCache()).thenReturn(firstResult);
        when(secondCache.reloadCache()).thenReturn(secondResult);
        AdminCacheReloadService service = new AdminCacheReloadService(List.of(firstCache, secondCache));

        assertThat(service.reloadAll()).containsExactly(firstResult, secondResult);
        verify(firstCache).reloadCache();
        verify(secondCache).reloadCache();
    }
}
