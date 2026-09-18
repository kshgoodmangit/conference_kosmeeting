package com.bjworld21.congress.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ConferenceAdminWebMvcConfig implements WebMvcConfigurer {
    private final AdminIpAccessInterceptor adminIpAccessInterceptor;
    private final AdminSessionInterceptor adminSessionInterceptor;
    private final AdminAccountSessionInterceptor adminAccountSessionInterceptor;
    private final ReviewerSessionInterceptor reviewerSessionInterceptor;
    private final MaintenanceSessionInterceptor maintenanceSessionInterceptor;

    public ConferenceAdminWebMvcConfig(
            AdminIpAccessInterceptor adminIpAccessInterceptor,
            AdminSessionInterceptor adminSessionInterceptor,
            AdminAccountSessionInterceptor adminAccountSessionInterceptor,
            ReviewerSessionInterceptor reviewerSessionInterceptor,
            MaintenanceSessionInterceptor maintenanceSessionInterceptor
    ) {
        this.adminIpAccessInterceptor = adminIpAccessInterceptor;
        this.adminSessionInterceptor = adminSessionInterceptor;
        this.adminAccountSessionInterceptor = adminAccountSessionInterceptor;
        this.reviewerSessionInterceptor = reviewerSessionInterceptor;
        this.maintenanceSessionInterceptor = maintenanceSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminIpAccessInterceptor)
                .addPathPatterns("/admin", "/admin/**", "/api/**")
                .order(Ordered.HIGHEST_PRECEDENCE);

        registry.addInterceptor(adminSessionInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns(
                        "/api/admin/login",
                        "/api/admin/logout",
                        "/api/admin/session",
                        "/api/admin/me/**",
                        "/api/admin/access-info/client-ip",
                        "/api/admin/cache/reload"
                );

        registry.addInterceptor(adminAccountSessionInterceptor)
                .addPathPatterns("/api/admin/me/**");

        registry.addInterceptor(reviewerSessionInterceptor)
                .addPathPatterns("/api/reviewer/**");

        registry.addInterceptor(maintenanceSessionInterceptor)
                .addPathPatterns("/api/maintenance/**");
    }
}
