package com.e2d.odin.adapter.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
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

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.application.usage.CardCounts;
import com.e2d.odin.application.usage.CardDecisions;
import com.e2d.odin.application.usage.CompanyUsage;
import com.e2d.odin.application.usage.DocumentCounts;
import com.e2d.odin.application.usage.GapGroup;
import com.e2d.odin.application.usage.GapReport;
import com.e2d.odin.application.usage.PersonUsage;
import com.e2d.odin.application.usage.QuestionCounts;
import com.e2d.odin.application.usage.TaggingCounts;
import com.e2d.odin.application.usage.WorkerTool;
import com.e2d.odin.domain.knowledge.QuestionKind;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
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
    void uploadAcceptsCaseDocumentField() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        mvc.perform(upload(company, admin, "es-un-caso".getBytes()).param("caseDocument", "true"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.document.caseDocument").value(true));
        mvc.perform(upload(company, admin, "no-es-caso".getBytes())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.caseDocument").value(false));
        // un duplicado ignora el valor nuevo
        mvc.perform(upload(company, admin, "es-un-caso".getBytes()).param("caseDocument", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.document.caseDocument").value(true));
    }

    @Test
    void patchDocumentMarksCase() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        Document d = Fixtures.readyDocument(w.docs, company, "caso");
        String url = "/v1/admin/documents/" + d.id().value();

        mvc.perform(patch(url).cookie(admin).contentType(MediaType.APPLICATION_JSON).content("{\"caseDocument\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(d.id().value().toString()))
                .andExpect(jsonPath("$.caseDocument").value(true));
        org.assertj.core.api.Assertions.assertThat(w.docs.get(d.id()).orElseThrow().caseDocument()).isTrue();
        mvc.perform(patch(url).cookie(admin).contentType(MediaType.APPLICATION_JSON).content("{\"caseDocument\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.caseDocument").value(false));
        mvc.perform(patch(url).cookie(admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/v1/admin/documents/" + UUID.randomUUID()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"caseDocument\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch(url).header("Authorization", workerKey(company)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseDocument\":true}")).andExpect(status().is4xxClientError());
    }

    @Test
    void caseMarksMarksBatch() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        Document one = Fixtures.readyDocument(w.docs, company, "uno");
        Document two = Fixtures.readyDocument(w.docs, company, "dos");
        String url = "/v1/admin/companies/" + company.value() + "/documents/case-marks";
        String ids = "\"documentIds\":[\"" + one.id().value() + "\",\"" + two.id().value() + "\"]";

        mvc.perform(post(url).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{" + ids + ",\"caseDocument\":true}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));
        org.assertj.core.api.Assertions.assertThat(w.docs.get(one.id()).orElseThrow().caseDocument()).isTrue();
        org.assertj.core.api.Assertions.assertThat(w.docs.get(two.id()).orElseThrow().caseDocument()).isTrue();
        mvc.perform(post(url).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{" + ids + ",\"caseDocument\":false}")).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(w.docs.get(one.id()).orElseThrow().caseDocument()).isFalse();
        mvc.perform(post(url).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[],\"caseDocument\":true}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Elige entre 1 y 500 documentos"));
        mvc.perform(post(url).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{" + ids + "}")).andExpect(status().isBadRequest());
        mvc.perform(post(url).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[null],\"caseDocument\":true}")).andExpect(status().isBadRequest());
        mvc.perform(post(url).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[\"" + UUID.randomUUID() + "\"],\"caseDocument\":true}"))
                .andExpect(status().isNotFound());
    }

    private String supersededBy(Document document) {
        return "/v1/admin/documents/" + document.id().value() + "/superseded-by";
    }

    private org.springframework.test.web.servlet.RequestBuilder supersede(Document old, Cookie admin, String body) {
        return put(supersededBy(old)).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String replacementJson(Document replacement) {
        return "{\"replacementId\":\"" + replacement.id().value() + "\"}";
    }

    @Test
    void putSupersededByReturnsTheDocumentView() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        Document old = Fixtures.readyDocument(w.docs, company, "póliza 2022");
        Document current = Fixtures.readyDocument(w.docs, company, "póliza 2023");
        mvc.perform(supersede(old, admin, replacementJson(current))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(old.id().value().toString()))
                .andExpect(jsonPath("$.supersededBy.id").value(current.id().value().toString()))
                .andExpect(jsonPath("$.supersededBy.title").value(current.title()))
                .andExpect(jsonPath("$.supersededAt").exists());
        org.assertj.core.api.Assertions.assertThat(w.docs.get(old.id()).orElseThrow().supersededBy())
                .contains(current.id());
    }

    @Test
    void deleteSupersededByIs204EvenIfNotMarked() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        Document old = Fixtures.readyDocument(w.docs, company, "póliza 2022");
        mvc.perform(delete(supersededBy(old)).cookie(admin)).andExpect(status().isNoContent());
        w.docs.markSuperseded(company, old.id(), Fixtures.readyDocument(w.docs, company, "nueva").id(),
                w.clock.instant());
        mvc.perform(delete(supersededBy(old)).cookie(admin)).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(w.docs.get(old.id()).orElseThrow().superseded()).isFalse();
    }

    @Test
    void supersedeWithoutOrWithBadReplacementIdIs400() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        Document old = Fixtures.readyDocument(w.docs, company, "póliza 2022");
        mvc.perform(supersede(old, admin, "{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Indica el documento que lo sustituye"));
        mvc.perform(supersede(old, admin, "{\"replacementId\":\"no-es-un-uuid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(supersede(old, admin, "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void supersedeConflictIs409WithItsMessage() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        Document old = Fixtures.readyDocument(w.docs, company, "póliza 2022");
        Document processing = Fixtures.document(w.docs, company, DocumentStatus.PROCESSING);
        mvc.perform(supersede(old, admin, replacementJson(processing))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "El documento que lo sustituye aún no está listo. Márcalo cuando termine de procesarse."));
    }

    @Test
    void supersedeWithAWorkerKeyIsRejected() throws Exception {
        CompanyId company = newCompany();
        Document old = Fixtures.readyDocument(w.docs, company, "póliza 2022");
        Document current = Fixtures.readyDocument(w.docs, company, "póliza 2023");
        mvc.perform(put(supersededBy(old)).header("Authorization", workerKey(company))
                .contentType(MediaType.APPLICATION_JSON).content(replacementJson(current)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void documentViewCarriesSupersededByAndAt() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        Document old = Fixtures.readyDocument(w.docs, company, "póliza 2022");
        Document current = Fixtures.readyDocument(w.docs, company, "póliza 2023");
        w.docs.markSuperseded(company, old.id(), current.id(), w.clock.instant());
        mvc.perform(get("/v1/admin/documents/" + old.id().value()).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.supersededBy.id").value(current.id().value().toString()))
                .andExpect(jsonPath("$.supersededAt").exists());
        mvc.perform(get("/v1/admin/documents/" + current.id().value()).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.supersededBy").doesNotExist())
                .andExpect(jsonPath("$.supersededAt").doesNotExist());
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/documents").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + old.id().value() + "')].supersededBy.title")
                        .value(current.title()));
    }

    @Test
    void meSaysWhetherScannedDocumentsAreSupported() throws Exception {
        CompanyId company = newCompany();
        mvc.perform(get("/v1/admin/me").cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(company))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scannedDocumentsSupported").value(true));
    }

    @Test
    void documentViewIncludesPagesWithoutText() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        Document mixed = Fixtures.document(w.docs, company, DocumentStatus.READY);
        w.docs.save(mixed.markReady(true, java.util.List.of(3, 5)));
        mvc.perform(get("/v1/admin/documents/" + mixed.id().value()).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pagesWithoutText[0]").value(3))
                .andExpect(jsonPath("$.pagesWithoutText[1]").value(5));
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
        var issued = mvc.perform(post("/v1/admin/companies/" + company.value() + "/members/" + worker.value() + "/keys").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"movil\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.key").value(startsWith("odin_")))
                .andExpect(jsonPath("$.prefix").isNotEmpty()).andReturn();
        String key = com.jayway.jsonpath.JsonPath.read(issued.getResponse().getContentAsString(), "$.key");
        String listing = mvc.perform(get("/v1/admin/companies/" + company.value() + "/members/" + worker.value() + "/keys").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").doesNotExist()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(listing).doesNotContain(key.substring(key.lastIndexOf('_') + 1))
                .doesNotContainIgnoringCase("secretHash").doesNotContainIgnoringCase("hash");
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + key)).andExpect(status().isOk());
    }

    @Test
    void addMemberReturns201AndListingHasNoHashes() throws Exception {
        CompanyId company = newCompany();
        Cookie group = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        var added = mvc.perform(post("/v1/admin/companies/" + company.value() + "/members").cookie(group)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Cora\",\"lastName\":\"Gil\",\"email\":\"cora@e.test\",\"roles\":[\"COMPANY_ADMIN\","
                        + "\"WORKER\"],\"password\":\"clave-muy-larga-1\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.alreadyExisted").value(false))
                .andExpect(jsonPath("$.member.displayName").value("Cora Gil"))
                .andExpect(jsonPath("$.member.firstName").value("Cora"))
                .andExpect(jsonPath("$.member.lastName").value("Gil"))
                .andExpect(jsonPath("$.member.personCode").value(matchesPattern("[A-Z0-9]{4}")))
                .andExpect(jsonPath("$.member.roles", containsInAnyOrder("COMPANY_ADMIN", "WORKER")))
                .andExpect(jsonPath("$.member.active").value(true)).andExpect(jsonPath("$.member.hasPassword").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        String personCode = com.jayway.jsonpath.JsonPath.read(added.getResponse().getContentAsString(),
                "$.member.personCode");
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/members").cookie(group))
                .andExpect(jsonPath("$[0].memberCode")
                        .value(personCode + "-" + w.orgs.findCompany(company).orElseThrow().code()));
        String listing = mvc.perform(get("/v1/admin/companies/" + company.value() + "/members").cookie(group))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").isNotEmpty()).andExpect(jsonPath("$[0].email").value("cora@e.test"))
                .andExpect(jsonPath("$[0].personCode").value(personCode)).andExpect(jsonPath("$[0].lastName").value("Gil"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(listing).doesNotContainIgnoringCase("hash")
                .doesNotContain("clave-muy-larga-1");
    }

    @Test
    void memberListSaysWhetherThePersonHasAPassword() throws Exception {
        CompanyId company = newCompany();
        UserId worker = workerId(company);
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/members").cookie(admin))
                .andExpect(jsonPath("$[?(@.userId=='" + worker.value() + "')].hasPassword").value(false));
    }

    @Test
    void addingAnExistingEmailReportsAlreadyExisted() throws Exception {
        CompanyId first = newCompany();
        CompanyId second = newCompany();
        companyAdmin(first, "dup@e.test");
        mvc.perform(post("/v1/admin/companies/" + second.value() + "/members")
                .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(second))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Otro\",\"lastName\":\"Nombre\",\"email\":\"dup@e.test\",\"roles\":[\"WORKER\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.alreadyExisted").value(true))
                .andExpect(jsonPath("$.member.displayName").value("Carlos Pérez"));
        mvc.perform(post("/v1/admin/companies/" + second.value() + "/members")
                .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(second))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Otro\",\"lastName\":\"Nombre\",\"email\":\"dup@e.test\",\"roles\":[\"WORKER\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esa persona ya pertenece a esta empresa"));
    }

    @Test
    void patchMemberChangesRolesAndActive() throws Exception {
        CompanyId company = newCompany();
        UserId worker = workerId(company);
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        mvc.perform(patch("/v1/admin/companies/" + company.value() + "/members/" + worker.value()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(worker.value().toString()))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("WORKER"))).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.memberCode").value(w.users.findById(worker).orElseThrow().personCode() + "-"
                        + w.orgs.findCompany(company).orElseThrow().code()));
        mvc.perform(patch("/v1/admin/companies/" + company.value() + "/members/" + worker.value())
                .cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true,\"roles\":[\"WORKER\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void companyAdminCannotDeactivateAColleaguesAdminMembership() throws Exception {
        CompanyId company = newCompany();
        User colleague = companyAdmin(company, "colega@e.test");
        String path = "/v1/admin/companies/" + company.value() + "/members/" + colleague.id().value();
        mvc.perform(patch(path).cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(company)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail")
                        .value("Solo el administrador del grupo da o quita la administración de una empresa"));
        mvc.perform(patch(path).cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
    }

    @Test
    void deleteMemberIs204AndKeepsThePerson() throws Exception {
        CompanyId company = newCompany();
        UserId worker = workerId(company);
        mvc.perform(delete("/v1/admin/companies/" + company.value() + "/members/" + worker.value())
                .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(company)))).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(w.memberships.find(worker, company)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(w.users.findById(worker)).isPresent();
    }

    @Test
    void adminMeListsTheCompaniesTheAdminAdministers() throws Exception {
        CompanyId first = newCompany();
        CompanyId second = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(first));
        UserId id = w.memberships.listByCompany(first).get(0).userId();
        w.memberships.save(new com.e2d.odin.domain.identity.Membership(id, second,
                java.util.Set.of(Role.COMPANY_ADMIN, Role.WORKER), true));

        mvc.perform(get("/v1/admin/me").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COMPANY_ADMIN"))
                .andExpect(jsonPath("$.companies", hasSize(2)))
                .andExpect(jsonPath("$.companies[*].id", containsInAnyOrder(first.value().toString(),
                        second.value().toString())))
                .andExpect(jsonPath("$.companies[0].name").isNotEmpty())
                .andExpect(jsonPath("$.companies[0].code").isNotEmpty())
                .andExpect(jsonPath("$.personCode").value(w.users.findById(id).orElseThrow().personCode()))
                .andExpect(jsonPath("$.firstName").value(w.users.findById(id).orElseThrow().firstName()))
                .andExpect(jsonPath("$.lastName").value(w.users.findById(id).orElseThrow().lastName().orElseThrow()))
                .andExpect(jsonPath("$.companyId").doesNotExist());
    }

    @Test
    void deactivatingUserBlocksTheirKey() throws Exception {
        CompanyId company = newCompany();
        String key = workerKey(company);
        UserId worker = w.memberships.listByCompany(company).get(0).userId();
        mvc.perform(patch("/v1/admin/users/" + worker.value()).cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(worker.value().toString()));
        mvc.perform(get("/v1/me").header("Authorization", key)).andExpect(status().isUnauthorized());
    }

    @Test
    void patchUserUpdatesFields() throws Exception {
        CompanyId company = newCompany();
        workerKey(company);
        UserId worker = w.memberships.listByCompany(company).get(0).userId();
        mvc.perform(patch("/v1/admin/users/" + worker.value()).cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Nuevo\",\"lastName\":\"Apellido\",\"email\":\"N@e.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(worker.value().toString()));
        org.assertj.core.api.Assertions.assertThat(w.users.findById(worker).orElseThrow())
                .satisfies(u -> {
                    org.assertj.core.api.Assertions.assertThat(u.displayName()).isEqualTo("Nuevo Apellido");
                    org.assertj.core.api.Assertions.assertThat(u.email()).contains("n@e.test");
                });
    }

    @Test
    void patchUserReactivates() throws Exception {
        CompanyId company = newCompany();
        workerKey(company);
        UserId worker = w.memberships.listByCompany(company).get(0).userId();
        Cookie group = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        mvc.perform(patch("/v1/admin/users/" + worker.value()).cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(w.users.findById(worker).orElseThrow().active()).isFalse();
        mvc.perform(patch("/v1/admin/users/" + worker.value()).cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}")).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(w.users.findById(worker).orElseThrow().active()).isTrue();
    }

    @Test
    void deleteUserIs204() throws Exception {
        CompanyId company = newCompany();
        String key = workerKey(company);
        UserId worker = w.memberships.listByCompany(company).get(0).userId();
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
        return People.member(w.users, w.memberships, new UserId(UUID.randomUUID()), "Carlos", Optional.of(email),
                Role.COMPANY_ADMIN, company, true, Optional.of(w.passwords.hash("clave-muy-larga-1")));
    }

    @Test
    void workerWithPasswordIs400() throws Exception {
        CompanyId company = newCompany();
        mvc.perform(post("/v1/admin/companies/" + company.value() + "/members")
                .cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Wanda\",\"lastName\":\"Gil\",\"roles\":[\"WORKER\"],"
                        + "\"password\":\"clave-muy-larga-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Un trabajador no tiene contraseña: entra con su clave personal"));
    }

    @Test
    void companyCodeIsValidatedAndUnique() throws Exception {
        Cookie group = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        String first = "{\"name\":\"Acme\",\"code\":\"ACM\"}";
        mvc.perform(post("/v1/admin/companies").cookie(group).contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());

        mvc.perform(post("/v1/admin/companies").cookie(group).contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una empresa con ese código"));
        mvc.perform(post("/v1/admin/companies").cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sin\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail")
                        .value("El código de la empresa tiene de 2 a 6 letras mayúsculas o cifras"));
    }

    @Test
    void missingNamesIs400() throws Exception {
        CompanyId company = newCompany();
        mvc.perform(post("/v1/admin/companies/" + company.value() + "/members")
                .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(company))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Wanda\",\"roles\":[\"WORKER\"]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Faltan el nombre o los apellidos"));
        org.assertj.core.api.Assertions.assertThat(w.memberships.listByCompany(company)).hasSize(1);
    }

    @Test
    void personCodeInARequestIsIgnored() throws Exception {
        CompanyId company = newCompany();
        UserId worker = workerId(company);
        String code = w.users.findById(worker).orElseThrow().personCode();

        mvc.perform(patch("/v1/admin/users/" + worker.value()).cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Otra\",\"personCode\":\"AAAA\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/admin/companies/" + company.value() + "/members")
                .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(company))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Nuevo\",\"lastName\":\"Gil\",\"personCode\":\"AAAA\","
                        + "\"roles\":[\"WORKER\"]}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.member.personCode").value(org.hamcrest.Matchers.not("AAAA")));
        org.assertj.core.api.Assertions.assertThat(w.users.findById(worker).orElseThrow().personCode()).isEqualTo(code);
    }

    @Test
    void companyLifecycleAndCardReview() throws Exception {
        Cookie group = adminCookie(Role.GROUP_ADMIN, Optional.empty());
        var created = mvc.perform(post("/v1/admin/companies").cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme\",\"code\":\"ACM\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ACM")).andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(patch("/v1/admin/companies/" + id).cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme 2\"}")).andExpect(jsonPath("$.name").value("Acme 2"))
                .andExpect(jsonPath("$.code").value("ACM"));
        mvc.perform(patch("/v1/admin/companies/" + id).cookie(group).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ACME2\"}")).andExpect(jsonPath("$.code").value("ACME2"))
                .andExpect(jsonPath("$.name").value("Acme 2"));
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

    private String createTopic(Cookie admin, CompanyId company, String name) throws Exception {
        var created = mvc.perform(post("/v1/admin/companies/" + company.value() + "/topics").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"definition\":\"Todo lo de " + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void topicsAreCreatedListedPatchedAndDeleted() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String id = createTopic(admin, company, "Agua");
        String base = "/v1/admin/companies/" + company.value() + "/topics";

        mvc.perform(get(base).cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$[0].name").value("Agua"))
                .andExpect(jsonPath("$[0].definition").value("Todo lo de Agua"));
        mvc.perform(patch(base + "/" + id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"definition\":\"Fugas y humedades\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Agua"))
                .andExpect(jsonPath("$.definition").value("Fugas y humedades"));
        mvc.perform(delete(base + "/" + id).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get(base).cookie(admin)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete(base + "/" + id).cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateTopicNameIs409() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        createTopic(admin, company, "Agua");

        mvc.perform(post("/v1/admin/companies/" + company.value() + "/topics").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"agua\",\"definition\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Ya hay un tema con ese nombre"));
    }

    @Test
    void topicNameContainingAnotherIs400() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        createTopic(admin, company, "Agua");

        mvc.perform(post("/v1/admin/companies/" + company.value() + "/topics").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Agua y humedades\",\"definition\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Un nombre de tema no puede contener a otro: Agua"));
    }

    @Test
    void taggingStatusViewHasWindowShape() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        createTopic(admin, company, "Agua");
        Document pending = Fixtures.readyDocument(w.docs, company, "uno");
        String url = "/v1/admin/companies/" + company.value() + "/tagging-status";

        mvc.perform(get(url).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingDocuments").value(1))
                .andExpect(jsonPath("$.oldestPendingSince").value(pending.uploadedAt().toString()))
                .andExpect(jsonPath("$.window.from").value("22:00")).andExpect(jsonPath("$.window.to").value("07:00"))
                .andExpect(jsonPath("$.window.always").value(false))
                .andExpect(jsonPath("$.timezone").value("Europe/Madrid"));
        mvc.perform(get(url).cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(newCompany()))))
                .andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", workerKey(company))).andExpect(status().isUnauthorized());
    }

    @Test
    void documentViewIncludesTopicsAndTaggingStatus() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String water = createTopic(admin, company, "Agua");
        Document tagged = Fixtures.readyDocument(w.docs, company, "uno");
        w.tagging.seed(tagged.id(), new com.e2d.odin.domain.topic.TopicId(UUID.fromString(water)),
                com.e2d.odin.domain.topic.TopicSource.AI, com.e2d.odin.domain.topic.TopicState.ACTIVE);
        w.tagging.seedRow(tagged, com.e2d.odin.domain.topic.TaggingStatus.DONE);

        mvc.perform(get("/v1/admin/companies/" + company.value() + "/documents").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].taggingStatus").value("DONE"))
                .andExpect(jsonPath("$[0].topics[0].id").value(water))
                .andExpect(jsonPath("$[0].topics[0].name").value("Agua"))
                .andExpect(jsonPath("$[0].topics[0].source").value("AI"));
    }

    @Test
    void documentTopicsAreReviewedListedAndRetagged() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String water = createTopic(admin, company, "Agua");
        Document document = Fixtures.readyDocument(w.docs, company, "uno");
        w.tagging.seedRow(document, com.e2d.odin.domain.topic.TaggingStatus.DONE);
        String base = "/v1/admin/documents/" + document.id().value() + "/topics";

        mvc.perform(put(base + "/" + water).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get(base).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0].topicId").value(water))
                .andExpect(jsonPath("$.topics[0].name").value("Agua"))
                .andExpect(jsonPath("$.topics[0].source").value("ADMIN"))
                .andExpect(jsonPath("$.topics[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$.topics[0].decidedByName").value("Admin Pérez"))
                .andExpect(jsonPath("$.tagging.status").value("DONE"))
                .andExpect(jsonPath("$.tagging.topicListVersion").value(1));
        mvc.perform(delete(base + "/" + water).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get(base).cookie(admin)).andExpect(jsonPath("$.topics[0].state").value("REMOVED"));
        mvc.perform(post(base + "/retag").cookie(admin)).andExpect(status().isAccepted());
        mvc.perform(get(base).cookie(admin)).andExpect(jsonPath("$.tagging").doesNotExist())
                .andExpect(jsonPath("$.topics[0].state").value("REMOVED"));
        mvc.perform(put(base + "/" + UUID.randomUUID()).cookie(admin)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Tema no encontrado"));
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload(
            CompanyId company, Cookie admin, byte[] bytes) {
        return multipart("/v1/admin/companies/" + company.value() + "/documents")
                .file(new MockMultipartFile("file", "manual.pdf", "application/pdf", bytes))
                .param("title", "Manual").cookie(admin);
    }

    @Test
    void newProjectIsClosedAndPatchTogglesCompanyWide() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String id = createProject(admin, company, "Obra");
        String url = "/v1/admin/companies/" + company.value() + "/projects/" + id;
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/projects").cookie(admin))
                .andExpect(jsonPath("$[0].restricted").value(true));
        mvc.perform(patch(url).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"restricted\":false}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.restricted").value(false)).andExpect(jsonPath("$.name").value("Obra"));
        mvc.perform(patch(url).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"restricted\":true,\"name\":\"Reforma\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.restricted").value(true)).andExpect(jsonPath("$.name").value("Reforma"));
    }

    @Test
    void projectListCarriesTheMemberCount() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String obra = createProject(admin, company, "Obra");
        createProject(admin, company, "Otra");
        mvc.perform(put("/v1/admin/companies/" + company.value() + "/projects/" + obra + "/members/"
                + workerId(company).value()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/projects").cookie(admin))
                .andExpect(jsonPath("$[0].memberCount").value(1)).andExpect(jsonPath("$[1].memberCount").value(0));
    }

    @Test
    void projectMemberRoutes() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String project = createProject(admin, company, "Obra");
        UserId worker = workerId(company);
        String members = "/v1/admin/companies/" + company.value() + "/projects/" + project + "/members";
        mvc.perform(get(members).cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(put(members + "/" + worker.value()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(put(members + "/" + worker.value()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get(members).cookie(admin)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(worker.value().toString()))
                .andExpect(jsonPath("$[0].displayName").value("Wilma Pérez"));
        mvc.perform(delete(members + "/" + worker.value()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get(members).cookie(admin)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void onlyWorkersCanBeProjectMembers() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String project = createProject(admin, company, "Obra");
        mvc.perform(put("/v1/admin/companies/" + company.value() + "/projects/" + project + "/members/"
                + UUID.randomUUID()).cookie(admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Solo un trabajador de la empresa puede ser miembro"));
    }

    @Test
    void memberProjectsListsTheProjectsThePersonBelongsTo() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String obra = createProject(admin, company, "Obra");
        createProject(admin, company, "Otra");
        UserId worker = workerId(company);
        mvc.perform(put("/v1/admin/companies/" + company.value() + "/projects/" + obra + "/members/"
                + worker.value()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/members/" + worker.value() + "/projects")
                .cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(obra)).andExpect(jsonPath("$[0].restricted").value(true));
    }

    @Test
    void addMemberAcceptsProjectIds() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        String obra = createProject(admin, company, "Obra");
        String url = "/v1/admin/companies/" + company.value() + "/members";
        mvc.perform(post(url).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(
                "{\"firstName\":\"Pepa\",\"lastName\":\"Gil\",\"roles\":[\"WORKER\"],\"projectIds\":[\"" + obra + "\"]}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/projects/" + obra + "/members").cookie(admin))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].displayName").value("Pepa Gil"));
        mvc.perform(post(url).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(
                "{\"firstName\":\"Pepe\",\"lastName\":\"Gil\",\"roles\":[\"WORKER\"],\"projectIds\":[\"" + UUID.randomUUID()
                        + "\"]}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Proyecto no encontrado"));
    }
    private CompanyUsage someUsage() {
        return new CompanyUsage(new QuestionCounts(5, 2), 1, new CardCounts(3, 0, Optional.of(new CardDecisions(1, 1, 1)), false),
                new DocumentCounts(4, 3, 1, 0), Optional.of(new TaggingCounts(2, 1)));
    }

    @Test
    void usageStatsRoute() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        UserId ana = new UserId(UUID.randomUUID());
        w.usage.seed(company, someUsage(), List.of(
                new PersonUsage(Optional.of(ana), "Ana Pérez", Optional.of("7F3K-E2D"), 4, 1, false),
                PersonUsage.departed(3, 2)));
        String url = "/v1/admin/companies/" + company.value() + "/usage-stats";

        mvc.perform(get(url).cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.month").value("2026-11"))
                .andExpect(jsonPath("$.company.questions.search").value(5))
                .andExpect(jsonPath("$.company.questions.similarCases").value(2))
                .andExpect(jsonPath("$.company.questionsWithoutResults").value(1))
                .andExpect(jsonPath("$.company.cards.corrected").value(1))
                .andExpect(jsonPath("$.company.documents.waitingForReader").value(0))
                .andExpect(jsonPath("$.company.tagging.pending").value(1))
                .andExpect(jsonPath("$.people[0].userId").value(ana.value().toString()))
                .andExpect(jsonPath("$.people[0].displayName").value("Ana Pérez"))
                .andExpect(jsonPath("$.people[0].memberCode").value("7F3K-E2D"))
                .andExpect(jsonPath("$.people[0].questions").value(4))
                .andExpect(jsonPath("$.people[0].cardsSaved").value(1))
                .andExpect(jsonPath("$.people[1].displayName").value("Personas dadas de baja"))
                .andExpect(jsonPath("$.people[1].userId").doesNotExist())
                .andExpect(jsonPath("$.people[1].memberCode").doesNotExist());
        mvc.perform(get(url + "?month=2026-10").cookie(admin)).andExpect(jsonPath("$.month").value("2026-10"));
        mvc.perform(get(url).header("Authorization", workerKey(company))).andExpect(status().isUnauthorized());
    }

    @Test
    void usageStatsAndGapsRejectBadMonths() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        for (String route : new String[] {"usage-stats", "gaps"}) {
            String url = "/v1/admin/companies/" + company.value() + "/" + route;
            mvc.perform(get(url + "?month=2026-13").cookie(admin)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("El mes tiene que tener la forma AAAA-MM"));
            mvc.perform(get(url + "?month=2024-01").cookie(admin)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Solo se pueden consultar los 12 últimos meses"));
        }
    }

    @Test
    void gapsRoute() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        w.usage.seedGaps(company, List.of(new GapGroup("goteras", QuestionKind.SIMILAR_CASES, 3,
                LocalDate.of(2026, 11, 8))));
        String url = "/v1/admin/companies/" + company.value() + "/gaps";

        mvc.perform(get(url).cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.gaps", hasSize(1)))
                .andExpect(jsonPath("$.withheld").value(false)).andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.gaps[0].text").value("goteras"))
                .andExpect(jsonPath("$.gaps[0].kind").value("SIMILAR_CASES"))
                .andExpect(jsonPath("$.gaps[0].times").value(3));
        mvc.perform(get(url).header("Authorization", workerKey(company))).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/admin/companies/" + UUID.randomUUID() + "/gaps").cookie(adminCookie(Role.GROUP_ADMIN,
                Optional.empty()))).andExpect(status().isNotFound());
    }

    @Test
    void withheldGapsAndCardBreakdownCarryTheIndicatorAndNoData() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        w.usage.seedGapReport(company, new GapReport(List.of(), true));
        w.usage.seed(company, new CompanyUsage(new QuestionCounts(1, 0), 1,
                new CardCounts(2, 1, Optional.empty(), true), new DocumentCounts(0, 0, 0, 0), Optional.empty()),
                List.of());

        mvc.perform(get("/v1/admin/companies/" + company.value() + "/gaps").cookie(admin))
                .andExpect(jsonPath("$.withheld").value(true)).andExpect(jsonPath("$.reason").value("FEW_PEOPLE"))
                .andExpect(jsonPath("$.gaps", hasSize(0)));
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/usage-stats").cookie(admin))
                .andExpect(jsonPath("$.company.cards.saved").value(2))
                .andExpect(jsonPath("$.company.cards.withheld").value(true))
                .andExpect(jsonPath("$.company.cards.reason").value("FEW_PEOPLE"))
                .andExpect(jsonPath("$.company.cards.approved").doesNotExist())
                .andExpect(jsonPath("$.company.cards.corrected").doesNotExist())
                .andExpect(jsonPath("$.company.cards.rejected").doesNotExist());
    }

    @Test
    void gapViewHasDateWithoutTime() throws Exception {
        CompanyId company = newCompany();
        w.usage.seedGaps(company, List.of(new GapGroup("goteras", QuestionKind.SEARCH, 1, LocalDate.of(2026, 11, 8))));

        mvc.perform(get("/v1/admin/companies/" + company.value() + "/gaps")
                .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(company))))
                .andExpect(jsonPath("$.gaps[0].lastAskedOn").value("2026-11-08"))
                .andExpect(jsonPath("$.gaps[0].authorId").doesNotExist())
                .andExpect(jsonPath("$.gaps[0].userId").doesNotExist())
                .andExpect(jsonPath("$.gaps[0].askedAt").doesNotExist());
    }
    @Test
    void deliveredVolumeRoute() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        w.meter.record(company, WorkerTool.SEARCH, 120, w.clock.instant());
        w.meter.record(company, WorkerTool.SEARCH, 30, w.clock.instant());
        String url = "/v1/admin/companies/" + company.value() + "/delivered-volume";

        mvc.perform(get(url).cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.month").value("2026-11"))
                .andExpect(jsonPath("$.totals", hasSize(WorkerTool.values().length)))
                .andExpect(jsonPath("$.totals[?(@.tool == 'SEARCH')].calls").value(2))
                .andExpect(jsonPath("$.totals[?(@.tool == 'SEARCH')].bytes").value(150))
                .andExpect(jsonPath("$.totals[?(@.tool == 'MY_CARDS')].bytes").value(0))
                .andExpect(jsonPath("$.totals[0].userId").doesNotExist());
        mvc.perform(get(url + "?month=2024-01").cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(get(url).header("Authorization", workerKey(company))).andExpect(status().isUnauthorized());
    }
}
