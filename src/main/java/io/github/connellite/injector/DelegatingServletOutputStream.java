package io.github.connellite.injector;

import lombok.RequiredArgsConstructor;

#if JAKARTA
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
#else
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
#endif
import java.io.IOException;

@RequiredArgsConstructor
final class DelegatingServletOutputStream extends ServletOutputStream {

    private final ServletOutputStream delegate;

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setWriteListener(WriteListener listener) {
        delegate.setWriteListener(listener);
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
