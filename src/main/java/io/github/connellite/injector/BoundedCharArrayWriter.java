package io.github.connellite.injector;

import lombok.RequiredArgsConstructor;

import java.io.CharArrayWriter;

@RequiredArgsConstructor
public final class BoundedCharArrayWriter extends CharArrayWriter {

    private final long maxChars;

    @Override
    public void write(int c) {
        ensureCapacity(1);
        super.write(c);
    }

    @Override
    public void write(char[] c, int off, int len) {
        ensureCapacity(len);
        super.write(c, off, len);
    }

    @Override
    public void write(String str, int off, int len) {
        ensureCapacity(len);
        super.write(str, off, len);
    }

    @Override
    public CharArrayWriter append(CharSequence csq) {
        CharSequence sequence = csq == null ? "null" : csq;
        ensureCapacity(sequence.length());
        return super.append(csq);
    }

    @Override
    public CharArrayWriter append(CharSequence csq, int start, int end) {
        ensureCapacity(end - start);
        return super.append(csq, start, end);
    }

    @Override
    public CharArrayWriter append(char c) {
        ensureCapacity(1);
        return super.append(c);
    }

    private void ensureCapacity(int additionalChars) {
        if (additionalChars <= 0 || maxChars == CharResponseWrapper.UNLIMITED_BUFFER_CHARS) {
            return;
        }
        long nextSize = (long) size() + additionalChars;
        if (nextSize > maxChars) {
            throw new IllegalStateException("Response body exceeds buffer limit of " + maxChars + " characters");
        }
    }
}