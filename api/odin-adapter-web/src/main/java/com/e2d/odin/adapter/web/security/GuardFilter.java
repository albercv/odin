package com.e2d.odin.adapter.web.security;

import com.e2d.odin.domain.shared.DomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Filtro que rechaza con las mismas respuestas RFC 9457 que los controladores. */
abstract class GuardFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver resolver;

    GuardFilter(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    abstract void check(HttpServletRequest request);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            check(request);
        } catch (DomainException e) {
            resolver.resolveException(request, response, null, e);
            return;
        }
        chain.doFilter(request, response);
    }
}
