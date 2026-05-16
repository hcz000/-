package com.example.demo.util;

public final class PageParamUtil {

    private PageParamUtil() {
    }

    public static int resolvePage(Integer page, Integer pageNum) {
        int value = page != null ? page : (pageNum != null ? pageNum : 1);
        return Math.max(value, 1);
    }

    public static int resolveSize(Integer size, Integer pageSize, int defaultSize, int maxSize) {
        int value = size != null ? size : (pageSize != null ? pageSize : defaultSize);
        if (value <= 0) {
            value = defaultSize;
        }
        return Math.min(value, maxSize);
    }
}
