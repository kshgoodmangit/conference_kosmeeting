package com.bjworld21.conference.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public final class IpCidrRange {
    private final String canonicalCidr;
    private final byte[] networkAddress;
    private final int prefixLength;

    private IpCidrRange(String canonicalCidr, byte[] networkAddress, int prefixLength) {
        this.canonicalCidr = canonicalCidr;
        this.networkAddress = networkAddress;
        this.prefixLength = prefixLength;
    }

    public static IpCidrRange parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IP 또는 CIDR은 필수입니다.");
        }

        String[] parts = value.trim().split("/", -1);
        if (parts.length > 2 || parts[0].isBlank()) {
            throw new IllegalArgumentException("올바른 IP 또는 CIDR 형식이 아닙니다.");
        }

        InetAddress address = parseLiteralAddress(parts[0]);
        int maxPrefix = address.getAddress().length * 8;
        int prefixLength = maxPrefix;
        if (parts.length == 2) {
            try {
                prefixLength = Integer.parseInt(parts[1]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("CIDR prefix는 숫자여야 합니다.", exception);
            }
        }
        if (prefixLength < 0 || prefixLength > maxPrefix) {
            throw new IllegalArgumentException("CIDR prefix 범위가 올바르지 않습니다.");
        }

        byte[] network = address.getAddress().clone();
        maskHostBits(network, prefixLength);
        try {
            String canonicalAddress = InetAddress.getByAddress(network).getHostAddress();
            return new IpCidrRange(canonicalAddress + "/" + prefixLength, network, prefixLength);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("IP 주소를 정규화하지 못했습니다.", exception);
        }
    }

    public static InetAddress parseLiteralAddress(String value) {
        if (value == null || value.isBlank() || value.contains("%") || value.contains("/")) {
            throw new IllegalArgumentException("올바른 IP 주소가 아닙니다.");
        }

        String address = value.trim();
        boolean ipv4Candidate = address.indexOf(':') < 0;
        if (ipv4Candidate && !isValidIpv4(address)) {
            throw new IllegalArgumentException("올바른 IPv4 주소가 아닙니다.");
        }
        if (!ipv4Candidate && !address.matches("[0-9A-Fa-f:.]+")) {
            throw new IllegalArgumentException("올바른 IPv6 주소가 아닙니다.");
        }

        try {
            InetAddress parsed = InetAddress.getByName(address);
            if (ipv4Candidate && !(parsed instanceof Inet4Address)) {
                throw new IllegalArgumentException("올바른 IPv4 주소가 아닙니다.");
            }
            if (!ipv4Candidate && !(parsed instanceof Inet6Address)) {
                throw new IllegalArgumentException("올바른 IPv6 주소가 아닙니다.");
            }
            return parsed;
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("올바른 IP 주소가 아닙니다.", exception);
        }
    }

    public boolean contains(InetAddress address) {
        byte[] candidate = address.getAddress();
        if (candidate.length != networkAddress.length) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        if (!Arrays.equals(networkAddress, 0, fullBytes, candidate, 0, fullBytes)) {
            return false;
        }
        int remainingBits = prefixLength % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (networkAddress[fullBytes] & mask) == (candidate[fullBytes] & mask);
    }

    public String canonicalCidr() {
        return canonicalCidr;
    }

    private static void maskHostBits(byte[] address, int prefixLength) {
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        if (remainingBits != 0) {
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            address[fullBytes] = (byte) (address[fullBytes] & mask);
            fullBytes++;
        }
        Arrays.fill(address, fullBytes, address.length, (byte) 0);
    }

    private static boolean isValidIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(Character::isDigit)) {
                return false;
            }
            if (octet.length() > 1 && octet.startsWith("0")) {
                return false;
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }
}
