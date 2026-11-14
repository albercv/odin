package com.e2d.odin.adapter.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class IntakeControllerTest extends WebTestSupport {

    private CompanyId company;
    private Cookie admin;
    private String batches;
    private String batch;

    @BeforeEach
    void openBatch() throws Exception {
        company = newCompany();
        admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
        batches = "/v1/admin/companies/" + company.value() + "/intake-batches";
        String body = mvc.perform(post(batches).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"PANEL\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("OPEN")).andExpect(jsonPath("$.uploaded").value(0))
                .andReturn().getResponse().getContentAsString();
        batch = batches + "/" + com.jayway.jsonpath.JsonPath.<String>read(body, "$.id");
    }

    private MockMultipartHttpServletRequestBuilder file(String content, Cookie as) {
        return multipart(batch + "/files").file(new MockMultipartFile("file", "a.pdf", "application/pdf",
                content.getBytes())).param("path", "Cliente/2019/a.pdf").cookie(as);
    }

    @Test
    void uploadIsResumableAndTheHashQueryAnswersWhatIsThere() throws Exception {
        String sha = mvc.perform(file("uno", admin)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("CREATED"))
                .andReturn().getResponse().getContentAsString();
        String known = com.jayway.jsonpath.JsonPath.<String>read(sha, "$.document.id");

        mvc.perform(file("uno", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("ALREADY_THERE"))
                .andExpect(jsonPath("$.document.id").value(known));
        mvc.perform(get(batch).cookie(admin)).andExpect(jsonPath("$.uploaded").value(1))
                .andExpect(jsonPath("$.pending").value(1)).andExpect(jsonPath("$.alreadyThere").value(1))
                .andExpect(jsonPath("$.discarded").value(0))
                // la prueba tiene lector pero no modelo local
                .andExpect(jsonPath("$.warnings", hasSize(1))).andExpect(jsonPath("$.warnings[0].code").value("NO_LOCAL_MODEL"));
        String digest = w.docs.all().stream().filter(d -> d.companyId().equals(company)).findFirst().orElseThrow()
                .sha256();
        mvc.perform(get(batch + "/files").param("sha256", digest, "otra").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sha256", hasSize(1))).andExpect(jsonPath("$.sha256", hasItem(digest)));
    }

    @Test
    void theOpenBatchesOfTheCompanyAreListedAndOnlyForItsAdmin() throws Exception {
        String id = batch.substring(batch.lastIndexOf('/') + 1);
        mvc.perform(get(batches).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.batches", hasSize(1))).andExpect(jsonPath("$.batches[0].id").value(id))
                .andExpect(jsonPath("$.batches[0].state").value("OPEN"))
                .andExpect(jsonPath("$.batches[0].channel").value("PANEL"))
                .andExpect(jsonPath("$.batches[0].createdAt").exists());

        Cookie other = adminCookie(Role.COMPANY_ADMIN, Optional.of(newCompany()));
        mvc.perform(get(batches).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(get(batches).header("Authorization", workerKey(company))).andExpect(status().isUnauthorized());
        mvc.perform(get(batches)).andExpect(status().isUnauthorized());
    }

    @Test
    void aCompanyWithoutOpenBatchesListsNone() throws Exception {
        CompanyId empty = newCompany();
        mvc.perform(get("/v1/admin/companies/" + empty.value() + "/intake-batches")
                .cookie(adminCookie(Role.COMPANY_ADMIN, Optional.of(empty)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.batches", hasSize(0)));
    }

    @Test
    void closingMovesTheBatchToReading() throws Exception {
        mvc.perform(file("uno", admin)).andExpect(status().isCreated());

        mvc.perform(post(batch + "/close-upload").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READING")).andExpect(jsonPath("$.uploaded").value(1));
        mvc.perform(get(batch).cookie(admin)).andExpect(jsonPath("$.warnings[*].code", hasItem("PROJECT_LIST_NOT_CONFIRMED")));
        mvc.perform(file("dos", admin)).andExpect(status().isConflict());
    }

    @Test
    void invalidChannelIs400() throws Exception {
        mvc.perform(post("/v1/admin/companies/" + newCompany().value() + "/intake-batches")
                .cookie(adminCookie(Role.GROUP_ADMIN, Optional.empty())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"OTRO\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void anotherCompanyAdminAndAWorkerKeyAreRefused() throws Exception {
        CompanyId otherCompany = newCompany();
        Cookie other = adminCookie(Role.COMPANY_ADMIN, Optional.of(otherCompany));
        String id = batch.substring(batch.lastIndexOf('/') + 1);
        String ownPathOtherBatch = "/v1/admin/companies/" + otherCompany.value() + "/intake-batches/" + id;

        // el lote de otra empresa, pedido con la propia, no existe: 404
        mvc.perform(get(ownPathOtherBatch).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(post(ownPathOtherBatch + "/close-upload").cookie(other)).andExpect(status().isNotFound());
        // con la empresa del lote, la política de acceso lo rechaza (como en el resto de rutas de empresa): 403
        mvc.perform(get(batch).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(file("uno", other)).andExpect(status().isForbidden());
        // un trabajador solo tiene clave de acceso, y las rutas de administración no la aceptan (el 403 del caso de
        // uso lo cubre IntakeUploadTest)
        String worker = workerKey(company);
        mvc.perform(get(batch).header("Authorization", worker)).andExpect(status().isUnauthorized());
        mvc.perform(post(batches).header("Authorization", worker).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"PANEL\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get(batch)).andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(w.docs.all()).noneMatch(d -> d.companyId().equals(company));
    }

    private MockMultipartHttpServletRequestBuilder list(byte[] bytes, Cookie as) {
        return multipart(batch + "/project-candidates/import")
                .file(new MockMultipartFile("file", "proyectos.csv", "text/csv", bytes)).cookie(as);
    }

    @Test
    void theProjectListIsImportedListedAndConfirmedWithNoCreationWhenMerging() throws Exception {
        String candidates = batch + "/project-candidates";
        mvc.perform(list("Calle Olmo 24\n\nCALLE OLMO 24\nCalle Haya 2\n".getBytes(), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates", hasSize(2))).andExpect(jsonPath("$.candidates[0].origin").value("IMPORTED"));
        mvc.perform(get(candidates).cookie(admin)).andExpect(jsonPath("$.candidates", hasSize(2)))
                .andExpect(jsonPath("$.candidates[0].state").value("PROPOSED"));

        mvc.perform(post(candidates + "/confirm").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisions\":[{\"name\":\"Calle Olmo 24\",\"decision\":\"CREATE\"},"
                        + "{\"name\":\"Calle Haya 2\",\"decision\":\"REJECT\"}]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[?(@.name=='Calle Olmo 24')].state").value("CONFIRMED"))
                .andExpect(jsonPath("$.candidates[?(@.name=='Calle Haya 2')].state").value("REJECTED"));
        org.assertj.core.api.Assertions.assertThat(w.orgs.listProjects(company)).hasSize(1);
        mvc.perform(post(candidates + "/confirm").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisions\":[{\"name\":\"Calle Olmo 24\",\"decision\":\"REJECT\"}]}"))
                .andExpect(status().isConflict());
        mvc.perform(post(candidates + "/confirm").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisions\":[{\"name\":\"Calle Olmo 24\",\"decision\":\"OTRA\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(candidates + "/confirm").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisions\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(post(candidates + "/discover").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates", hasSize(2)));
    }

    @Test
    void anAdminOfAnotherCompanyAndAWorkerCannotUseTheProjectList() throws Exception {
        CompanyId otherCompany = newCompany();
        Cookie other = adminCookie(Role.COMPANY_ADMIN, Optional.of(otherCompany));
        String candidates = batch + "/project-candidates";
        mvc.perform(list("Calle Olmo 24".getBytes(), admin)).andExpect(status().isOk());

        mvc.perform(get(candidates).cookie(other)).andExpect(status().isForbidden());
        mvc.perform(list("Calle Haya 2".getBytes(), other)).andExpect(status().isForbidden());
        mvc.perform(post(candidates + "/discover").cookie(other)).andExpect(status().isForbidden());
        mvc.perform(post(candidates + "/confirm").cookie(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisions\":[{\"name\":\"Calle Olmo 24\",\"decision\":\"CREATE\"}]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(candidates).header("Authorization", workerKey(company))).andExpect(status().isUnauthorized());
        mvc.perform(get(candidates)).andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(w.orgs.listProjects(company)).isEmpty();
    }

    private com.e2d.odin.domain.document.Document uploaded(String content) throws Exception {
        String body = mvc.perform(file(content, admin)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return w.docs.get(new com.e2d.odin.domain.shared.DocumentId(java.util.UUID.fromString(
                com.jayway.jsonpath.JsonPath.<String>read(body, "$.document.id")))).orElseThrow();
    }

    private com.e2d.odin.domain.intake.IntakeProposal proposal(com.e2d.odin.domain.document.Document d,
            com.e2d.odin.domain.shared.ProjectId project) {
        var p = new com.e2d.odin.domain.intake.IntakeProposal(java.util.UUID.randomUUID(), company,
                new com.e2d.odin.domain.intake.IntakeBatchId(java.util.UUID.fromString(
                        batch.substring(batch.lastIndexOf('/') + 1))), d.id(),
                com.e2d.odin.domain.intake.ProposalKind.PROJECT, Optional.of(project), Optional.empty(),
                Optional.empty(), com.e2d.odin.domain.intake.ProposalConfidence.HIGH,
                com.e2d.odin.domain.intake.ProposalSource.RULES, "Calle Olmo 24",
                com.e2d.odin.domain.intake.DecisionState.PROPOSED, Optional.empty(), Optional.empty());
        w.intake.save(p);
        return p;
    }

    private void inReview() throws Exception {
        mvc.perform(post(batch + "/close-upload").cookie(admin)).andExpect(status().isOk());
        var id = new com.e2d.odin.domain.intake.IntakeBatchId(java.util.UUID.fromString(
                batch.substring(batch.lastIndexOf('/') + 1)));
        w.intake.save(w.intake.find(company, id).orElseThrow().finishReading());
    }

    private com.e2d.odin.domain.shared.ProjectId olmo;
    private com.e2d.odin.domain.document.Document first;
    private com.e2d.odin.domain.document.Document second;
    private com.e2d.odin.domain.document.Document third;
    private com.e2d.odin.domain.intake.IntakeProposal firstProposal;
    private com.e2d.odin.domain.intake.IntakeProposal secondProposal;

    private void threeProposals() throws Exception {
        olmo = com.e2d.odin.application.fake.Fixtures.project(w.orgs, company);
        first = uploaded("uno");
        second = uploaded("dos");
        third = uploaded("tres");
        firstProposal = proposal(first, olmo);
        secondProposal = proposal(second, olmo);
        proposal(third, olmo);
        inReview();
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void proposalsAreListedGroupedAndFiltered() throws Exception {
        threeProposals();

        mvc.perform(get(batch + "/proposals").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups", hasSize(1))).andExpect(jsonPath("$.groups[0].kind").value("PROJECT"))
                .andExpect(jsonPath("$.groups[0].confidence").value("HIGH"))
                .andExpect(jsonPath("$.groups[0].projectId").value(olmo.value().toString()))
                .andExpect(jsonPath("$.groups[0].proposals", hasSize(3)))
                .andExpect(jsonPath("$.groups[0].proposals[0].reason").value("Calle Olmo 24"));
        mvc.perform(get(batch + "/proposals").param("kind", "CASE_MARK").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups", hasSize(0)));
        mvc.perform(get(batch + "/proposals").param("kind", "OTRA").cookie(admin)).andExpect(status().isBadRequest());
    }

    @Test
    void pairsCarryTheTitleOfTheOtherDocument() throws Exception {
        threeProposals();
        var older = com.e2d.odin.application.fake.Fixtures.titledDocument(w.docs, company, "Versión anterior");
        w.intake.save(new com.e2d.odin.domain.intake.IntakeProposal(java.util.UUID.randomUUID(), company,
                firstProposal.batchId(), third.id(), com.e2d.odin.domain.intake.ProposalKind.SUPERSEDES, Optional.empty(),
                Optional.of(older.id()), Optional.empty(), com.e2d.odin.domain.intake.ProposalConfidence.HIGH,
                com.e2d.odin.domain.intake.ProposalSource.RULES, "sustituye a la anterior",
                com.e2d.odin.domain.intake.DecisionState.PROPOSED, Optional.empty(), Optional.empty()));

        mvc.perform(get(batch + "/proposals").param("kind", "SUPERSEDES").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].proposals[0].targetTitle").value("Versión anterior"));
    }

    @Test
    void aGroupIsConfirmedOnceAndAnEmptyGroupIsRefused() throws Exception {
        threeProposals();
        String body = "{\"proposalIds\":[\"" + firstProposal.id() + "\"]}";

        postJson(batch + "/proposals/confirm", body).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(1));
        org.assertj.core.api.Assertions.assertThat(w.docs.get(first.id()).orElseThrow().projectId()).contains(olmo);
        postJson(batch + "/proposals/confirm", body).andExpect(status().isConflict());
        postJson(batch + "/proposals/confirm", "{\"proposalIds\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void proposalsAreDecidedOneByOne() throws Exception {
        threeProposals();
        String decide = batch + "/proposals/" + secondProposal.id() + "/decide";

        postJson(decide, "{\"decision\":\"OTRA\"}").andExpect(status().isBadRequest());
        postJson(decide, "{\"decision\":\"CORRECT_PROJECT\"}").andExpect(status().isBadRequest());
        postJson(decide, "{\"decision\":\"DONT_KNOW\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PROPOSED"));
        postJson(decide, "{\"decision\":\"COMPANY_WIDE\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED")).andExpect(jsonPath("$.decidedAt").exists());
        org.assertj.core.api.Assertions.assertThat(w.docs.get(second.id()).orElseThrow().projectId()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(w.docs.get(second.id()).orElseThrow().intakePending()).isFalse();
    }

    @Test
    void discardingWithdrawsTheDocumentAndTheBatchClosesWhenNothingIsLeft() throws Exception {
        threeProposals();
        postJson(batch + "/proposals/confirm", "{\"proposalIds\":[\"" + firstProposal.id() + "\","
                + "\"" + secondProposal.id() + "\"]}").andExpect(status().isOk());

        mvc.perform(post(batch + "/documents/" + third.id().value() + "/withdraw").cookie(admin))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(w.docs.get(third.id()).orElseThrow().withdrawn()).isTrue();
        mvc.perform(get(batch + "/proposals").cookie(admin)).andExpect(jsonPath("$.groups", hasSize(0)));
        mvc.perform(get(batch).cookie(admin)).andExpect(jsonPath("$.state").value("CLOSED"));
    }

    @Test
    void anAdminOfAnotherCompanyIsRefusedTheConfirmationRoutes() throws Exception {
        threeProposals();
        CompanyId otherCompany = newCompany();
        Cookie other = adminCookie(Role.COMPANY_ADMIN, Optional.of(otherCompany));
        String id = batch.substring(batch.lastIndexOf('/') + 1);
        String ownPath = "/v1/admin/companies/" + otherCompany.value() + "/intake-batches/" + id;
        String body = "{\"proposalIds\":[\"" + firstProposal.id() + "\"]}";

        for (String root : List.of(batch, ownPath)) {
            // con la empresa del lote, 403; con la suya, el lote no existe: 404
            int refused = root.equals(batch) ? 403 : 404;
            mvc.perform(get(root + "/proposals").cookie(other)).andExpect(status().is(refused));
            mvc.perform(post(root + "/proposals/confirm").cookie(other).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().is(refused));
            mvc.perform(post(root + "/proposals/" + firstProposal.id() + "/decide").cookie(other)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRM\"}"))
                    .andExpect(status().is(refused));
            mvc.perform(post(root + "/documents/" + first.id().value() + "/withdraw").cookie(other))
                    .andExpect(status().is(refused));
        }
        org.assertj.core.api.Assertions.assertThat(w.docs.get(first.id()).orElseThrow().intakePending()).isTrue();
        org.assertj.core.api.Assertions.assertThat(w.docs.get(first.id()).orElseThrow().withdrawn()).isFalse();
    }

    @Test
    void aWorkerKeyAndAnAnonymousCallerAreRefusedTheConfirmationRoutes() throws Exception {
        threeProposals();
        String worker = workerKey(company);
        String json = "{\"proposalIds\":[\"" + firstProposal.id() + "\"]}";

        mvc.perform(get(batch + "/proposals").header("Authorization", worker)).andExpect(status().isUnauthorized());
        mvc.perform(post(batch + "/proposals/confirm").header("Authorization", worker)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isUnauthorized());
        mvc.perform(post(batch + "/proposals/" + firstProposal.id() + "/decide").header("Authorization", worker)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(batch + "/documents/" + first.id().value() + "/withdraw").header("Authorization", worker))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(batch + "/proposals")).andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(w.docs.get(first.id()).orElseThrow().intakePending()).isTrue();
    }

    private void oneProposedOneUnplaced() throws Exception {
        olmo = com.e2d.odin.application.fake.Fixtures.project(w.orgs, company);
        first = uploaded("uno");
        second = uploaded("dos");
        firstProposal = proposal(second, olmo);
        inReview();
    }

    @Test
    void theBatchCountsUndecidedProposalsByKindAndUnplacedDocuments() throws Exception {
        oneProposedOneUnplaced();

        mvc.perform(get(batch).cookie(admin)).andExpect(jsonPath("$.proposed.PROJECT").value(1))
                .andExpect(jsonPath("$.unplaced").value(1));
    }

    @Test
    void anUnplacedDocumentIsListedAndPlacedByTheAdminWithExactlyOneDestination() throws Exception {
        oneProposedOneUnplaced();
        String place = batch + "/documents/" + first.id().value() + "/place";

        mvc.perform(get(batch + "/documents").param("unplaced", "true").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].id").value(first.id().value().toString()))
                .andExpect(jsonPath("$.documents[0].path").value("Cliente/2019/a.pdf"))
                .andExpect(jsonPath("$.documents[0].status").exists());
        mvc.perform(get(batch + "/documents").param("unplaced", "false").cookie(admin)).andExpect(status().isBadRequest());
        postJson(place, "{}").andExpect(status().isBadRequest());
        postJson(place, "{\"projectId\":\"" + olmo.value() + "\",\"companyWide\":true}").andExpect(status().isBadRequest());
        postJson(batch + "/documents/" + second.id().value() + "/place", "{\"companyWide\":true}")
                .andExpect(status().isConflict());
        postJson(place, "{\"projectId\":\"" + olmo.value() + "\"}").andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(w.docs.get(first.id()).orElseThrow().projectId()).contains(olmo);
        mvc.perform(get(batch).cookie(admin)).andExpect(jsonPath("$.unplaced").value(0));
    }

    @Test
    void theUnplacedRoutesAreRefusedToAnotherCompanyAdminAndToAWorker() throws Exception {
        oneProposedOneUnplaced();
        CompanyId otherCompany = newCompany();
        Cookie other = adminCookie(Role.COMPANY_ADMIN, Optional.of(otherCompany));
        String ownPath = "/v1/admin/companies/" + otherCompany.value() + "/intake-batches/"
                + batch.substring(batch.lastIndexOf('/') + 1);
        String worker = workerKey(company);
        String body = "{\"companyWide\":true}";

        for (String root : List.of(batch, ownPath)) {
            int refused = root.equals(batch) ? 403 : 404;
            mvc.perform(get(root + "/documents").param("unplaced", "true").cookie(other)).andExpect(status().is(refused));
            mvc.perform(post(root + "/documents/" + first.id().value() + "/place").cookie(other)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(refused));
        }
        mvc.perform(get(batch + "/documents").param("unplaced", "true").header("Authorization", worker))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(batch + "/documents/" + first.id().value() + "/place").header("Authorization", worker)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(w.docs.get(first.id()).orElseThrow().intakePending()).isTrue();
    }

    @Test
    void theProjectListIsClosedByItsAdminOnlyWhenNothingIsLeftToDecide() throws Exception {
        mvc.perform(list("Calle Olmo 24".getBytes(), admin)).andExpect(status().isOk());
        mvc.perform(post(batch + "/close-upload").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.projectListClosedAt").doesNotExist());
        String close = batch + "/project-list/close";

        mvc.perform(post(close).cookie(admin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Falta 1 candidato por decidir"));
        postJson(batch + "/project-candidates/confirm", "{\"decisions\":[{\"name\":\"Calle Olmo 24\",\"decision\":\"REJECT\"}]}")
                .andExpect(status().isOk());
        mvc.perform(post(close).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READING")).andExpect(jsonPath("$.projectListClosedAt").exists())
                .andExpect(jsonPath("$.warnings[*].code", org.hamcrest.Matchers.not(hasItem("PROJECT_LIST_NOT_CONFIRMED"))));
        mvc.perform(post(close).cookie(admin)).andExpect(status().isConflict());
        mvc.perform(list("Calle Haya 2".getBytes(), admin)).andExpect(status().isConflict());
        mvc.perform(post(batch + "/project-candidates/discover").cookie(admin)).andExpect(status().isConflict());
    }

    @Test
    void closingTheProjectListIsRefusedToAnotherCompanyAdminAndToAWorker() throws Exception {
        mvc.perform(post(batch + "/close-upload").cookie(admin)).andExpect(status().isOk());
        CompanyId otherCompany = newCompany();
        Cookie other = adminCookie(Role.COMPANY_ADMIN, Optional.of(otherCompany));
        String ownPath = "/v1/admin/companies/" + otherCompany.value() + "/intake-batches/"
                + batch.substring(batch.lastIndexOf('/') + 1);

        mvc.perform(post(batch + "/project-list/close").cookie(other)).andExpect(status().isForbidden());
        mvc.perform(post(ownPath + "/project-list/close").cookie(other)).andExpect(status().isNotFound());
        mvc.perform(post(batch + "/project-list/close").header("Authorization", workerKey(company)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(batch).cookie(admin)).andExpect(jsonPath("$.projectListClosedAt").doesNotExist());
    }
}
