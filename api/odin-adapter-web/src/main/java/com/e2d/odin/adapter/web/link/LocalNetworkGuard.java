package com.e2d.odin.adapter.web.link;

import com.e2d.odin.adapter.web.security.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Deja pasar solo a los equipos de la red local: loopback, privadas, de enlace local e IPv6 local única. */
@Component
public class LocalNetworkGuard {

    private final ClientAddress addresses;

    public LocalNetworkGuard(ClientAddress addresses) {
        this.addresses = addresses;
    }

    /** La dirección del equipo, normalizada, si es de la red local; vacío si no lo es o no se puede leer. */
    public Optional<String> clientAddress(HttpServletRequest request) {
        return addresses.resolve(request).filter(LocalNetworkGuard::isLocal).map(LocalNetworkGuard::normalised);
    }

    public Optional<String> clientAddress(String remoteAddress, String forwardedFor) {
        return addresses.resolve(remoteAddress, forwardedFor).filter(LocalNetworkGuard::isLocal)
                .map(LocalNetworkGuard::normalised);
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
