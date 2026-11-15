package com.e2d.odin.adapter.web.security;

import com.e2d.odin.application.port.AuditContext.AuditRequest;
import com.e2d.odin.domain.audit.AuditChannel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fija canal, id de petición, IP y navegador según la credencial; ninguna cabecera del cliente decide el canal. */
@Component
@Order(0)
public class AuditContextFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = "odin.audit";
    private static final int MAX_AGENT = 200;

    private final ClientAddress addresses;

    public AuditContextFilter(ClientAddress addresses) {
        this.addresses = addresses;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        request.setAttribute(ATTRIBUTE, new AuditRequest(channel(request.getRequestURI()),
                Optional.of(UUID.randomUUID().toString()), Optional.of(addresses.of(request)),
                Optional.ofNullable(agent).map(a -> a.length() > MAX_AGENT ? a.substring(0, MAX_AGENT) : a)));
        chain.doFilter(request, response);
    }

    static AuditChannel channel(String path) {
        if (path.startsWith("/v1/admin")) {
            return AuditChannel.PANEL;
        }
        return WorkerRoutes.isWorkerPath(path) ? AuditChannel.CHAT : AuditChannel.API;
    }
}
