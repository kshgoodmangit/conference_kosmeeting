package com.bjworld21.conference.controller;

import com.bjworld21.conference.config.AdminSessionInterceptor;
import com.bjworld21.conference.config.SecurityConfig;
import com.bjworld21.conference.service.SocietyMemberService;
import com.bjworld21.conference.service.SocietyMemberBulkImportService;
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

@SpringJUnitWebConfig({SecurityConfig.class, SocietyMemberControllerTest.AccessConfig.class})
class SocietyMemberControllerTest {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class AccessConfig implements WebMvcConfigurer {
        @Bean SocietyMemberController controller() {
            return new SocietyMemberController(mock(SocietyMemberService.class), mock(SocietyMemberBulkImportService.class));
        }
        @Override public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new AdminSessionInterceptor()).addPathPatterns("/api/admin/**");
        }
    }
    @Test
    void allPersonalDataEndpointsRequireAdminSession() throws Exception {
        mvc.perform(get("/api/admin/society-members")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/society-members").sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "reviewer"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/society-members").sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/society-members/fee-quote").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void mutationsRequireCsrfEvenWithAdminSession() throws Exception {
        mvc.perform(post("/api/admin/society-members").sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin")
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/society-members").with(csrf()).sessionAttr("adminSeq", 1L).sessionAttr("adminRole", "admin")
                .contentType("application/json").content("{}"))
                .andExpect(status().isCreated());
    }
}
