package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnlinkScanCopyTest {

    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final com.e2d.odin.domain.shared.CompanyId company = Fixtures.company(orgs);
    private final UnlinkScanCopy unlink = new UnlinkScanCopy(docs);

    @Test
    void unlinkingPutsTheScanBackAndTheMainLosesTheCopy() {
        Document main = Fixtures.document(docs, company, DocumentStatus.READY);
        Document scan = Fixtures.document(docs, company, DocumentStatus.READY);
        docs.markScanOf(company, scan.id(), main.id());
        var admin = Actors.companyAdmin(company);
        var scope = admin.scope();
        assertThat(ScanLinks.of(docs, scope, ProjectVisibility.all(), Optional.empty(), List.of(main))
                .get(main.id()).scanCopies()).hasSize(1);

        unlink.execute(admin, scan.id());

        assertThat(docs.get(scan.id()).orElseThrow().scanOf()).isEmpty();
        assertThat(ScanLinks.of(docs, scope, ProjectVisibility.all(), Optional.empty(), List.of(main))
                .get(main.id()).scanCopies()).isEmpty();
    }

    @Test
    void aDocumentThatIsNotAScanCopyIsNotFound() {
        Document plain = Fixtures.document(docs, company, DocumentStatus.READY);

        assertThatThrownBy(() -> unlink.execute(Actors.companyAdmin(company), plain.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aWorkerCannotUnlink() {
        Document main = Fixtures.document(docs, company, DocumentStatus.READY);
        Document scan = Fixtures.document(docs, company, DocumentStatus.READY);
        docs.markScanOf(company, scan.id(), main.id());

        assertThatThrownBy(() -> unlink.execute(Actors.worker(company), scan.id()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(docs.get(scan.id()).orElseThrow().scanOf()).isPresent();
    }
}
