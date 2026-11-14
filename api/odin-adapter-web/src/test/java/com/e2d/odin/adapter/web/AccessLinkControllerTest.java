package com.e2d.odin.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Las rutas con token de los enlaces de subida: el JSON de cada destino y el aviso de duplicado. */
class AccessLinkControllerTest extends WebTestSupport {

    private static final String AUTH = "Authorization";

    private CompanyId company;
    private ProjectId project;
    private String key;

    @BeforeEach
    void setUp() {
        company = newCompany();
        project = Fixtures.project(w.orgs, company);
        UserId worker = workerId(company);
        key = keyOf(worker, company);
        w.projectMembers.add(project, worker);
    }

    private String tokenOf(String issuePath) throws Exception {
        String body = mvc.perform(post(issuePath).header(AUTH, key)).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        String url = new tools.jackson.databind.json.JsonMapper().readTree(body).get("url").asString();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private MockMultipartHttpServletRequestBuilder upload(String token, String content) {
        return multipart("/v1/upload-links/" + token + "/documents")
                .file(new MockMultipartFile("file", "acta.pdf", "application/pdf", content.getBytes()));
    }

    @Test
    void projectLinkDescribesProjectWithoutCaseTitle() throws Exception {
        String token = tokenOf("/v1/projects/" + project.value() + "/upload-links");
        mvc.perform(get("/v1/upload-links/" + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value("PROJECT")).andExpect(jsonPath("$.projectName").value("Proyecto"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty()).andExpect(jsonPath("$.caseTitle").doesNotExist());
    }

    @Test
    void caseLinkKeepsItsFieldsAndAddsTarget() throws Exception {
        Document caseDoc = Fixtures.documentIn(w.docs, company, project, "ficha");
        w.docs.markCaseDocuments(company, Set.of(caseDoc.id()), true);
        String token = tokenOf("/v1/cases/" + caseDoc.id().value() + "/upload-links");
        mvc.perform(get("/v1/upload-links/" + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value("CASE")).andExpect(jsonPath("$.caseTitle").isNotEmpty())
                .andExpect(jsonPath("$.projectName").value("Proyecto"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void projectUploadReportsDuplicateAlways() throws Exception {
        String token = tokenOf("/v1/projects/" + project.value() + "/upload-links");
        mvc.perform(upload(token, "contenido-uno")).andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("acta.pdf")).andExpect(jsonPath("$.duplicate").value(false));
        mvc.perform(upload(token, "contenido-uno")).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void caseUploadReportsDuplicateFalse() throws Exception {
        Document caseDoc = Fixtures.documentIn(w.docs, company, project, "ficha");
        w.docs.markCaseDocuments(company, Set.of(caseDoc.id()), true);
        String token = tokenOf("/v1/cases/" + caseDoc.id().value() + "/upload-links");
        mvc.perform(upload(token, "otro-contenido")).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));
    }
}
