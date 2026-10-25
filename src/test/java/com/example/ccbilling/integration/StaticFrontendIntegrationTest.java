package com.example.ccbilling.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** The bundled web UI is public static content; every data endpoint stays protected. */
class StaticFrontendIntegrationTest extends IntegrationTestBase {

    @Test
    void rootServesTheSinglePageApplicationWithoutLogin() throws Exception {
        String forwarded = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getForwardedUrl();
        assertTrue(forwarded != null && forwarded.endsWith("index.html"), "root should forward to index.html but was " + forwarded);
        String html = raw(mvc.perform(get("/index.html")).andExpect(status().isOk()));
        assertTrue(html.contains("Credit Card Billing"));
        assertTrue(html.contains("/js/main.js"));
    }

    @Test
    void scriptsAndStylesAreServed() throws Exception {
        mvc.perform(get("/js/main.js")).andExpect(status().isOk());
        mvc.perform(get("/js/api.js")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    void contentSecurityPolicyForbidsInlineScriptsAndFraming() throws Exception {
        mvc.perform(get("/index.html")).andExpect(header().string("Content-Security-Policy",
                org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("script-src 'self'"),
                        org.hamcrest.Matchers.containsString("frame-ancestors 'none'"))));
    }

    @Test
    void apiStaysProtectedEvenThoughTheUiIsPublic() throws Exception {
        mvc.perform(get("/api/customers/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/customers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/some/other/path")).andExpect(status().isUnauthorized());
    }
}
