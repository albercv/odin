package com.e2d.odin.application.identity;

import com.e2d.odin.domain.identity.Membership;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.organization.Company;

public record MemberAdded(User user, Membership membership, Company company, boolean alreadyExisted) {
}
