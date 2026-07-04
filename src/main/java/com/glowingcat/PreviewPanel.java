/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * A panel that renders Markdown as HTML using JavaFX WebView when available,
 * falling back to Swing's JEditorPane on platforms where WebView is not supported
 * (e.g. Windows ARM64).
 */
public class PreviewPanel extends JPanel {

    private final Parser parser;
    private final HtmlRenderer renderer;
    private String lastHtml = "";
    private java.io.File tempHtmlFile;

    // JavaFX WebView (primary renderer)
    private javafx.embed.swing.JFXPanel jfxPanel;
    private javafx.scene.web.WebEngine webEngine;
    private boolean useWebView = false;

    // Swing fallback renderer
    private JEditorPane editorPane;
    private JScrollPane scrollPane;

    public PreviewPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Preview"));

        List<Extension> extensions = Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListItemsExtension.create(),
                AutolinkExtension.create(),
                FootnotesExtension.create(),
                HeadingAnchorExtension.create(),
                ImageAttributesExtension.create(),
                InsExtension.create(),
                YamlFrontMatterExtension.create()
        );
        parser = Parser.builder().extensions(extensions).build();
        renderer = HtmlRenderer.builder().extensions(extensions).build();

        // Try to initialize JavaFX WebView; fall back to JEditorPane if it fails
        if (initWebView()) {
            useWebView = true;
        } else {
            initFallback();
        }
    }

    /**
     * Attempts to initialize JavaFX WebView. Returns true on success, false if
     * WebView is unavailable (e.g. Windows ARM64).
     */
    private boolean initWebView() {
        try {
            jfxPanel = new javafx.embed.swing.JFXPanel();
            add(jfxPanel, BorderLayout.CENTER);

            javafx.application.Platform.runLater(() -> {
                try {
                    javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
                    webEngine = webView.getEngine();
                    webEngine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
                        if (newUrl != null && (newUrl.startsWith("http://") || newUrl.startsWith("https://"))) {
                            javafx.application.Platform.runLater(() -> {
                                if (tempHtmlFile != null) {
                                    webEngine.load(tempHtmlFile.toURI().toString());
                                }
                            });
                            try {
                                java.awt.Desktop.getDesktop().browse(new java.net.URI(newUrl));
                            } catch (Exception ex) {
                                // Silently fail
                            }
                        }
                    });
                    javafx.scene.Scene scene = new javafx.scene.Scene(webView);
                    jfxPanel.setScene(scene);
                } catch (Throwable t) {
                    // WebView creation failed on the FX thread — switch to fallback
                    SwingUtilities.invokeLater(() -> {
                        remove(jfxPanel);
                        jfxPanel = null;
                        useWebView = false;
                        initFallback();
                        revalidate();
                        repaint();
                    });
                }
            });
            return true;
        } catch (Throwable t) {
            // JFXPanel or Platform init failed entirely
            if (jfxPanel != null) {
                remove(jfxPanel);
                jfxPanel = null;
            }
            return false;
        }
    }

    /**
     * Initializes the Swing JEditorPane fallback renderer.
     */
    private void initFallback() {
        editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setEditable(false);
        editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    java.awt.Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    // Silently fail
                }
            }
        });
        scrollPane = new JScrollPane(editorPane);
        add(scrollPane, BorderLayout.CENTER);

        // Notify the user that preview functionality is reduced
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                "JavaFX WebView is not supported on this platform.\n"
                + "The preview will use a basic HTML renderer with reduced functionality\n"
                + "(no advanced CSS styling or MathJax support).",
                "Preview — Reduced Functionality",
                JOptionPane.INFORMATION_MESSAGE));
    }

    /**
     * Returns the JavaFX WebEngine used for rendering, or null if using fallback.
     */
    public javafx.scene.web.WebEngine getWebEngine() {
        return webEngine;
    }

    /**
     * Updates the preview with the given markdown text.
     */
    public void updatePreview(String markdown, File currentFile, Preferences preferences) {
        // Pre-process: encode spaces in image/link URLs
        java.util.regex.Pattern mdLinkPattern = java.util.regex.Pattern.compile(
                "(!?\\[[^\\]]*\\]\\()([^)]+)(\\))");
        java.util.regex.Matcher mdMatcher = mdLinkPattern.matcher(markdown);
        StringBuilder mdSb = new StringBuilder();
        while (mdMatcher.find()) {
            String url = mdMatcher.group(2);
            if (!url.startsWith("http://") && !url.startsWith("https://") && url.contains(" ")) {
                url = url.replace(" ", "%20");
            }
            mdMatcher.appendReplacement(mdSb,
                    java.util.regex.Matcher.quoteReplacement(mdMatcher.group(1) + url + mdMatcher.group(3)));
        }
        mdMatcher.appendTail(mdSb);
        markdown = mdSb.toString();

        Node document = parser.parse(markdown);
        String html = renderer.render(document);

        // Resolve relative image paths to absolute file:// URLs
        if (currentFile != null && currentFile.getParentFile() != null) {
            File baseDir = currentFile.getParentFile();
            File assetsDir = new File(baseDir, "assets");
            boolean isTextBundle = baseDir.getName().toLowerCase().endsWith(".textbundle");
            java.util.regex.Pattern imgPattern = java.util.regex.Pattern.compile(
                    "(<img[^>]+src=\")([^\"]+)(\"[^>]*>)");
            java.util.regex.Matcher matcher = imgPattern.matcher(html);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String src = matcher.group(2);
                if (!src.startsWith("http://") && !src.startsWith("https://")
                        && !src.startsWith("data:") && !src.startsWith("file://")) {
                    String decodedSrc = src.replace("%20", " ");
                    File imgFile = new File(baseDir, decodedSrc);
                    if (!imgFile.exists() && isTextBundle && assetsDir.exists()) {
                        File inAssets = new File(assetsDir, decodedSrc);
                        if (inAssets.exists()) imgFile = inAssets;
                    }
                    src = imgFile.toURI().toString();
                }
                matcher.appendReplacement(sb,
                        java.util.regex.Matcher.quoteReplacement(matcher.group(1) + src + matcher.group(3)));
            }
            matcher.appendTail(sb);
            html = sb.toString();
        }

        String styledHtml = getStyledHtml(html, currentFile, preferences);
        lastHtml = styledHtml;

        if (useWebView) {
            javafx.application.Platform.runLater(() -> {
                if (webEngine != null) {
                    try {
                        if (tempHtmlFile == null) {
                            tempHtmlFile = java.io.File.createTempFile("purpleplatypus_preview", ".html");
                            tempHtmlFile.deleteOnExit();
                        }
                        java.nio.file.Files.writeString(tempHtmlFile.toPath(), styledHtml, java.nio.charset.StandardCharsets.UTF_8);
                        webEngine.load(tempHtmlFile.toURI().toString());
                    } catch (Exception ex) {
                        webEngine.loadContent(styledHtml);
                    }
                }
            });
        } else if (editorPane != null) {
            // Fallback: render in JEditorPane (limited CSS support, no MathJax)
            // Rewrite img tags with explicit pixel width (70% of panel) since
            // JEditorPane doesn't support CSS max-width percentages
            String fallbackHtml = styledHtml;
            int imgWidth = (int) (editorPane.getWidth() * 0.70);
            if (imgWidth > 100) {
                fallbackHtml = fallbackHtml.replaceAll(
                        "(<img\\b[^>]*?)(/?>)",
                        "$1 width=\"" + imgWidth + "\"$2");
            }
            int caretPos = editorPane.getCaretPosition();
            editorPane.setText(fallbackHtml);
            try {
                editorPane.setCaretPosition(Math.min(caretPos, editorPane.getDocument().getLength()));
            } catch (Exception ex) {
                editorPane.setCaretPosition(0);
            }
        }
    }

    /**
     * Builds styled HTML from the given body content.
     */
    public String getStyledHtml(String bodyHtml, File currentFile, Preferences preferences) {
        String fontFamily = preferences != null ? preferences.getPreviewFontFamily() : "SansSerif";
        int fontSize = preferences != null ? preferences.getPreviewFontSize() : 14;
        String codeFontFamily = preferences != null ? preferences.getPreviewCodeFontFamily() : "Monospaced";
        int codeFontSize = preferences != null ? preferences.getPreviewCodeFontSize() : 13;

        String baseTag = "";
        if (currentFile != null && currentFile.getParentFile() != null) {
            try {
                baseTag = "<base href=\"" + currentFile.getParentFile().toURI().toURL() + "\">";
            } catch (Exception ex) {
                // Ignore
            }
        }

        return "<html><head>" + baseTag + "<style>"
                + "body { font-family: '" + fontFamily + "', sans-serif; font-size: " + fontSize + "pt; padding: 10px; line-height: 1.6; overflow-x: hidden; }"
                + "h1, h2, h3 { color: #333; }"
                + "code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-family: '" + codeFontFamily + "', monospace; font-size: " + codeFontSize + "pt; }"
                + "pre { background: #f4f4f4; padding: 10px; border-radius: 5px; overflow-x: auto; font-family: '" + codeFontFamily + "', monospace; font-size: " + codeFontSize + "pt; }"
                + "blockquote { border-left: 4px solid #ccc; margin-left: 0; padding-left: 16px; color: #666; }"
                + "table { border-collapse: collapse; margin: 12px auto; }"
                + "th, td { border: 1px solid #ddd; padding: 6px 12px; }"
                + "td[align=left], th[align=left] { text-align: left; }"
                + "td[align=center], th[align=center] { text-align: center; }"
                + "td[align=right], th[align=right] { text-align: right; }"
                + "th { background-color: #f0f0f0; font-weight: bold; }"
                + "tr:nth-child(even) { background-color: #f9f9f9; }"
                + "img { max-width: 75%; display: block; margin: 12px auto; }"
                + "a { color: #0366d6; }"
                + "li { margin: 0; padding: 0; }"
                + "li p { margin: 0; display: inline; }"
                + "ul, ol { padding-left: 24px; }"
                + "input[type=checkbox] { margin-right: 6px; vertical-align: middle; }"
                + "li:has(> input[type=checkbox]) { list-style-type: none; }"
                + "mjx-container { overflow: visible !important; display: inline-block; vertical-align: middle; }"
                + "mjx-container[display=true] { display: block; text-align: center; margin: 1em 0 !important; }"
                + "mjx-container svg { overflow: visible; }"
                + "</style>"
                + "<script>"
                + "MathJax = {"
                + "  tex: { inlineMath: [['$','$']], displayMath: [['$$','$$']] },"
                + "  options: { skipHtmlTags: ['script','noscript','style','textarea','pre','code'] },"
                + "  svg: { fontCache: 'global' }"
                + "};"
                + "</script>"
                + "<script src=\"https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-svg.js\" async></script>"
                + "</head><body>" + bodyHtml + "</body></html>";
    }
}
