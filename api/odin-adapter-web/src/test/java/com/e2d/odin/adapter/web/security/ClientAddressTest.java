package com.e2d.odin.adapter.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressTest {

    private static MockHttpServletRequest from(String remote, String forwardedFor) {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setRemoteAddr(remote);
        if (forwardedFor != null) {
            raw.addHeader("X-Forwarded-For", forwardedFor);
        }
        return raw;
    }

    @Test
    void theConnectionAddressIsReturnedWhenNoProxyIsTrusted() {
        assertThat(new ClientAddress("").of(from("8.8.8.8", "10.0.0.5"))).isEqualTo("8.8.8.8");
    }

    @Test
    void forwardedForOnlyCountsFromATrustedProxy() {
        var addresses = new ClientAddress("10.0.0.2");

        assertThat(addresses.of(from("10.0.0.2", "192.168.1.50"))).isEqualTo("192.168.1.50");
        assertThat(addresses.of(from("10.0.0.3", "192.168.1.50"))).isEqualTo("10.0.0.3");
    }

    @Test
    void theRewrittenRemoteAddressOfAWrapperIsIgnored() {
        var wrapper = new HttpServletRequestWrapper(from("10.0.0.3", "1.2.3.4")) {
            @Override
            public String getRemoteAddr() {
                return "1.2.3.4";
            }
        };

        assertThat(new ClientAddress("").of(wrapper)).isEqualTo("10.0.0.3");
    }

    @Test
    void garbageFromATrustedProxyFallsBackToTheProxyAddress() {
        assertThat(new ClientAddress("10.0.0.2").of(from("10.0.0.2", "evil.example.com"))).isEqualTo("10.0.0.2");
    }
}
