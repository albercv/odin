package com.e2d.odin.adapter.web.security;

import com.e2d.odin.application.port.AuditContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Lo que fijó {@link AuditContextFilter} en la petición en curso; fuera de una petición HTTP, el sistema. */
public class RequestAuditContext implements AuditContext {

    @Override
    public AuditRequest current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null
                : attributes.getAttribute(AuditContextFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof AuditRequest request ? request : SYSTEM;
    }
}
