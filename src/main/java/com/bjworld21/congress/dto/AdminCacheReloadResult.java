package com.bjworld21.congress.dto;

import java.time.LocalDateTime;

public record AdminCacheReloadResult(
        String cacheName,
        int cachedEntryCount,
        LocalDateTime reloadedAt
) {
}
