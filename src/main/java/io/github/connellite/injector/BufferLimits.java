package io.github.connellite.injector;

import lombok.experimental.UtilityClass;

@UtilityClass
final class BufferLimits {

    static final long UNLIMITED = Long.MAX_VALUE;

    static boolean isUnlimited(long limit) {
        return limit == UNLIMITED;
    }
}
