package com.e2d.odin.adapter.web.security;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Desde qué equipo llega una petición. Es la dirección de la conexión; {@code X-Forwarded-For} solo cuenta si la
 * conexión viene de uno de los proxies de {@code ODIN_TRUSTED_PROXIES} (nombres o direcciones, que se resuelven al
 * usarlos). Lo usan los límites por IP y los enlaces: ninguno puede fiarse de una cabecera que escribe el cliente.
 */
@Component
public class ClientAddress {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]+");

    private final String[] trustedProxies;

    public ClientAddress(@Value("${odin.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /** Como texto, para los límites por IP: si la cabecera de un proxy no es una dirección, la del proxy. */
    public String of(HttpServletRequest request) {
        HttpServletRequest raw = unwrapped(request);
        return resolve(raw.getRemoteAddr(), raw.getHeader(FORWARDED_FOR)).map(InetAddress::getHostAddress)
                .orElseGet(raw::getRemoteAddr);
    }

    /** Vacío si la conexión o la cabecera de un proxy de confianza no son una dirección. */
    public Optional<InetAddress> resolve(HttpServletRequest request) {
        HttpServletRequest raw = unwrapped(request);
        return resolve(raw.getRemoteAddr(), raw.getHeader(FORWARDED_FOR));
    }

    public Optional<InetAddress> resolve(String remoteAddress, String forwardedFor) {
        Optional<InetAddress> connection = literal(remoteAddress);
        boolean forwarded = connection.isPresent() && forwardedFor != null && !forwardedFor.isBlank()
                && isTrusted(connection.get());
        return forwarded ? literal(lastOf(forwardedFor)) : connection;
    }

    /**
     * La petición sin envolver: {@code ForwardedHeaderFilter} (forward-headers-strategy: framework) reescribe
     * {@code getRemoteAddr()} con el {@code X-Forwarded-For} de cualquiera.
     */
    private static HttpServletRequest unwrapped(HttpServletRequest request) {
        ServletRequest raw = request;
        while (raw instanceof ServletRequestWrapper wrapper) {
            raw = wrapper.getRequest();
        }
        return (HttpServletRequest) raw;
    }

    /** El último valor es el que añadió el proxy de confianza; los anteriores los pudo escribir cualquiera. */
    private static String lastOf(String forwardedFor) {
        String[] parts = forwardedFor.split(",");
        return parts[parts.length - 1].strip();
    }

    private boolean isTrusted(InetAddress connection) {
        Set<InetAddress> resolved = new HashSet<>();
        for (String proxy : trustedProxies) {
            try {
                resolved.addAll(Arrays.asList(InetAddress.getAllByName(proxy)));
            } catch (UnknownHostException e) {
                // un proxy que no resuelve no se tiene por de confianza
            }
        }
        return resolved.contains(connection);
    }

    /** Solo literales: una cadena que no es una dirección nunca llega a un DNS. */
    private static Optional<InetAddress> literal(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String value = text.strip();
        boolean address = IPV4.matcher(value).matches() || (value.contains(":") && IPV6.matcher(value).matches());
        try {
            return address ? Optional.of(InetAddress.getByName(value)) : Optional.empty();
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }
}
