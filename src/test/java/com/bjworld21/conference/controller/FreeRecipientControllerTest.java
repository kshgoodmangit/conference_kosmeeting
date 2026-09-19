package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.AdminSessionInterceptor;
import com.bjworld21.conference.config.SecurityConfig;
import com.bjworld21.conference.service.FreeRecipientService;
import com.bjworld21.conference.service.FreeRecipientBulkImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.mockito.Mockito.mock;

@SpringJUnitWebConfig({SecurityConfig.class, FreeRecipientControllerTest.AccessConfig.class})
class FreeRecipientControllerTest {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class AccessConfig implements WebMvcConfigurer {
        @Bean FreeRecipientController controller() {
            return new FreeRecipientController(mock(FreeRecipientService.class), mock(FreeRecipientBulkImportService.class));
        }
        @Override public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new AdminSessionInterceptor()).addPathPatterns("/api/admin/**");
        }
    }
    @Test
    void allPersonalDataEndpointsRequireAdminSession() throws Exception {
        mvc.perform(get("/api/admin/free-recipients")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/free-recipients").sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "reviewer"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/free-recipients").header("X-Conference-Seq", "1")
                        .sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/free-recipients").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void mutationsRequireCsrfEvenWithAdminSession() throws Exception {
        mvc.perform(post("/api/admin/free-recipients").sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin")
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/free-recipients").header("X-Conference-Seq", "1")
                .with(csrf()).sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin")
                .contentType("application/json").content("{}"))
                .andExpect(status().isCreated());
    }
}

