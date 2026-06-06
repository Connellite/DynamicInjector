package io.github.connellite.injector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlPatternMatcherTest {

    @Test
    void parsePatternsIgnoresBlankEntries() {
        assertEquals(List.of("/Download/**", "/*.jsp"), UrlPatternMatcher.parsePatterns(" /Download/** , , /*.jsp "));
    }

    @Test
    void matchesDownloadSubtree() {
        assertTrue(UrlPatternMatcher.matches("/Download/**", "/Download"));
        assertTrue(UrlPatternMatcher.matches("/Download/**", "/Download/file.pdf"));
        assertFalse(UrlPatternMatcher.matches("/Download/**", "/Downloads/file.pdf"));
    }

    @Test
    void matchesSingleSegmentWildcardPattern() {
        assertTrue(UrlPatternMatcher.matches("/Download/*", "/Download/file.pdf"));
        assertFalse(UrlPatternMatcher.matches("/Download/*", "/Download"));
        assertFalse(UrlPatternMatcher.matches("/Download/*", "/Other/file.pdf"));
    }

    @Test
    void matchesExtensionPattern() {
        assertTrue(UrlPatternMatcher.matches("/*.jsp", "/login.jsp"));
        assertTrue(UrlPatternMatcher.matches("/**/*.jsp", "/app/login.jsp"));
        assertFalse(UrlPatternMatcher.matches("/*.jsp", "/login.html"));
    }

    @Test
    void matchesExactPattern() {
        assertTrue(UrlPatternMatcher.matches("/health", "/health"));
        assertFalse(UrlPatternMatcher.matches("/health", "/health/check"));
    }

    @Test
    void matchesAnyPattern() {
        List<String> patterns = UrlPatternMatcher.parsePatterns("/Download/**, /*.jsp");
        assertTrue(UrlPatternMatcher.matchesAny("/Download/export", patterns));
        assertTrue(UrlPatternMatcher.matchesAny("/login.jsp", patterns));
        assertFalse(UrlPatternMatcher.matchesAny("/home", patterns));
    }
}
