package com.bjworld21.conference.dto;

import java.util.List;

public record AdminCacheReloadResponse(
        List<AdminCacheReloadResult> caches,
        String message
) {
}
