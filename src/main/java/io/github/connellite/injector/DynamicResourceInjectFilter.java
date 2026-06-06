package io.github.connellite.injector;
import lombok.extern.java.Log;

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
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

@Log
public class DynamicResourceInjectFilter implements Filter {

    private ResourcePathResolver resolver;
    private long maxBufferChars = FilterConfigParser.DEFAULT_MAX_BUFFER_BYTES;
    private List<String> excludeUrlPatterns = List.of();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        try {
            FilterConfigParser.Settings settings = FilterConfigParser.parse(filterConfig);
            this.maxBufferChars = settings.maxBufferChars();
            this.excludeUrlPatterns = settings.excludeUrlPatterns();
            this.resolver = new ResourcePathResolver(settings.resourceRoot());
        } catch (ServletException e) {
            log.severe(e.getMessage());
            throw e;
        }
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

        if (isExcluded(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        CharResponseWrapper wrappedResponse = new CharResponseWrapper(httpResponse, maxBufferChars);
        try {
            chain.doFilter(request, wrappedResponse);
            writeResponse(httpRequest, httpResponse, wrappedResponse);
        } catch (SecurityException e) {
            log.log(Level.WARNING, "Blocked resource path for {0}: {1}", new Object[]{httpRequest.getRequestURI(), e.getMessage()});
            sendErrorIfPossible(httpResponse, HttpServletResponse.SC_FORBIDDEN, e);
        } catch (IllegalArgumentException e) {
            log.log(Level.WARNING, "Invalid request path encoding for {0}: {1}", new Object[]{httpRequest.getRequestURI(), e.getMessage()});
            sendErrorIfPossible(httpResponse, HttpServletResponse.SC_BAD_REQUEST, e);
        }
    }

    @Override
    public void destroy() {
        resolver = null;
    }

    private void writeResponse(HttpServletRequest request, HttpServletResponse response,
                               CharResponseWrapper wrappedResponse) throws IOException {
        if (response.isCommitted()) {
            log.fine(() -> "Response already committed for " + request.getRequestURI() + ", skipping injection");
            return;
        }

        if (!wrappedResponse.hasBody()) {
            return;
        }

        if (!isHtmlContent(wrappedResponse.getContentType())) {
            // resetBuffer() only; preserves headers set by the downstream servlet.
            response.resetBuffer();
            wrappedResponse.writeBodyTo(response);
            return;
        }

        String html = wrappedResponse.getBodyAsString();
        String requestPath = ResourcePathResolver.requestPath(request);

        Path cssPath = resolver.resolveResourcePath(requestPath, ".css");
        Path jsPath = resolver.resolveResourcePath(requestPath, ".js");
        Path htmlPath = resolver.resolveResourcePath(requestPath, ".html");

        String css = ResourceLoader.loadResource(cssPath);
        String js = ResourceLoader.loadResource(jsPath);
        String fragment = ResourceLoader.loadResource(htmlPath);

        String modifiedHtml = HtmlInjector.parse(html)
                .injectCss(css)
                .injectJs(js)
                .injectHtmlFragment(fragment)
                .toHtml();

        Charset charset = CharResponseWrapper.resolveCharset(wrappedResponse.getCharacterEncoding());
        byte[] body = modifiedHtml.getBytes(charset);
        // resetBuffer() clears the body but keeps headers (ETag, Content-Language, etc.); do not use reset().
        response.resetBuffer();
        response.setContentType("text/html; charset=" + charset.name());
        response.setCharacterEncoding(charset.name());
        response.setContentLengthLong(body.length);
        response.getOutputStream().write(body);
    }

    private boolean isExcluded(HttpServletRequest request) {
        if (excludeUrlPatterns.isEmpty()) {
            return false;
        }
        return UrlPatternMatcher.matchesAny(requestPathWithoutContext(request), excludeUrlPatterns);
    }

    static String requestPathWithoutContext(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.isEmpty() ? "/" : path;
    }

    private static boolean isHtmlContent(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("text/html");
    }

    private static void sendErrorIfPossible(HttpServletResponse response, int status, Throwable cause) throws ServletException, IOException {
        if (response.isCommitted()) {
            throw new ServletException(cause);
        }
        response.resetBuffer();
        response.sendError(status);
    }
}
