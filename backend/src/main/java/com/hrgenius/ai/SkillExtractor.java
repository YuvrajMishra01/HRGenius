package com.hrgenius.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.springframework.stereotype.Component;

/**
 * Deterministic skill extraction (Phase 16). Every dictionary alias is
 * matched case-insensitively on word boundaries, so "java" never matches
 * inside "javascript". After matching, a skill is dropped when another
 * matched skill strictly contains it ("Oracle SQL" suppresses "SQL").
 * Output keeps dictionary order — stable and explainable.
 */
@Component
public class SkillExtractor {

    /** Resume text sources the extractor accepts. */
    public String extractText(String fileName, byte[] bytes) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return extractFromPdfBytes(bytes);
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Resume must be a .pdf or .txt file");
    }

    public String extractFromPdfBytes(byte[] bytes) {
        try (PdfReader reader = new PdfReader(bytes)) {
            PdfTextExtractor pageText = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(pageText.getTextFromPage(page)).append(' ');
            }
            return text.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("Resume PDF could not be read: is it a valid PDF?", e);
        }
    }

    /** Convenience overload for already-stored files. */
    public String extractFromPdf(Path file) {
        try {
            return extractFromPdfBytes(java.nio.file.Files.readAllBytes(file));
        } catch (IOException e) {
            throw new IllegalArgumentException("Resume PDF could not be read", e);
        }
    }

    /**
     * Canonical skills found in the text, dictionary-ordered. Suppression is
     * span-based: a skill's earliest alias match is dropped only when that
     * span lies strictly inside another skill's longer match ("sql" inside
     * "oracle sql"). Substring pruning was tried and rejected — "JavaScript"
     * contains "java" as a string without being a superset skill.
     */
    public Set<String> extract(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        String hay = text.toLowerCase(Locale.ROOT);
        record Hit(String skill, int start, int end) {
        }
        List<Hit> hits = new ArrayList<>();
        for (var entry : SkillDictionary.SKILLS) {
            java.util.regex.Matcher best = null;
            for (String alias : entry.getValue()) {
                java.util.regex.Matcher m = matcher(alias).matcher(hay);
                if (m.find() && (best == null || m.start() < best.start())) {
                    best = m;
                }
            }
            if (best != null) {
                hits.add(new Hit(entry.getKey(), best.start(), best.end()));
            }
        }
        hits.stream()
                .filter(hit -> hits.stream().noneMatch(other ->
                        !other.skill().equals(hit.skill())
                                && other.start() <= hit.start() && hit.end() <= other.end()
                                && (other.end() - other.start()) > (hit.end() - hit.start())))
                .map(Hit::skill)
                .forEach(found::add);
        return found;
    }

    /** Case-insensitive, word-boundary-safe alias matcher. */
    private static Pattern matcher(String alias) {
        String quoted = Pattern.quote(alias);
        return Pattern.compile("(?<![a-z0-9+#])" + quoted + "(?![a-z0-9+#])");
    }
}
