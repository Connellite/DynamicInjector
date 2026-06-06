package io.github.connellite.injector;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.List;

/**
 * Parses {@code excludeUrlPatterns} and matches request paths using {@link AntPathMatcher}.
 *
 * <p>Pattern syntax follows Spring Ant-style rules; see {@link AntPathMatcher} for details
 * and the upstream source link.
 */
@UtilityClass
final class UrlPatternMatcher {

    private static final class PathMatherHolder {
        private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    }

    static List<String> parsePatterns(String rawPatterns) {
        if (rawPatterns == null || rawPatterns.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawPatterns.split("[,\\s]+"))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList();
    }

    static boolean matchesAny(String path, List<String> patterns) {
        for (String pattern : patterns) {
            if (PathMatherHolder.PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    static boolean matches(String pattern, String path) {
        return PathMatherHolder.PATH_MATCHER.match(pattern, path);
    }
}
