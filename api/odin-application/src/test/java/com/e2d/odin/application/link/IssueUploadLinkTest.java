package com.e2d.odin.application.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.link.AccessLinkKind;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ValidationException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IssueUploadLinkTest {

    private final LinkWorld w = new LinkWorld();

    @Test
    void urlHasOnlyTheTokenAndExpiresInFifteenMinutes() {
        Document caseDoc = w.caseIn(w.project);

        IssuedLink link = w.issueUpload().execute(w.worker, caseDoc.id());

        String token = LinkWorld.token(link);
        assertThat(link.url()).isEqualTo("http://odin.local/subir/" + token);
        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        assertThat(link.expiresAt()).isEqualTo(w.clock.instant().plus(Duration.ofMinutes(15)));
        assertThat(w.links.all()).singleElement().satisfies(stored -> {
            assertThat(stored.kind()).isEqualTo(AccessLinkKind.UPLOAD);
            assertThat(stored.caseId()).contains(caseDoc.id());
            assertThat(stored.userId()).isEqualTo(w.worker.userId());
        });
    }

    @Test
    void onlyTheHashIsStored() {
        IssuedLink link = w.issueUpload().execute(w.worker, w.caseIn(w.project).id());

        assertThat(w.links.hashes()).singleElement().isEqualTo(w.secrets.hash(LinkWorld.token(link)))
                .isNotEqualTo(LinkWorld.token(link));
    }

    @Test
    void missingPublicUrlSaysSoAndStoresNothing() {
        var noUrl = new LinkSettings("", Duration.ofMinutes(15));
        var issuer = new LinkIssuer(w.links, w.tokens, w.secrets, w.ids, w.clock, noUrl, w.audit);
        var issue = new IssueUploadLink(w.orgs, w.members, w.docs, issuer);
        Document caseDoc = w.caseIn(w.project);

        assertThatThrownBy(() -> issue.execute(w.worker, caseDoc.id())).isInstanceOf(ValidationException.class)
                .hasMessage("Falta configurar la dirección de Odín en la red local; avisa al administrador");
        assertThat(w.links.all()).isEmpty();
    }

    @Test
    void caseTheWorkerDoesNotSeeIsNotFound() {
        Document hidden = w.caseIn(com.e2d.odin.application.fake.Fixtures.project(w.orgs, w.company));

        assertThatThrownBy(() -> w.issueUpload().execute(w.worker, hidden.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(w.links.all()).isEmpty();
    }

    @Test
    void aDocumentThatIsNotACaseIsNotFound() {
        Document plain = com.e2d.odin.application.fake.Fixtures.documentIn(w.docs, w.company, w.project, "texto");

        assertThatThrownBy(() -> w.issueUpload().execute(w.worker, plain.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void originalLinkCarriesThePageInTheFragment() {
        Document doc = com.e2d.odin.application.fake.Fixtures.documentIn(w.docs, w.company, w.project, "texto");

        IssuedLink link = w.issueOriginal().execute(w.worker, doc.id(), java.util.Optional.of(3));

        assertThat(link.url()).startsWith("http://odin.local/v1/originals/").endsWith("#page=3");
        assertThat(w.links.all()).singleElement().satisfies(stored -> {
            assertThat(stored.kind()).isEqualTo(AccessLinkKind.ORIGINAL);
            assertThat(stored.documentId()).contains(doc.id());
        });
    }

    @Test
    void originalLinkOfAnUnseenDocumentIsNotFound() {
        Document hidden = com.e2d.odin.application.fake.Fixtures.documentIn(w.docs, w.company,
                com.e2d.odin.application.fake.Fixtures.project(w.orgs, w.company), "texto");

        assertThatThrownBy(() -> w.issueOriginal().execute(w.worker, hidden.id(), java.util.Optional.empty()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void caseInADeletedProjectIsNotFound() {
        Document caseDoc = w.caseIn(w.project);
        w.orgs.deleteProject(w.company, w.project, w.clock.instant());

        assertThatThrownBy(() -> w.issueUpload().execute(w.worker, caseDoc.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void caseWithoutProjectIsTheSameNotFoundAsAMissingCase() {
        Document general = w.caseInGeneral();

        assertThatThrownBy(() -> w.issueUpload().execute(w.worker, general.id())).isInstanceOf(NotFoundException.class)
                .hasMessage("Caso no encontrado");
        assertThat(w.links.all()).isEmpty();
    }
}
