package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** V25: los enlaces de caso anteriores siguen válidos y las empresas existentes no reciben UPLOAD_DOCUMENTS. */
class MigrationUploadLinksToProjectsIT {

    @Test
    void existingCaseLinkSurvivesAndCompaniesDoNotGetTheNewCapability() {
        DataSource database = Db.freshDatabase("migration_upload_links_to_projects");
        Map<String, String> placeholders = Map.of("appPassword", Db.APP_PASSWORD);
        Flyway.configure().dataSource(database).placeholders(placeholders).target("24").load().migrate();
        JdbcClient jdbc = JdbcClient.create(database);
        UUID company = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID caseDocument = UUID.randomUUID();
        jdbc.sql("INSERT INTO company (id, name, code) VALUES (:id, 'Empresa', 'EMP1')").param("id", company).update();
        jdbc.sql("INSERT INTO company_capability (company_id, capability) VALUES (:id, 'SEARCH_KNOWLEDGE')")
                .param("id", company).update();
        jdbc.sql("INSERT INTO app_user (id, first_name, person_code, active) VALUES (:id, 'Ana', 'P1', true)").param("id", user).update();
        jdbc.sql("""
                INSERT INTO document (id, company_id, title, filename, format, sha256, status, paginated, uploaded_by,
                    uploaded_at, attempt_count, case_document)
                VALUES (:id, :company, 'Ficha', 'f.pdf', 'PDF', 'abc', 'READY', true, :user, now(), 0, true)""")
                .param("id", caseDocument).param("company", company).param("user", user).update();
        jdbc.sql("""
                INSERT INTO access_link (id, kind, token_hash, company_id, user_id, case_document_id, created_at,
                    expires_at)
                VALUES (:id, 'UPLOAD', 'h', :company, :user, :doc, now(), now() + interval '15 minutes')""")
                .param("id", UUID.randomUUID()).param("company", company).param("user", user).param("doc", caseDocument)
                .update();

        Flyway.configure().dataSource(database).placeholders(placeholders).load().migrate();

        assertThat(jdbc.sql("SELECT count(*) FROM access_link WHERE case_document_id = :d AND project_id IS NULL")
                .param("d", caseDocument).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT capability FROM company_capability WHERE company_id = :c").param("c", company)
                .query(String.class).list()).containsExactly("SEARCH_KNOWLEDGE");
    }
}
