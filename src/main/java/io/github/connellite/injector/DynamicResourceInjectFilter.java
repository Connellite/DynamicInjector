package io.github.connellite.injector;

#if JAKARTA
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
#else

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
    private static final String PARAM_NAME = "resources";
    private static final Logger LOGGER = Logger.getLogger(DynamicResourceInjectFilter.class.getName());

    private ResourcePathResolver resolver;
    private ResourceLoader loader;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String resourceRoot = filterConfig.getInitParameter(PARAM_NAME);

        if (resourceRoot == null || resourceRoot.trim().isEmpty()) {
            String errorMsg = "Filter init parameter '" + PARAM_NAME + "' is not configured.";
            LOGGER.severe(errorMsg);
            throw new ServletException(errorMsg);
        }

        File rootDir = new File(resourceRoot);
        if (!rootDir.exists() || !rootDir.isDirectory() || !rootDir.canRead()) {
            String errorMsg = "Resource directory does not exist or is not readable: " + resourceRoot;
            LOGGER.severe(errorMsg);
            throw new ServletException(errorMsg);
        }

        this.resolver = new ResourcePathResolver(resourceRoot);
        loader = new ResourceLoader();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        CharResponseWrapper wrappedResp = new CharResponseWrapper(res);
        try {
            chain.doFilter(request, wrappedResp);

            String html = wrappedResp.toString();

            if (!isHtmlContent(wrappedResp.getContentType())) {
                response.getWriter().write(html);
                return;
            }

            File cssFile = resolver.resolveResourcePath(req.getServletPath(), ".css").toFile();
            File jsFile = resolver.resolveResourcePath(req.getServletPath(), ".js").toFile();
            File htmlFile = resolver.resolveResourcePath(req.getServletPath(), ".html").toFile();

            String css = loader.loadResource(cssFile);
            String js = loader.loadResource(jsFile);
            String fragment = loader.loadResource(htmlFile);

            String out = HtmlInjector.parse(html)
                    .injectCss(css)
                    .injectJs(js)
                    .injectHtmlFragment(fragment)
                    .toHtml();

            byte[] outBytes = out.getBytes(StandardCharsets.UTF_8);
            res.setContentLength(outBytes.length);
            res.setContentType("text/html; charset=UTF-8");
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getOutputStream().write(outBytes);
        } catch (SecurityException e) {
            LOGGER.log(Level.WARNING, "Blocked resource path for {0}: {1}", new Object[]{req.getRequestURI(), e.getMessage()});
            if (res.isCommitted()) {
                throw new ServletException(e);
            }
            res.resetBuffer();
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    private boolean isHtmlContent(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("text/html");
    }
}
