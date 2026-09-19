package com.bjworld21.conference.controller;

import com.bjworld21.conference.publicsite.PublicPageRoutingConfig;
import com.bjworld21.conference.publicsite.PublicSiteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBrandingResourceTest {
    @ParameterizedTest
    @CsvSource({
            "logo-v3.png,*/*",
            "logo-v3.png,text/html",
            "logo-v3.png,'image/avif,image/webp,image/*,*/*;q=0.8'",
            "favicon-v3.ico,*/*"
    })
    void servesBrandingFilesWithoutResolvingAConference(String filename, String accept) throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            byte[] expected = new ClassPathResource("static/images/admin-branding/" + filename).getContentAsByteArray();
            var result = mvc.perform(get("/images/admin-branding/" + filename).header("Accept", accept))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(expected))
                    .andReturn();
            assertThat(result.getHandler()).isInstanceOf(ResourceHttpRequestHandler.class);
            verifyNoInteractions(context.getBean(PublicSiteService.class), context.getBean(PublicPageController.class));
        }
    }

    @Test
    void missingImageRemainsAResource404() throws Exception {
        try (var context = context()) {
            var result = MockMvcBuilders.webAppContextSetup(context).build()
                    .perform(get("/images/admin-branding/missing.png"))
                    .andExpect(status().isNotFound()).andReturn();
            assertThat(result.getHandler()).isInstanceOf(ResourceHttpRequestHandler.class);
            verifyNoInteractions(context.getBean(PublicSiteService.class), context.getBean(PublicPageController.class));
        }
    }

    private AnnotationConfigWebApplicationContext context() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        return context;
    }

    @Configuration
    @EnableWebMvc
    @Import(PublicPageRoutingConfig.class)
    static class Config implements WebMvcConfigurer {
        @Bean
        PublicPageController publicPageController() { return mock(PublicPageController.class); }

        @Bean
        PublicSiteService publicSiteService() { return mock(PublicSiteService.class); }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        }
    }
}
