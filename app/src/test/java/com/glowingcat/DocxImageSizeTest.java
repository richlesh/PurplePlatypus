package com.glowingcat;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public class DocxImageSizeTest {

    private static long[] firstExtent(File docx) throws Exception {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(docx)) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("<wp:extent cx=\"(\\d+)\" cy=\"(\\d+)\"").matcher(xml);
            assertTrue(m.find());
            return new long[] {Long.parseLong(m.group(1)), Long.parseLong(m.group(2))};
        }
    }

    private static long[] export(File dir, String name, int w, int h) throws Exception {
        javax.imageio.ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", new File(dir, name));
        File md = new File(dir, "doc.md");
        Files.writeString(md.toPath(), "![x](" + name + ")\n");
        DocxExporter exporter = new DocxExporter(md);
        File out = new File(dir, "out.docx");
        exporter.export(org.commonmark.parser.Parser.builder().build().parse("![x](" + name + ")\n"), out);
        return firstExtent(out);
    }

    @Test
    public void smallImageKeepsNaturalSizeAndRatio() throws Exception {
        File dir = Files.createTempDirectory("docximg").toFile();
        long[] e = export(dir, "a.png", 200, 100);
        assertEquals(200 * 9525L, e[0]);
        assertEquals(100 * 9525L, e[1]);
    }

    @Test
    public void wideImageIsScaledToPageWidthKeepingRatio() throws Exception {
        File dir = Files.createTempDirectory("docximg").toFile();
        long[] e = export(dir, "b.png", 1600, 400);
        assertEquals(6L * 914400, e[0]);
        assertEquals(6L * 914400 / 4, e[1]);
    }
}
