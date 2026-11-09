package com.e2d.odin.adapter.web.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 5 intentos por minuto por correo y por IP, en memoria (shortcut: se pierde al reiniciar y no se purgan las claves). */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String email, String ip) {
        boolean byEmail = hit("email:" + email);
        boolean byIp = hit("ip:" + ip);
        if (!byEmail || !byIp) {
            throw new TooManyAttemptsException();
        }
    }

    private boolean hit(String key) {
        Instant now = clock.instant();
        Deque<Instant> window = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(WINDOW))) {
                window.pollFirst();
            }
            window.addLast(now);
            return window.size() <= MAX_ATTEMPTS;
        }
    }
}
