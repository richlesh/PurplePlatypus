/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoxTableConverterTest {

    /** The example from the feature request, including its uneven indentation. */
    private static final String EXAMPLE = String.join("\n",
        "┌───────┬─────────────────────────────────────┬──────────────────────────────────────────────────────────────────────────────┐",
        "  │ Count │ Type / field                        │ What changed                                                                 │",
        "  ├───────┼─────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────┤",
        "  │ 5     │ MText.pcdata_                       │ Text content edits. One has an extra trailing [S453362675] reference in      │",
        "  │       │                                     │ F15E. The other four are the same wording change (the COMM1*/COMM2* \"no      │",
        "  │       │                                     │ display\" text)                                                               │",
        "  ├───────┼─────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────┤",
        "  │ 4     │ MWMStateHistory                     │ lastSnapshotTransaction_ and the wmStateSnapshots_ list. F15E has later      │",
        "  │       │                                     │ values and more snapshots                                                    │",
        "  ├───────┼─────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────┤",
        "  │ 1     │ MIdCounter.counter_                 │ F15E is higher: 453,362,684 versus 453,036,648                               │",
        "  └───────┴─────────────────────────────────────┴──────────────────────────────────────────────────────────────────────────────┘",
        " ");

    private static List<String> rows(String gfm) {
        return Arrays.asList(gfm.split("\n"));
    }

    @Test
    public void detectsTableAndIgnoresPlainText() {
        assertTrue(BoxTableConverter.containsTable(EXAMPLE));
        assertFalse(BoxTableConverter.containsTable("Just text\n| a | b |\n|---|---|\n+---+---+"));
        assertFalse(BoxTableConverter.containsTable("A rule ──────── alone"));
    }

    @Test
    public void convertsExampleMergingWrappedRows() {
        String out = BoxTableConverter.convertAll(EXAMPLE);
        List<String> r = rows(out);
        assertTrue(r.get(0).matches("\\| Count \\| Type / field +\\| What changed +\\|"));
        assertTrue(r.get(1).matches("\\| -+ \\| -+ \\| -+ \\|"));
        assertTrue(r.get(2).startsWith("| 5     | MText.pcdata_ "));
        assertTrue(r.get(2).contains("| Text content edits. One has an extra trailing [S453362675] reference in "
                + "F15E. The other four are the same wording change (the COMM1*/COMM2* \"no display\" text)"));
        assertTrue(r.get(3).contains("lastSnapshotTransaction_ and the wmStateSnapshots_ list. F15E has later values and more snapshots"));
        assertTrue(r.get(4).contains("| 1     | MIdCounter.counter_"));
        assertEquals("header + separator + 3 rows + the trailing ' ' line", 6, r.size());
        assertFalse("no box characters left", out.chars().anyMatch(c -> c >= 0x2500 && c <= 0x257F));
        // Every row has the same width (padded)
        assertEquals(r.get(0).length(), r.get(2).length());
    }

    @Test
    public void headerSeparatorOnlyTreatsEachLineAsARow() {
        String t = String.join("\n", "┌───┬───┐", "│ A │ B │", "├───┼───┤", "│ 1 │ 2 │", "│ 3 │ 4 │", "└───┴───┘");
        assertEquals("| A   | B   |\n| --- | --- |\n| 1   | 2   |\n| 3   | 4   |", BoxTableConverter.convertAll(t));
    }

    @Test
    public void doubleLineAndHeavyStylesWork() {
        String t = String.join("\n", "╔═══╦═══╗", "║ A ║ B ║", "╠═══╬═══╣", "║ 1 ║ 2 ║", "╚═══╩═══╝");
        assertEquals("| A   | B   |\n| --- | --- |\n| 1   | 2   |", BoxTableConverter.convertAll(t));
    }

    @Test
    public void pipesInCellsAreEscapedAndSurroundingTextKept() {
        String t = String.join("\n", "Before", "┌─────┬───┐", "│ a|b │ c │", "├─────┼───┤", "│ 1   │ 2 │", "└─────┴───┘", "After");
        assertEquals("Before\n| a\\|b | c   |\n| ---- | --- |\n| 1    | 2   |\nAfter", BoxTableConverter.convertAll(t));
    }

    @Test
    public void findsMultipleTables() {
        String t = String.join("\n", "┌───┐", "│ A │", "└───┘", "text", "┌───┐", "│ B │", "└───┘");
        assertEquals(2, BoxTableConverter.findTables(t.split("\n")).size());
    }
}
