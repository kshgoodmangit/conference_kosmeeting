package com.bjworld21.conference.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpCidrRangeTest {

    @Test
    void normalizesSingleIpv4AndMatchesOnlyThatAddress() {
        IpCidrRange range = IpCidrRange.parse("203.0.113.10");

        assertThat(range.canonicalCidr()).isEqualTo("203.0.113.10/32");
        assertThat(range.contains(IpCidrRange.parseLiteralAddress("203.0.113.10"))).isTrue();
        assertThat(range.contains(IpCidrRange.parseLiteralAddress("203.0.113.11"))).isFalse();
    }

    @Test
    void masksHostBitsAndMatchesIpv4Subnet() {
        IpCidrRange range = IpCidrRange.parse("192.168.10.25/24");

        assertThat(range.canonicalCidr()).isEqualTo("192.168.10.0/24");
        assertThat(range.contains(IpCidrRange.parseLiteralAddress("192.168.10.255"))).isTrue();
        assertThat(range.contains(IpCidrRange.parseLiteralAddress("192.168.11.1"))).isFalse();
    }

    @Test
    void supportsIpv6Subnet() {
        IpCidrRange range = IpCidrRange.parse("2001:db8::1234/64");

        assertThat(range.contains(IpCidrRange.parseLiteralAddress("2001:db8::ffff"))).isTrue();
        assertThat(range.contains(IpCidrRange.parseLiteralAddress("2001:db9::1"))).isFalse();
    }

    @Test
    void rejectsHostNamesAndInvalidPrefix() {
        assertThatThrownBy(() -> IpCidrRange.parse("localhost"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpCidrRange.parse("192.168.0.1/33"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
