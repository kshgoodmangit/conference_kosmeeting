package com.bjworld21.conference.security;

import com.bjworld21.conference.config.AdminIpAccessProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;

@Component
public class ClientIpResolver {
    private final List<IpCidrRange> trustedProxies;

    public ClientIpResolver(AdminIpAccessProperties properties) {
        this.trustedProxies = properties.getTrustedProxies().stream()
                .map(IpCidrRange::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        InetAddress remoteAddress = IpCidrRange.parseLiteralAddress(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress.getHostAddress();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress.getHostAddress();
        }

        String[] hops = forwardedFor.split(",");
        for (int index = hops.length - 1; index >= 0; index--) {
            InetAddress hop;
            try {
                hop = IpCidrRange.parseLiteralAddress(hops[index].trim());
            } catch (IllegalArgumentException exception) {
                return remoteAddress.getHostAddress();
            }
            if (!isTrustedProxy(hop)) {
                return hop.getHostAddress();
            }
        }
        return remoteAddress.getHostAddress();
    }

    public boolean isTrustedProxy(HttpServletRequest request) {
        return isTrustedProxy(IpCidrRange.parseLiteralAddress(request.getRemoteAddr()));
    }

    private boolean isTrustedProxy(InetAddress address) {
        return trustedProxies.stream().anyMatch(proxy -> proxy.contains(address));
    }
}
