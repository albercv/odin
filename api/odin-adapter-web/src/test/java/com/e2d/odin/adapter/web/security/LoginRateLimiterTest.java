package com.e2d.odin.adapter.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.application.fake.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    private void attempts(int count, String email, String ip) {
        for (int i = 0; i < count; i++) {
            limiter.check(email, ip);
        }
    }

    @Test
    void staleAttemptsExpireAfterOneMinute() {
        attempts(5, "a@e.test", "1.1.1.1");
        assertThatThrownBy(() -> limiter.check("a@e.test", "1.1.1.1")).isInstanceOf(TooManyAttemptsException.class);
        clock.advance(Duration.ofSeconds(61));
        assertThatCode(() -> limiter.check("a@e.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void rejectedAttemptsDoNotExtendLockout() {
        attempts(5, "b@e.test", "2.2.2.2");
        for (int i = 0; i < 30; i++) {
            clock.advance(Duration.ofSeconds(2));
            assertThatThrownBy(() -> limiter.check("b@e.test", "2.2.2.2"))
                    .isInstanceOf(TooManyAttemptsException.class);
        }
        clock.advance(Duration.ofSeconds(2));
        assertThatCode(() -> limiter.check("b@e.test", "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void stateIsBoundedForManyDistinctEmails() {
        for (int i = 0; i < 20_000; i++) {
            limiter.check("user" + i + "@e.test", "ip" + i);
        }
        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(LoginRateLimiter.MAX_KEYS);
    }

    @Test
    void longEmailIsTruncatedForKey() {
        String prefix = "x".repeat(300);
        for (int i = 0; i < 5; i++) {
            limiter.check(prefix + i, "ip" + i);
        }
        assertThatThrownBy(() -> limiter.check(prefix + "z", "ipz")).isInstanceOf(TooManyAttemptsException.class);
        assertThat(limiter.trackedKeys()).isEqualTo(7);
    }

    @Test
    void emailIsNormalized() {
        for (int i = 0; i < 5; i++) {
            limiter.check(i % 2 == 0 ? " A@E.test " : "a@e.test", "ip" + i);
        }
        assertThatThrownBy(() -> limiter.check("A@e.TEST", "ipz")).isInstanceOf(TooManyAttemptsException.class);
    }
}
