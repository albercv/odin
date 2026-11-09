package com.e2d.odin.adapter.web.security;

import com.e2d.odin.domain.shared.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
@Order(1)
public class OriginCheckFilter extends GuardFilter {

    private final String panelOrigin;

    public OriginCheckFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            @Value("${odin.panel-origin:}") String panelOrigin) {
        super(resolver);
        this.panelOrigin = panelOrigin;
    }

    @Override
    void check(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean guarded = path.startsWith("/v1/admin") || path.equals("/v1/setup");
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (guarded && !"GET".equals(request.getMethod()) && origin != null && !origin.equals(panelOrigin)) {
            throw new ForbiddenException("Origen no permitido");
        }
    }
}
