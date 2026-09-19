package com.bjworld21.conference.publicsite;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Request-bound context; never fall back to a latest/default conference here. */
public final class PublicApiRequest {
    private PublicApiRequest() {}

    public static PublicSiteContext context() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new IllegalStateException("A public site request context is required");
        }
        PublicSiteContext context = PublicSiteContext.from(attributes.getRequest());
        if (context == null) throw new IllegalStateException("A public site request context is required");
        return context;
    }
}
