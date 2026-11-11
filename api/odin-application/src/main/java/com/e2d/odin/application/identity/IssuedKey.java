package com.e2d.odin.application.identity;

import com.e2d.odin.domain.shared.AccessKeyId;

public record IssuedKey(AccessKeyId id, String prefix, String plainKey) {
}
