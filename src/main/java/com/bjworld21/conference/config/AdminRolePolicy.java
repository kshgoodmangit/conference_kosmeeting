package com.bjworld21.conference.config;

public final class AdminRolePolicy {
    private AdminRolePolicy() {
    }

    public static boolean isFullAdministrator(Object role) {
        return "admin".equals(role) || "maintenance".equals(role);
    }
}
