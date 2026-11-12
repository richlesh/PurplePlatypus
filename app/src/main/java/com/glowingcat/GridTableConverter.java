/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts Pandoc grid tables to GFM pipe tables:
 *
 * <pre>
 * +-------+----------------+
 * | Count | Field          |
 * +=======+================+
 * | 5     | A long value   |
 * |       | that wraps     |
 * +-------+----------------+
 * </pre>
 *
 * Each group of lines between two separator lines is one row; text wrapped across
 * several physical lines in a cell is joined with spaces. Rows above the {@code +===+}
 * separator form the header. Column alignment comes from {@code :} markers on the header
 * separator (or the top border if there is no header): {@code +:---+} left,
 * {@code +---:+} right, {@code +:---:+} center. GFM requires a header, so a table without
 * a {@code +===+} separator uses its first row as the header.
 */
public final class GridTableConverter {

    private GridTableConverter() {}

    /** A border/separator line such as {@code +-----+:====+}. */
    static boolean isSeparatorLine(String line) {
        return line.strip().matches("\\+(?:[-=:]+\\+)+");
    }

    /** A content row: starts and ends with {@code |}. */
    static boolean isRowLine(String line) {
        String t = line.strip();
        return t.length() >= 2 && t.charAt(0) == '|' && t.charAt(t.length() - 1) == '|';
    }

    /**
     * Finds every grid table: a run of separator and row lines that starts with a
     * separator, contains at least one row, and ends at its last separator line.
     */
    public static List<TableRange> findTables(String[] lines) {
        List<TableRange> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            if (!isSeparatorLine(lines[i])) { i++; continue; }
            int start = i;
            int lastSeparator = i;
            boolean hasRow = false;
            int j = i + 1;
            while (j < lines.length && (isSeparatorLine(lines[j]) || isRowLine(lines[j]))) {
                if (isSeparatorLine(lines[j])) {
                    if (hasRow) lastSeparator = j;
                } else {
                    hasRow = true;
                }
                j++;
            }
            if (hasRow && lastSeparator > start) {
                blocks.add(new TableRange(start, lastSeparator));
                i = lastSeparator + 1;
            } else {
                i = j;
            }
        }
        return blocks;
    }

    /** Converts the lines of one grid table (as returned by {@link #findTables}) to GFM. */
    public static String convertTable(List<String> tableLines) {
        String border = tableLines.get(0).strip();
        List<Integer> plus = new ArrayList<>();
        for (int i = 0; i < border.length(); i++) {
            if (border.charAt(i) == '+') plus.add(i);
        }

        String alignSource = border;
        List<List<List<String>>> groups = new ArrayList<>();
        List<List<String>> current = new ArrayList<>();
        int headerGroups = -1; // number of groups above the +===+ separator
        for (String line : tableLines) {
            if (isSeparatorLine(line)) {
                if (!current.isEmpty()) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
                if (headerGroups < 0 && line.contains("=")) {
                    headerGroups = groups.size();
                    alignSource = line.strip();
                }
            } else if (isRowLine(line)) {
                current.add(parseCells(line.strip(), plus));
            }
        }
        if (!current.isEmpty()) groups.add(current);
        if (groups.isEmpty()) return String.join("\n", tableLines);

        List<List<String>> rows = new ArrayList<>();
        int firstBody;
        if (headerGroups > 0) {
            // Merge everything above the header separator into the single GFM header row.
            List<List<String>> headerLines = new ArrayList<>();
            for (int g = 0; g < headerGroups; g++) headerLines.addAll(groups.get(g));
            rows.add(GfmTableWriter.mergeLines(headerLines));
            firstBody = headerGroups;
        } else {
            rows.add(GfmTableWriter.mergeLines(groups.get(0)));
            firstBody = 1;
        }
        for (int g = firstBody; g < groups.size(); g++) rows.add(GfmTableWriter.mergeLines(groups.get(g)));
        return GfmTableWriter.format(rows, alignments(alignSource));
    }

    /** Converts every grid table in {@code text} and returns the new text. */
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
        if (next >= lines.length && out.length() > 0) out.setLength(out.length() - 1);
        return out.toString();
    }

    /**
     * Splits a row into cells using the column positions of the border's {@code +}
     * characters, so a {@code |} inside cell text doesn't split the cell. Falls back to
     * splitting on {@code |} when the row doesn't line up with the border (e.g. wide
     * characters or hand-edited spacing).
     */
    private static List<String> parseCells(String row, List<Integer> plus) {
        List<String> cells = new ArrayList<>();
        boolean aligned = plus.size() >= 2 && row.length() == plus.get(plus.size() - 1) + 1;
        if (aligned) {
            for (int p : plus) {
                if (row.charAt(p) != '|') { aligned = false; break; }
            }
        }
        if (aligned) {
            for (int c = 0; c + 1 < plus.size(); c++) {
                cells.add(row.substring(plus.get(c) + 1, plus.get(c + 1)).strip());
            }
        } else {
            String inner = row.substring(1, row.length() - 1);
            for (String part : inner.split("\\|", -1)) cells.add(part.strip());
        }
        return cells;
    }

    private static List<GfmTableWriter.Align> alignments(String separator) {
        List<GfmTableWriter.Align> aligns = new ArrayList<>();
        String inner = separator.substring(1, separator.length() - 1);
        for (String seg : inner.split("\\+", -1)) {
            boolean left = seg.startsWith(":");
            boolean right = seg.endsWith(":") && seg.length() > 1;
            aligns.add(left && right ? GfmTableWriter.Align.CENTER
                    : left ? GfmTableWriter.Align.LEFT
                    : right ? GfmTableWriter.Align.RIGHT
                    : GfmTableWriter.Align.NONE);
        }
        return aligns;
    }
}
