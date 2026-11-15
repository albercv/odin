package com.e2d.odin.application.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpenOriginalTest {

    private static final String HOME = LinkWorld.HOME;

    private final LinkWorld w = new LinkWorld();
    private final byte[] content = "original".getBytes();
    private final Document doc = stored();

    private Document stored() {
        Document base = Fixtures.documentIn(w.docs, w.company, w.project, "t");
        String sha = w.store.store(content);
        Document d = new Document(base.id(), w.company, base.projectId(), "Informe", "informe año.pdf",
                DocumentFormat.PDF, sha, base.status(), Optional.empty(), true, base.uploadedBy(), base.uploadedAt(),
                Optional.empty(), 0, Optional.empty(), Optional.empty());
        w.docs.save(d);
        return d;
    }

    private String token() {
        return LinkWorld.token(w.issueOriginal().execute(w.worker, doc.id(), Optional.of(2)));
    }

    @Test
    void returnsTheStoredFileWithItsNameAndFormat() {
        OriginalFile file = w.openOriginal().execute(token(), HOME);

        assertThat(file.filename()).isEqualTo("informe año.pdf");
        assertThat(file.format()).isEqualTo(DocumentFormat.PDF);
        assertThat(file.content()).isEqualTo(content);
    }

    @Test
    void severalUsesFromTheSameAddressWork() {
        String token = token();

        w.openOriginal().execute(token, HOME);

        assertThat(w.openOriginal().execute(token, HOME).content()).isEqualTo(content);
    }

    @Test
    void otherAddressAfterTheFirstUseIsNotFound() {
        String token = token();
        w.openOriginal().execute(token, HOME);

        assertThatThrownBy(() -> w.openOriginal().execute(token, "192.168.1.99"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void expiredIsNotFound() {
        String token = token();
        w.clock.advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> w.openOriginal().execute(token, HOME)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void withdrawnDocumentIsNotFound() {
        String token = token();
        w.docs.save(doc.withWithdrawal(Optional.of(Instant.now()), Optional.of(w.worker.userId())));

        assertThatThrownBy(() -> w.openOriginal().execute(token, HOME)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anUploadTokenCannotOpenAnOriginal() {
        Document caseDoc = w.caseIn(w.project);
        String upload = LinkWorld.token(w.issueUpload().execute(w.worker, caseDoc.id()));

        assertThatThrownBy(() -> w.openOriginal().execute(upload, HOME)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void administratorOpensItWithoutAToken() {
        OriginalFile file = w.openOriginal().execute(Actors.companyAdmin(w.company), doc.id());

        assertThat(file.content()).isEqualTo(content);
    }

    @Test
    void administratorOfAnotherCompanyGetsNotFound() {
        var other = Actors.companyAdmin(Fixtures.company(w.orgs));

        assertThatThrownBy(() -> w.openOriginal().execute(other, doc.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aWorkerCannotUseTheAdministratorRoute() {
        assertThatThrownBy(() -> w.openOriginal().execute(w.worker, doc.id())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aLinkDownloadWritesNoEntryFromTheAdminAudit() {
        w.openOriginal().execute(token(), HOME);

        assertThat(w.audit.entries).isEmpty();
    }
}
