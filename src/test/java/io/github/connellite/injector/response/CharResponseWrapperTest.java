package io.github.connellite.injector.response;

import io.github.connellite.injector.support.StubHttpServletResponse;
import io.github.connellite.injector.util.BufferLimits;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void rejectsWriterAfterStream() throws IOException {
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
    void enforcesMaxBufferSizeForHtmlStream() {
        CharResponseWrapper wrapper = new CharResponseWrapper(new StubHttpServletResponse(), 4);
        wrapper.setContentType("text/html;charset=UTF-8");

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
                BufferLimits.UNLIMITED
        );

        wrapper.getOutputStream().write(new byte[10_000]);

        assertEquals(10_000, wrapper.getBodyAsBytes().length);
    }

    @Test
    void enforcesMaxBufferSizeAtExactLimitForHtml() throws IOException {
        CharResponseWrapper wrapper = new CharResponseWrapper(new StubHttpServletResponse(), 4);
        wrapper.setContentType("text/html;charset=UTF-8");

        wrapper.getOutputStream().write(new byte[]{1, 2, 3, 4});

        assertEquals(4, wrapper.getBodyAsBytes().length);
    }

    @Test
    void writeBodyToUsesContentLengthLong() throws IOException {
        StubHttpServletResponse response = new StubHttpServletResponse();
        CharResponseWrapper wrapper = new CharResponseWrapper(response, 1024);
        wrapper.getOutputStream().write(new byte[]{1, 2, 3});

        wrapper.writeBodyTo(response);

        assertEquals(3, response.getContentLengthLong());
    }

    @Test
    void charsetParameterReadsDeclaredCharsetOnly() {
        assertNull(CharResponseWrapper.charsetParameter(null));
        assertNull(CharResponseWrapper.charsetParameter("text/html"));
        assertEquals("UTF-8", CharResponseWrapper.charsetParameter("text/html; charset=UTF-8"));
        assertEquals("utf-8", CharResponseWrapper.charsetParameter("text/html;charset=\"utf-8\""));
        assertEquals("UTF-8", CharResponseWrapper.charsetParameter("text/html; Charset = \"UTF-8\""));
        assertEquals("UTF-8", CharResponseWrapper.charsetParameter("text/html; charset=\"UTF\\-8\""));
    }

    @Test
    void resolveCharsetFallsBackToUtf8ForUnknownEncoding() {
        assertEquals(StandardCharsets.UTF_8, CharResponseWrapper.resolveCharset("X-UNKNOWN-CHARSET"));
    }

    @Test
    void resolveCharsetUsesValidEncoding() {
        assertEquals(StandardCharsets.ISO_8859_1, CharResponseWrapper.resolveCharset("ISO-8859-1"));
    }

    @Test
    void readsWriterBodyWithConfiguredCharset() throws IOException {
        StubHttpServletResponse response = new StubHttpServletResponse();
        response.setCharacterEncoding("ISO-8859-1");
        CharResponseWrapper wrapper = new CharResponseWrapper(response, 1024);
        wrapper.setContentType("text/html;charset=ISO-8859-1");

        try (PrintWriter writer = wrapper.getWriter()) {
            writer.write("caf\u00E9");
        }

        assertArrayEquals("caf\u00E9".getBytes(StandardCharsets.ISO_8859_1), wrapper.getBodyAsBytes());
    }

    @Test
    void streamsNonHtmlResponsesWithoutBuffering() throws IOException {
        StubHttpServletResponse response = new StubHttpServletResponse();
        CharResponseWrapper wrapper = new CharResponseWrapper(response, 4);

        wrapper.setContentType("application/octet-stream");
        wrapper.getOutputStream().write(new byte[1000]);

        assertTrue(wrapper.isPassthrough());
        assertEquals(0, wrapper.getBodyAsBytes().length);
        assertEquals(1000, response.getBodyBytes().length);
    }

    @Test
    void stillBuffersHtmlAfterContentTypeIsSet() throws IOException {
        StubHttpServletResponse response = new StubHttpServletResponse();
        CharResponseWrapper wrapper = new CharResponseWrapper(response, 1024);

        wrapper.setContentType("text/html;charset=UTF-8");
        wrapper.getOutputStream().write("hello".getBytes(StandardCharsets.UTF_8));

        assertFalse(wrapper.isPassthrough());
        assertEquals("hello", wrapper.getBodyAsString());
    }

    @Test
    void writerBuffersWhenNonHtmlContentTypeIsSetFirst() throws IOException {
        StubHttpServletResponse response = new StubHttpServletResponse();
        CharResponseWrapper wrapper = new CharResponseWrapper(response, 1024);
        String json = "{\"ok\":true}";

        wrapper.setContentType("text/plain");
        try (PrintWriter writer = wrapper.getWriter()) {
            writer.write(json);
        }

        assertFalse(wrapper.isPassthrough());
        assertTrue(wrapper.hasBody());
        assertEquals(json, wrapper.getBodyAsString());

        wrapper.writeBodyTo(response);

        assertEquals(json.length(), response.getContentLengthLong());
        assertArrayEquals(json.getBytes(StandardCharsets.UTF_8), response.getBodyBytes());
    }

    @Test
    void writerStillBuffersAfterNonHtmlContentType() throws IOException {
        StubHttpServletResponse response = new StubHttpServletResponse();
        CharResponseWrapper wrapper = new CharResponseWrapper(response, 1024);

        try (PrintWriter writer = wrapper.getWriter()) {
            writer.write("{\"ok\":true}");
        }
        wrapper.setContentType("application/json");

        assertFalse(wrapper.isPassthrough());
        assertEquals("{\"ok\":true}", wrapper.getBodyAsString());
    }

    @Test
    void switchesToPassthroughWhenUndecidedWriteExceedsLimit() throws IOException {
        StubHttpServletResponse response = new StubHttpServletResponse();
        CharResponseWrapper wrapper = new CharResponseWrapper(response, 4);

        wrapper.getOutputStream().write(new byte[1000]);

        assertTrue(wrapper.isPassthrough());
        assertEquals(0, wrapper.getBodyAsBytes().length);
        assertEquals(1000, response.getBodyBytes().length);
    }

    @Test
    void streamsAfterLateContentTypeWithoutKeepingEntirePayload() throws IOException {
        StubHttpServletResponse response = new StubHttpServletResponse();
        CharResponseWrapper wrapper = new CharResponseWrapper(response, 4);

        wrapper.getOutputStream().write(new byte[]{1, 2});
        wrapper.setContentType("application/pdf");
        wrapper.getOutputStream().write(new byte[]{3, 4, 5, 6, 7});

        assertTrue(wrapper.isPassthrough());
        assertEquals(0, wrapper.getBodyAsBytes().length);
        assertEquals(7, response.getBodyBytes().length);
    }

    @Test
    void flushesPrefatoryBytesWhenSwitchingToStreaming() throws IOException {
        StubHttpServletResponse response = new StubHttpServletResponse();
        CharResponseWrapper wrapper = new CharResponseWrapper(response, 1024);

        wrapper.getOutputStream().write(new byte[]{1, 2});
        wrapper.setContentType("application/pdf");
        wrapper.getOutputStream().write(new byte[]{3, 4, 5});

        assertTrue(wrapper.isPassthrough());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, response.getBodyBytes());
    }
}
