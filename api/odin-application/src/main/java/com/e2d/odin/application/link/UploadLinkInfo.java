package com.e2d.odin.application.link;

import java.time.Instant;

public record UploadLinkInfo(String caseTitle, String projectName, Instant expiresAt) {
}
