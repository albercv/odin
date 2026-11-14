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
}
