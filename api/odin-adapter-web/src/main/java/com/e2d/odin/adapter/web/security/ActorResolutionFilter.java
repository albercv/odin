package com.e2d.odin.adapter.web.security;

import com.e2d.odin.application.identity.ResolveActor;
import com.e2d.odin.domain.identity.Actor;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Bearer solo para las rutas del trabajador; cookie solo para /v1/admin. El resto es público. */
@Component
@Order(2)
public class ActorResolutionFilter extends GuardFilter {

    public static final String SESSION_COOKIE = "odin_session";
    private static final String BEARER = "Bearer ";
    private static final List<String> WORKER_PREFIXES = List.of("/v1/me", "/v1/search", "/v1/cards",
            "/v1/documents", "/v1/citations", "/v1/similar-cases");

    private final ResolveActor resolveActor;

    public ActorResolutionFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            ResolveActor resolveActor) {
        super(resolver);
        this.resolveActor = resolveActor;
    }

    @Override
    void check(HttpServletRequest request) {
        String path = request.getRequestURI();
        Actor actor = null;
        if (path.startsWith("/v1/admin")) {
            boolean login = "POST".equals(request.getMethod()) && path.equals("/v1/admin/session");
            actor = login ? null : resolveActor.fromSessionToken(cookie(request));
        } else if (WORKER_PREFIXES.stream().anyMatch(path::startsWith)) {
            actor = resolveActor.fromAccessKey(bearer(request));
        }
        request.setAttribute(CurrentActor.ATTRIBUTE, actor);
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())
                ? header.substring(BEARER.length()).trim() : null;
    }

    private static String cookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (SESSION_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
