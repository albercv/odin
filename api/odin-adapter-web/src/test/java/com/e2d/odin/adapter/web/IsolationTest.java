package com.e2d.odin.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class IsolationTest extends WebTestSupport {

    @Test
    void companyAdminOfBCannotListDocumentsOfA() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Fixtures.readyDocument(w.docs, a, "secreto de A");
        mvc.perform(get("/v1/admin/companies/" + a.value() + "/documents")
                .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(b)))).andExpect(status().isForbidden());
    }

    @Test
    void workerOfBGets404ReadingPageOfA() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document secret = Fixtures.readyDocument(w.docs, a, "secreto de A");
        mvc.perform(get("/v1/documents/" + secret.id().value() + "/pages/1")
                .header("Authorization", workerKey(b))).andExpect(status().isNotFound());
    }

    @Test
    void otherCompanyAdminCannotPatchOrDeleteUser() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        workerKey(a);
        String id = w.users.listByCompany(a).get(0).id().value().toString();
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        mvc.perform(patch("/v1/admin/users/" + id).cookie(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isForbidden());
        mvc.perform(delete("/v1/admin/users/" + id).cookie(other)).andExpect(status().isForbidden());
    }

    @Test
    void companyAdminCannotResetUserOfAnotherCompany() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        var victim = new com.e2d.odin.domain.identity.User(new com.e2d.odin.domain.shared.UserId(
                java.util.UUID.randomUUID()), "Ana", Optional.of("ana@e.test"), Role.COMPANY_ADMIN, Optional.of(a),
                true, Optional.of(w.passwords.hash("clave-muy-larga-1")));
        w.users.save(victim);
        mvc.perform(put("/v1/admin/users/" + victim.id().value() + "/password")
                .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(b))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"clave-nueva-1234\"}")).andExpect(status().isForbidden());
    }

    @Test
    void otherCompanyAdminCannotRenameOrDeleteProject() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        String id = Fixtures.project(w.orgs, a).value().toString();
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        mvc.perform(patch("/v1/admin/companies/" + a.value() + "/projects/" + id).cookie(other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mio\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/v1/admin/companies/" + a.value() + "/projects/" + id).cookie(other))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/v1/admin/companies/" + b.value() + "/projects/" + id).cookie(other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mio\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/v1/admin/companies/" + b.value() + "/projects/" + id).cookie(other))
                .andExpect(status().isNotFound());
    }
}
