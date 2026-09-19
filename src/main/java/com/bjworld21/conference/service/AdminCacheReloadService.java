package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.AdminCacheReloadResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminCacheReloadService {
    private final List<AdminReloadableCache> reloadableCaches;

    public AdminCacheReloadService(List<AdminReloadableCache> reloadableCaches) {
        this.reloadableCaches = List.copyOf(reloadableCaches);
    }

    public List<AdminCacheReloadResult> reloadAll() {
        return reloadableCaches.stream()
                .map(AdminReloadableCache::reloadCache)
                .toList();
    }
}
