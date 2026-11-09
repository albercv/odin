package com.e2d.odin.adapter.web.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 5 intentos por minuto por correo y por IP, en memoria y acotado (shortcut: se pierde al reiniciar). */
@Component
public class LoginRateLimiter {

    static final int MAX_KEYS = 10_000;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_KEY_LENGTH = 254;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
            return size() > MAX_KEYS;
        }
    };

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void check(String email, String ip) {
        boolean byEmail = hit("email:" + normalize(email));
        boolean byIp = hit("ip:" + normalize(ip));
        if (!byEmail || !byIp) {
            throw new TooManyAttemptsException();
        }
    }

    synchronized int trackedKeys() {
        return attempts.size();
    }

    private static String normalize(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return text.length() > MAX_KEY_LENGTH ? text.substring(0, MAX_KEY_LENGTH) : text;
    }

    private boolean hit(String key) {
        Instant now = clock.instant();
        Deque<Instant> window = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(WINDOW))) {
            window.pollFirst();
        }
        if (window.size() >= MAX_ATTEMPTS) {
            return false;
        }
        window.addLast(now);
        return true;
    }
}
