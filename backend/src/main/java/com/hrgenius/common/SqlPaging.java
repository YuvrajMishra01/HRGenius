package com.hrgenius.common;

import org.springframework.data.domain.Page;

/**
 * Shared helpers for real DB-side paging (the slice-paging successor for
 * scale-critical lists). Every query here binds only concrete typed values
 * (Long, Integer, String) — never Boolean or null-flag booleans, which
 * Oracle-mode databases reject against NUMBER(1) columns (the Phase 12
 * lesson), and never conditional fragments that make Hibernate's derived
 * count query brittle.
 */
public final class SqlPaging {

    private SqlPaging() {
    }

    /**
     * SQL LIKE pattern with the user term lower-cased and LIKE-wildcards
     * escaped. The query must use {@code ESCAPE '\'} — this pairs with
     * {@link Lists#cleanSearch}, which already caps the term's length.
     */
    public static String likeEscape(String cleanedTerm) {
        return "%" + cleanedTerm.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                + "%";
    }

    /** The one stable envelope, straight from a real SQL page. */
    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.of(page);
    }

    /**
     * Full pipeline: clean the raw term (trim/cap/blank→null), then build
     * the escaped LIKE pattern. Null in → null out (no filter).
     */
    public static String likeEscapeOrNull(String rawTerm) {
        String cleaned = Lists.cleanSearch(rawTerm);
        return cleaned == null ? null : likeEscape(cleaned);
    }
}
