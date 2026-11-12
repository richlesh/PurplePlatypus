/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts HTML selected in the preview pane back to Markdown for "Copy Markdown".
 * <p>
 * The input is prepared by {@code preview_selection.js}: constructs a generic HTML to
 * Markdown converter cannot reproduce (math, images, alerts, footnotes) are replaced
 * there by placeholder tokens {@code PPZTOK<n>PP}, and {@code tokens.get(n)} holds the
 * exact Markdown for token {@code n}. Tokens are substituted after conversion, so the
 * converter never escapes or reinterprets them. Images are always tokenized because
 * the converter otherwise turns images whose file name matches an emoji name (e.g.
 * {@code cat.png}) into emoji shortcodes.
 */
public final class PreviewSelectionConverter {

    private static final Pattern TOKEN = Pattern.compile("PPZTOK(\\d+)PP");

    /**
     * Flexmark inserts a space between a closing emphasis/code marker and following
     * punctuation ("**bold** ,"). A closing marker directly follows a non-space character.
     */
    private static final Pattern SPACE_BEFORE_PUNCT =
            Pattern.compile("(?<=\\S)(\\*\\*|~~|\\+\\+|[*_`])[ \\t]+(?=[,.;:!?)\\]])");

    /** An alert marker followed by an empty quote line ("> [!NOTE]\n>\n"). */
    private static final Pattern ALERT_BLANK_LINE =
            Pattern.compile("(?m)^(>\\s*\\[!(?:NOTE|TIP|IMPORTANT|WARNING|CAUTION)\\])\\n>\\s*\\n");

    private static final FlexmarkHtmlConverter CONVERTER;
    static {
        MutableDataSet options = new MutableDataSet();
        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false);
        options.set(FlexmarkHtmlConverter.OUTPUT_ATTRIBUTES_ID, false);
        options.set(FlexmarkHtmlConverter.SKIP_ATTRIBUTES, true);
        options.set(FlexmarkHtmlConverter.UNORDERED_LIST_DELIMITER, '-');
        CONVERTER = FlexmarkHtmlConverter.builder(options).build();
    }

    private PreviewSelectionConverter() {}

    /**
     * @param html   selection HTML prepared by preview_selection.js
     * @param tokens Markdown for each {@code PPZTOK<n>PP} placeholder (may be null)
     * @return the Markdown, trimmed; "" for null/blank input
     */
    public static String toMarkdown(String html, List<String> tokens) {
        if (html == null || html.isBlank()) return "";
        String md = CONVERTER.convert(html);
        md = removeSpaceBeforePunctuation(md);
        md = substituteTokens(md, tokens);
        md = ALERT_BLANK_LINE.matcher(md).replaceAll("$1\n");
        return md.strip();
    }

    /** Applies {@link #SPACE_BEFORE_PUNCT} to every line outside fenced code blocks. */
    private static String removeSpaceBeforePunctuation(String md) {
        StringBuilder out = new StringBuilder(md.length());
        boolean inFence = false;
        for (String line : md.split("\n", -1)) {
            if (line.stripLeading().startsWith("```") || line.stripLeading().startsWith("~~~")) {
                inFence = !inFence;
                out.append(line);
            } else {
                out.append(inFence ? line : SPACE_BEFORE_PUNCT.matcher(line).replaceAll("$1"));
            }
            out.append('\n');
        }
        out.setLength(out.length() - 1);
        return out.toString();
    }

    private static String substituteTokens(String md, List<String> tokens) {
        Matcher m = TOKEN.matcher(md);
        StringBuilder sb = new StringBuilder(md.length());
        while (m.find()) {
            int i = Integer.parseInt(m.group(1));
            String value = tokens != null && i < tokens.size() && tokens.get(i) != null
                    ? tokens.get(i) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
