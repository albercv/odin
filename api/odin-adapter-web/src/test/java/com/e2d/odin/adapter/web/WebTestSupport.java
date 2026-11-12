package com.e2d.odin.adapter.web;

import com.e2d.odin.adapter.web.security.LoginRateLimiter;
import com.e2d.odin.domain.identity.AccessKey;
import com.e2d.odin.domain.identity.AdminSession;
import com.e2d.odin.domain.identity.Role;
import com.e2d.odin.domain.identity.User;
import com.e2d.odin.domain.shared.AccessKeyId;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.People;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(properties = "odin.panel-origin=http://panel.test")
@Import({TestWiring.class, LoginRateLimiter.class})
abstract class WebTestSupport {

    static final String PANEL = "http://panel.test";
    private static final AtomicInteger COUNTER = new AtomicInteger(10_000_000);

    @Autowired MockMvc mvc;
    @Autowired TestWiring w;

    @BeforeEach
    void expireRateLimitWindow() {
        w.clock.advance(Duration.ofMinutes(2));
    }

    CompanyId newCompany() {
        return Fixtures.company(w.orgs);
    }

    String workerKey(CompanyId company) {
        User user = People.member(w.users, w.memberships, new UserId(UUID.randomUUID()), "Wanda", Optional.empty(),
                Role.WORKER, company, true, Optional.empty());
        String prefix = String.valueOf(COUNTER.incrementAndGet());
        String secret = "secret-" + UUID.randomUUID();
        w.keys.save(new AccessKey(new AccessKeyId(UUID.randomUUID()), user.id(), company, "portatil", prefix,
                w.secrets.hash(secret), w.clock.instant(), Optional.empty()));
        return "Bearer odin_" + prefix + "_" + secret;
    }

    UserId workerId(CompanyId company) {
        return People.member(w.users, w.memberships, new UserId(UUID.randomUUID()), "Wilma", Optional.empty(),
                Role.WORKER, company, true, Optional.empty()).id();
    }

    Cookie adminCookie(Role role, Optional<CompanyId> company) {
        UserId id = new UserId(UUID.randomUUID());
        Optional<String> email = Optional.of(UUID.randomUUID() + "@e.test");
        Optional<String> hash = Optional.of(w.passwords.hash("clave-muy-larga-1"));
        User user = company.isPresent()
                ? People.member(w.users, w.memberships, id, "Admin", email, role, company.get(), true, hash)
                : People.groupAdmin(w.users, id, "Admin", email, true, hash);
        String token = "tok-" + UUID.randomUUID();
        w.sessions.save(new AdminSession(w.secrets.hash(token), user.id(), w.clock.instant(), w.clock.instant()));
        return new Cookie("odin_session", token);
    }
}
