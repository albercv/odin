package com.e2d.odin.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
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
        String id = w.memberships.listByCompany(a).get(0).userId().value().toString();
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        mvc.perform(patch("/v1/admin/users/" + id).cookie(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isForbidden());
        mvc.perform(delete("/v1/admin/users/" + id).cookie(other)).andExpect(status().isForbidden());
    }

    @Test
    void otherCompanyAdminCannotListAddPatchOrRemoveMembers() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        UserId member = workerId(a);
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        String base = "/v1/admin/companies/" + a.value() + "/members";
        mvc.perform(get(base).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(post(base).cookie(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Intruso\",\"roles\":[\"WORKER\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(base + "/" + member.value()).cookie(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isForbidden());
        mvc.perform(delete(base + "/" + member.value()).cookie(other)).andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(w.memberships.find(member, a)).get()
                .satisfies(m -> org.assertj.core.api.Assertions.assertThat(m.active()).isTrue());
        org.assertj.core.api.Assertions.assertThat(w.memberships.listByCompany(a)).hasSize(1);
    }

    @Test
    void otherCompanyAdminCannotIssueKeyForForeignMembership() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        UserId member = workerId(a);
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        for (CompanyId path : new CompanyId[] {a, b}) {
            var request = post("/v1/admin/companies/" + path.value() + "/members/" + member.value() + "/keys")
                    .cookie(other).contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"robada\"}");
            mvc.perform(request).andExpect(status().is(path.equals(a) ? 403 : 404));
        }
        mvc.perform(get("/v1/admin/companies/" + a.value() + "/members/" + member.value() + "/keys").cookie(other))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(w.keys.listByUser(member)).isEmpty();
    }

    @Test
    void companyAdminCannotResetUserOfAnotherCompany() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        var victim = People.member(w.users, w.memberships, new com.e2d.odin.domain.shared.UserId(
                java.util.UUID.randomUUID()), "Ana", Optional.of("ana@e.test"), Role.COMPANY_ADMIN, a,
                true, Optional.of(w.passwords.hash("clave-muy-larga-1")));
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
