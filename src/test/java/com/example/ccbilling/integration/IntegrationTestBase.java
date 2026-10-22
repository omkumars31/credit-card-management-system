package com.example.ccbilling.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ccbilling.support.MutableClock;
import com.example.ccbilling.support.TestClockConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Full application context on in-memory H2 (MySQL mode) with Flyway migrations and a controllable clock. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
abstract class IntegrationTestBase {

    // Matches src/test/resources/application-test.properties (test-only credentials)
    protected static final String ADMIN_USER = "testadmin";
    protected static final String ADMIN_PASSWORD = "Test-Only-Admin-Passw0rd";
    protected static final String CUSTOMER_PASSWORD = "Sup3rSecretPass";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected MutableClock clock;

    protected static BigDecimal dec(String v) {
        return new BigDecimal(v);
    }

    protected String json(Object... keyValues) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return mapper.writeValueAsString(map);
    }

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    protected MockHttpSession login(String userType, String identifier, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("userType", userType, "identifier", identifier, "password", password)))
                .andExpect(status().isOk());
        return session;
    }

    protected MockHttpSession adminSession() throws Exception {
        return login("ADMIN", ADMIN_USER, ADMIN_PASSWORD);
    }

    protected long register(String email) throws Exception {
        ResultActions r = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("fullName", "Test " + email, "email", email, "phone", "+919876543210",
                        "password", CUSTOMER_PASSWORD)))
                .andExpect(status().isCreated());
        return read(r).get("id").asLong();
    }

    protected ResultActions getAs(MockHttpSession s, String url) throws Exception {
        return mvc.perform(get(url).session(s));
    }

    protected ResultActions postAs(MockHttpSession s, String url, String body) throws Exception {
        return mvc.perform(post(url).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions putAs(MockHttpSession s, String url, String body) throws Exception {
        return mvc.perform(put(url).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions patchAs(MockHttpSession s, String url, String body) throws Exception {
        return mvc.perform(patch(url).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected String raw(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
