package io.github.connellite.injector;

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
final class FilterConfigParser {

    static final String PARAM_RESOURCES = "resources";
    static final String PARAM_MAX_BUFFER_BYTES = "maxBufferBytes";
    static final String PARAM_BUFFER_LIMIT_DISABLED = "bufferLimitDisabled";
    static final String PARAM_EXCLUDE_URL_PATTERNS = "excludeUrlPatterns";
    static final long DEFAULT_MAX_BUFFER_BYTES = 5 * 1024 * 1024;

    record Settings(String resourceRoot, long maxBufferChars, List<String> excludeUrlPatterns) {
    }

    static Settings parse(FilterConfig filterConfig) throws ServletException {
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

        long maxBufferChars;
        if (parseBoolean(filterConfig.getInitParameter(PARAM_BUFFER_LIMIT_DISABLED), false)) {
            maxBufferChars = CharResponseWrapper.UNLIMITED_BUFFER_CHARS;
        } else {
            maxBufferChars = parsePositiveLong(
                    filterConfig.getInitParameter(PARAM_MAX_BUFFER_BYTES),
                    DEFAULT_MAX_BUFFER_BYTES,
                    PARAM_MAX_BUFFER_BYTES
            );
        }

        return new Settings(resourceRoot, maxBufferChars, excludeUrlPatterns);
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
