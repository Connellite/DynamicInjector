package io.github.connellite.injector.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class BufferLimits {

    public static final long UNLIMITED = Long.MAX_VALUE;

    public static boolean isUnlimited(long limit) {
        return limit == UNLIMITED;
    }
}
