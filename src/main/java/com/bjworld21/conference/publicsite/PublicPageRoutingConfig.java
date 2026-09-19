package com.bjworld21.conference.publicsite;

import com.bjworld21.conference.controller.PublicPageController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

@Configuration
public class PublicPageRoutingConfig implements WebMvcConfigurer {
    private final PublicSiteService sites;
    public PublicPageRoutingConfig(PublicSiteService sites) { this.sites = sites; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (handler instanceof HandlerMethod method && method.getBeanType() == PublicPageController.class
                        && method.getMethod().getName().equals("route")) {
                    var page = sites.resolvePage(request.getRequestURI().substring(request.getContextPath().length()));
                    request.setAttribute("publicResolvedPage", page);
                    if (page.context() != null) {
                        request.setAttribute(PublicSiteContext.ATTRIBUTE, page.context());
                        request.setAttribute(PublicSiteContext.PAGE_PATH_ATTRIBUTE, page.pagePath());
                        LocaleContextHolder.setLocale(Locale.forLanguageTag(page.context().language()));
                    }
                }
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
                LocaleContextHolder.resetLocaleContext();
            }
        }).addPathPatterns("/**").order(-200);
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new LocaleResolver() {
            public Locale resolveLocale(HttpServletRequest request) {
                Object value = request.getAttribute(PublicSiteContext.ATTRIBUTE);
                return value instanceof PublicSiteContext context ? Locale.forLanguageTag(context.language()) : Locale.ENGLISH;
            }
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                throw new UnsupportedOperationException("Public language is selected by the URL.");
            }
        };
    }
}
