package com.e2d.odin.adapter.web.dto;

import com.e2d.odin.application.casefile.CaseFileAssignment;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.organization.Capability;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.shared.ValidationException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class Requests {

    private Requests() {
    }

    public record Setup(String setupToken, String groupName, String adminName, String adminEmail, String password) {
    }

    public record Login(String email, String password) {
    }

    public record Name(String name) {
    }

    public record SupersedeRequest(@Schema(description = "El documento que lo sustituye entero.") UUID replacementId) {
    }

    public record UpdateDocumentRequest(@Schema(description = "Si el documento es la ficha de un caso resuelto.",
            requiredMode = Schema.RequiredMode.REQUIRED) Boolean caseDocument) {
    }

    public record CaseFileDocumentsRequest(
            @Schema(description = "De 1 a 500 documentos que ya están en Odín.",
                    requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> documentIds) {

        public CaseFileAssignment assignment(UUID caseId) {
            if (documentIds == null || documentIds.isEmpty() || documentIds.contains(null)) {
                throw new ValidationException("Elige al menos un documento");
            }
            return new CaseFileAssignment(new DocumentId(caseId),
                    documentIds.stream().map(DocumentId::new).collect(Collectors.toSet()));
        }
    }

    public record OriginalLinkRequest(
            @Schema(description = "Página en la que abre el visor de PDF; si falta, abre por la primera.")
            Integer page) {
    }

    public record CaseMarksRequest(
            @Schema(description = "De 1 a 500 documentos de la empresa.", requiredMode = Schema.RequiredMode.REQUIRED)
            List<UUID> documentIds,
            @Schema(description = "Verdadero para marcarlos como casos, falso para quitar la marca.",
                    requiredMode = Schema.RequiredMode.REQUIRED) Boolean caseDocument) {
    }

    public record Capabilities(Set<Capability> capabilities) {
    }

    public record NewCompany(String name,
            @Schema(description = "De 2 a 6 letras mayúsculas o cifras; único en el grupo.") String code) {
    }

    public record UpdateCompany(String name,
            @Schema(description = "Solo lo cambia el administrador del grupo.") String code) {
    }

    public record AddMember(String firstName, String lastName, String email, Set<Role> roles, String password,
            Set<UUID> projectIds) {
    }

    public record UpdateProject(String name,
            @Schema(description = "Verdadero: solo lo ven sus miembros. Falso: visible para toda la empresa.")
            Boolean restricted) {
    }

    public record TopicRequest(String name, String definition) {
    }

    public record TopicPatch(String name, String definition) {
    }

    public record UpdateMember(Set<Role> roles, Boolean active, String password) {
    }

    public record UpdateUser(String firstName, String lastName,
            @Schema(description = "Cadena vacía: quita el correo de un trabajador. Ausente o null: no cambia.")
            String email, Boolean active) {
    }

    public record SetPassword(String currentPassword, String newPassword) {
    }

    public record AddGroupAdmin(String firstName, String lastName,
            @Schema(description = "Obligatorio: es la puerta al panel.") String email,
            @Schema(description = "Solo hace falta si la persona no tiene ya contraseña.") String password) {
    }

    public record RecoverAdmin(String setupToken,
            @Schema(description = "Obligatorio si hay más de un administrador del grupo.") String email,
            String newPassword) {
    }

    public record KeyLabel(String label) {
    }

    public record Review(ReviewDecision decision, String correctedBody, String note) {
    }

    public record SearchRequest(List<String> queries, UUID projectId, Integer limit, UUID caseId) {
    }

    public record SimilarCasesRequestBody(List<String> queries, UUID projectId, Integer limit) {
    }

    public record VerifyCitation(UUID documentId, Integer pageNumber, String quote) {
    }

    public record VerifyCitations(List<VerifyCitation> citations) {
    }

    public record SaveCard(String title, String body, UUID projectId) {
    }
}
