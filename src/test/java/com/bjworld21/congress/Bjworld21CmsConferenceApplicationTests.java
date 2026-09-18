package com.bjworld21.congress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Bjworld21CmsConferenceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void contextLoads() {
    }

    @Test
    void servesCkEditorStaticResource() throws Exception {
        mockMvc.perform(get("/vendor/ckeditor4/ckeditor.js"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnsafeApiRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/cache/reload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-CSRF-ERROR", "true"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void acceptsTokenIssuedByCsrfEndpoint() throws Exception {
        MvcResult tokenResult = mockMvc.perform(get("/api/security/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        JsonNode tokenResponse = objectMapper.readTree(tokenResult.getResponse().getContentAsByteArray());
        String headerName = tokenResponse.path("headerName").asText();
        String token = tokenResponse.path("token").asText();
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/admin/cache/reload")
                        .session(session)
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("X-CSRF-ERROR"));
    }

    @Test
    void protectsCkEditorImageUploadsWithCsrfToken() throws Exception {
        String[] uploadPaths = {
                "/api/admin/boards/images",
                "/api/admin/popups/images",
                "/api/admin/mail/images"
        };
        for (String uploadPath : uploadPaths) {
            mockMvc.perform(multipart(uploadPath)
                            .file(new MockMultipartFile("upload", "image.png", "image/png", new byte[]{1})))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("X-CSRF-ERROR", "true"));
        }

        MvcResult tokenResult = mockMvc.perform(get("/api/security/csrf-token"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokenResponse = objectMapper.readTree(tokenResult.getResponse().getContentAsByteArray());
        String headerName = tokenResponse.path("headerName").asText();
        String token = tokenResponse.path("token").asText();
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);

        for (String uploadPath : uploadPaths) {
            mockMvc.perform(multipart(uploadPath)
                            .file(new MockMultipartFile("upload", "image.png", "image/png", new byte[]{1}))
                            .session(session)
                            .header(headerName, token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("X-CSRF-ERROR"));
        }
    }

    @Test
    void doesNotApplyBrowserCsrfProtectionToSignedMailWebhook() throws Exception {
        mockMvc.perform(post("/api/webhooks/mail/unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(header().doesNotExist("X-CSRF-ERROR"));
    }

    @Test
    void separatesPublicAndAdminApiPaths() {
        Set<String> paths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(paths).contains(
                "/api/conference-settings",
                "/api/countries/used",
                "/api/members/register",
                "/api/registration-fees",
                "/api/popups/{seq}/image",
                "/api/sponsors/{seq}/logo",
                "/api/sponsorship-applications",
                "/api/admin/conference-settings",
                "/api/admin/conference-settings/{seq}",
                "/api/admin/countries",
                "/api/admin/countries/{seq}/is-used",
                "/api/admin/abstracts",
                "/api/admin/abstract-evaluation-items",
                "/api/admin/common-codes",
                "/api/admin/menu-settings",
                "/api/admin/members",
                "/api/admin/popups",
                "/api/admin/popups/{seq}",
                "/api/admin/registration-fees",
                "/api/admin/sponsors",
                "/api/admin/sponsors/types",
                "/api/admin/sponsors/{seq}",
                "/api/admin/me/password",
                "/api/admin/sponsorship-applications",
                "/api/admin/sponsorship-applications/{seq}",
                "/api/admin/sponsorship-applications/{seq}/business-license"
        );
        assertThat(paths).doesNotContain(
                "/api/conference-settings/list",
                "/api/abstracts",
                "/api/abstract-evaluation-items",
                "/api/common-codes",
                "/api/countries",
                "/api/menu-settings",
                "/api/members",
                "/api/members/{seq}",
                "/api/popups/page",
                "/api/popups/{seq}",
                "/api/sponsors/page",
                "/api/sponsors/types",
                "/api/sponsors/{seq}",
                "/api/admin-account/me/password",
                "/api/sponsorship-applications/{seq}"
        );
    }

}
