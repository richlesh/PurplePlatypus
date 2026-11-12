/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import java.util.ArrayList;
import java.util.List;

/** Formats rows of cell text as a padded GFM pipe table. The first row is the header. */
final class GfmTableWriter {

    enum Align { NONE, LEFT, CENTER, RIGHT }

    private GfmTableWriter() {}

    /**
     * @param rows   header row followed by body rows; short rows are padded with empty cells
     * @param aligns per-column alignment; missing entries mean {@link Align#NONE}
     * @return the table without a trailing newline, or "" if there are no rows
     */
    static String format(List<List<String>> rows, List<Align> aligns) {
        if (rows.isEmpty()) return "";
        int cols = 0;
        for (List<String> r : rows) cols = Math.max(cols, r.size());
        int[] width = new int[cols];
        List<List<String>> cells = new ArrayList<>();
        for (List<String> r : rows) {
            List<String> escaped = new ArrayList<>(cols);
            for (int c = 0; c < cols; c++) {
                // Escape pipes that are not already escaped so they don't split the cell
                String v = c < r.size() ? r.get(c).replaceAll("(?<!\\\\)\\|", "\\\\|") : "";
                escaped.add(v);
                width[c] = Math.max(width[c], Math.max(3, displayWidth(v)));
            }
            cells.add(escaped);
        }
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < cells.size(); r++) {
            appendRow(sb, cells.get(r), width);
            if (r == 0) {
                sb.append('|');
                for (int c = 0; c < cols; c++) {
                    Align a = c < aligns.size() ? aligns.get(c) : Align.NONE;
                    sb.append(' ').append(separator(width[c], a)).append(" |");
                }
                sb.append('\n');
            }
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private static String separator(int w, Align a) {
        return switch (a) {
            case LEFT -> ":" + "-".repeat(w - 1);
            case RIGHT -> "-".repeat(w - 1) + ":";
            case CENTER -> ":" + "-".repeat(w - 2) + ":";
            case NONE -> "-".repeat(w);
        };
    }

    private static void appendRow(StringBuilder sb, List<String> row, int[] width) {
        sb.append('|');
        for (int c = 0; c < width.length; c++) {
            String v = row.get(c);
            sb.append(' ').append(v).append(" ".repeat(width[c] - displayWidth(v))).append(" |");
        }
        sb.append('\n');
    }

    private static int displayWidth(String s) {
        return s.codePointCount(0, s.length());
    }

    /** Joins the wrapped physical lines of one logical row, column by column. */
    static List<String> mergeLines(List<List<String>> lines) {
        int cols = 0;
        for (List<String> l : lines) cols = Math.max(cols, l.size());
        List<String> merged = new ArrayList<>(cols);
        for (int c = 0; c < cols; c++) {
            StringBuilder sb = new StringBuilder();
            for (List<String> l : lines) {
                String part = c < l.size() ? l.get(c) : "";
                if (part.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(part);
            }
            merged.add(sb.toString());
        }
        return merged;
    }
}
