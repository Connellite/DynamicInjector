package io.github.connellite.injector;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class BufferedServletOutputStream extends ServletOutputStream {

    private final ByteArrayOutputStream buffer;
    private final int maxBufferBytes;

    public BufferedServletOutputStream(ByteArrayOutputStream buffer, int maxBufferBytes) {
        this.buffer = buffer;
        this.maxBufferBytes = maxBufferBytes;
    }

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
        long nextSize = (long) buffer.size() + additionalBytes;
        if (nextSize > maxBufferBytes) {
            throw new IOException("Response body exceeds buffer limit of " + maxBufferBytes + " bytes");
        }
    }
}