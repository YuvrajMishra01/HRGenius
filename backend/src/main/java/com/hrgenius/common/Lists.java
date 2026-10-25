package com.hrgenius.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Cross-cutting list-endpoint hardening (Phase 14): one shared policy for
 * search cleanup, pagination clamping, and slice-based paging.
 *
 * Conventions applied by every list endpoint:
 *  - {@code search} is trimmed, capped at {@link #MAX_SEARCH_LENGTH}, blank → null;
 *  - {@code page} is 0-based and clamped (a far-out page yields empty content,
 *    never an exception);
 *  - {@code size} is clamped to [1, {@link #MAX_PAGE_SIZE}];
 *  - slices over already-loaded projections keep response shaping cheap while
 *    still giving every list the stable PageResponse contract.
 *
 * NOTE: slice paging is honest about its trade-off — totalElements equals the
 * number of rows matching the filter, not a SQL COUNT over the whole table.
 * Endpoints with heavy row counts (employees) already use real JPA paging.
 */
public final class Lists {

    /** Client-supplied search terms longer than this are rejected as noise. */
    public static final int MAX_SEARCH_LENGTH = 100;

    /** Upper bound for any page size, everywhere. */
    public static final int MAX_PAGE_SIZE = 100;

    private Lists() {
    }

    /** Trimmed, null-if-blank, length-capped search term. */
    public static String cleanSearch(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_SEARCH_LENGTH
                ? trimmed.substring(0, MAX_SEARCH_LENGTH)
                : trimmed;
    }

    /** 0-based page number, negative input clamps to 0. */
    public static int cleanPage(Integer raw) {
        return raw == null || raw < 0 ? 0 : raw;
    }

    /** Page size clamped into [1, MAX_PAGE_SIZE]. */
    public static int cleanSize(Integer raw) {
        if (raw == null || raw < 1) {
            return 20;
        }
        return Math.min(raw, MAX_PAGE_SIZE);
    }

    /** Case-insensitive contains across a row's search text; null term matches all. */
    public static <T> Predicate<T> containsTerm(java.util.function.Function<T, String> text, String term) {
        if (term == null) {
            return row -> true;
        }
        String needle = term.toLowerCase();
        return row -> {
            String hay = text.apply(row);
            return hay != null && hay.toLowerCase().contains(needle);
        };
    }

    /** One page of a filtered list, as a stable PageResponse. */
    public static <T> PageResponse<T> page(List<T> rows, int page, int size) {
        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        List<T> content = rows.subList(from, to);
        int totalPages = rows.isEmpty() ? 0 : (int) Math.ceil((double) rows.size() / size);
        return new PageResponse<>(List.copyOf(content), page, size, rows.size(), totalPages,
                page == 0, page >= totalPages - 1);
    }

    /** Defensive copy helper for building filtered lists. */
    public static <T> List<T> mutable() {
        return new ArrayList<>();
    }
}
