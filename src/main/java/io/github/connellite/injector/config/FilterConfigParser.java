package io.github.connellite.injector.config;

import io.github.connellite.injector.pattern.UrlPatternMatcher;
import io.github.connellite.injector.util.BufferLimits;
import lombok.experimental.UtilityClass;

#if JAKARTA
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
#else
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
#endif
import java.io.File;
import java.util.List;

@UtilityClass
public final class FilterConfigParser {

    public static final String PARAM_RESOURCES = "resources";
    public static final String PARAM_MAX_BUFFER_SIZE = "maxBufferSize";
    public static final String PARAM_BUFFER_LIMIT_DISABLED = "bufferLimitDisabled";
    public static final String PARAM_EXCLUDE_URL_PATTERNS = "excludeUrlPatterns";
    public static final long DEFAULT_MAX_BUFFER_SIZE = 5 * 1024 * 1024;

    public record Settings(String resourceRoot, long maxBufferSize, List<String> excludeUrlPatterns) {
    }

    public static Settings parse(FilterConfig filterConfig) throws ServletException {
        String resourceRoot = filterConfig.getInitParameter(PARAM_RESOURCES);
        if (resourceRoot == null || resourceRoot.trim().isEmpty()) {
            throw new ServletException("Filter init parameter '" + PARAM_RESOURCES + "' is not configured.");
        }

        File rootDir = new File(resourceRoot);
        if (!rootDir.exists() || !rootDir.isDirectory() || !rootDir.canRead()) {
            throw new ServletException("Resource directory does not exist or is not readable: " + resourceRoot);
        }

        List<String> excludeUrlPatterns = UrlPatternMatcher.parsePatterns(
                filterConfig.getInitParameter(PARAM_EXCLUDE_URL_PATTERNS)
        );

        long maxBufferSize;
        if (parseBoolean(filterConfig.getInitParameter(PARAM_BUFFER_LIMIT_DISABLED), false)) {
            maxBufferSize = BufferLimits.UNLIMITED;
        } else {
            maxBufferSize = parsePositiveLong(
                    filterConfig.getInitParameter(PARAM_MAX_BUFFER_SIZE),
                    DEFAULT_MAX_BUFFER_SIZE,
                    PARAM_MAX_BUFFER_SIZE
            );
        }

        return new Settings(resourceRoot, maxBufferSize, excludeUrlPatterns);
    }

    static boolean parseBoolean(String rawValue, boolean defaultValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(rawValue.trim());
    }

    static long parsePositiveLong(String rawValue, long defaultValue, String paramName) throws ServletException {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            long value = Long.parseLong(rawValue.trim());
            if (value <= 0) {
                throw new NumberFormatException("not positive");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ServletException("Invalid filter init parameter '" + paramName + "': " + rawValue, e);
        }
    }
}
