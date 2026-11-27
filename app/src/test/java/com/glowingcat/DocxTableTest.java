package com.glowingcat;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.Test;

public class DocxTableTest {

    @Test
    public void gfmTableExportsAsRealTable() throws Exception {
        String md = "| Name | **Qty** | Note |\n|:-----|-----:|:----:|\n| Apple | 3 | `fresh` |\n| Pear | 12 | |\n\nAfter\n";
        org.commonmark.parser.Parser parser = org.commonmark.parser.Parser.builder()
                .extensions(List.of(org.commonmark.ext.gfm.tables.TablesExtension.create())).build();
        File out = File.createTempFile("tbl", ".docx");
        out.deleteOnExit();
        new DocxExporter(null).export(parser.parse(md), out);

        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(out))) {
            assertEquals(1, doc.getTables().size());
            XWPFTable t = doc.getTables().get(0);
            assertEquals(3, t.getRows().size());
            assertEquals(3, t.getRow(0).getTableCells().size());
            assertEquals("Name", t.getRow(0).getCell(0).getText());
            assertEquals("Qty", t.getRow(0).getCell(1).getText());
            assertEquals("Apple", t.getRow(1).getCell(0).getText());
            assertEquals("fresh", t.getRow(1).getCell(2).getText());
            assertEquals("12", t.getRow(2).getCell(1).getText());
            assertEquals(ParagraphAlignment.RIGHT, t.getRow(1).getCell(1).getParagraphs().get(0).getAlignment());
            assertTrue(t.getCTTbl().getTblPr().isSetTblBorders());
        }
    }
}
