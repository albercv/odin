package com.e2d.odin.application.link;

import java.time.Instant;

public record IssuedLink(String url, Instant expiresAt) {
}
