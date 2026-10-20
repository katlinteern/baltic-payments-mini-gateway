package com.baltic.gateway.platform.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequests {
    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {}

    /** Clamps client-supplied paging values: a negative page becomes 0 and the size stays within 1..100. */
    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }
}
