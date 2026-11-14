package com.e2d.odin.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.link.AccessLink;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.domain.shared.ProjectId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresAccessLinkRepositoryIT extends AbstractPostgresIT {

    private final PostgresAccessLinkRepository links = new PostgresAccessLinkRepository(tx);

    private AccessLink saved() {
        CompanyId company = newCompany();
        UserId user = newUser(company);
        Document doc = new Document(new DocumentId(UUID.randomUUID()), company, Optional.empty(), "Ficha", "f.pdf",
                DocumentFormat.PDF, UUID.randomUUID().toString(), DocumentStatus.READY, Optional.empty(), true, user,
                NOW, Optional.empty(), 0, Optional.empty(), Optional.empty());
        docs.save(doc);
        AccessLink link = new AccessLink(UUID.randomUUID(), AccessLinkKind.ORIGINAL, company, user, Optional.empty(),
                Optional.empty(), Optional.of(doc.id()), NOW, NOW.plusSeconds(900), Optional.empty(), 0);
        links.save(link, "hash-" + link.id());
        return link;
    }

    @Test
    void bindKeepsTheFirstAddress() {
        AccessLink link = saved();

        assertThat(links.bind(link, "10.0.0.1")).contains("10.0.0.1");
        assertThat(links.bind(link, "10.0.0.2")).contains("10.0.0.1");
    }

    @Test
    void bindOfALinkDeletedMeanwhileIsEmptyInsteadOfAnError() {
        AccessLink link = saved();
        links.deleteOf(link.companyId(), link.userId());

        assertThat(links.bind(link, "10.0.0.1")).isEmpty();
    }

    private AccessLink projectLink(CompanyId company, UserId user, ProjectId project) {
        return new AccessLink(UUID.randomUUID(), AccessLinkKind.UPLOAD, company, user, Optional.empty(),
                Optional.of(project), Optional.empty(), NOW, NOW.plusSeconds(900), Optional.empty(), 0);
    }

    @Test
    void aProjectLinkIsSavedAndRead() {
        CompanyId company = newCompany();
        UserId user = newUser(company);
        AccessLink link = projectLink(company, user, newProject(company, "Calle Pino 26"));
        links.save(link, "hash-p");

        assertThat(links.findByTokenHash("hash-p")).contains(link);
    }

    @Test
    void aCaseUploadLinkIsStillReadAsBefore() {
        CompanyId company = newCompany();
        UserId user = newUser(company);
        Document ficha = newDocument(company, user, "Ficha", DocumentStatus.READY);
        AccessLink link = new AccessLink(UUID.randomUUID(), AccessLinkKind.UPLOAD, company, user,
                Optional.of(ficha.id()), Optional.empty(), Optional.empty(), NOW, NOW.plusSeconds(900),
                Optional.empty(), 0);
        links.save(link, "hash-c");

        assertThat(links.findByTokenHash("hash-c")).contains(link);
    }

    @Test
    void theDatabaseRejectsAnUploadLinkWithoutDestinationOrWithBoth() {
        CompanyId company = newCompany();
        UserId user = newUser(company);
        Document ficha = newDocument(company, user, "Ficha", DocumentStatus.READY);
        ProjectId project = newProject(company, "Obra");
        AccessLink none = new AccessLink(UUID.randomUUID(), AccessLinkKind.UPLOAD, company, user, Optional.empty(),
                Optional.empty(), Optional.empty(), NOW, NOW.plusSeconds(900), Optional.empty(), 0);
        AccessLink both = new AccessLink(UUID.randomUUID(), AccessLinkKind.UPLOAD, company, user,
                Optional.of(ficha.id()), Optional.of(project), Optional.empty(), NOW, NOW.plusSeconds(900),
                Optional.empty(), 0);

        assertThatThrownBy(() -> links.save(none, "h-none")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> links.save(both, "h-both")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAProjectLinkToAProjectOfAnotherCompany() {
        CompanyId company = newCompany();
        UserId user = newUser(company);
        ProjectId foreign = newProject(newCompany(), "Ajena");

        assertThatThrownBy(() -> links.save(projectLink(company, user, foreign), "h-foreign"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aCompanyDoesNotSeeTheProjectLinksOfAnother() {
        CompanyId company = newCompany();
        UserId user = newUser(company);
        links.save(projectLink(company, user, newProject(company, "Obra")), "h-rls");

        int own = tx.inScope(com.e2d.odin.domain.shared.CompanyScope.of(company), jdbc -> jdbc
                .sql("SELECT count(*) FROM access_link").query(Integer.class).single());
        int other = tx.inScope(com.e2d.odin.domain.shared.CompanyScope.of(newCompany()), jdbc -> jdbc
                .sql("SELECT count(*) FROM access_link").query(Integer.class).single());

        assertThat(own).isEqualTo(1);
        assertThat(other).isZero();
    }
}
