package com.e2d.odin.adapter.web.dto;

import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.knowledge.ReviewDecision;
import com.e2d.odin.domain.organization.Capability;
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

    public record Capabilities(Set<Capability> capabilities) {
    }

    public record CreateUser(String displayName, String email, Role role, String password) {
    }

    public record UserActive(Boolean active) {
    }

    public record KeyLabel(String label) {
    }

    public record Review(ReviewDecision decision, String correctedBody, String note) {
    }

    public record Search(List<String> queries, UUID projectId, Integer limit) {
    }

    public record VerifyCitation(UUID documentId, Integer pageNumber, String quote) {
    }

    public record SaveCard(String title, String body, UUID projectId) {
    }
}
