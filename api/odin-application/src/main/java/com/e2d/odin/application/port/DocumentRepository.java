package com.e2d.odin.application.port;

import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentEntry;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.document.DocumentStatus;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ProjectId;
import com.e2d.odin.domain.shared.ProjectVisibility;
import com.e2d.odin.domain.topic.TopicId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public interface DocumentRepository {

    void save(Document document);

    void replacePages(DocumentId id, List<DocumentPage> pages);

    Optional<Document> findById(CompanyScope scope, ProjectVisibility visibility, DocumentId id);

    Optional<DocumentEntry> findEntry(CompanyScope scope, ProjectVisibility visibility, DocumentId id);

    /** Como {@link #findEntry}, pero también encuentra los retirados: solo para retirarlos y recuperarlos. */
    Optional<DocumentEntry> findEntryIncludingWithdrawn(CompanyScope scope, ProjectVisibility visibility,
            DocumentId id);

    /** Escribe solo la retirada del documento. Al recuperar, si hay otro igual sin retirar en su proyecto, 409. */
    void markWithdrawal(Document document);

    /** Solo los retirados, para la vista «Retirados» del administrador, con su paginación. */
    List<DocumentEntry> listWithdrawn(CompanyScope scope, ProjectVisibility visibility, int offset,
            int limit);

    /** Pone la marca de sustituido con su propia orden: {@link #save} no la escribe ni la borra. */
    void markSuperseded(CompanyId companyId, DocumentId id, DocumentId replacementId, Instant at);

    /**
     * Ejecuta el trabajo con los dos documentos bloqueados hasta el final, en la misma transacción y en un orden fijo
     * (por identificador), para que dos marcas cruzadas no se pisen ni se bloqueen entre sí.
     */
    <T> T withDocumentsLocked(CompanyId companyId, DocumentId first, DocumentId second, Supplier<T> work);

    void clearSuperseded(CompanyId companyId, DocumentId id);

    /** Marca o desmarca la ficha de caso de todos los documentos de la empresa en una transacción; si falta alguno, 404 y no cambia ninguno. */
    int markCaseDocuments(CompanyId companyId, Set<DocumentId> documentIds, boolean caseDocument);

    /** Si el mismo fichero está varias veces en la empresa, el más antiguo. */
    Optional<Document> findBySha256(CompanyId companyId, String sha256);

    /** Busca solo en ese proyecto y entre los documentos sin proyecto; si hay varios, el más antiguo. */
    Optional<Document> findBySha256InProject(CompanyId companyId, Optional<ProjectId> projectId, String sha256);

    /**
     * Con {@code topicId}, solo los documentos que tienen ese tema {@code ACTIVE}. Con {@code caseDocument}, solo los
     * que tienen esa marca de ficha de caso.
     */
    List<DocumentEntry> list(CompanyScope scope, ProjectVisibility visibility, Optional<ProjectId> projectId,
            Optional<TopicId> topicId, Optional<Boolean> caseDocument, Optional<DocumentStatus> status, int offset,
            int limit);

    /** Todos los documentos de la empresa para el administrador: a diferencia de {@code list}, con los escaneos enlazados. */
    List<DocumentEntry> listForAdmin(CompanyScope scope, int offset, int limit);

    default List<DocumentEntry> list(CompanyScope scope, ProjectVisibility visibility, Optional<ProjectId> projectId,
            Optional<TopicId> topicId, Optional<DocumentStatus> status, int offset, int limit) {
        return list(scope, visibility, projectId, topicId, Optional.empty(), status, offset, limit);
    }

    default List<DocumentEntry> list(CompanyScope scope, ProjectVisibility visibility, Optional<ProjectId> projectId,
            Optional<DocumentStatus> status, int offset, int limit) {
        return list(scope, visibility, projectId, Optional.empty(), Optional.empty(), status, offset, limit);
    }

    Optional<DocumentPage> findPage(CompanyScope scope, ProjectVisibility visibility, DocumentId id, int pageNumber);

    Optional<Document> claimNextForProcessing(Instant now, Duration staleAfter);

    /** Toma el siguiente documento del carril del lector (en espera o con la lectura caducada) y lo pasa a lectura. */
    Optional<Document> claimNextForReader(Instant now, Duration staleAfter);
}
