package com.winlator.star.core;

/**
 * Standalone trim of Bannerlator's {@code StringUtils}. The Fusion HUD backend only reaches
 * {@code StringUtils} through the {@link KeyValueSet} it ships with, which calls exactly one method:
 * {@link #replace(String, int, int, String)} (from {@link KeyValueSet#put}). The body is copied
 * verbatim so behaviour is identical. If you sync a HUD fix that starts calling another
 * {@code StringUtils} method, add it here with the same signature.
 */
public class StringUtils {

    public static String replace(String text, int start, int end, String value) {
        return text.substring(0, start) + value + text.substring(end);
    }
}
