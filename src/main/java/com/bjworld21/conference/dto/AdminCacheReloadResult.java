package com.bjworld21.conference.dto;

import java.time.LocalDateTime;

public record AdminCacheReloadResult(
        String cacheName,
        int cachedEntryCount,
        LocalDateTime reloadedAt
) {
}
