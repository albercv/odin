package com.e2d.odin.adapter.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentRef;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class WorkerApiTest extends WebTestSupport {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    @Test
    void searchReturnsHitsAndNoSource() throws Exception {
        String key = workerKey(newCompany());
        w.search.returns(new SearchResult(List.of(new SearchHit(SourceType.ORIGINAL_DOCUMENT, UUID.randomUUID(),
                "Manual", Optional.of(3), true, Optional.empty(), "Ana", Fixtures.NOW, "texto", 0.5, false, Optional.empty()))));
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"vacaciones\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.noSource").value(false))
                .andExpect(jsonPath("$.hits[0].sourceType").value("ORIGINAL_DOCUMENT"))
                .andExpect(jsonPath("$.hits[0].pageNumber").value(3))
                .andExpect(jsonPath("$.hits[0].authorName").value("Ana"));
        w.search.returns(SearchResult.noSource());
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"nada\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.noSource").value(true)).andExpect(jsonPath("$.hits", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"queries\":[]}", "{\"queries\":[\"  \"]}", "{\"queries\":[null]}"})
    void invalidSearchInputIs400(String body) throws Exception {
        mvc.perform(post("/v1/search").header(AUTH, workerKey(newCompany()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void tooManyOrTooLongQueriesIs400() throws Exception {
        String key = workerKey(newCompany());
        String eleven = IntStream.range(0, 11).mapToObj(i -> "\"q" + i + "\"").collect(Collectors.joining(","));
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[" + eleven + "]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"" + "a".repeat(501) + "\"]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"" + "a".repeat(500) + "\"]}")).andExpect(status().isOk());
    }

    @Test
    void disabledCapabilityIs403WithMessage() throws Exception {
        CompanyId company = Fixtures.companyWith(w.orgs, EnumSet.of(Capability.SAVE_KNOWLEDGE_CARD));
        mvc.perform(post("/v1/search").header(AUTH, workerKey(company)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"x\"]}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Tu empresa no tiene activada esta función"));
    }

    @Test
    void problemJsonShape() throws Exception {
        mvc.perform(get("/v1/documents/" + UUID.randomUUID() + "/pages/1").header(AUTH, workerKey(newCompany())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").value("Documento no encontrado"));
        mvc.perform(get("/v1/documents/not-a-uuid/pages/1").header(AUTH, workerKey(newCompany())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void meListsCompanyAndProjects() throws Exception {
        CompanyId company = newCompany();
        Fixtures.companyWideProject(w.orgs, company);
        mvc.perform(get("/v1/me").header(AUTH, workerKey(company))).andExpect(status().isOk())
                .andExpect(jsonPath("$.company.id").value(company.value().toString()))
                .andExpect(jsonPath("$.projects", hasSize(1)));
    }

    @Test
    void readsPagesAndVerifiesCitations() throws Exception {
        CompanyId company = newCompany();
        String key = workerKey(company);
        Document doc = Fixtures.readyDocument(w.docs, company, "Las vacaciones son 22 días");
        mvc.perform(get("/v1/documents/" + doc.id().value() + "/pages/1").header(AUTH, key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Las vacaciones son 22 días"))
                .andExpect(jsonPath("$.pageNumber").value(1));
        mvc.perform(get("/v1/documents").header(AUTH, key)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        String quote = "{\"documentId\":\"" + doc.id().value() + "\",\"pageNumber\":1,\"quote\":\"%s\"}";
        mvc.perform(post("/v1/citations/verify").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content(quote.formatted("vacaciones  SON 22"))).andExpect(jsonPath("$.verified").value(true));
        mvc.perform(post("/v1/citations/verify").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content(quote.formatted("vacaciones son 23"))).andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void searchHitCarriesSupersededAndReplacement() throws Exception {
        String key = workerKey(newCompany());
        Document current = Fixtures.readyDocument(w.docs, newCompany(), "vigente");
        w.search.returns(new SearchResult(List.of(
                new SearchHit(SourceType.ORIGINAL_DOCUMENT, UUID.randomUUID(), "Viejo", Optional.of(1), true,
                        Optional.empty(), "Ana", Fixtures.NOW, "texto", 0.5, true, Optional.of(DocumentRef.of(current))),
                new SearchHit(SourceType.ORIGINAL_DOCUMENT, UUID.randomUUID(), "Sin ver", Optional.of(1), true,
                        Optional.empty(), "Ana", Fixtures.NOW, "texto", 0.4, true, Optional.empty()))));
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"x\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].superseded").value(true))
                .andExpect(jsonPath("$.hits[0].replacement.documentId").value(current.id().value().toString()))
                .andExpect(jsonPath("$.hits[0].replacement.title").value(current.title()))
                .andExpect(jsonPath("$.hits[1].superseded").value(true))
                .andExpect(jsonPath("$.hits[1].replacement").doesNotExist());
    }

    @Test
    void pageCarriesSupersededAndReplacement() throws Exception {
        CompanyId company = newCompany();
        String key = workerKey(company);
        Document old = Fixtures.readyDocument(w.docs, company, "viejo");
        Document current = Fixtures.readyDocument(w.docs, company, "vigente");
        w.docs.markSuperseded(company, old.id(), current.id(), Fixtures.NOW);
        mvc.perform(get("/v1/documents/" + old.id().value() + "/pages/1").header(AUTH, key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.superseded").value(true))
                .andExpect(jsonPath("$.replacement.documentId").value(current.id().value().toString()))
                .andExpect(jsonPath("$.replacement.title").value(current.title()));
        mvc.perform(get("/v1/documents/" + current.id().value() + "/pages/1").header(AUTH, key))
                .andExpect(jsonPath("$.superseded").value(false)).andExpect(jsonPath("$.replacement").doesNotExist());
    }

    @Test
    void workerListingCarriesSupersededAndReplacement() throws Exception {
        CompanyId company = newCompany();
        Document old = Fixtures.readyDocument(w.docs, company, "viejo");
        Document current = Fixtures.readyDocument(w.docs, company, "vigente");
        w.docs.markSuperseded(company, old.id(), current.id(), Fixtures.NOW);
        mvc.perform(get("/v1/documents").header(AUTH, workerKey(company))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + old.id().value() + "')].superseded").value(true))
                .andExpect(jsonPath("$[?(@.id=='" + old.id().value() + "')].replacement.documentId")
                        .value(current.id().value().toString()))
                .andExpect(jsonPath("$[?(@.id=='" + current.id().value() + "')].superseded").value(false));
    }

    @Test
    void workerListingShowsOnlyReadyDocumentsWithoutInternalFields() throws Exception {
        CompanyId company = newCompany();
        Document ready = Fixtures.readyDocument(w.docs, company, "texto");
        w.docs.save(Fixtures.document(w.docs, company, DocumentStatus.FAILED).markFailed("interno"));
        mvc.perform(get("/v1/documents").header(AUTH, workerKey(company))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(ready.id().value().toString()))
                .andExpect(jsonPath("$[0].failureReason").doesNotExist())
                .andExpect(jsonPath("$[0].attemptCount").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist());
    }

    @Test
    void savingCardReturns201Pending() throws Exception {
        mvc.perform(post("/v1/cards").header(AUTH, workerKey(newCompany())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Truco\",\"body\":\"Se hace así\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Ficha guardada; queda pendiente de revisión."));
    }

    @Test
    void filterByUnassignedProjectIs404() throws Exception {
        CompanyId company = newCompany();
        String key = workerKey(company);
        var project = Fixtures.project(w.orgs, company);
        mvc.perform(post("/v1/search").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"queries\":[\"vacaciones\"],\"projectId\":\"" + project.value() + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Proyecto no encontrado"));
        mvc.perform(get("/v1/documents?projectId=" + project.value()).header(AUTH, key))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Proyecto no encontrado"));
        mvc.perform(get("/v1/me").header(AUTH, key)).andExpect(jsonPath("$.projects", hasSize(0)));

        w.projectMembers.add(project, w.memberships.listByCompany(company).get(0).userId());
        mvc.perform(get("/v1/documents?projectId=" + project.value()).header(AUTH, key))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/me").header(AUTH, key)).andExpect(jsonPath("$.projects", hasSize(1)));
    }
}
