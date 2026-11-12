package com.e2d.odin.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.application.fake.People;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListGroupAdminsTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final ListGroupAdmins list = new ListGroupAdmins(users);

    @Test
    void groupAdminSeesEveryGroupAdminAndNobodyElse() {
        User gema = People.groupAdmin(users, new UserId(UUID.randomUUID()), "Gema", Optional.of("g@demo.test"), true,
                Optional.of("pw:x"));
        User gael = People.groupAdmin(users, new UserId(UUID.randomUUID()), "Gael", Optional.of("gael@demo.test"),
                false, Optional.of("pw:x"));
        users.save(new User(new UserId(UUID.randomUUID()), "Wanda", Optional.empty(), "WWWW", Optional.empty(), false,
                true, Optional.empty()));

        assertThat(list.execute(Actors.groupAdmin())).containsExactlyInAnyOrder(gema, gael);
    }

    @Test
    void companyAdminIsForbidden() {
        assertThatThrownBy(() -> list.execute(Actors.companyAdmin(new CompanyId(UUID.randomUUID()))))
                .isInstanceOf(ForbiddenException.class);
    }
}
