package io.github.connellite.injector;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourcePathResolverTest {

    @Test
    void combineRequestPathWithPathInfo() {
        assertEquals("/app/home", ResourcePathResolver.combineRequestPath("/app", "/home"));
        assertEquals("/home", ResourcePathResolver.combineRequestPath("", "/home"));
        assertEquals("/app", ResourcePathResolver.combineRequestPath("/app", null));
    }

    @Test
    void resolveBasicPath() {
        ResourcePathResolver resolver = new ResourcePathResolver("/base/path");
        Path result = resolver.resolveResourcePath("/styles/main.css", ".css");
        assertPathsEqual("/base/path/styles/main.css", result);
    }

    @Test
    void resolvePathWithQueryString() {
        ResourcePathResolver resolver = new ResourcePathResolver("/assets");
        Path result = resolver.resolveResourcePath("/image.jpg?width=200&height=300", ".jpg");
        assertPathsEqual("/assets/image.jpg", result);
    }

    @Test
    void resolvePathWithUriEncoding() {
        ResourcePathResolver resolver = new ResourcePathResolver("/data");
        Path result = resolver.resolveResourcePath("/docs/spaces%20in%20name.txt", ".txt");
        assertPathsEqual("/data/docs/spaces in name.txt", result);
    }

    @Test
    void resolvePathWithDifferentSeparators() {
        ResourcePathResolver resolver = new ResourcePathResolver("/root");
        Path result = resolver.resolveResourcePath("\\mixed\\separators\\file.json", ".json");
        assertPathsEqual("/root/mixed/separators/file.json", result);
    }

    @Test
    void resolvePathWithMultipleLeadingSlashes() {
        ResourcePathResolver resolver = new ResourcePathResolver("/content");
        Path result = resolver.resolveResourcePath("/////deep/path.xml", ".xml");
        assertPathsEqual("/content/deep/path.xml", result);
    }

    @Test
    void throwsSecurityExceptionForPathTraversal() {
        ResourcePathResolver resolver = new ResourcePathResolver("/safe/dir");
        assertThrows(SecurityException.class, () -> resolver.resolveResourcePath("../../../etc/passwd", ""));
    }

    @Test
    void handlesEmptyPath() {
        ResourcePathResolver resolver = new ResourcePathResolver("/base");
        Path result = resolver.resolveResourcePath("", ".html");
        String expected = "/base/.html".replace("/", File.separator);
        assertPathsEqual(expected, result);
    }

    @Test
    void handlesRootPath() {
        ResourcePathResolver resolver = new ResourcePathResolver("/www");
        Path result = resolver.resolveResourcePath("/", ".index");
        assertPathsEqual("/www/.index", result);
    }

    @Test
    void uriDecodeSimpleString() {
        String result = ResourcePathResolver.uriDecode("plain%20text", StandardCharsets.UTF_8);
        assertEquals("plain text", result);
    }

    @Test
    void uriDecodeWithMultipleEncodings() {
        String result = ResourcePathResolver.uriDecode("a%20b%3Cc%3E", StandardCharsets.UTF_8);
        assertEquals("a b<c>", result);
    }

    @Test
    void uriDecodeThrowsOnInvalidEncoding() {
        assertThrows(IllegalArgumentException.class, () -> ResourcePathResolver.uriDecode("invalid%zz", StandardCharsets.UTF_8));
    }

    @Test
    void uriDecodeHandlesIncompleteEscape() {
        assertThrows(IllegalArgumentException.class, () -> ResourcePathResolver.uriDecode("cut%2", StandardCharsets.UTF_8));
    }

    @Test
    void uriDecodePreservesUnencodedChars() {
        String input = "normal!@#$string";
        String result = ResourcePathResolver.uriDecode(input, StandardCharsets.UTF_8);
        assertEquals(input, result);
    }

    private void assertPathsEqual(String expected, Path actual) {
        assertEquals(
                Paths.get(expected).normalize().toString(),
                actual.normalize().toString()
        );
    }
}
