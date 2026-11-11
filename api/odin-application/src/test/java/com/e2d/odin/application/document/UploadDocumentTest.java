package com.e2d.odin.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryOriginalFileStore;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.application.fake.StubFormatDetector;
import com.e2d.odin.application.shared.UploadSettings;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.PayloadTooLargeException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.shared.UnsupportedFormatException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UploadDocumentTest {

    private static final byte[] CONTENT = "contenido".getBytes();

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryOriginalFileStore store = new InMemoryOriginalFileStore();
    private final StubFormatDetector detector = new StubFormatDetector(DocumentFormat.PDF);
    private final UploadDocument upload = new UploadDocument(docs, orgs, store, detector, new UploadSettings(100),
            new SequenceIdGenerator(), new MutableClock());
    private final CompanyId companyA = Fixtures.company(orgs);
    private final CompanyId companyB = Fixtures.company(orgs);
    private final Actor adminA = Actors.companyAdmin(companyA);

    private UploadOutcome send(Actor actor, CompanyId company, byte[] content) {
        return upload.execute(actor, company, Optional.empty(), "Manual", "manual.pdf", content);
    }

    @Test
    void storesOriginalAndCreatesReceived() {
        UploadOutcome outcome = send(adminA, companyA, CONTENT);

        assertThat(outcome.duplicate()).isFalse();
        assertThat(outcome.document().status()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(outcome.document().uploadedBy()).isEqualTo(adminA.userId());
        assertThat(store.read(outcome.document().sha256())).isEqualTo(CONTENT);
        assertThat(docs.get(outcome.document().id())).contains(outcome.document());
    }

    @Test
    void duplicateInSameCompanyReturnsExisting() {
        UploadOutcome first = send(adminA, companyA, CONTENT);
        UploadOutcome second = send(adminA, companyA, CONTENT);

        assertThat(second.duplicate()).isTrue();
        assertThat(second.document().id()).isEqualTo(first.document().id());
    }

    @Test
    void sameFileInOtherCompanyIsNotDuplicate() {
        UploadOutcome first = send(adminA, companyA, CONTENT);
        UploadOutcome other = send(Actors.companyAdmin(companyB), companyB, CONTENT);

        assertThat(other.duplicate()).isFalse();
        assertThat(other.document().id()).isNotEqualTo(first.document().id());
        assertThat(other.document().sha256()).isEqualTo(first.document().sha256());
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void failedDuplicateIsRequeued() {
        UploadOutcome first = send(adminA, companyA, CONTENT);
        docs.save(first.document().markFailed("roto"));

        UploadOutcome again = send(adminA, companyA, CONTENT);

        assertThat(again.duplicate()).isTrue();
        assertThat(again.document().status()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(docs.get(first.document().id()).orElseThrow().status()).isEqualTo(DocumentStatus.RECEIVED);
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> send(adminA, companyA, new byte[0])).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsTooLarge() {
        assertThatThrownBy(() -> send(adminA, companyA, new byte[101])).isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void rejectsUndetectedFormat() {
        detector.detects(null);

        assertThatThrownBy(() -> send(adminA, companyA, CONTENT)).isInstanceOf(UnsupportedFormatException.class);
    }

    @Test
    void rejectsProjectOfOtherCompany() {
        var projectOfB = Fixtures.project(orgs, companyB);

        assertThatThrownBy(() -> upload.execute(adminA, companyA, Optional.of(projectOfB), "M", "m.pdf", CONTENT))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void companyAdminCannotUploadToOtherCompany() {
        assertThatThrownBy(() -> send(Actors.companyAdmin(companyB), companyA, CONTENT))
                .isInstanceOf(ForbiddenException.class);
        assertThat(docs.list(Actors.groupAdmin().scope(), ProjectVisibility.all(), Optional.empty(), Optional.empty(), 0, 10)).isEmpty();
        assertThat(store.size()).isZero();
    }

    @Test
    void workerCannotUpload() {
        assertThatThrownBy(() -> send(Actors.worker(companyA), companyA, CONTENT)).isInstanceOf(ForbiddenException.class);
    }
}
