package com.bjworld21.congress.service;

import com.bjworld21.congress.entity.AdminIpAllowlist;
import com.bjworld21.congress.dto.AdminCacheReloadResult;
import com.bjworld21.congress.repository.AdminIpAllowlistRepository;
import com.bjworld21.congress.security.IpCidrRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.InetAddress;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminIpAccessCache implements AdminReloadableCache {
    private static final Logger log = LoggerFactory.getLogger(AdminIpAccessCache.class);

    private final AdminIpAllowlistRepository repository;
    private final Clock clock;
    private volatile CacheSnapshot snapshot = new CacheSnapshot(List.of(), null);

    public AdminIpAccessCache(AdminIpAllowlistRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        reloadSafely("application-start");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAllowlistChanged(AdminIpAllowlistChangedEvent event) {
        reloadSafely("crud-" + event.action());
    }

    public synchronized int reload() {
        List<CachedRule> nextRules = new ArrayList<>();
        for (AdminIpAllowlist rule : repository.findEnabled()) {
            try {
                nextRules.add(new CachedRule(rule.getSeq(), IpCidrRange.parse(rule.getIpCidr()),
                        rule.getUseStartDate(), rule.getUseEndDate()));
            } catch (IllegalArgumentException exception) {
                log.error("Ignoring invalid admin IP allowlist rule: seq={}, ipCidr={}",
                        rule.getSeq(), rule.getIpCidr(), exception);
            }
        }
        snapshot = new CacheSnapshot(List.copyOf(nextRules), LocalDateTime.now(clock));
        return nextRules.size();
    }

    @Override
    public AdminCacheReloadResult reloadCache() {
        int cachedEntryCount = reload();
        return new AdminCacheReloadResult("adminIpAllowlist", cachedEntryCount, getReloadedAt());
    }

    public boolean isAllowed(InetAddress address) {
        LocalDate today = LocalDate.now(clock);
        return snapshot.rules().stream().anyMatch(rule ->
                (rule.useStartDate() == null || !today.isBefore(rule.useStartDate()))
                        && (rule.useEndDate() == null || !today.isAfter(rule.useEndDate()))
                        && rule.range().contains(address));
    }

    public int getCachedRuleCount() {
        return snapshot.rules().size();
    }

    public LocalDateTime getReloadedAt() {
        return snapshot.reloadedAt();
    }

    private void reloadSafely(String reason) {
        try {
            int count = reload();
            log.info("Admin IP allowlist cache reloaded: reason={}, cachedRules={}", reason, count);
        } catch (Exception exception) {
            log.error("Failed to reload admin IP allowlist cache: reason={}", reason, exception);
        }
    }

    private record CachedRule(Long seq, IpCidrRange range, LocalDate useStartDate, LocalDate useEndDate) {
    }

    private record CacheSnapshot(List<CachedRule> rules, LocalDateTime reloadedAt) {
    }
}
