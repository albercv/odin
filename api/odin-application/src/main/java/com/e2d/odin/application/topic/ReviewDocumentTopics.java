package com.e2d.odin.application.topic;

import com.e2d.odin.application.audit.AuditSupport;
import com.e2d.odin.application.port.AuditLog;
import com.e2d.odin.domain.audit.AuditAction;
import com.e2d.odin.domain.audit.AuditEntry;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.port.TopicRepository;
import com.e2d.odin.application.shared.AccessPolicy;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.NotFoundException;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicState;
import java.time.Clock;

/** El administrador corrige los temas de un documento; lo que decide manda sobre la IA para siempre. */
public class ReviewDocumentTopics {

    private final DocumentRepository documents;
    private final TopicRepository topics;
    private final TaggingRepository tagging;
    private final Clock clock;
    private final AuditLog audit;

    public ReviewDocumentTopics(DocumentRepository documents, TopicRepository topics, TaggingRepository tagging,
            Clock clock, AuditLog audit) {
        this.audit = audit;
        this.documents = documents;
        this.topics = topics;
        this.tagging = tagging;
        this.clock = clock;
    }

    public void add(Actor actor, DocumentId documentId, TopicId topicId) {
        decide(actor, documentId, topicId, TopicState.ACTIVE, AuditAction.DOCUMENT_TOPIC_ADDED);
    }

    public void remove(Actor actor, DocumentId documentId, TopicId topicId) {
        decide(actor, documentId, topicId, TopicState.REMOVED, AuditAction.DOCUMENT_TOPIC_REMOVED);
    }

    /** Borra el estado del etiquetado: el documento vuelve a la cola y las decisiones del administrador se quedan. */
    public void retag(Actor actor, DocumentId documentId) {
        Document document = requireDocument(actor, documentId);
        tagging.retag(actor.scope(), documentId);
        audit.record(AuditSupport.onDocument(AuditEntry.by(actor, AuditAction.DOCUMENT_RETAGGED), document));
    }

    private void decide(Actor actor, DocumentId documentId, TopicId topicId, TopicState state, AuditAction action) {
        Document document = requireDocument(actor, documentId);
        Topic topic = topics.find(document.companyId(), topicId)
                .orElseThrow(() -> new NotFoundException("Tema no encontrado"));
        tagging.decide(actor.scope(), documentId, topicId, state, actor.userId(), clock.instant());
        audit.record(AuditEntry.by(actor, action).onCompany(document.companyId())
                .on("TOPIC", topicId.value().toString(), topic.name())
                .with("documentId", documentId.value().toString()));
    }

    /** Con el ámbito del actor: el documento de otra empresa no existe para él, igual que en los demás casos. */
    private Document requireDocument(Actor actor, DocumentId documentId) {
        AccessPolicy.requireAdmin(actor);
        return documents.findById(actor.scope(), ProjectVisibility.all(), documentId)
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
    }
}
