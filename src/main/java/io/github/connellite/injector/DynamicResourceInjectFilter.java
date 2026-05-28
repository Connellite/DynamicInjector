package io.github.connellite.injector;

#if JAKARTA
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
#else

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
#endif
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DynamicResourceInjectFilter implements Filter {

    static final String PARAM_RESOURCES = "resources";
    static final String PARAM_MAX_BUFFER_BYTES = "maxBufferBytes";
    static final int DEFAULT_MAX_BUFFER_BYTES = 5 * 1024 * 1024;

    private static final Logger LOGGER = Logger.getLogger(DynamicResourceInjectFilter.class.getName());

    private ResourcePathResolver resolver;
    private ResourceLoader loader;
    private int maxBufferBytes = DEFAULT_MAX_BUFFER_BYTES;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String resourceRoot = filterConfig.getInitParameter(PARAM_RESOURCES);

        if (resourceRoot == null || resourceRoot.trim().isEmpty()) {
            String errorMsg = "Filter init parameter '" + PARAM_RESOURCES + "' is not configured.";
            LOGGER.severe(errorMsg);
            throw new ServletException(errorMsg);
        }

        File rootDir = new File(resourceRoot);
        if (!rootDir.exists() || !rootDir.isDirectory() || !rootDir.canRead()) {
            String errorMsg = "Resource directory does not exist or is not readable: " + resourceRoot;
            LOGGER.severe(errorMsg);
            throw new ServletException(errorMsg);
        }

        maxBufferBytes = parsePositiveInt(
                filterConfig.getInitParameter(PARAM_MAX_BUFFER_BYTES),
                DEFAULT_MAX_BUFFER_BYTES,
                PARAM_MAX_BUFFER_BYTES
        );

        this.resolver = new ResourcePathResolver(resourceRoot);
        this.loader = new ResourceLoader();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        if (httpRequest.getDispatcherType() != DispatcherType.REQUEST) {
            chain.doFilter(request, response);
            return;
        }

        CharResponseWrapper wrappedResponse = new CharResponseWrapper(httpResponse, maxBufferBytes);
        try {
            chain.doFilter(request, wrappedResponse);
            writeResponse(httpRequest, httpResponse, wrappedResponse);
        } catch (SecurityException e) {
            LOGGER.log(Level.WARNING, "Blocked resource path for {0}: {1}", new Object[]{httpRequest.getRequestURI(), e.getMessage()});
            sendErrorIfPossible(httpResponse, HttpServletResponse.SC_FORBIDDEN, e);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Invalid request path encoding for {0}: {1}", new Object[]{httpRequest.getRequestURI(), e.getMessage()});
            sendErrorIfPossible(httpResponse, HttpServletResponse.SC_BAD_REQUEST, e);
        }
    }

    @Override
    public void destroy() {
        resolver = null;
        loader = null;
    }

    private void writeResponse(HttpServletRequest request, HttpServletResponse response,
                               CharResponseWrapper wrappedResponse) throws IOException {
        if (response.isCommitted()) {
            LOGGER.fine(() -> "Response already committed for " + request.getRequestURI() + ", skipping injection");
            return;
        }

        if (!wrappedResponse.hasBody()) {
            return;
        }

        if (!isHtmlContent(wrappedResponse.getContentType())) {
            response.resetBuffer();
            wrappedResponse.writeBodyTo(response);
            return;
        }

        String html = wrappedResponse.getBodyAsString();
        String requestPath = ResourcePathResolver.requestPath(request);

        File cssFile = resolver.resolveResourcePath(requestPath, ".css").toFile();
        File jsFile = resolver.resolveResourcePath(requestPath, ".js").toFile();
        File htmlFile = resolver.resolveResourcePath(requestPath, ".html").toFile();

        String css = loader.loadResource(cssFile);
        String js = loader.loadResource(jsFile);
        String fragment = loader.loadResource(htmlFile);

        String modifiedHtml = HtmlInjector.parse(html)
                .injectCss(css)
                .injectJs(js)
                .injectHtmlFragment(fragment)
                .toHtml();

        byte[] body = modifiedHtml.getBytes(StandardCharsets.UTF_8);
        response.resetBuffer();
        response.setContentType("text/html; charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static boolean isHtmlContent(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("text/html");
    }

    private static int parsePositiveInt(String rawValue, int defaultValue, String paramName) throws ServletException {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(rawValue.trim());
            if (value <= 0) {
                throw new NumberFormatException("not positive");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ServletException("Invalid filter init parameter '" + paramName + "': " + rawValue, e);
        }
    }

    private static void sendErrorIfPossible(HttpServletResponse response, int status, Throwable cause) throws ServletException, IOException {
        if (response.isCommitted()) {
            throw new ServletException(cause);
        }
        response.resetBuffer();
        response.sendError(status);
    }
}
