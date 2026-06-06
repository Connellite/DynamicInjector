package io.github.connellite.injector;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharResponseWrapperTest {

    private static final int MAX_BUFFER = 1024;

    @Test
    void buffersWriterOutput() throws IOException {
        CharResponseWrapper wrapper = new CharResponseWrapper(new StubHttpServletResponse(), MAX_BUFFER);

        try (PrintWriter writer = wrapper.getWriter()) {
            writer.write("hello");
        }

        assertTrue(wrapper.usedCharacterWriter());
        assertEquals("hello", wrapper.getBodyAsString());
        assertEquals("hello", new String(wrapper.getBodyAsBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void buffersStreamOutput() throws IOException {
        CharResponseWrapper wrapper = new CharResponseWrapper(new StubHttpServletResponse(), MAX_BUFFER);

        wrapper.getOutputStream().write(new byte[]{1, 2, 3});

        assertFalse(wrapper.usedCharacterWriter());
        assertArrayEquals(new byte[]{1, 2, 3}, wrapper.getBodyAsBytes());
    }

    @Test
    void rejectsWriterAfterStream() {
        CharResponseWrapper wrapper = new CharResponseWrapper(new StubHttpServletResponse(), MAX_BUFFER);
        wrapper.getOutputStream();

        assertThrows(IllegalStateException.class, wrapper::getWriter);
    }

    @Test
    void rejectsStreamAfterWriter() throws IOException {
        CharResponseWrapper wrapper = new CharResponseWrapper(new StubHttpServletResponse(), MAX_BUFFER);
        wrapper.getWriter();

        assertThrows(IllegalStateException.class, wrapper::getOutputStream);
    }

    @Test
    void enforcesMaxBufferSize() {
        CharResponseWrapper wrapper = new CharResponseWrapper(new StubHttpServletResponse(), 4);

        IOException error = assertThrows(IOException.class, () -> wrapper.getOutputStream().write(new byte[5]));
        assertTrue(error.getMessage().contains("buffer limit"));
    }

    @Test
    void enforcesMaxBufferSizeForWriter() {
        CharResponseWrapper wrapper = new CharResponseWrapper(new StubHttpServletResponse(), 4);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> {
            try (PrintWriter writer = wrapper.getWriter()) {
                writer.write("12345");
                writer.flush();
            }
        });
        assertNotNull(error.getMessage());
        assertTrue(error.getMessage().contains("buffer limit"));
    }

    @Test
    void allowsUnlimitedBufferSize() throws IOException {
        CharResponseWrapper wrapper = new CharResponseWrapper(
                new StubHttpServletResponse(),
                CharResponseWrapper.UNLIMITED_BUFFER_BYTES
        );

        wrapper.getOutputStream().write(new byte[10_000]);

        assertEquals(10_000, wrapper.getBodyAsBytes().length);
    }
}
