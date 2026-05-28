package io.github.connellite.injector;

import java.io.File;

/**
 * Small I/O helpers replacing the former commons-io usage in this project.
 */
public final class IoUtils {

    private IoUtils() {
    }

    public static String separatorsToSystem(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        char separator = File.separatorChar;
        if (separator == '/') {
            return path.replace('\\', '/');
        }
        return path.replace('/', '\\');
    }

    public static String removeExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int extensionIndex = indexOfExtension(filename);
        if (extensionIndex == -1) {
            return filename;
        }
        return filename.substring(0, extensionIndex);
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int indexOfExtension(String filename) {
        int extensionPos = filename.lastIndexOf('.');
        if (extensionPos == -1) {
            return -1;
        }
        int lastSeparator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return lastSeparator > extensionPos ? -1 : extensionPos;
    }
}
