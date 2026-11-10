package com.e2d.odin.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.shared.CompanyId;
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
    void otherCompanyAdminCannotPatchOrDeleteUser() throws Exception {
        CompanyId a = newCompany();
        CompanyId b = newCompany();
        workerKey(a);
        String id = w.users.listByCompany(a).get(0).id().value().toString();
        var other = adminCookie(Role.COMPANY_ADMIN, Optional.of(b));
        mvc.perform(patch("/v1/admin/users/" + id).cookie(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isForbidden());
        mvc.perform(delete("/v1/admin/users/" + id).cookie(other)).andExpect(status().isForbidden());
    }
}
