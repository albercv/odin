package com.e2d.odin.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void otherCompanyAdminCannotMarkOrUnmarkForeignDocument() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document old = Fixtures.readyDocument(w.docs, a, "viejo de A");
        Document current = Fixtures.readyDocument(w.docs, a, "nuevo de A");
        Document ownOfB = Fixtures.readyDocument(w.docs, b, "de B");
        w.docs.markSuperseded(a, old.id(), current.id(), w.clock.instant());
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        String url = "/v1/admin/documents/" + old.id().value() + "/superseded-by";

        mvc.perform(put(url).cookie(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"replacementId\":\"" + ownOfB.id().value() + "\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Documento no encontrado"));
        mvc.perform(delete(url).cookie(other)).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(w.docs.get(old.id()).orElseThrow().supersededBy())
                .contains(current.id());
    }

    @Test
    void companyAdminCannotUseAReplacementOfAnotherCompany() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document old = Fixtures.readyDocument(w.docs, a, "viejo de A");
        Document foreign = Fixtures.readyDocument(w.docs, b, "ajeno");
        var admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(a));
        var group = adminCookie(Role.GROUP_ADMIN, Optional.empty());

        for (var cookie : new jakarta.servlet.http.Cookie[] {admin, group}) {
            mvc.perform(put("/v1/admin/documents/" + old.id().value() + "/superseded-by").cookie(cookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"replacementId\":\"" + foreign.id().value() + "\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Documento que lo sustituye no encontrado"));
        }
        org.assertj.core.api.Assertions.assertThat(w.docs.get(old.id()).orElseThrow().superseded()).isFalse();
    }

    @Test
    void otherCompanyAdminCannotManageTopics() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        var ownerOfA = adminCookie(Role.COMPANY_ADMIN, Optional.of(a));
        var created = mvc.perform(post("/v1/admin/companies/" + a.value() + "/topics").cookie(ownerOfA)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Agua\",\"definition\":\"Fugas\"}"))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        String base = "/v1/admin/companies/" + a.value() + "/topics";

        mvc.perform(get(base).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(post(base).cookie(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Seguros\",\"definition\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(patch(base + "/" + id).cookie(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Robado\"}")).andExpect(status().isForbidden());
        mvc.perform(delete(base + "/" + id).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(get(base).cookie(ownerOfA)).andExpect(jsonPath("$[0].name").value("Agua"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void otherCompanyAdminCannotReviewForeignDocumentTopics() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        var ownerOfA = adminCookie(Role.COMPANY_ADMIN, Optional.of(a));
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        var created = mvc.perform(post("/v1/admin/companies/" + a.value() + "/topics").cookie(ownerOfA)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Agua\",\"definition\":\"Fugas\"}"))
                .andReturn();
        String topic = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        Document secret = Fixtures.readyDocument(w.docs, a, "secreto de A");
        w.tagging.seedRow(secret, com.e2d.odin.domain.topic.TaggingStatus.DONE);
        String base = "/v1/admin/documents/" + secret.id().value() + "/topics";

        mvc.perform(get(base).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(put(base + "/" + topic).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(delete(base + "/" + topic).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/retag").cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/v1/admin/companies/" + a.value() + "/tagging-status").cookie(other))
                .andExpect(status().isForbidden());
        mvc.perform(get(base).cookie(ownerOfA)).andExpect(jsonPath("$.tagging.status").value("DONE"))
                .andExpect(jsonPath("$.topics.length()").value(0));
    }

    @Test
    void companyAdminCannotChangeACompanyCode() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        String before = w.orgs.findCompany(a).orElseThrow().code();

        for (CompanyId own : new CompanyId[] {a, b}) {
            mvc.perform(patch("/v1/admin/companies/" + a.value())
                    .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(own))).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"HACKED\"}")).andExpect(status().isForbidden());
        }
        org.assertj.core.api.Assertions.assertThat(w.orgs.findCompany(a).orElseThrow().code()).isEqualTo(before);
    }

    @Test
    void workerRoutesCarryNoCodes() throws Exception {
        CompanyId a = newCompany();
        w.orgs.saveProject(new com.e2d.odin.domain.organization.Project(
                new com.e2d.odin.domain.shared.ProjectId(java.util.UUID.randomUUID()), a, "Obra", false));

        String body = mvc.perform(get("/v1/me").header("Authorization", workerKey(a))).andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[0].name").value("Obra"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContainIgnoringCase("code")
                .doesNotContain("restricted").doesNotContain("memberCount").doesNotContain("companyId");
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
                .content("{\"firstName\":\"Intruso\",\"lastName\":\"Gil\",\"roles\":[\"WORKER\"]}"))
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
        org.assertj.core.api.Assertions.assertThat(w.keys.listByMembership(member, a)).isEmpty();
    }

    @Test
    void otherCompanyAdminGets404RevokingAKeyOfA() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        String key = workerKey(a);
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        var keyId = w.keys.listByMembership(w.memberships.listByCompany(a).get(0).userId(), a).get(0).id();
        mvc.perform(delete("/v1/admin/keys/" + keyId.value()).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(delete("/v1/admin/keys/" + java.util.UUID.randomUUID()).cookie(other))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(w.keys.findById(keyId).orElseThrow().revokedAt()).isEmpty();
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

    @Test
    void otherCompanyAdminCannotManageProjectMembers() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        String project = Fixtures.project(w.orgs, a).value().toString();
        UserId worker = workerId(a);
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        String inA = "/v1/admin/companies/" + a.value() + "/projects/" + project + "/members";
        String inB = "/v1/admin/companies/" + b.value() + "/projects/" + project + "/members";
        mvc.perform(get(inA).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(put(inA + "/" + worker.value()).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(delete(inA + "/" + worker.value()).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(get(inB).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(put(inB + "/" + worker.value()).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(delete(inB + "/" + worker.value()).cookie(other)).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(w.projectMembers.listMembers(
                new com.e2d.odin.domain.shared.ProjectId(java.util.UUID.fromString(project)))).isEmpty();
    }

    @Test
    void otherCompanyAdminCannotListMemberProjects() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        UserId worker = workerId(a);
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        mvc.perform(get("/v1/admin/companies/" + a.value() + "/members/" + worker.value() + "/projects")
                .cookie(other)).andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/companies/" + b.value() + "/members/" + worker.value() + "/projects")
                .cookie(other)).andExpect(status().isNotFound());
    }

    @Test
    void companyAdminCannotManageGroupAdmins() throws Exception {
        CompanyId a = newCompany();
        var company = adminCookie(Role.COMPANY_ADMIN, Optional.of(a));
        UserId groupAdmin = People.groupAdmin(w.users, new UserId(java.util.UUID.randomUUID()), "Gema",
                Optional.of("gema-iso@e.test"), true, Optional.of("pw:x")).id();
        String body = "{\"firstName\":\"Gael\",\"lastName\":\"Ruiz\",\"email\":\"gael-iso@e.test\","
                + "\"password\":\"clave-muy-larga-1\"}";

        mvc.perform(get("/v1/admin/group-admins").cookie(company)).andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/group-admins").cookie(company).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden());
        mvc.perform(delete("/v1/admin/group-admins/" + groupAdmin.value()).cookie(company))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/v1/admin/users/" + groupAdmin.value()).cookie(company)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(w.users.findById(groupAdmin).orElseThrow())
                .satisfies(u -> org.assertj.core.api.Assertions.assertThat(u.groupAdmin() && u.active()).isTrue());
        org.assertj.core.api.Assertions.assertThat(w.users.findByEmail("gael-iso@e.test")).isEmpty();
    }
}
