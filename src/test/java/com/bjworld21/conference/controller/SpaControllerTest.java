package com.bjworld21.conference.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SpaController()).build();

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/dashboard",
            "/admin/abstracts",
            "/admin/members",
            "/admin/commoncode",
            "/admin/admin",
            "/admin/conference",
            "/admin/menu",
            "/admin/popup",
            "/admin/sponsorship",
            "/admin/custom-db-route"
    })
    void forwardsDynamicSpaPathToReactEntryPoint(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/apdrc8",
            "/program/scientific-program",
            "/abstract/submission-guideline",
            "/registration/online-registration",
            "/information/about-seoul-korea",
            "/sponsors/sponsorship",
            "/mypage",
            "/join",
            "/commoncode"
    })
    void doesNotClaimPublicPagePaths(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound());
    }

}
