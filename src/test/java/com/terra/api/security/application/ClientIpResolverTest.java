package com.terra.api.security.application;

import com.terra.api.security.infrastructure.config.SecurityNetworkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    @Test
    void shouldUseRemoteAddressWhenForwardedHeadersAreDisabled() {
        ClientIpResolver resolver = new ClientIpResolver(networkProperties(false));
        MockHttpServletRequest request = request("198.51.100.40", "203.0.113.10");

        String resolved = resolver.resolve(request);

        assertEquals("198.51.100.40", resolved);
    }

    @Test
    void shouldUseForwardedAddressWhenHeaderIsTrustedAndValid() {
        ClientIpResolver resolver = new ClientIpResolver(networkProperties(true));
        MockHttpServletRequest request = request("127.0.0.1", "198.51.100.40:4444, 10.0.0.1");

        String resolved = resolver.resolve(request);

        assertEquals("198.51.100.40", resolved);
    }

    @Test
    void shouldIgnoreForwardedAddressWhenRemoteAddressIsNotTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(networkProperties(true));
        MockHttpServletRequest request = request("198.51.100.88", "203.0.113.10");

        String resolved = resolver.resolve(request);

        assertEquals("198.51.100.88", resolved);
    }

    @Test
    void shouldFallbackToRemoteAddressWhenForwardedHeaderContainsOnlyInvalidValues() {
        ClientIpResolver resolver = new ClientIpResolver(networkProperties(true));
        MockHttpServletRequest request = request("127.0.0.1", "unknown, invalid-hostname, [invalid]");

        String resolved = resolver.resolve(request);

        assertEquals("127.0.0.1", resolved);
    }

    private SecurityNetworkProperties networkProperties(boolean trustForwardedHeaders) {
        SecurityNetworkProperties properties = new SecurityNetworkProperties();
        properties.setTrustForwardedHeaders(trustForwardedHeaders);
        return properties;
    }

    private MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
