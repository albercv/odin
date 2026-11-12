package com.e2d.odin.adapter.web.dto;

import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.organization.Capability;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    public record UpdateMember(Set<Role> roles, Boolean active, String password) {
    }

    public record UpdateUser(String firstName, String lastName,
            @Schema(description = "Cadena vacía: quita el correo de un trabajador. Ausente o null: no cambia.")
            String email, Boolean active) {
    }

    public record SetPassword(String currentPassword, String newPassword) {
    }

    public record RecoverAdmin(String setupToken, String newPassword) {
    }

    public record KeyLabel(String label) {
    }

    public record Review(ReviewDecision decision, String correctedBody, String note) {
    }

    public record SearchRequest(List<String> queries, UUID projectId, Integer limit) {
    }

    public record VerifyCitation(UUID documentId, Integer pageNumber, String quote) {
    }

    public record SaveCard(String title, String body, UUID projectId) {
    }
}
