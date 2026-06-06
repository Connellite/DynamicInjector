package io.github.connellite.injector;

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
 * Buffers the downstream response body. Either {@link #getWriter()} or {@link #getOutputStream()}
 * may be used, never both (Servlet specification).
 */
public class CharResponseWrapper extends HttpServletResponseWrapper {

    static final long UNLIMITED_BUFFER_CHARS = Long.MAX_VALUE;

    private enum OutputChannel {
        NONE, WRITER, STREAM
    }

    private final long maxBufferChars;

    private OutputChannel channel = OutputChannel.NONE;
    private CharArrayWriter charBuffer;
    private ByteArrayOutputStream byteBuffer;
    private ServletOutputStream servletOutputStream;
    private PrintWriter writer;

    public CharResponseWrapper(HttpServletResponse response, long maxBufferChars) {
        super(response);
        if (maxBufferChars <= 0) {
            throw new IllegalArgumentException("maxBufferChars must be positive");
        }
        this.maxBufferChars = maxBufferChars;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (channel == OutputChannel.WRITER) {
            throw new IllegalStateException("getWriter() has already been called on this response");
        }
        if (channel == OutputChannel.NONE) {
            channel = OutputChannel.STREAM;
            byteBuffer = new ByteArrayOutputStream();
            servletOutputStream = new BufferedServletOutputStream(byteBuffer, maxBufferChars);
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
            charBuffer = new BoundedCharArrayWriter(maxBufferChars);
            writer = new PrintWriter(charBuffer, true);
        }
        return writer;
    }

    public boolean hasBody() {
        return switch (channel) {
            case NONE -> false;
            case WRITER -> charBuffer.size() > 0;
            case STREAM -> byteBuffer.size() > 0;
        };
    }

    public boolean usedCharacterWriter() {
        return channel == OutputChannel.WRITER;
    }

    public String getBodyAsString() {
        return switch (channel) {
            case NONE -> "";
            case WRITER -> charBuffer.toString();
            case STREAM -> byteBuffer.toString(characterEncodingOrUtf8());
        };
    }

    public byte[] getBodyAsBytes() {
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

    static Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding.trim());
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    private Charset characterEncodingOrUtf8() {
        return resolveCharset(getCharacterEncoding());
    }
}
