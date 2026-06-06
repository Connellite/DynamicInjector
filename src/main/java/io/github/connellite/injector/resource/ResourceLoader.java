package io.github.connellite.injector.resource;

import lombok.experimental.UtilityClass;
import lombok.extern.java.Log;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

@Log
@UtilityClass
public class ResourceLoader {
    /**
     * Loads file contents when the file exists and is readable; otherwise returns an empty string.
     */
    public static String loadResource(Path path) {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            return "";
        }

        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            return "";
        }
    }
}
