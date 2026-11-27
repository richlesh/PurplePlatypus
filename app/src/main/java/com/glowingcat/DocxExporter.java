package com.glowingcat;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.ext.ins.Ins;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.w3c.dom.Document;
import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleSession;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports markdown content to DOCX format using Apache POI.
 * Walks the commonmark AST to create properly styled Word documents.
 * Supports headings, bold, italic, strikethrough, underline, code,
 * block quotes, lists (ordered, unordered, task), tables, links,
 * images, horizontal rules, and LaTeX math (converted via SnuggleTeX + OMML).
 */
public class DocxExporter {

    private final XWPFDocument document;
    private final File sourceFile;
    private Transformer mml2ommlTransformer;
    private final SnuggleEngine snuggleEngine;
    private int listLevel = 0;
    private boolean inOrderedList = false;
    private int orderedItemNumber = 0;

    // Inline formatting state
    private boolean bold = false;
    private boolean italic = false;
    private boolean strikethrough = false;
    private boolean underline = false;
    private String linkUrl = null;

    public DocxExporter(File sourceFile) {
        this.sourceFile = sourceFile;
        this.document = new XWPFDocument();
        addHeadingStyles();
        this.snuggleEngine = new SnuggleEngine();
        initMathTransformer();
    }

    /**
     * A new XWPFDocument has no style definitions, so paragraphs tagged "Heading1".."Heading6"
     * would render as ordinary text. Define the six heading styles (bold, graduated sizes,
     * outline levels so Word's navigation pane and TOC recognise them).
     */
    private void addHeadingStyles() {
        XWPFStyles styles = document.createStyles();
        // Font sizes in half-points: 24pt, 20pt, 16pt, 14pt, 12pt, 11pt.
        int[] halfPoints = {48, 40, 32, 28, 24, 22};
        for (int level = 1; level <= 6; level++) {
            String id = "Heading" + level;
            CTStyle ct = CTStyle.Factory.newInstance();
            ct.setType(STStyleType.PARAGRAPH);
            ct.setStyleId(id);
            ct.addNewName().setVal("heading " + level);
            ct.addNewNext().setVal("Normal");
            ct.addNewUiPriority().setVal(java.math.BigInteger.valueOf(9));
            ct.addNewQFormat();

            CTPPrGeneral ppr = ct.addNewPPr();
            ppr.addNewKeepNext();
            ppr.addNewKeepLines();
            CTSpacing spacing = ppr.addNewSpacing();
            spacing.setBefore(java.math.BigInteger.valueOf(level == 1 ? 360 : 240));
            spacing.setAfter(java.math.BigInteger.valueOf(120));
            ppr.addNewOutlineLvl().setVal(java.math.BigInteger.valueOf(level - 1));

            CTRPr rpr = ct.addNewRPr();
            rpr.addNewB();
            rpr.addNewSz().setVal(java.math.BigInteger.valueOf(halfPoints[level - 1]));
            rpr.addNewSzCs().setVal(java.math.BigInteger.valueOf(halfPoints[level - 1]));

            styles.addStyle(new XWPFStyle(ct));
        }
    }

    // --- Display math protection ---

    private static final java.util.regex.Pattern MATH_PLACEHOLDER =
            java.util.regex.Pattern.compile("PPMATHBLOCK(\\d+)END");
    private static final java.util.regex.Pattern FENCE_LINE =
            java.util.regex.Pattern.compile("^ {0,3}(`{3,}|~{3,})");
    private static final java.util.regex.Pattern WHOLE_LINE_DISPLAY_MATH =
            java.util.regex.Pattern.compile("(?m)^[ \\t]{0,3}\\$\\$(?!\\$)([\\s\\S]+?)\\$\\$[ \\t]*$");
    private static final java.util.regex.Pattern CODE_SPAN_OR_DISPLAY_MATH =
            java.util.regex.Pattern.compile("(`+)(?:(?!\\1)[\\s\\S])+?\\1|\\$\\$(?!\\$)([\\s\\S]+?)\\$\\$");

    private final java.util.List<String> displayMathBlocks = new java.util.ArrayList<>();

    /**
     * Replaces display math ({@code $$ ... $$}), including formulas spread over several lines,
     * with opaque placeholders before the Markdown is parsed. Otherwise the parser splits a
     * multi-line block into separate text nodes (and may apply Markdown escapes/emphasis to
     * the LaTeX), so the exporter never sees a complete {@code $$...$$} pair. The placeholders
     * are turned back into equations by {@link #processTextWithMath}. Fenced code blocks and
     * inline code spans are left untouched. Call this on the Markdown source before parsing.
     */
    public String protectDisplayMath(String markdown) {
        if (markdown == null || !markdown.contains("$$")) return markdown;
        StringBuilder result = new StringBuilder(markdown.length());
        StringBuilder segment = new StringBuilder();
        String fence = null; // opening fence while inside a fenced code block
        int pos = 0;
        while (pos < markdown.length()) {
            int nl = markdown.indexOf('\n', pos);
            int end = nl < 0 ? markdown.length() : nl + 1;
            String line = markdown.substring(pos, end);
            pos = end;
            java.util.regex.Matcher fm = FENCE_LINE.matcher(line);
            if (fence != null) {
                result.append(line);
                if (fm.find() && fm.group(1).charAt(0) == fence.charAt(0)
                        && fm.group(1).length() >= fence.length()) {
                    fence = null;
                }
            } else if (fm.find()) {
                result.append(protectInSegment(segment.toString()));
                segment.setLength(0);
                fence = fm.group(1);
                result.append(line);
            } else {
                segment.append(line);
            }
        }
        result.append(protectInSegment(segment.toString()));
        return result.toString();
    }

    private String protectInSegment(String text) {
        if (!text.contains("$$")) return text;
        // Blocks that occupy whole lines become their own paragraph.
        java.util.regex.Matcher m = WHOLE_LINE_DISPLAY_MATH.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            displayMathBlocks.add(m.group(1).trim());
            String ph = "PPMATHBLOCK" + (displayMathBlocks.size() - 1) + "END";
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("\n" + ph + "\n"));
        }
        m.appendTail(sb);
        // Any remaining $$...$$ (mid-line, in lists or quotes) is replaced in place.
        m = CODE_SPAN_OR_DISPLAY_MATH.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            if (m.group(2) == null) {
                m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(m.group()));
            } else {
                displayMathBlocks.add(m.group(2).trim());
                String ph = "PPMATHBLOCK" + (displayMathBlocks.size() - 1) + "END";
                m.appendReplacement(out, ph);
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    private void initMathTransformer() {
        try {
            InputStream xslStream = getClass().getResourceAsStream("/MML2OMML.XSL");
            if (xslStream != null) {
                // MML2OMML.XSL has complex XPath expressions that exceed Java's default limit
                System.setProperty("jdk.xml.xpathExprGrpLimit", "0");
                System.setProperty("jdk.xml.xpathExprOpLimit", "0");
                System.setProperty("jdk.xml.xpathTotalOpLimit", "0");
                TransformerFactory tf = TransformerFactory.newInstance();
                mml2ommlTransformer = tf.newTransformer(new StreamSource(xslStream));
                xslStream.close();
            }
        } catch (Exception e) {
            // Math conversion won't be available
            mml2ommlTransformer = null;
        }
    }

    /**
     * Exports the given commonmark document node to a DOCX file.
     */
    public void export(Node docNode, File outputFile) throws IOException {
        // Walk the AST
        Node child = docNode.getFirstChild();
        while (child != null) {
            processBlock(child);
            child = child.getNext();
        }

        // Write the document
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            document.write(fos);
        }
        document.close();
    }

    private void processBlock(Node node) {
        if (node instanceof Heading heading) {
            processHeading(heading);
        } else if (node instanceof Paragraph para) {
            processParagraph(para);
        } else if (node instanceof FencedCodeBlock codeBlock) {
            processFencedCodeBlock(codeBlock);
        } else if (node instanceof IndentedCodeBlock codeBlock) {
            processIndentedCodeBlock(codeBlock);
        } else if (node instanceof BlockQuote quote) {
            processBlockQuote(quote);
        } else if (node instanceof BulletList list) {
            processBulletList(list);
        } else if (node instanceof OrderedList list) {
            processOrderedList(list);
        } else if (node instanceof ThematicBreak) {
            processThematicBreak();
        } else if (node instanceof TableBlock table) {
            processTable(table);
        } else if (node instanceof HtmlBlock htmlBlock) {
            processHtmlBlock(htmlBlock);
        } else {
            // Unknown block - try to process children
            Node child = node.getFirstChild();
            while (child != null) {
                processBlock(child);
                child = child.getNext();
            }
        }
    }

    private void processHeading(Heading heading) {
        XWPFParagraph para = document.createParagraph();
        String style;
        switch (heading.getLevel()) {
            case 1 -> style = "Heading1";
            case 2 -> style = "Heading2";
            case 3 -> style = "Heading3";
            case 4 -> style = "Heading4";
            case 5 -> style = "Heading5";
            case 6 -> style = "Heading6";
            default -> style = "Heading1";
        }
        para.setStyle(style);
        processInlineChildren(para, heading);
    }

    private void processParagraph(Paragraph para) {
        // Check if this paragraph is a single image
        if (isSingleImage(para)) {
            processImageParagraph(para);
            return;
        }

        XWPFParagraph xpara = document.createParagraph();
        if (listLevel > 0) {
            applyListFormatting(xpara);
        }
        processInlineChildren(xpara, para);
    }

    private boolean isSingleImage(Paragraph para) {
        Node child = para.getFirstChild();
        return child instanceof Image && child.getNext() == null;
    }

    private void processImageParagraph(Paragraph para) {
        Image image = (Image) para.getFirstChild();
        XWPFParagraph xpara = document.createParagraph();
        xpara.setAlignment(ParagraphAlignment.CENTER);

        File imgFile = resolveImagePath(image.getDestination());
        if (imgFile != null && imgFile.exists()) {
            try {
                XWPFRun run = xpara.createRun();
                String filename = imgFile.getName().toLowerCase();
                int pictureType;
                if (filename.endsWith(".png")) {
                    pictureType = XWPFDocument.PICTURE_TYPE_PNG;
                } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                    pictureType = XWPFDocument.PICTURE_TYPE_JPEG;
                } else if (filename.endsWith(".gif")) {
                    pictureType = XWPFDocument.PICTURE_TYPE_GIF;
                } else {
                    pictureType = XWPFDocument.PICTURE_TYPE_PNG;
                }
                addPictureWithNaturalSize(run, imgFile, pictureType);
            } catch (Exception e) {
                // Fall back to showing alt text
                XWPFRun run = xpara.createRun();
                run.setText("[" + image.getTitle() + "]");
            }
        } else {
            // Image not found - show alt text
            XWPFRun run = xpara.createRun();
            String alt = getTextContent(image);
            run.setText("[Image: " + (alt.isEmpty() ? image.getDestination() : alt) + "]");
            run.setItalic(true);
        }
    }

    /** Widest picture placed in the document: 6 inches fits Letter/A4 with 1" margins. */
    private static final long MAX_PICTURE_WIDTH_EMU = 6L * 914400;
    private static final long EMU_PER_PIXEL = 9525; // 96 DPI

    /**
     * Adds the image to the run at its natural size (96 DPI), scaled down proportionally
     * if wider than the page text width. The aspect ratio always follows the image's own
     * pixel dimensions.
     */
    private void addPictureWithNaturalSize(XWPFRun run, File imgFile, int pictureType) throws Exception {
        long widthEmu = 5L * 914400;            // used only if the size cannot be read
        long heightEmu = (long) (widthEmu * 0.6);
        int[] px = readImagePixelSize(imgFile);
        if (px != null) {
            widthEmu = px[0] * EMU_PER_PIXEL;
            heightEmu = px[1] * EMU_PER_PIXEL;
            if (widthEmu > MAX_PICTURE_WIDTH_EMU) {
                heightEmu = heightEmu * MAX_PICTURE_WIDTH_EMU / widthEmu;
                widthEmu = MAX_PICTURE_WIDTH_EMU;
            }
        }
        try (InputStream is = new FileInputStream(imgFile)) {
            run.addPicture(is, pictureType, imgFile.getName(), (int) widthEmu, (int) heightEmu);
        }
    }

    /** Reads width and height in pixels without decoding the whole image; null if unreadable. */
    private static int[] readImagePixelSize(File file) {
        try (javax.imageio.stream.ImageInputStream in = javax.imageio.ImageIO.createImageInputStream(file)) {
            if (in == null) return null;
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(in);
            while (readers.hasNext()) {
                javax.imageio.ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    int w = reader.getWidth(0);
                    int h = reader.getHeight(0);
                    if (w > 0 && h > 0) return new int[] {w, h};
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return null;
    }

    private File resolveImagePath(String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return null;

        path = path.replace("%20", " ");
        File imgFile = new File(path);
        if (imgFile.isAbsolute()) return imgFile;

        if (sourceFile != null && sourceFile.getParentFile() != null) {
            return new File(sourceFile.getParentFile(), path);
        }
        return null;
    }

    private void processFencedCodeBlock(FencedCodeBlock codeBlock) {
        String literal = codeBlock.getLiteral();
        if (literal == null) return;

        // Check for mermaid diagrams
        String info = codeBlock.getInfo();
        if ("mermaid".equalsIgnoreCase(info)) {
            if (processMermaidBlock(literal)) {
                return; // Successfully rendered as image
            }
            // Fall through to code block rendering if mermaid fails
        }

        String[] lines = literal.split("\n");
        for (String line : lines) {
            XWPFParagraph para = document.createParagraph();
            para.setSpacingBefore(0);
            para.setSpacingAfter(0);
            // Indented with monospace font
            para.setIndentationLeft(720); // 0.5 inch in twips
            CTPPr ppr = para.getCTP().addNewPPr();
            CTShd shd = ppr.addNewShd();
            shd.setFill("F5F5F5");
            shd.setVal(STShd.CLEAR);
            XWPFRun run = para.createRun();
            run.setFontFamily("Courier New");
            run.setFontSize(10);
            run.setText(line);
        }
    }

    /**
     * Renders a mermaid diagram to a PNG image using JavaFX WebView and embeds it
     * in the Word document. Returns true if successful.
     */
    private boolean processMermaidBlock(String mermaidCode) {
        try {
            // Render mermaid to PNG via JavaFX WebView snapshot
            byte[] pngData = renderMermaidToPng(mermaidCode);
            if (pngData == null || pngData.length == 0) return false;

            XWPFParagraph para = document.createParagraph();
            para.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = para.createRun();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(pngData)) {
                // Use 6 inches width, auto height based on aspect ratio
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(pngData));
                if (img != null) {
                    int imgWidth = img.getWidth();
                    int imgHeight = img.getHeight();
                    // Max 6 inches wide
                    int maxWidthEmu = 6 * 914400;
                    int widthEmu = maxWidthEmu;
                    int heightEmu = (int) ((long) imgHeight * maxWidthEmu / imgWidth);
                    // Cap height at 8 inches
                    int maxHeightEmu = 8 * 914400;
                    if (heightEmu > maxHeightEmu) {
                        heightEmu = maxHeightEmu;
                        widthEmu = (int) ((long) imgWidth * maxHeightEmu / imgHeight);
                    }
                    run.addPicture(bais, XWPFDocument.PICTURE_TYPE_PNG,
                            "mermaid_diagram.png", widthEmu, heightEmu);
                } else {
                    run.addPicture(bais, XWPFDocument.PICTURE_TYPE_PNG,
                            "mermaid_diagram.png", 6 * 914400, 4 * 914400);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Uses JavaFX WebView to render a mermaid diagram and capture it as a PNG.
     * Must be called when JavaFX is available. Returns null if rendering fails.
     */
    private byte[] renderMermaidToPng(String mermaidCode) {
        // Check if JavaFX Platform is available
        try {
            // Ensure JavaFX toolkit is initialized
            try {
                javafx.application.Platform.startup(() -> {});
            } catch (IllegalStateException e) {
                // Already initialized - this is fine
            }
            javafx.application.Platform.setImplicitExit(false);
        } catch (Exception e) {
            return null; // JavaFX not available
        }

        final byte[][] result = {null};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        javafx.application.Platform.runLater(() -> {
            try {
                javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
                webView.setPrefSize(1200, 800);
                javafx.scene.web.WebEngine engine = webView.getEngine();

                String html = buildMermaidHtml(mermaidCode);

                engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                        // Wait for mermaid to finish rendering
                        javafx.animation.PauseTransition pause =
                                new javafx.animation.PauseTransition(javafx.util.Duration.millis(1500));
                        pause.setOnFinished(ev -> {
                            try {
                                // Get the actual rendered size
                                Object widthObj = engine.executeScript(
                                        "document.querySelector('svg') ? document.querySelector('svg').getBoundingClientRect().width : 800");
                                Object heightObj = engine.executeScript(
                                        "document.querySelector('svg') ? document.querySelector('svg').getBoundingClientRect().height : 600");
                                double svgWidth = widthObj instanceof Number ? ((Number) widthObj).doubleValue() : 800;
                                double svgHeight = heightObj instanceof Number ? ((Number) heightObj).doubleValue() : 600;

                                // Resize WebView to fit content
                                webView.setPrefSize(Math.max(svgWidth + 40, 400), Math.max(svgHeight + 40, 300));

                                // Need a scene for snapshot
                                javafx.scene.Scene scene = new javafx.scene.Scene(
                                        new javafx.scene.layout.StackPane(webView),
                                        webView.getPrefWidth(), webView.getPrefHeight());

                                // Take snapshot after layout
                                javafx.animation.PauseTransition layoutPause =
                                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
                                layoutPause.setOnFinished(ev2 -> {
                                    try {
                                        javafx.scene.image.WritableImage snapshot = webView.snapshot(null, null);
                                        java.awt.image.BufferedImage buffered =
                                                javafx.embed.swing.SwingFXUtils.fromFXImage(snapshot, null);
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        javax.imageio.ImageIO.write(buffered, "png", baos);
                                        result[0] = baos.toByteArray();
                                    } catch (Exception ex) {
                                        // Snapshot failed
                                    }
                                    latch.countDown();
                                });
                                layoutPause.play();
                            } catch (Exception ex) {
                                latch.countDown();
                            }
                        });
                        pause.play();
                    } else if (newState == javafx.concurrent.Worker.State.FAILED) {
                        latch.countDown();
                    }
                });

                engine.loadContent(html);
            } catch (Exception e) {
                latch.countDown();
            }
        });

        try {
            // Wait up to 10 seconds for rendering
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    private String buildMermaidHtml(String mermaidCode) {
        String mermaidJs = com.glowingcat.aichat.WebResources.mermaidJs();
        return "<!DOCTYPE html><html><head>" +
                "<style>body { margin: 0; padding: 20px; background: white; }" +
                ".mermaid { display: inline-block; }</style>" +
                "<script>" + mermaidJs + "</script>" +
                "<script>mermaid.initialize({startOnLoad: true, theme: 'default'});</script>" +
                "</head><body><div class=\"mermaid\">" +
                escapeHtml(mermaidCode) +
                "</div></body></html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private void processIndentedCodeBlock(IndentedCodeBlock codeBlock) {
        String literal = codeBlock.getLiteral();
        if (literal == null) return;
        String[] lines = literal.split("\n");
        for (String line : lines) {
            XWPFParagraph para = document.createParagraph();
            para.setSpacingBefore(0);
            para.setSpacingAfter(0);
            para.setIndentationLeft(720);
            XWPFRun run = para.createRun();
            run.setFontFamily("Courier New");
            run.setFontSize(10);
            run.setText(line);
        }
    }

    private void processBlockQuote(BlockQuote quote) {
        // Process children with indentation
        Node child = quote.getFirstChild();
        while (child != null) {
            if (child instanceof Paragraph para) {
                XWPFParagraph xpara = document.createParagraph();
                xpara.setIndentationLeft(720);
                xpara.setBorderLeft(Borders.SINGLE);
                CTPPr ppr = xpara.getCTP().isSetPPr() ? xpara.getCTP().getPPr() : xpara.getCTP().addNewPPr();
                CTPBdr bdr = ppr.isSetPBdr() ? ppr.getPBdr() : ppr.addNewPBdr();
                CTBorder left = bdr.isSetLeft() ? bdr.getLeft() : bdr.addNewLeft();
                left.setVal(STBorder.SINGLE);
                left.setSz(BigInteger.valueOf(12));
                left.setColor("999999");
                left.setSpace(BigInteger.valueOf(8));
                processInlineChildren(xpara, para);
            } else {
                processBlock(child);
            }
            child = child.getNext();
        }
    }

    private void processBulletList(BulletList list) {
        listLevel++;
        inOrderedList = false;
        Node item = list.getFirstChild();
        while (item != null) {
            processListItem(item);
            item = item.getNext();
        }
        listLevel--;
    }

    private void processOrderedList(OrderedList list) {
        listLevel++;
        inOrderedList = true;
        orderedItemNumber = list.getStartNumber();
        Node item = list.getFirstChild();
        while (item != null) {
            processListItem(item);
            orderedItemNumber++;
            item = item.getNext();
        }
        listLevel--;
    }

    private void processListItem(Node item) {
        Node child = item.getFirstChild();
        while (child != null) {
            if (child instanceof Paragraph para) {
                XWPFParagraph xpara = document.createParagraph();
                applyListFormatting(xpara);

                // Check for task list marker
                Node firstInline = para.getFirstChild();
                if (firstInline instanceof TaskListItemMarker marker) {
                    XWPFRun checkRun = xpara.createRun();
                    checkRun.setText(marker.isChecked() ? "☑ " : "☐ ");
                }

                processInlineChildren(xpara, para);
            } else if (child instanceof BulletList || child instanceof OrderedList) {
                processBlock(child);
            } else {
                processBlock(child);
            }
            child = child.getNext();
        }
    }

    private void applyListFormatting(XWPFParagraph para) {
        int indent = listLevel * 360; // twips per level
        para.setIndentationLeft(indent);
        para.setIndentationHanging(360);

        // Add bullet or number
        XWPFRun bulletRun = para.createRun();
        if (inOrderedList) {
            bulletRun.setText(orderedItemNumber + ".\t");
        } else {
            bulletRun.setText("•\t");
        }
    }

    private void processThematicBreak() {
        XWPFParagraph para = document.createParagraph();
        CTPPr ppr = para.getCTP().addNewPPr();
        CTPBdr bdr = ppr.addNewPBdr();
        CTBorder bottom = bdr.addNewBottom();
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(6));
        bottom.setColor("AAAAAA");
        bottom.setSpace(BigInteger.valueOf(1));
    }

    private void processTable(TableBlock tableBlock) {
        List<List<Node>> headerRows = new ArrayList<>();
        List<List<Node>> bodyRows = new ArrayList<>();
        List<TableCell.Alignment> alignments = new ArrayList<>();

        Node child = tableBlock.getFirstChild();
        while (child != null) {
            if (child instanceof TableHead head) {
                collectTableRows(head, headerRows, alignments);
            } else if (child instanceof TableBody body) {
                collectTableRows(body, bodyRows, alignments);
            }
            child = child.getNext();
        }

        int numCols = 0;
        for (List<Node> r : headerRows) numCols = Math.max(numCols, r.size());
        for (List<Node> r : bodyRows) numCols = Math.max(numCols, r.size());
        int totalRows = headerRows.size() + bodyRows.size();
        if (numCols == 0 || totalRows == 0) return;

        XWPFTable table = document.createTable(totalRows, numCols);

        // Full-width table (5000 = 100% in fiftieths of a percent) with visible grid lines.
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) tblPr = table.getCTTbl().addNewTblPr();
        CTTblWidth tblWidth = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblWidth.setW(BigInteger.valueOf(5000));
        tblWidth.setType(STTblWidth.PCT);
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "808080");
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "808080");
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "808080");
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "808080");
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "808080");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "808080");
        table.setCellMargins(40, 80, 40, 80);

        BigInteger colWidth = BigInteger.valueOf(5000L / numCols);
        int rowIndex = 0;
        for (int pass = 0; pass < 2; pass++) {
            boolean header = pass == 0;
            for (List<Node> cells : header ? headerRows : bodyRows) {
                XWPFTableRow tblRow = table.getRow(rowIndex++);
                if (header) tblRow.setRepeatHeader(true);
                for (int i = 0; i < numCols; i++) {
                    XWPFTableCell cell = tblRow.getCell(i);
                    CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                    CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                    tcW.setW(colWidth);
                    tcW.setType(STTblWidth.PCT);
                    if (header) {
                        CTShd shd = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
                        shd.setFill("DDDDDD");
                        shd.setVal(STShd.CLEAR);
                    }
                    XWPFParagraph p = cell.getParagraphs().get(0);
                    applyCellAlignment(p, i < alignments.size() ? alignments.get(i) : null);
                    if (i < cells.size()) {
                        boolean savedBold = bold;
                        if (header) bold = true;
                        processInlineChildren(p, cells.get(i));
                        bold = savedBold;
                    }
                }
            }
        }
        // Keep following content from being merged into the table by Word.
        document.createParagraph();
    }

    private void collectTableRows(Node section, List<List<Node>> rows, List<TableCell.Alignment> alignments) {
        Node row = section.getFirstChild();
        while (row != null) {
            if (row instanceof TableRow) {
                List<Node> cells = new ArrayList<>();
                Node cell = row.getFirstChild();
                while (cell != null) {
                    if (cell instanceof TableCell tc) {
                        cells.add(tc);
                        if (alignments.size() < cells.size()) {
                            alignments.add(tc.getAlignment());
                        }
                    }
                    cell = cell.getNext();
                }
                rows.add(cells);
            }
            row = row.getNext();
        }
    }

    private void applyCellAlignment(XWPFParagraph para, TableCell.Alignment alignment) {
        if (alignment == null) return;
        switch (alignment) {
            case CENTER -> para.setAlignment(ParagraphAlignment.CENTER);
            case RIGHT -> para.setAlignment(ParagraphAlignment.RIGHT);
            default -> para.setAlignment(ParagraphAlignment.LEFT);
        }
    }

    private void processHtmlBlock(HtmlBlock htmlBlock) {
        // Render HTML blocks as plain text
        String literal = htmlBlock.getLiteral();
        if (literal != null && !literal.trim().isEmpty()) {
            // Strip HTML tags for plain text display
            String text = literal.replaceAll("<[^>]+>", "").trim();
            if (!text.isEmpty()) {
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(text);
            }
        }
    }

    // --- Inline processing ---

    private void processInlineChildren(XWPFParagraph para, Node parent) {
        Node child = parent.getFirstChild();
        while (child != null) {
            processInline(para, child);
            child = child.getNext();
        }
    }

    private void processInline(XWPFParagraph para, Node node) {
        if (node instanceof Text text) {
            processTextWithMath(para, text.getLiteral());
        } else if (node instanceof SoftLineBreak) {
            addTextRun(para, " ");
        } else if (node instanceof HardLineBreak) {
            XWPFRun run = para.createRun();
            run.addBreak();
        } else if (node instanceof Code code) {
            XWPFRun run = para.createRun();
            run.setFontFamily("Courier New");
            run.setFontSize(10);
            run.setText(code.getLiteral());
            applyInlineFormatting(run);
            // Light gray background for inline code
            CTRPr rpr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
            CTShd shd = rpr.addNewShd();
            shd.setFill("F0F0F0");
            shd.setVal(STShd.CLEAR);
        } else if (node instanceof StrongEmphasis) {
            bold = true;
            processInlineChildren(para, node);
            bold = false;
        } else if (node instanceof Emphasis) {
            italic = true;
            processInlineChildren(para, node);
            italic = false;
        } else if (node instanceof Strikethrough) {
            strikethrough = true;
            processInlineChildren(para, node);
            strikethrough = false;
        } else if (node instanceof Ins) {
            underline = true;
            processInlineChildren(para, node);
            underline = false;
        } else if (node instanceof Link link) {
            String prevUrl = linkUrl;
            linkUrl = link.getDestination();
            processInlineChildren(para, link);
            // If link text differs from URL, append URL in parentheses
            String linkText = getTextContent(link);
            if (linkUrl != null && !linkUrl.equals(linkText)) {
                XWPFRun urlRun = para.createRun();
                urlRun.setText(" (" + linkUrl + ")");
                urlRun.setFontSize(9);
                urlRun.setColor("666666");
            }
            linkUrl = prevUrl;
        } else if (node instanceof Image image) {
            processInlineImage(para, image);
        } else if (node instanceof HtmlInline htmlInline) {
            // Check for math delimiters or subscript/superscript
            String rawHtml = htmlInline.getLiteral();
            if (rawHtml != null) {
                if (rawHtml.equals("<sub>") || rawHtml.equals("<sup>") ||
                    rawHtml.equals("</sub>") || rawHtml.equals("</sup>")) {
                    // Skip HTML tags for sub/superscript - handled as plain text
                } else {
                    // Strip tags and output text
                    String text = rawHtml.replaceAll("<[^>]+>", "");
                    if (!text.isEmpty()) {
                        addTextRun(para, text);
                    }
                }
            }
        } else if (node instanceof TaskListItemMarker) {
            // Handled in processListItem
        } else {
            // Generic: process children
            processInlineChildren(para, node);
        }
    }

    private void processInlineImage(XWPFParagraph para, Image image) {
        File imgFile = resolveImagePath(image.getDestination());
        if (imgFile != null && imgFile.exists()) {
            try {
                XWPFRun run = para.createRun();
                String filename = imgFile.getName().toLowerCase();
                int pictureType;
                if (filename.endsWith(".png")) {
                    pictureType = XWPFDocument.PICTURE_TYPE_PNG;
                } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                    pictureType = XWPFDocument.PICTURE_TYPE_JPEG;
                } else if (filename.endsWith(".gif")) {
                    pictureType = XWPFDocument.PICTURE_TYPE_GIF;
                } else {
                    pictureType = XWPFDocument.PICTURE_TYPE_PNG;
                }
                addPictureWithNaturalSize(run, imgFile, pictureType);
            } catch (Exception e) {
                XWPFRun run = para.createRun();
                run.setText("[" + getTextContent(image) + "]");
                run.setItalic(true);
            }
        } else {
            XWPFRun run = para.createRun();
            String alt = getTextContent(image);
            run.setText("[Image: " + (alt.isEmpty() ? image.getDestination() : alt) + "]");
            run.setItalic(true);
        }
    }

    private void addTextRun(XWPFParagraph para, String text) {
        if (text == null || text.isEmpty()) return;

        XWPFRun run = para.createRun();
        run.setText(text);
        applyInlineFormatting(run);

        if (linkUrl != null) {
            // Create a hyperlink
            run.setColor("0366D6");
            run.setUnderline(UnderlinePatterns.SINGLE);
        }
    }

    /**
     * Processes a text string, detecting inline math ($...$) and display math ($$...$$)
     * and converting them to OMML equations in the Word document.
     */
    private void processTextWithMath(XWPFParagraph para, String text) {
        if (text == null || text.isEmpty()) return;
        if (text.contains("PPMATHBLOCK")) {
            java.util.regex.Matcher pm = MATH_PLACEHOLDER.matcher(text);
            int last = 0;
            while (pm.find()) {
                processTextWithMath(para, text.substring(last, pm.start()));
                int idx = Integer.parseInt(pm.group(1));
                String latex = idx < displayMathBlocks.size() ? displayMathBlocks.get(idx) : null;
                if (latex != null && !insertMath(para, latex, true)) {
                    addTextRun(para, "$$" + latex + "$$");
                } else if (latex == null) {
                    addTextRun(para, pm.group());
                }
                last = pm.end();
            }
            processTextWithMath(para, text.substring(last));
            return;
        }
        if (mml2ommlTransformer == null || !text.contains("$")) {
            addTextRun(para, text);
            return;
        }

        int i = 0;
        int len = text.length();
        StringBuilder buffer = new StringBuilder();

        while (i < len) {
            if (text.charAt(i) == '$') {
                // Check for display math $$...$$
                if (i + 1 < len && text.charAt(i + 1) == '$') {
                    int end = text.indexOf("$$", i + 2);
                    if (end > i) {
                        // Flush buffered text
                        if (buffer.length() > 0) {
                            addTextRun(para, buffer.toString());
                            buffer.setLength(0);
                        }
                        String latex = text.substring(i + 2, end);
                        if (!insertMath(para, latex, true)) {
                            // Fallback: show as text
                            addTextRun(para, "$$" + latex + "$$");
                        }
                        i = end + 2;
                        continue;
                    }
                }
                // Check for inline math $...$
                int end = text.indexOf('$', i + 1);
                if (end > i) {
                    // Make sure it's not an empty match or escaped
                    String latex = text.substring(i + 1, end);
                    if (!latex.isEmpty() && !latex.contains("\n")) {
                        // Flush buffered text
                        if (buffer.length() > 0) {
                            addTextRun(para, buffer.toString());
                            buffer.setLength(0);
                        }
                        if (!insertMath(para, latex, false)) {
                            // Fallback: show as text
                            addTextRun(para, "$" + latex + "$");
                        }
                        i = end + 1;
                        continue;
                    }
                }
            }
            buffer.append(text.charAt(i));
            i++;
        }

        // Flush remaining text
        if (buffer.length() > 0) {
            addTextRun(para, buffer.toString());
        }
    }

    private void applyInlineFormatting(XWPFRun run) {
        if (bold) run.setBold(true);
        if (italic) run.setItalic(true);
        if (strikethrough) run.setStrikeThrough(true);
        if (underline) run.setUnderline(UnderlinePatterns.SINGLE);
    }

    // --- Math conversion ---

    /**
     * Converts a LaTeX math expression to OMML and inserts it into the paragraph.
     * Returns true if successful, false if math conversion is unavailable.
     */
    public boolean insertMath(XWPFParagraph para, String latex, boolean display) {
        if (mml2ommlTransformer == null) return false;

        try {
            // Convert LaTeX to MathML using SnuggleTeX
            SnuggleSession session = snuggleEngine.createSession();
            String input;
            if (display) {
                input = "\\[" + latex + "\\]";
            } else {
                input = "\\(" + latex + "\\)";
            }
            session.parseInput(new SnuggleInput(input));
            String mathml = session.buildXMLString();

            if (mathml == null || mathml.isEmpty()) return false;

            // Parse MathML
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document mathmlDoc = dbf.newDocumentBuilder().parse(
                    new org.xml.sax.InputSource(new StringReader(mathml)));

            // Transform MathML to OMML
            DOMResult result = new DOMResult();
            mml2ommlTransformer.transform(new DOMSource(mathmlDoc), result);

            // Serialize the OMML output to a string
            Document ommlDoc = (Document) result.getNode();
            javax.xml.transform.Transformer serializer = TransformerFactory.newInstance().newTransformer();
            serializer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter sw = new java.io.StringWriter();
            serializer.transform(new DOMSource(ommlDoc),
                    new javax.xml.transform.stream.StreamResult(sw));
            String ommlXml = sw.toString();

            // Insert OMML into the paragraph using XmlCursor
            CTP ctp = para.getCTP();
            XmlObject ommlObj = XmlObject.Factory.parse(ommlXml);
            XmlCursor srcCursor = ommlObj.newCursor();
            srcCursor.toFirstContentToken();

            XmlCursor destCursor = ctp.newCursor();
            destCursor.toEndToken();

            srcCursor.copyXml(destCursor);

            srcCursor.close();
            destCursor.close();
            return true;
        } catch (Exception e) {
            // Math conversion failed - will fall back to text
        }
        return false;
    }

    // --- Utilities ---

    private String getTextContent(Node node) {
        StringBuilder sb = new StringBuilder();
        collectText(node, sb);
        return sb.toString();
    }

    private void collectText(Node node, StringBuilder sb) {
        if (node instanceof Text text) {
            sb.append(text.getLiteral());
        } else if (node instanceof Code code) {
            sb.append(code.getLiteral());
        } else if (node instanceof SoftLineBreak) {
            sb.append(" ");
        } else {
            Node child = node.getFirstChild();
            while (child != null) {
                collectText(child, sb);
                child = child.getNext();
            }
        }
    }
}
