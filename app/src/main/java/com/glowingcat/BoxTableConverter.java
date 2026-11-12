/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects tables drawn with Unicode box-drawing characters (U+2500–U+257F) and converts
 * them to GFM pipe tables. Such tables are common in output pasted from terminals and AI
 * tools:
 *
 * <pre>
 * ┌───────┬──────────────┐
 * │ Count │ Field        │
 * ├───────┼──────────────┤
 * │ 5     │ A long value │
 * │       │ that wraps   │
 * ├───────┼──────────────┤
 * │ 4     │ Other        │
 * └───────┴──────────────┘
 * </pre>
 *
 * Row grouping:
 * <ul>
 *   <li>If separator lines appear between body rows, each group of lines between two
 *       separators is one row, and wrapped cell text is joined with spaces.</li>
 *   <li>If there is only a header separator, the lines above it form the header and each
 *       line below it is its own row.</li>
 *   <li>With no separators, the first line is the header and each other line is a row.</li>
 * </ul>
 * Box tables carry no alignment information, so every column is left as default.
 */
public final class BoxTableConverter {

    /** Vertical box-drawing characters that separate cells. */
    private static final String VERTICALS = "│┃║┆┇┊┋╎╏";
    /** Horizontal box-drawing characters used in border/separator lines. */
    private static final String HORIZONTALS = "─━═┄┅┈┉╌╍";

    private BoxTableConverter() {}

    private static boolean isBoxChar(int cp) {
        return cp >= 0x2500 && cp <= 0x257F;
    }

    /** A border or separator line: only box-drawing characters and whitespace, with a horizontal rule. */
    static boolean isSeparatorLine(String line) {
        String t = line.strip();
        if (t.isEmpty()) return false;
        boolean hasHorizontal = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c)) continue;
            if (!isBoxChar(c)) return false;
            if (HORIZONTALS.indexOf(c) >= 0) hasHorizontal = true;
        }
        return hasHorizontal;
    }

    /** A content row: starts with a vertical bar and contains at least two of them. */
    static boolean isRowLine(String line) {
        String t = line.strip();
        if (t.isEmpty() || VERTICALS.indexOf(t.charAt(0)) < 0) return false;
        int count = 0;
        for (int i = 0; i < t.length(); i++) {
            if (VERTICALS.indexOf(t.charAt(i)) >= 0) count++;
        }
        return count >= 2;
    }

    /** Finds every box table in the given lines. */
    public static List<TableRange> findTables(String[] lines) {
        List<TableRange> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            if (isSeparatorLine(lines[i]) || isRowLine(lines[i])) {
                int start = i;
                boolean hasRow = false;
                while (i < lines.length && (isSeparatorLine(lines[i]) || isRowLine(lines[i]))) {
                    if (isRowLine(lines[i])) hasRow = true;
                    i++;
                }
                if (hasRow) blocks.add(new TableRange(start, i - 1));
            } else {
                i++;
            }
        }
        return blocks;
    }

    /** Returns true if the text contains at least one box table. */
    public static boolean containsTable(String text) {
        return text != null && !findTables(text.split("\n", -1)).isEmpty();
    }

    /**
     * Converts the lines of one box table (as returned by {@link #findTables}) to a GFM
     * pipe table, without a trailing newline.
     */
    public static String convertTable(List<String> tableLines) {
        // Split into groups of row lines separated by separator lines.
        List<List<List<String>>> groups = new ArrayList<>();
        List<List<String>> current = new ArrayList<>();
        int separatorsBetweenRows = 0;
        boolean seenRow = false;
        boolean pendingSeparator = false;
        for (String line : tableLines) {
            if (isRowLine(line)) {
                if (pendingSeparator && seenRow) separatorsBetweenRows++;
                pendingSeparator = false;
                seenRow = true;
                current.add(parseCells(line));
            } else if (isSeparatorLine(line)) {
                pendingSeparator = true;
                if (!current.isEmpty()) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
            }
        }
        if (!current.isEmpty()) groups.add(current);

        List<List<String>> rows = new ArrayList<>();
        if (separatorsBetweenRows >= 2) {
            for (List<List<String>> g : groups) rows.add(GfmTableWriter.mergeLines(g));
        } else if (separatorsBetweenRows == 1 && groups.size() == 2) {
            rows.add(GfmTableWriter.mergeLines(groups.get(0)));
            rows.addAll(groups.get(1));
        } else {
            for (List<List<String>> g : groups) rows.addAll(g);
        }
        return GfmTableWriter.format(rows, List.of());
    }

    /** Converts every box table in {@code text} and returns the new text. */
    public static String convertAll(String text) {
        String[] lines = text.split("\n", -1);
        List<TableRange> blocks = findTables(lines);
        if (blocks.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length());
        int next = 0;
        for (TableRange b : blocks) {
            for (int i = next; i < b.startLine(); i++) out.append(lines[i]).append('\n');
            out.append(convertTable(java.util.Arrays.asList(lines).subList(b.startLine(), b.endLine() + 1)));
            out.append('\n');
            next = b.endLine() + 1;
        }
        for (int i = next; i < lines.length; i++) {
            out.append(lines[i]);
            if (i < lines.length - 1) out.append('\n');
        }
        // A table ending the document gets no extra trailing newline.
        if (next >= lines.length && out.length() > 0) out.setLength(out.length() - 1);
        return out.toString();
    }

    private static List<String> parseCells(String line) {
        String t = line.strip();
        // Drop the leading and trailing border bars, then split on the inner ones.
        if (!t.isEmpty() && VERTICALS.indexOf(t.charAt(0)) >= 0) t = t.substring(1);
        if (!t.isEmpty() && VERTICALS.indexOf(t.charAt(t.length() - 1)) >= 0) t = t.substring(0, t.length() - 1);
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (VERTICALS.indexOf(c) >= 0) {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().strip());
        return cells;
    }
}
