package io.github.connellite.injector.response;

import io.github.connellite.injector.http.MediaType;
import io.github.connellite.injector.util.BufferLimits;

#if JAKARTA
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
#else
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
#endif
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Buffers HTML responses for injection. Non-HTML file responses are streamed directly
 * once {@code Content-Type} is known, or when the buffered volume exceeds the configured
 * limit while the type is still unknown.
 */
@SuppressWarnings("resource")
public class CharResponseWrapper extends HttpServletResponseWrapper {

    private enum OutputChannel {
        NONE, WRITER, STREAM
    }

    private enum DeliveryMode {
        UNDECIDED, BUFFER, PASSTHROUGH
    }

    private final long maxBufferSize;

    private DeliveryMode deliveryMode = DeliveryMode.UNDECIDED;
    private OutputChannel channel = OutputChannel.NONE;
    private CharArrayWriter charBuffer;
    private ByteArrayOutputStream byteBuffer;
    private BufferedServletOutputStream boundedStream;
    private DelegatingServletOutputStream passthroughStream;
    private ServletOutputStream servletOutputStream;
    private PrintWriter writer;

    public CharResponseWrapper(HttpServletResponse response, long maxBufferSize) {
        super(response);
        if (maxBufferSize <= 0) {
            throw new IllegalArgumentException("maxBufferSize must be positive");
        }
        this.maxBufferSize = maxBufferSize;
    }

    public boolean isPassthrough() {
        return deliveryMode == DeliveryMode.PASSTHROUGH;
    }

    public static boolean isHtmlContent(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("text/html");
    }

    @Override
    public void setContentType(String type) {
        super.setContentType(type);
        onContentType(type);
    }

    @Override
    public void setHeader(String name, String value) {
        super.setHeader(name, value);
        if ("Content-Type".equalsIgnoreCase(name)) {
            onContentType(value);
        }
    }

    @Override
    public void addHeader(String name, String value) {
        super.addHeader(name, value);
        if ("Content-Type".equalsIgnoreCase(name)) {
            onContentType(value);
        }
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (channel == OutputChannel.WRITER) {
            throw new IllegalStateException("getWriter() has already been called on this response");
        }
        if (channel == OutputChannel.NONE) {
            channel = OutputChannel.STREAM;
            servletOutputStream = new AdaptiveServletOutputStream(this);
        }
        return servletOutputStream;
    }

    @Override
    public PrintWriter getWriter() {
        if (channel == OutputChannel.STREAM) {
            throw new IllegalStateException("getOutputStream() has already been called on this response");
        }
        if (channel == OutputChannel.NONE) {
            channel = OutputChannel.WRITER;
            // setContentType("text/plain") may already have selected PASSTHROUGH.
            // The writer body stays in charBuffer and has not been sent yet.
            // https://github.com/openkm/document-management-system/blob/ce175be6e436a752e81ef68068e9f1acf2d28adb/src/main/java/com/openkm/servlet/frontend/FileUploadServlet.java#L87
            // https://github.com/openkm/document-management-system/blob/ce175be6e436a752e81ef68068e9f1acf2d28adb/src/main/java/com/openkm/servlet/frontend/FileUploadServlet.java#L470
            deliveryMode = DeliveryMode.BUFFER;
            charBuffer = new BoundedCharArrayWriter(maxBufferSize);
            writer = new PrintWriter(charBuffer, true);
        }
        return writer;
    }

    /**
     * Buffered bodies must not commit the real response before the filter writes them.
     */
    @Override
    public void flushBuffer() throws IOException {
        if (deliveryMode != DeliveryMode.PASSTHROUGH) {
            return;
        }
        flushPendingBytes();
        super.flushBuffer();
    }

    /**
     * Sends bytes written before the switch to streaming when the servlet wrote nothing afterwards.
     */
    public void finishPassthrough() throws IOException {
        if (deliveryMode == DeliveryMode.PASSTHROUGH) {
            flushPendingBytes();
        }
    }

    public boolean hasBody() {
        if (isPassthrough()) {
            return false;
        }
        return switch (channel) {
            case NONE -> false;
            case WRITER -> charBuffer.size() > 0;
            case STREAM -> byteBuffer != null && byteBuffer.size() > 0;
        };
    }

    public boolean usedCharacterWriter() {
        return channel == OutputChannel.WRITER;
    }

    public String getBodyAsString() {
        if (isPassthrough()) {
            return "";
        }
        return switch (channel) {
            case NONE -> "";
            case WRITER -> charBuffer.toString();
            case STREAM -> byteBuffer.toString(characterEncodingOrUtf8());
        };
    }

    public byte[] getBodyAsBytes() {
        if (isPassthrough()) {
            return new byte[0];
        }
        return switch (channel) {
            case NONE -> new byte[0];
            case WRITER -> charBuffer.toString().getBytes(characterEncodingOrUtf8());
            case STREAM -> byteBuffer.toByteArray();
        };
    }

    public void writeBodyTo(HttpServletResponse response) throws IOException {
        byte[] body = getBodyAsBytes();
        if (body.length == 0) {
            return;
        }
        response.setContentLengthLong(body.length);
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write(body);
        outputStream.flush();
    }

    public static Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding.trim());
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * Charset declared on the Content-Type header, or {@code null} when the header does not name one.
     * Parsed with Tomcat's media-type reader. {@code getCharacterEncoding()} is not used:
     * the container returns ISO-8859-1 when the header has no charset.
     */
    public static String charsetParameter(String contentType) {
        MediaType mediaType = MediaType.parse(contentType);
        if (mediaType == null) {
            return null;
        }
        String charset = mediaType.getCharset();
        if (charset == null || charset.isBlank()) {
            return null;
        }
        return charset;
    }

    void writeStreamByte(int b) throws IOException {
        if (deliveryMode == DeliveryMode.PASSTHROUGH) {
            flushPendingBytes();
            passthroughStream().write(b);
            return;
        }

        ensureByteBuffer();

        if (deliveryMode == DeliveryMode.BUFFER) {
            boundedStream().write(b);
            return;
        }

        if (shouldSwitchToPassthrough(1)) {
            enablePassthrough();
            flushPendingBytes();
            passthroughStream().write(b);
            return;
        }

        byteBuffer.write(b);
    }

    void writeStreamBytes(byte[] source, int offset, int length) throws IOException {
        if (length <= 0) {
            return;
        }
        if (deliveryMode == DeliveryMode.PASSTHROUGH) {
            flushPendingBytes();
            passthroughStream().write(source, offset, length);
            return;
        }

        ensureByteBuffer();

        if (deliveryMode == DeliveryMode.BUFFER) {
            boundedStream().write(source, offset, length);
            return;
        }

        if (shouldSwitchToPassthrough(length)) {
            enablePassthrough();
            flushPendingBytes();
            passthroughStream().write(source, offset, length);
            return;
        }

        byteBuffer.write(source, offset, length);
    }

    private boolean shouldSwitchToPassthrough(int incomingBytes) {
        if (BufferLimits.isUnlimited(maxBufferSize)) {
            return false;
        }
        long nextSize = (long) byteBuffer.size() + incomingBytes;
        return incomingBytes > maxBufferSize || nextSize > maxBufferSize;
    }

    private void onContentType(String contentType) {
        if (isHtmlContent(contentType)) {
            deliveryMode = DeliveryMode.BUFFER;
            return;
        }
        if (contentType != null && !contentType.isBlank()) {
            enablePassthrough();
        }
    }

    /**
     * Only records the mode. setContentType can run before the servlet has read the request body,
     * so the real output stream is touched only once body bytes are written or the filter finishes.
     */
    private void enablePassthrough() {
        if (deliveryMode == DeliveryMode.PASSTHROUGH) {
            return;
        }
        if (channel == OutputChannel.WRITER) {
            deliveryMode = DeliveryMode.BUFFER;
            return;
        }
        deliveryMode = DeliveryMode.PASSTHROUGH;
    }

    private void flushPendingBytes() throws IOException {
        if (byteBuffer == null) {
            return;
        }
        byte[] pending = byteBuffer.toByteArray();
        byteBuffer = null;
        boundedStream = null;
        if (pending.length > 0) {
            passthroughStream().write(pending);
        }
    }

    private void ensureByteBuffer() {
        if (byteBuffer == null) {
            byteBuffer = new ByteArrayOutputStream();
        }
    }

    private BufferedServletOutputStream boundedStream() {
        if (boundedStream == null) {
            boundedStream = new BufferedServletOutputStream(byteBuffer, maxBufferSize);
        }
        return boundedStream;
    }

    private DelegatingServletOutputStream passthroughStream() throws IOException {
        if (passthroughStream == null) {
            passthroughStream = new DelegatingServletOutputStream(getResponse().getOutputStream());
        }
        return passthroughStream;
    }

    private Charset characterEncodingOrUtf8() {
        return resolveCharset(getCharacterEncoding());
    }
}
