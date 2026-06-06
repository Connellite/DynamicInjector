package io.github.connellite.injector;
import lombok.RequiredArgsConstructor;

#if JAKARTA
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
#else
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
#endif
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RequiredArgsConstructor
public final class BufferedServletOutputStream extends ServletOutputStream {

    private final ByteArrayOutputStream buffer;
    private final long maxBufferSize;

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener listener) {
        throw new UnsupportedOperationException("Async writes are not supported");
    }

    @Override
    public void write(int b) throws IOException {
        ensureCapacity(1);
        buffer.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureCapacity(len);
        buffer.write(b, off, len);
    }

    private void ensureCapacity(int additionalBytes) throws IOException {
        if (BufferLimits.isUnlimited(maxBufferSize)) {
            return;
        }
        long nextSize = (long) buffer.size() + additionalBytes;
        if (nextSize > maxBufferSize) {
            throw new IOException("Response body exceeds buffer limit of " + maxBufferSize + " bytes");
        }
    }
}
