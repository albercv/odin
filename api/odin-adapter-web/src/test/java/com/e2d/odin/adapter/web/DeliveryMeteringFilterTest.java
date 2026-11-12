package com.e2d.odin.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.adapter.web.security.WorkerRoutes;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDeliveryMeter;
import com.e2d.odin.application.usage.WorkerTool;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.search.SearchHit;
import com.e2d.odin.domain.search.SearchResult;
import com.e2d.odin.domain.search.SourceType;
import com.e2d.odin.domain.shared.CompanyId;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class DeliveryMeteringFilterTest extends WebTestSupport {

    @Autowired RequestMappingHandlerMapping mappings;

    /** El contexto de Spring se comparte entre clases: el medidor llega con lo que apuntaron las demás pruebas. */
    @BeforeEach
    @AfterEach
    void cleanMeter() {
        w.meter.entries().clear();
        w.meter.failWith(null);
    }

    @Test
    void countsBytesOfSuccessfulSearch() throws Exception {
        CompanyId company = newCompany();
        w.search.returns(new SearchResult(List.of(new SearchHit(SourceType.ORIGINAL_DOCUMENT, UUID.randomUUID(),
                "Manual", Optional.of(3), true, Optional.empty(), "Ana", Fixtures.NOW, "texto ñ", 0.5, false,
                Optional.empty(), Optional.empty()))));

        byte[] body = mvc.perform(post("/v1/search").header(HttpHeaders.AUTHORIZATION, workerKey(company))
                .contentType(MediaType.APPLICATION_JSON).content("{\"queries\":[\"vacaciones\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(w.meter.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.company()).isEqualTo(company);
            assertThat(entry.tool()).isEqualTo(WorkerTool.SEARCH);
            assertThat(entry.bytes()).isEqualTo(body.length).isPositive();
            assertThat(entry.at()).isEqualTo(w.clock.instant());
        });
    }

    @Test
    void everyWorkerRouteHasATool() {
        List<String> withoutTool = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                for (var method : info.getMethodsCondition().getMethods()) {
                    if (WorkerRoutes.isWorkerPath(pattern) && WorkerRoutes.toolFor(method.name(), pattern).isEmpty()) {
                        withoutTool.add(method + " " + pattern);
                    }
                }
            }
        }
        assertThat(withoutTool).as("rutas del trabajador sin herramienta").isEmpty();
        assertThat(WorkerRoutes.toolFor("GET", "/v1/documents/{documentId}/pages/{pageNumber}"))
                .contains(WorkerTool.READ_PAGE);
        assertThat(WorkerRoutes.toolFor("GET", "/v1/admin/companies")).isEmpty();
    }

    @Test
    void onlySuccessfulWorkerResponsesAreMetered() throws Exception {
        String key = workerKey(newCompany());

        mvc.perform(get("/v1/documents/" + UUID.randomUUID() + "/pages/1").header(HttpHeaders.AUTHORIZATION, key))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/search").header(HttpHeaders.AUTHORIZATION, key).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        assertThat(w.meter.entries()).isEmpty();

        mvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, key)).andExpect(status().isOk());
        assertThat(w.meter.entries()).extracting(InMemoryDeliveryMeter.Entry::tool)
                .containsExactly(WorkerTool.DESCRIBE_ME);
    }

    @Test
    void adminRoutesAreNotMetered() throws Exception {
        CompanyId company = newCompany();
        Cookie admin = adminCookie(Role.COMPANY_ADMIN, Optional.of(company));

        mvc.perform(get("/v1/admin/companies/" + company.value() + "/documents").cookie(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/admin/companies/" + company.value() + "/usage-stats").cookie(admin))
                .andExpect(status().isOk());

        assertThat(w.meter.entries()).isEmpty();
    }

    @Test
    void meterFailureDoesNotBreakResponse() throws Exception {
        w.meter.failWith(new IllegalStateException("base de datos caída"));
        String key = workerKey(newCompany());

        String body = mvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, key)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotEmpty();
    }
}
