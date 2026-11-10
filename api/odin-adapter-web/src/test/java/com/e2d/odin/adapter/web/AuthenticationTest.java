package com.e2d.odin.adapter.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthenticationTest extends WebTestSupport {

    @Test
    void workerKeyCannotCallAdmin() throws Exception {
        String key = workerKey(newCompany());
        mvc.perform(get("/v1/admin/me").header(HttpHeaders.AUTHORIZATION, key)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionCannotCallWorkerRoutes() throws Exception {
        var cookie = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        mvc.perform(get("/v1/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void missingCredentialsIs401() throws Exception {
        mvc.perform(get("/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/admin/companies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer odin_xxxxxxxx_nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void foreignOriginIs403() throws Exception {
        var cookie = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        String body = "{\"name\":\"Nueva\"}";
        mvc.perform(post("/v1/admin/companies").cookie(cookie).header(HttpHeaders.ORIGIN, "http://evil.test")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/companies").cookie(cookie).header(HttpHeaders.ORIGIN, PANEL)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    @Test
    void cookieFlags() throws Exception {
        String email = "flags@e.test";
        savedAdmin(email);
        mvc.perform(post("/v1/admin/session").contentType(MediaType.APPLICATION_JSON)
                .content(login(email))).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("odin_session=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")));
    }

    @Test
    void loginThenMeThenLogout() throws Exception {
        String email = "flow@e.test";
        savedAdmin(email);
        var result = mvc.perform(post("/v1/admin/session").contentType(MediaType.APPLICATION_JSON)
                .content(login(email))).andReturn();
        var cookie = result.getResponse().getCookie("odin_session");
        mvc.perform(get("/v1/admin/me").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("GROUP_ADMIN"));
        mvc.perform(delete("/v1/admin/session").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(get("/v1/admin/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIs401WithoutHints() throws Exception {
        savedAdmin("wrong@e.test");
        mvc.perform(post("/v1/admin/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"wrong@e.test\",\"password\":\"otra-clave-larga\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.detail", not(containsString("wrong@e.test"))));
    }

    @Test
    void sixthLoginAttemptIs429() throws Exception {
        String body = "{\"email\":\"limit@e.test\",\"password\":\"mala-clave-larga\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/v1/admin/session").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/v1/admin/session").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void recoverAdminIsPublicAndNeedsToken() throws Exception {
        savedAdmin("recover@e.test");
        String email = w.users.findGroupAdmin().orElseThrow().email().orElseThrow();
        mvc.perform(post("/v1/setup/admin-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"setupToken\":\"otro\",\"newPassword\":\"clave-nueva-1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Código de instalación no válido"));
        mvc.perform(post("/v1/setup/admin-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"setupToken\":\"setup-secret\",\"newPassword\":\"clave-nueva-1234\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/v1/admin/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"clave-nueva-1234\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void sixthRecoverAttemptIs429() throws Exception {
        String body = "{\"setupToken\":\"otro\",\"newPassword\":\"clave-nueva-1234\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/v1/setup/admin-password").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/v1/setup/admin-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    private void savedAdmin(String email) {
        w.users.save(new User(new UserId(UUID.randomUUID()), "Gema", Optional.of(email), Role.GROUP_ADMIN,
                Optional.empty(), true, Optional.of(w.passwords.hash("clave-muy-larga-1"))));
    }

    private static String login(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"clave-muy-larga-1\"}";
    }
}
