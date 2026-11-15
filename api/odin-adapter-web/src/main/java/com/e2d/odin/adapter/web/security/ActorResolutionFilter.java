package com.e2d.odin.adapter.web.security;

import com.e2d.odin.application.identity.AdminChatToken;
import com.e2d.odin.application.identity.ChatAccess;
import com.e2d.odin.application.identity.ResolveActor;
import com.e2d.odin.application.identity.ResolveAdminChatKey;
import com.e2d.odin.domain.identity.Actor;
import com.e2d.odin.domain.shared.ForbiddenException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Bearer de trabajador solo en las rutas del trabajador; cookie solo en /v1/admin; la clave de administrador del chat
 * solo en {@link ChatRoutes}. El resto es público.
 */
@Component
@Order(2)
public class ActorResolutionFilter extends GuardFilter {

    public static final String SESSION_COOKIE = "odin_session";
    private static final String BEARER = "Bearer ";

    private final ResolveActor resolveActor;
    private final ResolveAdminChatKey resolveChatKey;

    public ActorResolutionFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            ResolveActor resolveActor, ResolveAdminChatKey resolveChatKey) {
        super(resolver);
        this.resolveActor = resolveActor;
        this.resolveChatKey = resolveChatKey;
    }

    @Override
    void check(HttpServletRequest request) {
        String path = request.getRequestURI();
        Actor actor = null;
        if (path.startsWith("/v1/admin")) {
            boolean login = "POST".equals(request.getMethod()) && path.equals("/v1/admin/session");
            actor = login ? null : resolveActor.fromSessionToken(cookie(request));
        } else if (ChatRoutes.accepts(path) && AdminChatToken.isAdminChatToken(bearer(request))) {
            ChatAccess access = resolveChatKey.execute(bearer(request));
            request.setAttribute(CurrentActor.CHAT_ACCESS, access);
            actor = access.actor();
        } else if (ChatRoutes.chatOnly(path)) {
            resolveActor.fromAccessKey(bearer(request));
            throw new ForbiddenException("Esta ruta solo admite la clave de administrador para el chat");
        } else if (WorkerRoutes.isWorkerPath(path)) {
            actor = resolveActor.fromAccessKey(bearer(request));
        }
        request.setAttribute(CurrentActor.ATTRIBUTE, actor);
    }

    static String bearer(HttpServletRequest request) {
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
