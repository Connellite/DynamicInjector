package io.github.connellite.injector;

#if JAKARTA
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
#else
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
#endif
import java.io.IOException;

final class AdaptiveServletOutputStream extends ServletOutputStream {

    private final CharResponseWrapper wrapper;

    AdaptiveServletOutputStream(CharResponseWrapper wrapper) {
        this.wrapper = wrapper;
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
        wrapper.writeStreamByte(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        wrapper.writeStreamBytes(b, off, len);
    }
}
