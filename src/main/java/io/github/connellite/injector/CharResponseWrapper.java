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

    private enum OutputChannel {
        NONE, WRITER, STREAM
    }

    private final long maxBufferBytes;

    private OutputChannel channel = OutputChannel.NONE;
    private CharArrayWriter charBuffer;
    private ByteArrayOutputStream byteBuffer;
    private ServletOutputStream servletOutputStream;
    private PrintWriter writer;

    public CharResponseWrapper(HttpServletResponse response, long maxBufferBytes) {
        super(response);
        if (maxBufferBytes <= 0) {
            throw new IllegalArgumentException("maxBufferBytes must be positive");
        }
        this.maxBufferBytes = maxBufferBytes;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (channel == OutputChannel.WRITER) {
            throw new IllegalStateException("getWriter() has already been called on this response");
        }
        if (channel == OutputChannel.NONE) {
            channel = OutputChannel.STREAM;
            byteBuffer = new ByteArrayOutputStream();
            servletOutputStream = new BufferedServletOutputStream(byteBuffer, maxBufferBytes);
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
            charBuffer = new BoundedCharArrayWriter(maxBufferBytes);
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
        response.setContentLength(body.length);
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write(body);
        outputStream.flush();
    }

    private Charset characterEncodingOrUtf8() {
        String encoding = getCharacterEncoding();
        if (encoding == null || encoding.isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(encoding);
    }
}
