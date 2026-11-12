/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GridTableConverterTest {

    /** Cell texts of one GFM row, without padding. */
    private static List<String> cells(String row) {
        String inner = row.substring(1, row.length() - 1);
        return Arrays.stream(inner.split("\\|")).map(String::strip).collect(Collectors.toList());
    }

    @Test
    public void wrappedRowsAreJoinedIntoOneLine() {
        String t = String.join("\n",
            "+-------+---------------------+",
            "| Count | What changed        |",
            "+=======+=====================+",
            "| 5     | Text content edits. |",
            "|       | The other four are  |",
            "|       | the same.           |",
            "+-------+---------------------+",
            "| 4     | Later values        |",
            "+-------+---------------------+");
        String[] out = GridTableConverter.convertAll(t).split("\n");
        assertEquals("header + separator + 2 rows, one physical line each", 4, out.length);
        assertEquals(List.of("Count", "What changed"), cells(out[0]));
        assertTrue(out[1].matches("\\| -+ \\| -+ \\|"));
        assertEquals(List.of("5", "Text content edits. The other four are the same."), cells(out[2]));
        assertEquals(List.of("4", "Later values"), cells(out[3]));
        for (String line : out) assertEquals("columns are padded evenly", out[0].length(), line.length());
    }

    @Test
    public void wrappedHeaderIsJoined() {
        String t = String.join("\n",
            "+--------+--------+",
            "| Long   | B      |",
            "| header |        |",
            "+========+========+",
            "| 1      | 2      |",
            "+--------+--------+");
        assertEquals("| Long header | B   |\n| ----------- | --- |\n| 1           | 2   |",
            GridTableConverter.convertAll(t));
    }

    @Test
    public void alignmentMarkersAreKept() {
        String t = String.join("\n",
            "+------+------+------+------+",
            "| L    | R    | C    | D    |",
            "+:=====+=====:+:====:+======+",
            "| a    | b    | c    | d    |",
            "+------+------+------+------+");
        assertEquals(String.join("\n",
            "| L   | R   | C   | D   |",
            "| :-- | --: | :-: | --- |",
            "| a   | b   | c   | d   |"),
            GridTableConverter.convertAll(t));
    }

    @Test
    public void tableWithoutHeaderSeparatorUsesFirstRowAsHeader() {
        String t = String.join("\n", "+---+---+", "| a | b |", "+---+---+", "| 1 | 2 |", "+---+---+");
        assertEquals("| a   | b   |\n| --- | --- |\n| 1   | 2   |", GridTableConverter.convertAll(t));
    }

    @Test
    public void pipeInsideCellDoesNotSplitIt() {
        String t = String.join("\n", "+-------+---+", "| a | b | c |", "+=======+===+", "| 1     | 2 |", "+-------+---+");
        assertTrue(GridTableConverter.convertAll(t).startsWith("| a \\| b | c   |"));
    }

    @Test
    public void surroundingTextAndGfmTablesAreUntouched() {
        String gfm = "| x | y |\n|---|---|\n| 1 | 2 |";
        assertEquals(gfm, GridTableConverter.convertAll(gfm));
        String t = String.join("\n", "Before", "+---+", "| a |", "+===+", "| 1 |", "+---+", "After");
        assertEquals("Before\n| a   |\n| --- |\n| 1   |\nAfter", GridTableConverter.convertAll(t));
        assertEquals(2, GridTableConverter.findTables(
            (t + "\n\n" + t).split("\n")).size());
    }
}
