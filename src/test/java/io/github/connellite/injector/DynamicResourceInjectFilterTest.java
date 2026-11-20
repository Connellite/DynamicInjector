package io.github.connellite.injector;

import io.github.connellite.injector.support.StubHttpServletRequest;
import io.github.connellite.injector.support.StubHttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

#if JAKARTA
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
#else
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
#endif
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void leavesUndeclaredHtmlBytesAndContentTypeUntouched(@TempDir Path resources) throws Exception {
        DynamicResourceInjectFilter filter = filterWithResources(resources);
        StubHttpServletRequest request = new StubHttpServletRequest();
        request.setServletPath("/frontend/Download");
        StubHttpServletResponse response = new StubHttpServletResponse();
        response.setCharacterEncoding("ISO-8859-1");
        byte[] file = "<html><body>Привет</body></html>".getBytes(StandardCharsets.UTF_8);

        filter.doFilter(request, response, (FilterChain) (servletRequest, servletResponse) -> {
            HttpServletResponse http = (HttpServletResponse) servletResponse;
            http.setContentType("text/html");
            http.getOutputStream().write(file);
        });

        assertEquals("text/html", response.getContentType());
        assertArrayEquals(file, response.getBodyBytes());
    }

    @Test
    void keepsDeclaredHtmlCharset(@TempDir Path resources) throws Exception {
        DynamicResourceInjectFilter filter = filterWithResources(resources);
        StubHttpServletRequest request = new StubHttpServletRequest();
        request.setServletPath("/frontend/index.jsp");
        StubHttpServletResponse response = new StubHttpServletResponse();
        String html = "<p>Привет</p>";

        filter.doFilter(request, response, (FilterChain) (servletRequest, servletResponse) -> {
            HttpServletResponse http = (HttpServletResponse) servletResponse;
            http.setContentType("text/html; charset=UTF-8");
            http.getWriter().write(html);
        });

        assertEquals("text/html; charset=UTF-8", response.getContentType());
        assertArrayEquals(html.getBytes(StandardCharsets.UTF_8), response.getBodyBytes());
    }

    private static DynamicResourceInjectFilter filterWithResources(Path resources) throws Exception {
        DynamicResourceInjectFilter filter = new DynamicResourceInjectFilter();
        filter.init(new FilterConfig() {
            @Override
            public String getFilterName() {
                return "DynamicResourceInjectFilter";
            }

            @Override
            public ServletContext getServletContext() {
                return null;
            }

            @Override
            public String getInitParameter(String name) {
                return "resources".equals(name) ? resources.toString() : null;
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return Collections.enumeration(Collections.singletonList("resources"));
            }
        });
        return filter;
    }
}
