package com.campus.common.util;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

public final class FileUtil {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private FileUtil() {
    }

    public static String extension(String filename, String fallback) {
        String ext = StringUtils.getFilenameExtension(filename);
        if (!StringUtils.hasText(ext)) {
            return fallback;
        }
        return ext.toLowerCase(Locale.ROOT);
    }

    public static boolean isAllowedAvatar(String ext) {
        return IMAGE_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT));
    }
}
