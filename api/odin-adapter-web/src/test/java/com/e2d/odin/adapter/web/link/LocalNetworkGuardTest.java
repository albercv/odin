package com.e2d.odin.adapter.web.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalNetworkGuardTest {

    private final LocalNetworkGuard noProxies = new LocalNetworkGuard("");

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.4.5.6", "172.16.0.1", "172.31.255.254", "192.168.1.20", "169.254.3.4",
            "::1", "fd00::1", "fd12:3456:789a::1", "fe80::1"})
    void localAddressesAreAccepted(String address) {
        assertThat(noProxies.clientAddress(address, null)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "172.32.0.1", "172.15.0.1", "11.0.0.1", "192.169.0.1", "2001:4860:4860::8888",
            "100.64.0.1", "not-an-address", ""})
    void otherAddressesAreRejected(String address) {
        assertThat(noProxies.clientAddress(address, null)).isEmpty();
    }

    @Test
    void ipv4MappedAddressesAreNormalised() {
        assertThat(noProxies.clientAddress("::ffff:10.0.0.5", null)).contains("10.0.0.5");
    }

    @Test
    void forwardedForIsUsedOnlyFromATrustedProxy() {
        var guard = new LocalNetworkGuard("10.0.0.2");

        assertThat(guard.clientAddress("10.0.0.2", "192.168.1.50")).contains("192.168.1.50");
    }

    @Test
    void spoofedForwardedForIgnored() {
        var guard = new LocalNetworkGuard("10.0.0.2");

        assertThat(guard.clientAddress("192.168.1.77", "192.168.1.50")).contains("192.168.1.77");
    }

    @Test
    void forwardedForIsIgnoredWhenNoProxyIsTrusted() {
        assertThat(noProxies.clientAddress("192.168.1.77", "192.168.1.50")).contains("192.168.1.77");
        assertThat(noProxies.clientAddress("8.8.8.8", "192.168.1.50")).isEmpty();
    }

    @Test
    void aTrustedProxyForwardingAPublicAddressIsRejected() {
        var guard = new LocalNetworkGuard("10.0.0.2");

        assertThat(guard.clientAddress("10.0.0.2", "8.8.8.8")).isEmpty();
    }

    @Test
    void aTrustedProxyWithoutTheHeaderMeansTheProxyItself() {
        var guard = new LocalNetworkGuard("10.0.0.2");

        assertThat(guard.clientAddress("10.0.0.2", null)).contains("10.0.0.2");
    }

    @Test
    void theLastForwardedAddressWinsAndGarbageIsRejected() {
        var guard = new LocalNetworkGuard("10.0.0.2");

        assertThat(guard.clientAddress("10.0.0.2", "8.8.8.8, 192.168.1.50")).contains("192.168.1.50");
        assertThat(guard.clientAddress("10.0.0.2", "evil.example.com")).isEmpty();
        assertThat(guard.clientAddress("10.0.0.2", "192.168.1.50, evil.example.com")).isEmpty();
    }

    @Test
    void proxyNamesAreResolvedWhenUsed() {
        var guard = new LocalNetworkGuard(" localhost , ");

        assertThat(guard.clientAddress("127.0.0.1", "10.1.1.1")).contains("10.1.1.1");
        assertThat(guard.clientAddress("10.9.9.9", "10.1.1.1")).contains("10.9.9.9");
    }

    @Test
    void unresolvableProxyNamesTrustNothing() {
        var guard = new LocalNetworkGuard("no-such-host.invalid");

        assertThat(guard.clientAddress("10.0.0.2", "192.168.1.50")).isEqualTo(Optional.of("10.0.0.2"));
    }
}
