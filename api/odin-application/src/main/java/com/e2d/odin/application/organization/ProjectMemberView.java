package com.e2d.odin.application.organization;

import com.e2d.odin.domain.shared.UserId;

public record ProjectMemberView(UserId userId, String displayName) {
}
