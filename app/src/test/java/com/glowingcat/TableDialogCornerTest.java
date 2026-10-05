package com.glowingcat;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the top-left corner cell (the header label for the row-header
 * column) is preserved when editing a table that has both column headers and
 * detected row headers.
 */
public class TableDialogCornerTest {

    @BeforeClass
    public static void requireDisplay() {
        // TableDialog builds real Swing components; skip if headless.
        Assume.assumeFalse("Requires a display", GraphicsEnvironment.isHeadless());
    }

    @Test
    public void cornerHeaderIsPreservedRoundTrip() {
        // "Name" is the top-left header cell; the first data column is bold,
        // so row headers are detected and "Name" labels the row-header column.
        String input = String.join("\n",
                "| Name | Age |",
                "|------|-----|",
                "| **Bob** | 42 |",
                "| **Sue** | 37 |");

        TableDialog dialog = new TableDialog(null, input);
        try {
            String output = dialog.getMarkdownTable();

            // The corner label "Name" must survive the round-trip.
            assertTrue("Corner header 'Name' should be preserved in output:\n" + output,
                    output.contains("Name"));
            // Row header values must still be present and bold.
            assertTrue("Row header **Bob** should be present:\n" + output,
                    output.contains("**Bob**"));
            assertTrue("Data value 42 should be present:\n" + output,
                    output.contains("42"));
        } finally {
            dialog.dispose();
        }
    }

    @Test
    public void plainTableTopLeftDataCellPreserved() {
        // Table with column headers but no row headers: top-left DATA cell "1".
        String input = String.join("\n",
                "| A | B |",
                "|---|---|",
                "| 1 | 2 |");

        TableDialog dialog = new TableDialog(null, input);
        try {
            String output = dialog.getMarkdownTable();
            assertTrue("Header A preserved:\n" + output, output.contains("A"));
            assertTrue("Top-left data cell 1 preserved:\n" + output, output.contains("1"));
            assertFalse("Should not fabricate bold row headers:\n" + output,
                    output.contains("**1**"));
        } finally {
            dialog.dispose();
        }
    }
}
