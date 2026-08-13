/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.spellcheck;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifies regions in Markdown text that should be skipped during spell checking.
 * <p>
 * This includes code blocks, inline code, YAML front matter, URLs, HTML tags,
 * and the URL portions of Markdown links and images.
 */
public class MarkdownSpellFilter {

    // YAML front matter: starts at beginning of text with --- and ends at next ---
    private static final Pattern YAML_FRONT_MATTER = Pattern.compile(
            "\\A---\\s*\\n.*?\\n---\\s*\\n", Pattern.DOTALL);

    // Fenced code blocks: ``` or ~~~ with optional language tag
    private static final Pattern FENCED_CODE_BLOCK = Pattern.compile(
            "^(`{3,}|~{3,}).*?\\n[\\s\\S]*?^\\1\\s*$", Pattern.MULTILINE);

    // Inline code: backtick-delimited (handles multiple backticks)
    private static final Pattern INLINE_CODE = Pattern.compile(
            "(`+)(.+?)\\1");

    // URLs: http:// or https:// followed by non-whitespace
    private static final Pattern URL = Pattern.compile(
            "https?://\\S+");

    // HTML tags: <...>
    private static final Pattern HTML_TAG = Pattern.compile(
            "<[^>]+>");

    // Markdown link/image URL part: the (url) portion in [text](url) or ![alt](url)
    private static final Pattern LINK_URL = Pattern.compile(
            "!?\\[[^\\]]*\\]\\(([^)]+)\\)");

    // Markdown reference-style link definitions: [label]: url
    private static final Pattern LINK_DEFINITION = Pattern.compile(
            "^\\s{0,3}\\[[^\\]]+\\]:\\s+\\S+.*$", Pattern.MULTILINE);

    private MarkdownSpellFilter() {}

    /**
     * Computes the regions of text that should be excluded from spell checking.
     *
     * @param text the full document text
     * @return sorted list of [startOffset, endOffset] pairs representing regions to skip
     */
    public static List<int[]> computeSkipRegions(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<int[]> regions = new ArrayList<>();

        addMatches(regions, YAML_FRONT_MATTER, text);
        addMatches(regions, FENCED_CODE_BLOCK, text);
        addMatches(regions, INLINE_CODE, text);
        addMatches(regions, URL, text);
        addMatches(regions, HTML_TAG, text);
        addMatches(regions, LINK_DEFINITION, text);

        // For link URLs, skip only the URL part (group 1 captures the URL inside parens)
        Matcher linkMatcher = LINK_URL.matcher(text);
        while (linkMatcher.find()) {
            regions.add(new int[]{linkMatcher.start(1), linkMatcher.end(1)});
        }

        // Sort by start offset and merge overlapping regions
        regions.sort(Comparator.comparingInt(a -> a[0]));
        return mergeRegions(regions);
    }

    private static void addMatches(List<int[]> regions, Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            regions.add(new int[]{matcher.start(), matcher.end()});
        }
    }

    private static List<int[]> mergeRegions(List<int[]> sorted) {
        if (sorted.isEmpty()) return sorted;
        List<int[]> merged = new ArrayList<>();
        int[] current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            int[] next = sorted.get(i);
            if (next[0] <= current[1]) {
                current[1] = Math.max(current[1], next[1]);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }
}
