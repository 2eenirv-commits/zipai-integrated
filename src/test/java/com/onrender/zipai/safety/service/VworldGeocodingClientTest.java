package com.onrender.zipai.safety.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class VworldGeocodingClientTest {

    @Test
    void searchUriUsesHttpsAndEncodesQueryAndRegisteredDomain() {
        URI uri = VworldGeocodingClient.buildRequestUri(
            "test-key",
            "https://zipai-integrated-29ac.onrender.com",
            "역삼동",
            "DISTRICT",
            "L4"
        );

        String value = uri.toASCIIString();
        assertEquals("https", uri.getScheme());
        assertTrue(value.contains("query=%EC%97%AD%EC%82%BC%EB%8F%99"));
        assertTrue(value.contains("type=DISTRICT"));
        assertTrue(value.contains("category=L4"));
        assertTrue(value.contains("key=test-key"));
        assertTrue(value.contains("domain=https://zipai-integrated-29ac.onrender.com"));
    }

    @Test
    void domainParameterIsOmittedWhenNotConfigured() {
        URI uri = VworldGeocodingClient.buildAddressUri("test-key", 37.5665, 126.9780);

        assertFalse(uri.toASCIIString().contains("domain="));
    }
}
