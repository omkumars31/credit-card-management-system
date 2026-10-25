package com.example.ccbilling.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ccbilling.service.AdminAccountService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

class ApiSecurityIntegrationTest extends IntegrationTestBase {

    @Autowired AdminAccountService adminAccountService;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpointsReturnGeneric401WhenNotAuthenticated() throws Exception {
        mvc.perform(get("/api/customers/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/admin/customers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfTokenEndpointReturnsHeaderNameAndToken() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("fullName", "No Csrf", "email", uniqueEmail("nocsrf"), "password", CUSTOMER_PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void registrationValidatesInputAndNeverReturnsThePassword() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("fullName", "", "email", "not-an-email", "password", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());

        String email = uniqueEmail("reg");
        String body = raw(mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("fullName", "Reg User", "email", email.toUpperCase(), "password", CUSTOMER_PASSWORD)))
                .andExpect(status().isCreated()));
        assertFalse(body.contains(CUSTOMER_PASSWORD));
        assertFalse(body.toLowerCase().contains("password"));
        assertTrue(body.contains(email.toLowerCase()));
    }

    @Test
    void duplicateRegistrationIsRejectedEvenWithDifferentCase() throws Exception {
        String email = uniqueEmail("dup");
        register(email);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("fullName", "Dup", "email", email.toUpperCase(), "password", CUSTOMER_PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGeneric401() throws Exception {
        String email = uniqueEmail("login");
        register(email);

        String wrongPassword = raw(mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("userType", "CUSTOMER", "identifier", email, "password", "Wrong-Password-1")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")));
        String unknownUser = raw(mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("userType", "CUSTOMER", "identifier", uniqueEmail("ghost"), "password", "Wrong-Password-1")))
                .andExpect(status().isUnauthorized()));

        JsonNode a = mapper.readTree(wrongPassword);
        JsonNode b = mapper.readTree(unknownUser);
        assertEquals(a.get("code"), b.get("code"));
        assertEquals(a.get("message"), b.get("message"));
    }

    @Test
    void customerCannotLogInAsAdministratorAndViceVersa() throws Exception {
        String email = uniqueEmail("cross");
        register(email);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("userType", "ADMIN", "identifier", email, "password", CUSTOMER_PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("userType", "CUSTOMER", "identifier", ADMIN_USER, "password", ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleBasedAccessIsEnforcedInBothDirections() throws Exception {
        String email = uniqueEmail("roles");
        register(email);
        MockHttpSession customer = login("CUSTOMER", email, CUSTOMER_PASSWORD);
        MockHttpSession admin = adminSession();

        getAs(customer, "/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.userType").value("CUSTOMER"));
        getAs(admin, "/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.userType").value("ADMIN"));

        getAs(customer, "/api/customers/me").andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email.toLowerCase()));
        getAs(customer, "/api/admin/customers").andExpect(status().isForbidden());
        getAs(customer, "/api/admin/audit-logs").andExpect(status().isForbidden());
        postAs(customer, "/api/admin/statements/generate", json("cardId", 1, "periodEnd", "2026-03-15")).andExpect(status().isForbidden());

        getAs(admin, "/api/admin/customers").andExpect(status().isOk());
        getAs(admin, "/api/customers/me").andExpect(status().isForbidden());
    }

    @Test
    void sessionLifecycleLoginKeepsUserSignedInAndLogoutInvalidatesTheSession() throws Exception {
        String email = uniqueEmail("logout");
        register(email);
        MockHttpSession session = login("CUSTOMER", email, CUSTOMER_PASSWORD);
        getAs(session, "/api/auth/me").andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        // logging out without a session or token is rejected as a CSRF failure, never silently accepted
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
    }

    @Test
    void browserStyleFlowUsingTheRealCsrfEndpointWorksEndToEnd() throws Exception {
        // exactly what the bundled web UI does: GET /api/auth/csrf, send the token header, repeat after login
        MockHttpSession session = new MockHttpSession();
        JsonNode first = read(mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()));
        mvc.perform(post("/api/auth/login").session(session)
                .header(first.get("headerName").asText(), first.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("userType", "ADMIN", "identifier", ADMIN_USER, "password", ADMIN_PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userType").value("ADMIN"));

        JsonNode second = read(mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()));
        String username = "ui-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/admin/administrators").session(session)
                .header(second.get("headerName").asText(), second.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username, "fullName", "UI Admin", "password", "a-long-passphrase-1")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/admin/administrators").session(session).header("X-CSRF-TOKEN", "bogus-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username + "x", "fullName", "UI Admin", "password", "a-long-passphrase-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledCustomerCannotLogIn() throws Exception {
        String email = uniqueEmail("disabled");
        long id = register(email);
        login("CUSTOMER", email, CUSTOMER_PASSWORD);
        MockHttpSession admin = adminSession();

        patchAs(admin, "/api/admin/customers/" + id + "/status", json("status", "DISABLED")).andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("userType", "CUSTOMER", "identifier", email, "password", CUSTOMER_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorListNeverExposesPasswordHashes() throws Exception {
        String body = raw(getAs(adminSession(), "/api/admin/administrators").andExpect(status().isOk()));
        assertTrue(body.contains(ADMIN_USER));
        assertFalse(body.toLowerCase().contains("password"));
        assertFalse(body.contains("{bcrypt}"));
    }

    @Test
    void malformedJsonAndBadSortAreClientErrorsNotServerErrors() throws Exception {
        MockHttpSession admin = adminSession();
        mvc.perform(post("/api/admin/bin-configurations").session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        getAs(admin, "/api/admin/customers?sort=doesNotExist").andExpect(status().isBadRequest());
        getAs(admin, "/api/admin/nonexistent").andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void adminServicesRejectNonAdministratorsEvenWhenCalledDirectly() {
        assertThrows(AccessDeniedException.class, () -> adminAccountService.list(Pageable.unpaged()));
    }
}
