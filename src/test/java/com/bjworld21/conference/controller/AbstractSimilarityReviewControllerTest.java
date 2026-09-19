package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.AdminSessionInterceptor;
import com.bjworld21.conference.config.LicenseProperties;
import com.bjworld21.conference.config.SecurityConfig;
import com.bjworld21.conference.service.AbstractSimilarityReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig({SecurityConfig.class, AbstractSimilarityReviewControllerTest.AccessConfig.class})
class AbstractSimilarityReviewControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired LicenseProperties licenseProperties;
    @Autowired AbstractSimilarityReviewService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        licenseProperties.setAbstractSimilarityEnabled(true);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class AccessConfig implements WebMvcConfigurer {
        @Bean AbstractSimilarityReviewService service() { return mock(AbstractSimilarityReviewService.class); }
        @Bean LicenseProperties licenseProperties() { return new LicenseProperties(); }
        @Bean AbstractSimilarityReviewController controller(AbstractSimilarityReviewService service, LicenseProperties license) {
            return new AbstractSimilarityReviewController(service, license);
        }
        @Override public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new AdminSessionInterceptor()).addPathPatterns("/api/admin/**");
        }
    }

    @Test
    void endpointsRequireFullAdministratorSession() throws Exception {
        mvc.perform(get("/api/admin/abstract-similarity-reviews"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/abstract-similarity-reviews")
                        .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "reviewer"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/abstract-similarity-reviews")
                        .header("X-Conference-Seq", "1")
                        .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin"))
                .andExpect(status().isOk());
    }

    @Test
    void disabledLicenseBlocksApiWithoutCallingService() throws Exception {
        licenseProperties.setAbstractSimilarityEnabled(false);

        mvc.perform(get("/api/admin/abstract-similarity-reviews")
                        .header("X-Conference-Seq", "1")
                        .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void mutationRequiresCsrf() throws Exception {
        String body = "{\"status\":\"IN_REVIEW\",\"reviewOpinion\":null,\"rejectionRecommended\":false}";
        mvc.perform(put("/api/admin/abstract-similarity-reviews/10")
                        .header("X-Conference-Seq", "1")
                        .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin")
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/abstract-similarity-reviews/10")
                        .with(csrf())
                        .header("X-Conference-Seq", "1")
                        .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
    }
}
