package io.trellis.util;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

public final class NanoIdUtil {
    private NanoIdUtil() {}

    public static String generate() {
        return NanoIdUtils.randomNanoId();
    }
}
