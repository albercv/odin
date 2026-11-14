package com.e2d.odin.adapter.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class CaseFileControllerTest extends WebTestSupport {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    private CompanyId company;
    private ProjectId project;
    private UserId worker;
    private String key;

    @BeforeEach
    void setUp() {
        company = newCompany();
        project = Fixtures.project(w.orgs, company);
        worker = workerId(company);
        key = keyOf(worker, company);
        w.projectMembers.add(project, worker);
    }

    private Document caseDoc() {
        Document d = Fixtures.documentIn(w.docs, company, project, "ficha");
        w.docs.markCaseDocuments(company, Set.of(d.id()), true);
        return w.docs.get(d.id()).orElseThrow();
    }

    private String url(Document caseDoc) {
        return "/v1/cases/" + caseDoc.id().value() + "/file";
    }

    private String body(Document... docs) {
        var ids = java.util.Arrays.stream(docs).map(d -> "\"" + d.id().value() + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"documentIds\":[" + ids + "]}";
    }

    @Test
    void addThenGetThenRemove() throws Exception {
        Document c = caseDoc();
        Document doc = Fixtures.documentIn(w.docs, company, project, "anexo");

        mvc.perform(post(url(c) + "/documents").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content(body(doc))).andExpect(status().isOk()).andExpect(jsonPath("$.added").value(1));
        mvc.perform(get(url(c)).header(AUTH, key)).andExpect(status().isOk())
                .andExpect(jsonPath("$.caseDocument.id").value(c.id().value().toString()))
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].id").value(doc.id().value().toString()))
                .andExpect(jsonPath("$.documents[0].addedBy").value(worker.value().toString()))
                .andExpect(jsonPath("$.documents[0].superseded").value(false));
        mvc.perform(delete(url(c) + "/documents/" + doc.id().value()).header(AUTH, key))
                .andExpect(status().isNoContent());
        mvc.perform(get(url(c)).header(AUTH, key)).andExpect(jsonPath("$.documents", hasSize(0)));
    }

    @Test
    void unknownOrInvisibleCaseIs404OnEveryRoute() throws Exception {
        Document c = caseDoc();
        String outsider = keyOf(workerId(company), company);
        Document doc = Fixtures.documentIn(w.docs, company, project, "anexo");

        mvc.perform(get(url(c)).header(AUTH, outsider)).andExpect(status().isNotFound());
        mvc.perform(post(url(c) + "/documents").header(AUTH, outsider).contentType(MediaType.APPLICATION_JSON)
                .content(body(doc))).andExpect(status().isNotFound());
        mvc.perform(delete(url(c) + "/documents/" + doc.id().value()).header(AUTH, outsider))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/cases/" + UUID.randomUUID() + "/file").header(AUTH, key))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBodyIs400() throws Exception {
        Document c = caseDoc();

        mvc.perform(post(url(c) + "/documents").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(url(c) + "/documents").header(AUTH, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[]}")).andExpect(status().isBadRequest());
    }

    @Test
    void withoutKeyIs401AndWithoutCapabilityIs403() throws Exception {
        Document c = caseDoc();
        mvc.perform(get(url(c))).andExpect(status().isUnauthorized());

        CompanyId limited = Fixtures.companyWith(w.orgs, EnumSet.of(Capability.SEARCH_KNOWLEDGE));
        mvc.perform(get(url(c)).header(AUTH, workerKey(limited))).andExpect(status().isForbidden());
    }

    @Test
    void adminCookieDoesNotOpenTheWorkerRoute() throws Exception {
        Document c = caseDoc();

        mvc.perform(get(url(c)).cookie(adminCookie(Role.COMPANY_ADMIN, java.util.Optional.of(company))))
                .andExpect(status().isUnauthorized());
    }
}
