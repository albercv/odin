package com.e2d.odin.application.identity;

import com.e2d.odin.domain.shared.UserId;

public record NewSession(String token, UserId userId) {
}
