package com.e2d.odin.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryKnowledgeCardRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryProjectMemberRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.SequenceIdGenerator;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditActorKind;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.knowledge.KnowledgeCard;
import com.e2d.odin.domain.shared.CompanyId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Guardar una ficha deja una entrada con su id y proyecto; nunca el título ni el texto. */
class SaveCardAuditTest {

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryAuditLog audit = new InMemoryAuditLog();
    private final SaveKnowledgeCard save = new SaveKnowledgeCard(orgs, new InMemoryProjectMemberRepository(orgs),
            new InMemoryKnowledgeCardRepository(), new SequenceIdGenerator(), new MutableClock(), audit);
    private final CompanyId company = Fixtures.company(orgs);
    private final Actor worker = Actors.worker(company);

    @Test
    void savingACardRecordsOneEntryWithoutTitleOrText() {
        KnowledgeCard card = save.execute(worker, "TITULO-SECRETO", "CUERPO-SECRETO", Optional.empty());

        assertThat(audit.entries).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo(AuditAction.CARD_SAVED);
            assertThat(e.actorKind()).isEqualTo(AuditActorKind.PERSON);
            assertThat(e.actorUserId()).contains(worker.userId());
            assertThat(e.actorRole()).contains("WORKER");
            assertThat(e.company()).contains(company);
            assertThat(e.target()).contains(new AuditEntry.Target("CARD", card.id().value().toString(), null));
            assertThat(e.details()).isEmpty();
            assertThat(e.toString()).doesNotContain("SECRET");
        });
    }
}
