package io.github.connellite.injector;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HexFormat;

public class ResourcePathResolver {
    private final String resourceRoot;

    public ResourcePathResolver(String resourceRoot) {
        this.resourceRoot = resourceRoot;
    }

    public Path resolveResourcePath(String servletPath, String extension) {
        String cleanPath = servletPath.split("\\?", 2)[0];
        cleanPath = uriDecode(cleanPath, StandardCharsets.UTF_8);
        while (cleanPath.startsWith("/") || cleanPath.startsWith("\\")) {
            cleanPath = cleanPath.substring(1);
        }
        cleanPath = IoUtils.separatorsToSystem(cleanPath);
        cleanPath = IoUtils.removeExtension(cleanPath);

        Path resourceRootPath = Paths.get(resourceRoot).normalize();
        String combinedPath = cleanPath.isEmpty() ? extension : cleanPath + extension;
        Path finalPath = resourceRootPath.resolve(combinedPath).normalize();

        if (!finalPath.startsWith(resourceRootPath)) {
            throw new SecurityException("Attempt to access path outside resource root");
        }

        return finalPath;
    }

    /** URI percent-decoding similar to Spring's {@code UriUtils.decode}. */
    @SuppressWarnings("SameParameterValue")
    static String uriDecode(String source, Charset charset) {
        int length = source.length();
        int firstPercentIndex = source.indexOf('%');
        if (length == 0 || firstPercentIndex < 0) {
            return source;
        }

        StringBuilder output = new StringBuilder(length);
        output.append(source, 0, firstPercentIndex);
        byte[] bytes = null;
        int i = firstPercentIndex;
        while (i < length) {
            char ch = source.charAt(i);
            if (ch == '%') {
                try {
                    if (bytes == null) {
                        bytes = new byte[(length - i) / 3];
                    }

                    int pos = 0;
                    while (i + 2 < length && ch == '%') {
                        bytes[pos++] = (byte) HexFormat.fromHexDigits(source, i + 1, i + 3);
                        i += 3;
                        if (i < length) {
                            ch = source.charAt(i);
                        }
                    }

                    if (i < length && ch == '%') {
                        throw new IllegalArgumentException("Incomplete trailing escape (%) pattern");
                    }

                    output.append(new String(bytes, 0, pos, charset));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
                }
            } else {
                output.append(ch);
                i++;
            }
        }
        return output.toString();
    }
}
