package com.e2d.odin.adapter.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AdminCaseFileControllerTest extends WebTestSupport {

    private CompanyId company;
    private ProjectId project;
    private Cookie admin;

    @BeforeEach
    void setUp() {
        company = newCompany();
        project = Fixtures.project(w.orgs, company);
        admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));
    }

    private Document caseDoc() {
        Document d = Fixtures.documentIn(w.docs, company, project, "ficha");
        w.docs.markCaseDocuments(company, Set.of(d.id()), true);
        return w.docs.get(d.id()).orElseThrow();
    }

    private String base(Document c) {
        return "/v1/admin/companies/" + company.value() + "/cases/" + c.id().value();
    }

    @Test
    void addGetEventsRemove() throws Exception {
        Document c = caseDoc();
        Document doc = Fixtures.documentIn(w.docs, company, project, "anexo");

        mvc.perform(post(base(c) + "/file/documents").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[\"" + doc.id().value() + "\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1));
        mvc.perform(get(base(c) + "/file").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(1)));
        mvc.perform(get(base(c) + "/events").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].action").value("ADDED"))
                .andExpect(jsonPath("$[0].documentId").value(doc.id().value().toString()));
        mvc.perform(delete(base(c) + "/file/documents/" + doc.id().value()).cookie(admin))
                .andExpect(status().isNoContent());
        mvc.perform(get(base(c) + "/events").cookie(admin)).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].action").value("REMOVED"));
    }

    @Test
    void notFoundWhenNotACaseOrUnknown() throws Exception {
        Document plain = Fixtures.documentIn(w.docs, company, project, "suelto");

        mvc.perform(get(base(plain) + "/file").cookie(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/cases/" + java.util.UUID.randomUUID()
                + "/events").cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void unmarkingACaseWithDocumentsIs409() throws Exception {
        Document c = caseDoc();
        Document doc = Fixtures.documentIn(w.docs, company, project, "anexo");
        mvc.perform(post(base(c) + "/file/documents").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[\"" + doc.id().value() + "\"]}")).andExpect(status().isOk());

        mvc.perform(patch("/v1/admin/documents/" + c.id().value()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"caseDocument\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Quita antes los documentos del expediente"));
    }

    @Test
    void anotherCompanyAdminIs403AndNoSessionIs401() throws Exception {
        Document c = caseDoc();
        Cookie other = adminCookie(Role.COMPANY_ADMIN, Optional.of(newCompany()));

        mvc.perform(get(base(c) + "/file").cookie(other)).andExpect(status().isForbidden());
        mvc.perform(get(base(c) + "/file")).andExpect(status().isUnauthorized());
    }
}
