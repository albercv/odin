package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.casefile.CaseFileAction;
import com.e2d.odin.domain.casefile.CaseFileEntry;
import com.e2d.odin.domain.casefile.CaseFileEvent;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Consultas en bruto con el rol de la aplicación: sin el filtro del SQL de los repositorios, la RLS tiene que bastar. */
class CaseFileRowLevelSecurityIT extends AbstractPostgresIT {

    private final PostgresCaseFileRepository files = new PostgresCaseFileRepository(tx);
    private CompanyId company;
    private UserId user;
    private ProjectId project;
    private Document theCase;
    private Document member;

    @BeforeEach
    void seed() {
        company = newCompany();
        user = newUser(company);
        project = newProject(company, "A");
        theCase = save(Optional.of(project), true);
        member = save(Optional.of(project), false);
        docs.replacePages(member.id(), List.of(new DocumentPage(member.id(), 1, "texto", "lector", "1")));
        files.add(new CaseFileEntry(company, theCase.id(), member.id(), user, NOW));
        files.record(new CaseFileEvent(UUID.randomUUID(), company, theCase.id(),
                member.id(), CaseFileAction.ADDED, user, NOW));
    }

    private Document save(Optional<ProjectId> projectId, boolean caseDocument) {
        Document d = new Document(new DocumentId(UUID.randomUUID()), company, projectId, "Doc", "f.pdf",
                DocumentFormat.PDF, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(), true, user,
                NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(d);
        docs.markCaseDocuments(company, Set.of(d.id()), caseDocument);
        return d;
    }

    /** Cuenta filas con el rol de la aplicación y las variables de sesión dadas, sin ningún filtro propio. */
    private int count(String table, String projectIds, String includeWithdrawn) {
        return new TransactionTemplate(new DataSourceTransactionManager(Db.app()))
                .execute(status -> countIn(table, projectIds, includeWithdrawn));
    }

    private int countIn(String table, String projectIds, String includeWithdrawn) {
        JdbcClient jdbc = JdbcClient.create(Db.app());
        jdbc.sql("SELECT set_config('app.company_ids', :c, true), set_config('app.group_scope', 'off', true), "
                + "set_config('app.all_projects', 'off', true), set_config('app.project_ids', :p, true), "
                + "set_config('app.include_withdrawn', :w, true)").param("c", company.value().toString())
                .param("p", projectIds).param("w", includeWithdrawn).query().singleRow();
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    @Test
    void withoutTheProjectNoRelationNorEventIsSeen() {
        assertThat(count("case_file_entry", project.value().toString(), "off")).isEqualTo(1);
        assertThat(count("case_file_entry", "", "off")).isZero();
        assertThat(count("case_file_event", project.value().toString(), "off")).isEqualTo(1);
        assertThat(count("case_file_event", "", "off")).isZero();
    }

    @Test
    void relationWithInvisibleDocumentIsNotSeen() {
        Document general = save(Optional.empty(), false);
        files.add(new CaseFileEntry(company, theCase.id(), general.id(), user, NOW));
        assertThat(count("case_file_entry", project.value().toString(), "off")).isEqualTo(2);

        JdbcClient.create(Db.owner()).sql("UPDATE document SET withdrawn_at = now(), withdrawn_by = :u WHERE id = :id")
                .param("u", user.value()).param("id", general.id().value()).update();
        assertThat(count("case_file_entry", project.value().toString(), "off")).isEqualTo(1);
        assertThat(count("case_file_entry", project.value().toString(), "on")).isEqualTo(2);
    }

    @Test
    void withdrawnDocumentAndItsPagesAreHiddenUnlessIncluded() {
        JdbcClient.create(Db.owner()).sql("UPDATE document SET withdrawn_at = now(), withdrawn_by = :u WHERE id = :id")
                .param("u", user.value()).param("id", member.id().value()).update();
        String all = project.value().toString();

        assertThat(count("document WHERE id = '" + member.id().value() + "'", all, "off")).isZero();
        assertThat(count("document_page", all, "off")).isZero();
        assertThat(count("document WHERE id = '" + member.id().value() + "'", all, "on")).isEqualTo(1);
        assertThat(count("document_page", all, "on")).isEqualTo(1);
    }
}
