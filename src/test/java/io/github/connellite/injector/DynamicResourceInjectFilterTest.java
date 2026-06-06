package io.github.connellite.injector;

import io.github.connellite.injector.support.StubHttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicResourceInjectFilterTest {

    @Test
    void requestPathWithoutContextStripsContextPath() {
        StubHttpServletRequest request = new StubHttpServletRequest();
        request.setContextPath("/OpenKM");
        request.setRequestUri("/OpenKM/Download");

        assertEquals("/Download", DynamicResourceInjectFilter.requestPathWithoutContext(request));
    }

    @Test
    void requestPathWithoutContextUsesRootForEmptyPath() {
        StubHttpServletRequest request = new StubHttpServletRequest();
        request.setContextPath("/app");
        request.setRequestUri("/app");

        assertEquals("/", DynamicResourceInjectFilter.requestPathWithoutContext(request));
    }
}
