package com.e2d.odin.application.link;

import java.time.Instant;
import java.util.Optional;

/** {@code caseTitle} solo lo hay en un enlace de caso. */
public record UploadLinkInfo(UploadTargetKind kind, Optional<String> caseTitle, String projectName, Instant expiresAt) {
}
