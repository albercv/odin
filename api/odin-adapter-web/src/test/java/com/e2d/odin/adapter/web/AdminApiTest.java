package com.e2d.odin.adapter.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class AdminApiTest extends WebTestSupport {

    @AfterEach
    void resetDetector() {
        w.detector.detects(com.e2d.odin.domain.document.DocumentFormat.PDF);
    }

    @Test
    void uploadMultipartReturns201() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        mvc.perform(upload(company, admin, "contenido-uno".getBytes())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.document.status").value("RECEIVED"))
                .andExpect(jsonPath("$.document.title").value("Manual"))
                .andExpect(jsonPath("$.document.sha256").doesNotExist());
        mvc.perform(upload(company, admin, "contenido-uno".getBytes())).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void uploadTooLargeIs413() throws Exception {
        CompanyId company = newCompany();
        mvc.perform(upload(company, adminCookie(Role.COMPANY_ADMIN, Optional.of(company)),
                new byte[(int) TestWiring.MAX_UPLOAD + 1])).andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void unknownFormatIs415() throws Exception {
        w.detector.detects(null);
        CompanyId company = newCompany();
        mvc.perform(upload(company, adminCookie(Role.COMPANY_ADMIN, Optional.of(company)), "zzz".getBytes()))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void emptyFileIs400() throws Exception {
        CompanyId company = newCompany();
        mvc.perform(upload(company, adminCookie(Role.COMPANY_ADMIN, Optional.of(company)), new byte[0]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keyShownOnce() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        UserId worker = workerId(company);
        var issued = mvc.perform(post("/v1/admin/users/" + worker.value() + "/keys").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"movil\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.key").value(startsWith("odin_")))
                .andExpect(jsonPath("$.prefix").isNotEmpty()).andReturn();
        String key = com.jayway.jsonpath.JsonPath.read(issued.getResponse().getContentAsString(), "$.key");
        String listing = mvc.perform(get("/v1/admin/users/" + worker.value() + "/keys").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").doesNotExist()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(listing).doesNotContain(key.substring(key.lastIndexOf('_') + 1))
                .doesNotContainIgnoringCase("secretHash").doesNotContainIgnoringCase("hash");
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + key)).andExpect(status().isOk());
    }

    @Test
    void userListingHasNoHashes() throws Exception {
        CompanyId company = newCompany();
        Cookie group = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        mvc.perform(post("/v1/admin/companies/" + company.value() + "/users").cookie(group)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Cora\",\"email\":\"cora@e.test\",\"role\":\"COMPANY_ADMIN\","
                        + "\"password\":\"clave-muy-larga-1\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());
        String listing = mvc.perform(get("/v1/admin/companies/" + company.value() + "/users").cookie(group))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andReturn().getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(listing).doesNotContainIgnoringCase("hash")
                .doesNotContain("clave-muy-larga-1");
    }

    @Test
    void deactivatingUserBlocksTheirKey() throws Exception {
        CompanyId company = newCompany();
        String key = workerKey(company);
        UserId worker = w.users.listByCompany(company).get(0).id();
        mvc.perform(patch("/v1/admin/users/" + worker.value()).cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/v1/me").header("Authorization", key)).andExpect(status().isUnauthorized());
    }

    @Test
    void patchUserUpdatesFields() throws Exception {
        CompanyId company = newCompany();
        workerKey(company);
        UserId worker = w.users.listByCompany(company).get(0).id();
        mvc.perform(patch("/v1/admin/users/" + worker.value()).cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Nuevo\",\"email\":\"N@e.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Nuevo"))
                .andExpect(jsonPath("$.email").value("n@e.test")).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void patchUserReactivates() throws Exception {
        CompanyId company = newCompany();
        workerKey(company);
        UserId worker = w.users.listByCompany(company).get(0).id();
        Cookie group = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        mvc.perform(patch("/v1/admin/users/" + worker.value()).cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch("/v1/admin/users/" + worker.value()).cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deleteUserIs204() throws Exception {
        CompanyId company = newCompany();
        String key = workerKey(company);
        UserId worker = w.users.listByCompany(company).get(0).id();
        Cookie group = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        mvc.perform(delete("/v1/admin/users/" + worker.value()).cookie(group)).andExpect(status().isNoContent());
        mvc.perform(get("/v1/me").header("Authorization", key)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/v1/admin/users/" + worker.value()).cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}")).andExpect(status().isNotFound());
    }

    @Test
    void putPasswordIs204() throws Exception {
        CompanyId company = newCompany();
        User target = companyAdmin(company, "c1@e.test");
        mvc.perform(put("/v1/admin/users/" + target.id().value() + "/password")
                .cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"clave-nueva-1234\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/v1/admin/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"c1@e.test\",\"password\":\"clave-nueva-1234\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void putPasswordWithWrongCurrentIs400() throws Exception {
        CompanyId company = newCompany();
        User self = companyAdmin(company, "c2@e.test");
        var login = mvc.perform(post("/v1/admin/session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"c2@e.test\",\"password\":\"clave-muy-larga-1\"}")).andReturn();
        mvc.perform(put("/v1/admin/users/" + self.id().value() + "/password")
                .cookie(login.getResponse().getCookie("odin_session")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"equivocada-12345\",\"newPassword\":\"clave-nueva-1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña actual no es correcta"));
    }

    private User companyAdmin(CompanyId company, String email) {
        User user = new User(new UserId(UUID.randomUUID()), "Carlos", Optional.of(email), Role.COMPANY_ADMIN,
                Optional.of(company), true, Optional.of(w.passwords.hash("clave-muy-larga-1")));
        w.users.save(user);
        return user;
    }

    @Test
    void workerWithPasswordIs400() throws Exception {
        CompanyId company = newCompany();
        mvc.perform(post("/v1/admin/companies/" + company.value() + "/users")
                .cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Wanda\",\"role\":\"WORKER\",\"password\":\"clave-muy-larga-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Un trabajador no tiene contraseña: entra con su clave personal"));
    }

    @Test
    void companyLifecycleAndCardReview() throws Exception {
        Cookie group = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        var created = mvc.perform(post("/v1/admin/companies").cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme\"}")).andExpect(status().isCreated()).andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(patch("/v1/admin/companies/" + id).cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme 2\"}")).andExpect(jsonPath("$.name").value("Acme 2"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/v1/admin/companies/" + id + "/capabilities").cookie(group)
                .contentType(MediaType.APPLICATION_JSON).content("{\"capabilities\":[\"SEARCH_KNOWLEDGE\"]}"))
                .andExpect(jsonPath("$.capabilities", hasSize(1)));
        mvc.perform(post("/v1/admin/companies/" + id + "/projects").cookie(group)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Obra\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/v1/admin/companies/" + id + "/projects").cookie(group)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/v1/admin/companies/" + id + "/cards?status=PENDING").cookie(group))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/v1/admin/cards/" + UUID.randomUUID() + "/review").cookie(group)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isNotFound());
    }

    private String createProject(Cookie admin, CompanyId company, String name) throws Exception {
        var created = mvc.perform(post("/v1/admin/companies/" + company.value() + "/projects").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}")).andReturn();
        return com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void patchProjectRenames() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String id = createProject(admin, company, "Obra");
        mvc.perform(patch("/v1/admin/companies/" + company.value() + "/projects/" + id).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Reforma\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Reforma"));
    }

    @Test
    void deleteProjectIs204() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String id = createProject(admin, company, "Obra");
        mvc.perform(delete("/v1/admin/companies/" + company.value() + "/projects/" + id).cookie(admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/projects").cookie(admin))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/v1/admin/companies/" + company.value() + "/projects/" + id).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateProjectNameIs409() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        createProject(admin, company, "Obra");
        mvc.perform(post("/v1/admin/companies/" + company.value() + "/projects").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"obra\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un proyecto con ese nombre"));
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload(
            CompanyId company, Cookie admin, byte[] bytes) {
        return multipart("/v1/admin/companies/" + company.value() + "/documents")
                .file(new MockMultipartFile("file", "manual.pdf", "application/pdf", bytes))
                .param("title", "Manual").cookie(admin);
    }
}
