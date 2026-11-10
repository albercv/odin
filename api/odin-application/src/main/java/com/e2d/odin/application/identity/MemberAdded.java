package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.User;

public record MemberAdded(User user, Membership membership, boolean alreadyExisted) {
}
