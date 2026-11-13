package com.e2d.odin.adapter.web.link;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.Inet6Address;
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
 * Decide desde qué equipo llega una petición con token y si es de la red local. {@code X-Forwarded-For} solo cuenta
 * si la conexión viene de uno de los proxies de {@code ODIN_TRUSTED_PROXIES}; de cualquier otra dirección, privada
 * incluida, se toma la de la conexión y se ignora la cabecera.
 */
@Component
public class LocalNetworkGuard {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]+");

    private final String[] trustedProxies;

    public LocalNetworkGuard(@Value("${odin.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Mira la petición sin envolver: {@code ForwardedHeaderFilter} (forward-headers-strategy: framework) reescribe
     * {@code getRemoteAddr()} con el {@code X-Forwarded-For} de cualquiera, y aquí solo cuenta el de un proxy de confianza.
     */
    public Optional<String> clientAddress(HttpServletRequest request) {
        ServletRequest raw = request;
        while (raw instanceof ServletRequestWrapper wrapper) {
            raw = wrapper.getRequest();
        }
        HttpServletRequest connection = (HttpServletRequest) raw;
        return clientAddress(connection.getRemoteAddr(), connection.getHeader("X-Forwarded-For"));
    }

    /** La dirección del equipo, normalizada, si es de la red local; vacío si no lo es o no se puede leer. */
    public Optional<String> clientAddress(String remoteAddress, String forwardedFor) {
        Optional<InetAddress> connection = literal(remoteAddress);
        if (connection.isEmpty()) {
            return Optional.empty();
        }
        boolean forwarded = forwardedFor != null && !forwardedFor.isBlank() && isTrusted(connection.get());
        Optional<InetAddress> client = forwarded ? literal(lastOf(forwardedFor)) : connection;
        return client.filter(LocalNetworkGuard::isLocal).map(LocalNetworkGuard::normalised);
    }

    /** El último valor es el que añadió el proxy de confianza; los anteriores los pudo escribir cualquiera. */
    private static String lastOf(String forwardedFor) {
        String[] parts = forwardedFor.split(",");
        return parts[parts.length - 1].strip();
    }

    private boolean isTrusted(InetAddress connection) {
        return resolveTrusted().contains(connection);
    }

    private Set<InetAddress> resolveTrusted() {
        Set<InetAddress> resolved = new HashSet<>();
        for (String proxy : trustedProxies) {
            try {
                resolved.addAll(Arrays.asList(InetAddress.getAllByName(proxy)));
            } catch (UnknownHostException e) {
                // un proxy que no resuelve no se tiene por de confianza
            }
        }
        return resolved;
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

    private static boolean isLocal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            return address.isSiteLocalAddress();
        }
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xFE) == 0xFC;
    }

    private static String normalised(InetAddress address) {
        String text = address.getHostAddress();
        int zone = text.indexOf('%');
        return zone < 0 ? text : text.substring(0, zone);
    }
}
