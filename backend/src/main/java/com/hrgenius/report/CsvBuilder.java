package com.hrgenius.report;

import java.util.List;

/**
 * Minimal RFC 4180 CSV writer (Phase 15).
 *
 * Quoting: cells containing commas, double quotes or newlines are wrapped in
 * double quotes with embedded quotes doubled. Line endings are CRLF per the
 * RFC. A UTF-8 BOM is prepended by the caller so Excel detects the encoding.
 *
 * Cell sanitisation (before quoting): raw CR/LF are replaced with spaces and
 * a leading = + - @ is prefixed with a single quote — the OWASP-recommended
 * defence against spreadsheet formula injection (a reason like "=HYPERLINK…"
 * must never execute when the export opens in Excel or Sheets).
 */
public final class CsvBuilder {

    private final StringBuilder sb = new StringBuilder();

    public CsvBuilder header(List<String> cells) {
        return row(cells);
    }

    public CsvBuilder row(List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
        return this;
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        if (!sanitized.isEmpty()) {
            char first = sanitized.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                sanitized = "'" + sanitized;
            }
        }
        boolean mustQuote = sanitized.indexOf(',') >= 0 || sanitized.indexOf('"') >= 0;
        if (mustQuote) {
            return '"' + sanitized.replace("\"", "\"\"") + '"';
        }
        return sanitized;
    }

    public String build() {
        return sb.toString();
    }
}
