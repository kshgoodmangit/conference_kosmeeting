package com.bjworld21.congress.service;

import com.bjworld21.congress.entity.AdminIpAllowlist;
import com.bjworld21.congress.repository.AdminIpAllowlistRepository;
import com.bjworld21.congress.security.IpCidrRange;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminIpAccessCacheTest {

    @Test
    void reloadsEnabledRulesAndSkipsInvalidDatabaseValue() {
        AdminIpAllowlistRepository repository = mock(AdminIpAllowlistRepository.class);
        when(repository.findEnabled()).thenReturn(List.of(
                AdminIpAllowlist.builder().seq(1L).ipCidr("192.168.10.0/24").build(),
                AdminIpAllowlist.builder().seq(2L).ipCidr("invalid-host").build()
        ));
        AdminIpAccessCache cache = new AdminIpAccessCache(repository, Clock.systemUTC());

        assertThat(cache.reload()).isEqualTo(1);
        assertThat(cache.getCachedRuleCount()).isEqualTo(1);
        assertThat(cache.isAllowed(IpCidrRange.parseLiteralAddress("192.168.10.25"))).isTrue();
        assertThat(cache.isAllowed(IpCidrRange.parseLiteralAddress("192.168.11.1"))).isFalse();
        assertThat(cache.getReloadedAt()).isNotNull();
    }

    @Test
    void startsAndExpiresAtSeoulMidnightWithoutReloading() {
        AdminIpAllowlistRepository repository = mock(AdminIpAllowlistRepository.class);
        LocalDate date = LocalDate.of(2026, 9, 15);
        when(repository.findEnabled()).thenReturn(List.of(
                AdminIpAllowlist.builder().seq(1L).ipCidr("192.168.10.1")
                        .useStartDate(date).useEndDate(date).build()
        ));
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-09-14T14:59:59Z"));
        AdminIpAccessCache cache = new AdminIpAccessCache(repository, clock);
        cache.reload();
        var address = IpCidrRange.parseLiteralAddress("192.168.10.1");
        assertThat(cache.isAllowed(address)).isFalse();

        when(clock.instant()).thenReturn(Instant.parse("2026-09-14T15:00:00Z"));
        assertThat(cache.isAllowed(address)).isTrue();
        when(clock.instant()).thenReturn(Instant.parse("2026-09-15T14:59:59Z"));
        assertThat(cache.isAllowed(address)).isTrue();
        when(clock.instant()).thenReturn(Instant.parse("2026-09-15T15:00:00Z"));
        assertThat(cache.isAllowed(address)).isFalse();
        verify(repository, times(1)).findEnabled();
    }

    @Test
    void supportsOpenEndedDatesAndRejectsExpiredAndFutureRules() {
        AdminIpAllowlistRepository repository = mock(AdminIpAllowlistRepository.class);
        LocalDate today = LocalDate.of(2026, 9, 15);
        when(repository.findEnabled()).thenReturn(List.of(
                AdminIpAllowlist.builder().seq(1L).ipCidr("192.168.10.1").useStartDate(today).build(),
                AdminIpAllowlist.builder().seq(2L).ipCidr("192.168.10.2").useEndDate(today).build(),
                AdminIpAllowlist.builder().seq(3L).ipCidr("192.168.10.3").useEndDate(today.minusDays(1)).build(),
                AdminIpAllowlist.builder().seq(4L).ipCidr("192.168.10.4").useStartDate(today.plusDays(1)).build()
        ));
        AdminIpAccessCache cache = new AdminIpAccessCache(repository,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneId.of("Asia/Seoul")));
        cache.reload();
        assertThat(cache.isAllowed(IpCidrRange.parseLiteralAddress("192.168.10.1"))).isTrue();
        assertThat(cache.isAllowed(IpCidrRange.parseLiteralAddress("192.168.10.2"))).isTrue();
        assertThat(cache.isAllowed(IpCidrRange.parseLiteralAddress("192.168.10.3"))).isFalse();
        assertThat(cache.isAllowed(IpCidrRange.parseLiteralAddress("192.168.10.4"))).isFalse();
    }
}
