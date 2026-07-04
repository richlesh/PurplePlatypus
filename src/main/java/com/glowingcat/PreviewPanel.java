/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.network.CefRequest;
import org.cef.callback.CefCallback;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * A panel that renders Markdown as HTML using JCEF (Chromium Embedded Framework).
 */
public class PreviewPanel extends JPanel {

    private CefBrowser browser;
    private final Parser parser;
    private final HtmlRenderer renderer;
    private String lastHtml = "";
    private File tempHtmlFile;
    private boolean browserReady = false;

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

        initBrowser();
    }

    private void initBrowser() {
        CefClient client = CefAppManager.getClient();
        if (client == null) {
            // JCEF not yet initialized; show a placeholder
            add(new JLabel("Initializing preview...", SwingConstants.CENTER), BorderLayout.CENTER);
            return;
        }

        // Intercept external link navigation — open in system browser
        client.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser b, CefFrame frame, CefRequest request,
                                          boolean userGesture, boolean isRedirect) {
                String url = request.getURL();
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                    } catch (Exception ex) {
                        // Silently fail
                    }
                    return true; // Cancel navigation in embedded browser
                }
                return false;
            }
        });

        browser = client.createBrowser("about:blank", false, false);
        browserReady = true;
        Component browserUI = browser.getUIComponent();
        add(browserUI, BorderLayout.CENTER);
    }

    /**
     * Returns the CefBrowser instance for printing, or null if not initialized.
     */
    public CefBrowser getBrowser() {
        return browser;
    }

    /**
     * Updates the preview with the given markdown text.
     */
    public void updatePreview(String markdown, File currentFile, Preferences preferences) {
        if (!browserReady || browser == null) return;

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
            // For TextBundle: if baseDir is a .textbundle, also check assets/ subfolder
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
                    // If not found and we're in a TextBundle, try the assets/ subfolder
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

        try {
            if (tempHtmlFile == null) {
                tempHtmlFile = File.createTempFile("purpleplatypus_preview", ".html");
                tempHtmlFile.deleteOnExit();
            }
            Files.writeString(tempHtmlFile.toPath(), styledHtml, StandardCharsets.UTF_8);
            browser.loadURL(tempHtmlFile.toURI().toString());
        } catch (Exception ex) {
            // Fallback to loading HTML as data URI
            browser.loadURL("data:text/html;charset=utf-8," + java.net.URLEncoder.encode(styledHtml, StandardCharsets.UTF_8));
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

    /**
     * Disposes the browser when the panel is no longer needed.
     */
    public void dispose() {
        if (browser != null) {
            browser.close(true);
            browser = null;
            browserReady = false;
        }
    }
}
