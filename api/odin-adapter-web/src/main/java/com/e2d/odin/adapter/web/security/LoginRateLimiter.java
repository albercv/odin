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

/** 5 fallos por minuto por clave (correo) y por IP, en memoria y acotado (shortcut: se pierde al reiniciar). */
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

    /** Rechaza si la clave o la IP acumulan 5 fallos en la ventana; no apunta nada. */
    public synchronized void check(String key, String ip) {
        if (isFull(keyOf("key:", key)) || isFull(keyOf("ip:", ip))) {
            throw new TooManyAttemptsException();
        }
    }

    public synchronized void recordFailure(String key, String ip) {
        window(keyOf("key:", key)).addLast(clock.instant());
        window(keyOf("ip:", ip)).addLast(clock.instant());
    }

    synchronized int trackedKeys() {
        return attempts.size();
    }

    private static String normalize(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return text.length() > MAX_KEY_LENGTH ? text.substring(0, MAX_KEY_LENGTH) : text;
    }

    private static String keyOf(String prefix, String value) {
        return prefix + normalize(value);
    }

    private boolean isFull(String key) {
        return window(key).size() >= MAX_ATTEMPTS;
    }

    private Deque<Instant> window(String key) {
        Instant limit = clock.instant().minus(WINDOW);
        Deque<Instant> window = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!window.isEmpty() && window.peekFirst().isBefore(limit)) {
            window.pollFirst();
        }
        return window;
    }
}
