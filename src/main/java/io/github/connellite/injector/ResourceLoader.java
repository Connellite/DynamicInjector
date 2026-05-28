package io.github.connellite.injector;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResourceLoader {
    private static final Logger LOGGER = Logger.getLogger(ResourceLoader.class.getName());

    /**
     * Loads file contents when the file exists and is readable; otherwise returns an empty string.
     */
    public String loadResource(File file) {
        if (file == null || !file.exists() || !file.isFile() || !file.canRead()) {
            return "";
        }

        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return "";
        }
    }
}
