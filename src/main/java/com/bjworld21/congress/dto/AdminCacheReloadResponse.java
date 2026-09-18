package com.bjworld21.congress.dto;

import java.util.List;

public record AdminCacheReloadResponse(
        List<AdminCacheReloadResult> caches,
        String message
) {
}
