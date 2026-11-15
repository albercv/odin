package com.e2d.odin.application.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.Actors;
import com.e2d.odin.application.fake.FakePasswordHasher;
import com.e2d.odin.application.fake.InMemoryAuditLog;
import com.e2d.odin.application.fake.InMemoryAuditSettings;
import com.e2d.odin.application.fake.InMemoryUserRepository;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.ForbiddenException;
import com.e2d.odin.domain.shared.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateAuditSettingsTest {

    private static final String PASSWORD = "clave-muy-larga-1";
    private static final String POLICY = "https://intranet.test/politica";

    private final List<String> events = new ArrayList<>();
    private final InMemoryAuditLog log = new InMemoryAuditLog(events);
    private final InMemoryAuditSettings settings = new InMemoryAuditSettings(events);
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakePasswordHasher passwords = new FakePasswordHasher();
    private final UpdateAuditSettings update = new UpdateAuditSettings(settings, log, users, passwords);
    private final Actor admin = Actors.groupAdmin();

    UpdateAuditSettingsTest() {
        users.save(new User(admin.userId(), "Gema", Optional.empty(), "G1", Optional.of("g@demo.test"), true, true,
                Optional.of(passwords.hash(PASSWORD))));
    }

    @Test
    void enablingIsRecordedAfterTheSwitchTurnsOn() {
        update.execute(admin, PASSWORD, true, POLICY, false);

        assertThat(events).containsExactly("update:true", "record:AUDIT_ENABLED");
        assertThat(settings.get().enabled()).isTrue();
    }

    @Test
    void disablingIsRecordedBeforeTheSwitchTurnsOff() {
        update.execute(admin, PASSWORD, true, POLICY, false);
        events.clear();

        update.execute(admin, PASSWORD, false, null, false);

        assertThat(events).containsExactly("record:AUDIT_DISABLED", "update:false");
        assertThat(settings.get().usagePolicyReference()).contains(POLICY);
    }

    @Test
    void enablingWithoutPolicyReferenceIsRejected() {
        assertThatThrownBy(() -> update.execute(admin, PASSWORD, true, "  ", false))
                .isInstanceOf(ValidationException.class);
        assertThat(events).isEmpty();
    }

    @Test
    void wrongPasswordIsDeniedAndRecorded() {
        assertThatThrownBy(() -> update.execute(admin, "mala", true, POLICY, false))
                .isInstanceOf(ForbiddenException.class);
        assertThat(events).containsExactly("denied:REAUTHENTICATION_FAILED");
        assertThat(settings.get().enabled()).isFalse();
    }

    @Test
    void onlyGroupAdminsMayChangeIt() {
        Actor companyAdmin = Actors.companyAdmin(new CompanyId(UUID.randomUUID()));
        assertThatThrownBy(() -> update.execute(companyAdmin, PASSWORD, true, POLICY, false))
                .isInstanceOf(ForbiddenException.class);
        assertThat(events).isEmpty();
    }
}
