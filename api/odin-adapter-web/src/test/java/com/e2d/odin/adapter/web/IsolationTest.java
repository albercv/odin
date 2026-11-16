package com.e2d.odin.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.usage.CardCounts;
import com.e2d.odin.application.usage.CompanyUsage;
import com.e2d.odin.application.usage.DocumentCounts;
import com.e2d.odin.application.usage.GapGroup;
import com.e2d.odin.application.usage.QuestionCounts;
import com.e2d.odin.application.usage.WorkerTool;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.knowledge.CardStatus;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.knowledge.QuestionRecord;
import com.e2d.odin.domain.shared.CardId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class IsolationTest extends WebTestSupport {

    @Test
    void keyOfOtherPersonNeverListsSomeoneElsesCardsOrQuestions() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        UserId author = workerId(a);
        joinAsWorker(author, b);
        w.cards.save(new KnowledgeCard(new CardId(UUID.randomUUID()), a, Optional.empty(), "Secreta", "texto",
                Optional.empty(), author, w.clock.instant(), CardStatus.PENDING, Optional.empty()));
        w.questions.record(new QuestionRecord(UUID.randomUUID(), a, author, "pregunta secreta", 0, w.clock.instant(),
                QuestionKind.SEARCH));
        String own = keyOf(author, a);
        String colleague = workerKey(a);
        String sameAuthorInOtherCompany = keyOf(author, b);

        mvc.perform(get("/v1/me/cards").header("Authorization", own)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/v1/me/questions").header("Authorization", own)).andExpect(jsonPath("$", hasSize(1)));
        for (String key : new String[] {colleague, sameAuthorInOtherCompany, workerKey(b)}) {
            mvc.perform(get("/v1/me/cards").header("Authorization", key)).andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(get("/v1/me/questions").header("Authorization", key)).andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Test
    void adminSessionNeverListsCardsOrQuestionsOfAWorker() throws Exception {
        CompanyId a = newCompany();
        var admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(a));

        mvc.perform(get("/v1/me/cards").cookie(admin)).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/me/questions").cookie(admin)).andExpect(status().isUnauthorized());
    }

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
    void otherCompanyAdminCannotUnlinkAForeignScanCopy() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document main = Fixtures.readyDocument(w.docs, a, "principal de A");
        Document scan = Fixtures.readyDocument(w.docs, a, "escaneo de A");
        w.docs.markScanOf(a, scan.id(), main.id());
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));

        mvc.perform(delete("/v1/admin/documents/" + scan.id().value() + "/scan-of").cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/admin/documents/" + main.id().value()).cookie(other)).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(w.docs.get(scan.id()).orElseThrow().scanOf()).isPresent();
    }

    @Test
    void otherCompanyAdminCannotMarkForeignDocument() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document ofA = Fixtures.readyDocument(w.docs, a, "caso de A");
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));

        mvc.perform(patch("/v1/admin/documents/" + ofA.id().value()).cookie(other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"caseDocument\":true}"))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(w.docs.get(ofA.id()).orElseThrow().caseDocument()).isFalse();
    }

    @Test
    void otherCompanyAdminCannotMarkForeignBatch() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        Document ofA = Fixtures.readyDocument(w.docs, a, "caso de A");
        Document ofB = Fixtures.readyDocument(w.docs, b, "caso de B");
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        String body = "{\"documentIds\":[\"" + ofA.id().value() + "\"],\"caseDocument\":true}";

        // por la ruta de la empresa ajena: 403
        mvc.perform(post("/v1/admin/companies/" + a.value() + "/documents/case-marks").cookie(other)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        // por la propia empresa pero con un documento ajeno en el lote: 404 y no cambia ninguno
        String mixed = "{\"documentIds\":[\"" + ofA.id().value() + "\",\"" + ofB.id().value()
                + "\"],\"caseDocument\":true}";
        mvc.perform(post("/v1/admin/companies/" + b.value() + "/documents/case-marks").cookie(other)
                .contentType(MediaType.APPLICATION_JSON).content(mixed)).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(w.docs.get(ofA.id()).orElseThrow().caseDocument()).isFalse();
        org.assertj.core.api.Assertions.assertThat(w.docs.get(ofB.id()).orElseThrow().caseDocument()).isFalse();
    }

    @Test
    void workerOfBNeverGetsCasesOfA() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        var closedOfA = Fixtures.project(w.orgs, a);
        var key = workerKey(b);

        mvc.perform(post("/v1/similar-cases").header("Authorization", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"filtración\"]}")).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(w.search.lastSimilarCasesQuery().scope())
                .isEqualTo(com.e2d.odin.domain.shared.CompanyScope.of(b));
        org.assertj.core.api.Assertions.assertThat(w.search.lastSimilarCasesQuery().visibility().includes(closedOfA))
                .isFalse();
        mvc.perform(post("/v1/similar-cases").header("Authorization", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"filtración\"],\"projectId\":\"" + closedOfA.value() + "\"}"))
                .andExpect(status().isNotFound());
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
    void projectLabelOfAIsInvisibleToBAndOnlyTheGroupAdminChangesIt() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        String body = "{\"projectLabel\":{\"singular\":\"Comunidad\",\"plural\":\"Comunidades\",\"feminine\":true}}";

        for (CompanyId own : new CompanyId[] {a, b}) {
            mvc.perform(patch("/v1/admin/companies/" + a.value())
                    .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(own))).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/v1/me").header("Authorization", workerKey(a)))
                .andExpect(jsonPath("$.company.projectLabel.singular").value("proyecto"));

        mvc.perform(patch("/v1/admin/companies/" + a.value()).cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.projectLabel.plural").value("comunidades"));
        mvc.perform(get("/v1/me").header("Authorization", workerKey(a)))
                .andExpect(jsonPath("$.company.projectLabel.singular").value("comunidad"))
                .andExpect(jsonPath("$.company.projectLabel.feminine").value(true));
        mvc.perform(get("/v1/me").header("Authorization", workerKey(b)))
                .andExpect(jsonPath("$.company.projectLabel.singular").value("proyecto"))
                .andExpect(jsonPath("$.company.projectLabel.plural").value("proyectos"))
                .andExpect(jsonPath("$.company.projectLabel.feminine").value(false));
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

    private String createTopic(jakarta.servlet.http.Cookie admin, CompanyId company, String name) throws Exception {
        var created = mvc.perform(post("/v1/admin/companies/" + company.value() + "/topics").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"definition\":\"x\"}"))
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void workerOfBGets404ListingDocumentsByATopicOfA() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        String topicOfA = createTopic(adminCookie(Role.COMPANY_ADMIN, Optional.of(a)), a, "Agua");
        Fixtures.readyDocument(w.docs, a, "secreto de A");

        mvc.perform(get("/v1/documents?topicId=" + topicOfA).header("Authorization", workerKey(b)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Tema no encontrado"));
    }

    @Test
    void meOfWorkerOfBCarriesNeitherTopicsNorPendingCountOfA() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        createTopic(adminCookie(Role.COMPANY_ADMIN, Optional.of(a)), a, "Agua");
        Fixtures.readyDocument(w.docs, a, "secreto de A");
        Fixtures.readyDocument(w.docs, a, "otro de A");

        mvc.perform(get("/v1/me").header("Authorization", workerKey(b))).andExpect(status().isOk())
                .andExpect(jsonPath("$.topics.length()").value(0))
                .andExpect(jsonPath("$.tagging.pendingDocuments").value(0));
    }

    @Test
    void adminOfACannotAssignATopicOfBToADocumentOfA() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        var adminOfA = adminCookie(Role.COMPANY_ADMIN, Optional.of(a));
        String topicOfB = createTopic(adminCookie(Role.COMPANY_ADMIN, Optional.of(b)), b, "Seguros");
        Document own = Fixtures.readyDocument(w.docs, a, "de A");
        String base = "/v1/admin/documents/" + own.id().value() + "/topics";

        mvc.perform(put(base + "/" + topicOfB).cookie(adminOfA)).andExpect(status().isNotFound());
        mvc.perform(get(base).cookie(adminOfA)).andExpect(jsonPath("$.topics.length()").value(0));
    }
    @Test
    void otherCompanyAdminCannotReadUsageOrGaps() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        w.usage.seed(a, new CompanyUsage(new QuestionCounts(7, 0), 0, new CardCounts(0, 0, Optional.empty(), false),
                new DocumentCounts(0, 0, 0, 0), Optional.empty()), List.of());
        w.usage.seed(b, new CompanyUsage(new QuestionCounts(1, 0), 0, new CardCounts(0, 0, Optional.empty(), false),
                new DocumentCounts(0, 0, 0, 0), Optional.empty()), List.of());
        w.usage.seedGaps(a, List.of(new GapGroup("secreto de A", QuestionKind.SEARCH, 1, LocalDate.of(2026, 11, 1))));
        var ownerOfA = adminCookie(Role.COMPANY_ADMIN, Optional.of(a));
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        var group = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        String stats = "/v1/admin/companies/" + a.value() + "/usage-stats";
        String gaps = "/v1/admin/companies/" + a.value() + "/gaps";

        mvc.perform(get(stats).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(get(gaps).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(get(stats).cookie(ownerOfA)).andExpect(jsonPath("$.company.questions.search").value(7));
        mvc.perform(get(stats).cookie(group)).andExpect(jsonPath("$.company.questions.search").value(7));
        mvc.perform(get("/v1/admin/companies/" + b.value() + "/usage-stats").cookie(group))
                .andExpect(jsonPath("$.company.questions.search").value(1));
        mvc.perform(get("/v1/admin/companies/" + b.value() + "/gaps").cookie(group)).andExpect(jsonPath("$.gaps", hasSize(0)));
        mvc.perform(get(gaps).cookie(ownerOfA)).andExpect(jsonPath("$.gaps", hasSize(1)))
                .andExpect(jsonPath("$.gaps[0].text").value("secreto de A"));
        mvc.perform(get(gaps).cookie(group)).andExpect(jsonPath("$.gaps", hasSize(1)));
        for (String route : new String[] {"usage-stats", "gaps", "delivered-volume"}) {
            mvc.perform(get("/v1/admin/companies/" + a.value() + "/" + route).header("Authorization", workerKey(a)))
                    .andExpect(status().isUnauthorized());
        }
    }
    @Test
    void otherCompanyAdminCannotReadDeliveredVolume() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        w.meter.record(a, WorkerTool.SEARCH, 500, w.clock.instant());
        w.meter.record(b, WorkerTool.SEARCH, 7, w.clock.instant());
        String urlOfA = "/v1/admin/companies/" + a.value() + "/delivered-volume";
        var group = adminCookie(Role.GROUP_ADMIN, Optional.empty());

        mvc.perform(get(urlOfA).cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(b)))).andExpect(status().isForbidden());
        mvc.perform(get(urlOfA).cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(a))))
                .andExpect(jsonPath("$.totals[?(@.tool == 'SEARCH')].bytes").value(500));
        mvc.perform(get(urlOfA).cookie(group)).andExpect(jsonPath("$.totals[?(@.tool == 'SEARCH')].bytes").value(500));
        mvc.perform(get("/v1/admin/companies/" + b.value() + "/delivered-volume").cookie(group))
                .andExpect(jsonPath("$.totals[?(@.tool == 'SEARCH')].bytes").value(7));
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

    @Test
    void keyOfOtherCompanyCannotIssueAProjectUploadLink() throws Exception {
        CompanyId a = newCompany();
        var projectOfA = Fixtures.companyWideProject(w.orgs, a);
        mvc.perform(post("/v1/projects/" + projectOfA.value() + "/upload-links")
                .header("Authorization", workerKey(newCompany()))).andExpect(status().isNotFound());
    }
}
