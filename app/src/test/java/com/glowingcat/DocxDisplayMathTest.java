package com.glowingcat;

import static org.junit.Assert.*;

import org.junit.Test;

public class DocxDisplayMathTest {

    private final DocxExporter exporter = new DocxExporter(null);

    @Test
    public void multilineBlockBecomesSinglePlaceholderParagraph() {
        String md = "Before\n\n$$\nx = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\n$$\n\nAfter\n";
        String out = exporter.protectDisplayMath(md);
        assertFalse(out.contains("$$"));
        assertFalse(out.contains("frac"));
        assertTrue(out.contains("PPMATHBLOCK0END"));
    }

    @Test
    public void oneLineBlockIsProtected() {
        String out = exporter.protectDisplayMath("$$ a_1 + b_2 $$\n");
        assertTrue(out.contains("PPMATHBLOCK0END"));
        assertFalse(out.contains("a_1"));
    }

    @Test
    public void fencedCodeAndCodeSpansAreUntouched() {
        String md = "```\n$$\nx\n$$\n```\n\nUse `$$ y $$` here.\n";
        assertEquals(md, exporter.protectDisplayMath(md));
    }

    @Test
    public void inlineMathIsNotTouched() {
        String md = "Euler: $e^{i\\pi} + 1 = 0$ done.\n";
        assertEquals(md, exporter.protectDisplayMath(md));
    }

    @Test
    public void multilineBlockExportsAsEquation() throws Exception {
        String md = "Intro\n\n$$\nx = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\n$$\n\nOutro\n";
        org.commonmark.node.Node doc = org.commonmark.parser.Parser.builder().build()
                .parse(exporter.protectDisplayMath(md));
        java.io.File out = java.io.File.createTempFile("math", ".docx");
        out.deleteOnExit();
        exporter.export(doc, out);
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(out)) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue("equation expected", xml.contains("oMath"));
            assertFalse("raw LaTeX must not appear", xml.contains("\\frac"));
        }
    }
}
