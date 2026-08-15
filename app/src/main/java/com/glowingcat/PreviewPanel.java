/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;
import com.glowingcat.aichat.WebResources;

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

    // Scroll listener for synchronized scrolling
    private java.util.function.DoubleConsumer scrollListener;

    // Scroll ratio to restore after a WebView content reload (synchronized scrolling)
    private double pendingScrollRatio = -1;
    private java.util.function.DoubleSupplier scrollRatioSupplier;

    // Whether the WebView has completed its initial full page load
    private boolean webViewInitialLoadDone = false;
    // The last head/style content used, to detect when a full reload is needed
    private String lastHeadHtml = "";

    // Track which libraries were loaded in the current page to detect when a full reload is needed
    private boolean loadedMathJax = false;
    private boolean loadedHighlightJs = false;
    private boolean loadedMermaid = false;

    // Strong reference to prevent GC of the JavaScript bridge object
    private ScrollBridge scrollBridge;

    public PreviewPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(Messages.get("toolbar.preview.title")));

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
        renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .nodeRendererFactory(new FigureNodeRenderer.Factory())
                .build();

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
                    // Intercept external link navigation via JavaScript (see load worker
                    // listener below) to avoid a full page reload that resets scroll position.
                    // As a safety net, the locationProperty listener still catches any navigation
                    // that slips through and cancels it by loading about:blank then restoring content.
                    webEngine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
                        if (newUrl != null && (newUrl.startsWith("http://") || newUrl.startsWith("https://"))) {
                            // Cancel the navigation by executing history.back() which avoids a full reload
                            javafx.application.Platform.runLater(() -> {
                                webEngine.executeScript("history.back();");
                            });
                            try {
                                java.awt.Desktop.getDesktop().browse(new java.net.URI(newUrl));
                            } catch (Exception ex) {
                                // Silently fail
                            }
                        }
                    });
                    // Disable the default WebView context menu (which can lock up on
                    // Linux when "Open Image in new Window" is selected for emoji/images)
                    webView.setContextMenuEnabled(false);

                    javafx.scene.Scene scene = new javafx.scene.Scene(webView);
                    jfxPanel.setScene(scene);

                    // Register Java bridge for scroll callbacks from JavaScript
                    webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                        if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                            netscape.javascript.JSObject win = (netscape.javascript.JSObject) webEngine.executeScript("window");
                            scrollBridge = new ScrollBridge();
                            win.setMember("java", scrollBridge);
                            webViewInitialLoadDone = true;

                            // Render Mermaid diagrams on initial load
                            webEngine.executeScript(
                                "if(window.mermaid){document.querySelectorAll('pre code.language-mermaid').forEach(function(el){"
                                + "var pre=el.parentElement;var div=document.createElement('div');"
                                + "div.className='mermaid';div.textContent=el.textContent;"
                                + "pre.parentElement.replaceChild(div,pre);});"
                                + "mermaid.run();}");

                            // Restore scroll position after content reload (synchronized scrolling)
                            if (pendingScrollRatio >= 0) {
                                double ratio = pendingScrollRatio;
                                pendingScrollRatio = -1;
                                webEngine.executeScript(
                                    "window.scrollTo(0, (document.body.scrollHeight - window.innerHeight) * " + ratio + ");");
                            }
                        }
                    });
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
     * Forces the next updatePreview call to do a full page reload instead of an
     * incremental body update. Call this when preferences (fonts, etc.) change
     * or when a new file is opened (base URL changes).
     */
    public void forceFullReload() {
        webViewInitialLoadDone = false;
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

        // Convert grid/Pandoc-style tables to GFM pipe tables
        markdown = convertGridTables(markdown);

        // Convert LaTeX-style math delimiters to dollar-sign delimiters before parsing
        // \(...\) → $...$ (inline math) — single line only
        markdown = markdown.replaceAll("\\\\\\((.+?)\\\\\\)", "\\$$1\\$");
        // \[...\] → $$...$$ (display math) — may span lines
        markdown = markdown.replaceAll("(?s)\\\\\\[(.+?)\\\\\\]", "\\$\\$$1\\$\\$");

        Node document = parser.parse(markdown);
        String html = renderer.render(document);

        // Mark links where the display text matches the href URL so that
        // @media print CSS can skip appending the URL after them
        html = markUrlLinks(html);

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

        // Replace non-BMP characters (emoji) with Twemoji SVG images
        html = com.glowingcat.aichat.EmojiReplacer.replaceEmoji(html);

        String styledHtml = getStyledHtml(html, currentFile, preferences, false, markdown);
        lastHtml = styledHtml;

        // Detect which libraries are needed
        boolean needsMathJax = markdown.contains("$");
        boolean needsMermaid = markdown.contains("```mermaid");
        boolean needsHighlightJs = java.util.regex.Pattern.compile("```(?!mermaid)\\w").matcher(markdown).find();

        if (useWebView) {
            final String bodyContent = html;
            final String fullHtml = styledHtml;
            // Check if a full reload is needed because new libraries are required
            final boolean forceReload = (needsMathJax && !loadedMathJax)
                    || (needsHighlightJs && !loadedHighlightJs)
                    || (needsMermaid && !loadedMermaid);
            // Capture scroll ratio on the EDT before switching to the FX thread
            final double scrollRatio = (scrollRatioSupplier != null) ? scrollRatioSupplier.getAsDouble() : -1;
            // Update loaded state
            if (needsMathJax) loadedMathJax = true;
            if (needsHighlightJs) loadedHighlightJs = true;
            if (needsMermaid) loadedMermaid = true;
            javafx.application.Platform.runLater(() -> {
                if (webEngine != null) {
                    // After the initial page load, update only the body via JavaScript
                    // to avoid resetting scroll position (which causes flashing)
                    if (webViewInitialLoadDone && !forceReload) {
                        String escaped = bodyContent
                                .replace("\\", "\\\\")
                                .replace("'", "\\'")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r")
                                .replace("</", "<\\/");
                        webEngine.executeScript("document.body.innerHTML = '" + escaped + "';");
                        // Render Mermaid diagrams (before hljs so they don't get highlighted)
                        webEngine.executeScript(
                            "if(window.mermaid){document.querySelectorAll('pre code.language-mermaid').forEach(function(el){"
                            + "var pre=el.parentElement;var div=document.createElement('div');"
                            + "div.className='mermaid';div.textContent=el.textContent;"
                            + "pre.parentElement.replaceChild(div,pre);});"
                            + "mermaid.run();}");
                        // Re-highlight code blocks (skip mermaid)
                        webEngine.executeScript(
                            "if(window.hljs){document.querySelectorAll('pre code').forEach(function(el){hljs.highlightElement(el);});}");
                        // Re-typeset MathJax if present
                        webEngine.executeScript(
                            "if(window.MathJax && MathJax.typesetPromise) MathJax.typesetPromise();");
                    } else {
                        // First load or forced reload: write the full page (head + body)
                        // to establish styles, scripts, and the scroll event listener
                        pendingScrollRatio = scrollRatio;
                        try {
                            if (tempHtmlFile == null) {
                                tempHtmlFile = java.io.File.createTempFile("purpleplatypus_preview", ".html");
                                tempHtmlFile.deleteOnExit();
                            }
                            java.nio.file.Files.writeString(tempHtmlFile.toPath(), fullHtml, java.nio.charset.StandardCharsets.UTF_8);
                            webViewInitialLoadDone = false;
                            webEngine.load(tempHtmlFile.toURI().toString());
                        } catch (Exception ex) {
                            webEngine.loadContent(fullHtml);
                        }
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
     * When called for export, includes all libraries (MathJax, highlight.js, mermaid).
     */
    public String getStyledHtml(String bodyHtml, File currentFile, Preferences preferences, boolean forExport) {
        return getStyledHtml(bodyHtml, currentFile, preferences, forExport, null);
    }

    /**
     * Builds styled HTML from the given body content.
     * When markdown source is provided, conditionally includes libraries based on content.
     * When forExport is true or markdown is null, includes all libraries.
     */
    public String getStyledHtml(String bodyHtml, File currentFile, Preferences preferences, boolean forExport, String markdown) {
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

        String selColor = preferences != null ? preferences.getSelectionColor() : "#B482FF";

        boolean dark = preferences != null && preferences.isDarkMode();

        // Detect which libraries are needed based on markdown content
        boolean needsMathJax;
        boolean needsHighlightJs;
        boolean needsMermaid;
        if (markdown == null) {
            // When markdown is unavailable, include everything
            needsMathJax = true;
            needsHighlightJs = true;
            needsMermaid = true;
        } else {
            needsMathJax = markdown.contains("$");
            needsMermaid = markdown.contains("```mermaid");
            // Needs highlight.js if there's a fenced code block that isn't mermaid
            needsHighlightJs = java.util.regex.Pattern.compile("```(?!mermaid)\\w").matcher(markdown).find();
        }

        String title = "";
        if (currentFile != null) {
            title = currentFile.getName();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head>");
        sb.append("<title>").append(title).append("</title>");
        sb.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\">");
        sb.append(baseTag).append("<style>");
        sb.append("body { font-family: '").append(fontFamily).append("', sans-serif; font-size: ").append(fontSize).append("pt; }");
        sb.append("code, pre { font-family: '").append(codeFontFamily).append("', monospace; font-size: ").append(codeFontSize).append("pt; }");
        sb.append("::selection { background: ").append(selColor).append("; }");
        sb.append("</style>");
        sb.append("<style>").append(loadPreviewCss(dark)).append("</style>");
        // Emoji image styles
        sb.append("<style>").append(com.glowingcat.aichat.EmojiReplacer.emojiCss()).append("</style>");

        if (needsHighlightJs) {
            if (forExport) {
                sb.append("<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11/build/styles/")
                  .append(dark ? "github-dark" : "github").append(".min.css\">");
                sb.append("<script src=\"https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11/build/highlight.min.js\"></script>");
            } else {
                sb.append("<style>").append(WebResources.highlightCss(dark)).append("</style>");
                sb.append("<script>").append(WebResources.highlightJs()).append("</script>");
            }
        }
        if (needsMermaid) {
            if (forExport) {
                sb.append("<script src=\"https://cdn.jsdelivr.net/npm/mermaid@11.16.1/dist/mermaid.min.js\"></script>");
            } else {
                sb.append("<script>").append(WebResources.mermaidJs()).append("</script>");
            }
        }
        if (needsMathJax) {
            sb.append("<script>");
            sb.append("MathJax = {");
            sb.append("  tex: { inlineMath: [['$','$'], ['\\\\(','\\\\)']], displayMath: [['$$','$$'], ['\\\\[','\\\\]']] },");
            sb.append("  options: { skipHtmlTags: ['script','noscript','style','textarea','pre','code'] },");
            sb.append("  svg: { fontCache: 'global' }");
            sb.append("};");
            sb.append("</script>");
            if (forExport) {
                sb.append("<script src=\"https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-svg.js\" async></script>");
            } else {
                sb.append("<script>").append(WebResources.mathjaxJs()).append("</script>");
            }
        }

        sb.append("<script>");
        if (needsMermaid) {
            sb.append("if(window.mermaid){mermaid.initialize({startOnLoad: false, theme: '").append(dark ? "dark" : "default").append("'});}");
        }
        sb.append("document.addEventListener('DOMContentLoaded', function(){");
        if (needsMermaid) {
            sb.append("if(window.mermaid){document.querySelectorAll('pre code.language-mermaid').forEach(function(el){");
            sb.append("var pre=el.parentElement;var div=document.createElement('div');");
            sb.append("div.className='mermaid';div.textContent=el.textContent;");
            sb.append("pre.parentElement.replaceChild(div,pre);});");
            sb.append("mermaid.run();}");
        }
        if (needsHighlightJs) {
            sb.append("if(window.hljs){hljs.highlightAll();}");
        }
        sb.append("});");
        sb.append("</script>");

        if (!forExport) {
            sb.append("<script>window.addEventListener('scroll', function() {");
            sb.append("  var ratio = window.scrollY / Math.max(1, document.body.scrollHeight - window.innerHeight);");
            sb.append("  if(window.java) window.java.onScroll(ratio);");
            sb.append("});");
            sb.append("document.addEventListener('click', function(e) {");
            sb.append("  var a = e.target.closest('a');");
            sb.append("  if(a && a.href && (a.href.startsWith('http://') || a.href.startsWith('https://'))) {");
            sb.append("    e.preventDefault();");
            sb.append("    if(window.java) window.java.openLink(a.href);");
            sb.append("  }");
            sb.append("});");
            sb.append("document.addEventListener('contextmenu', function(e) {");
            sb.append("  var sel = window.getSelection().toString();");
            sb.append("  if(sel && sel.trim().length > 0 && window.java) {");
            sb.append("    e.preventDefault();");
            sb.append("    window.java.findInSource(sel.trim());");
            sb.append("  }");
            sb.append("});</script>");
        }

        // Lightbox for images and Mermaid diagrams (right-click to enlarge)
        sb.append("<style>").append(WebResources.lightboxCss()).append("</style>");
        sb.append("<script>").append(WebResources.lightboxJs()).append("</script>");

        sb.append("</head><body dir=\"auto\"").append(dark ? " class=\"dark-mode\"" : "").append(">").append(bodyHtml).append("</body></html>");
        return sb.toString();
    }

    /**
     * Scrolls the preview to a given ratio (0.0 = top, 1.0 = bottom).
     * Uses JavaScript for WebView or JScrollPane for the fallback renderer.
     */
    public void scrollToRatio(double ratio) {
        if (useWebView && webEngine != null) {
            javafx.application.Platform.runLater(() -> {
                webEngine.executeScript(
                    "window.scrollTo(0, (document.body.scrollHeight - window.innerHeight) * " + ratio + ");");
            });
        } else if (scrollPane != null) {
            JScrollBar vBar = scrollPane.getVerticalScrollBar();
            int max = vBar.getMaximum() - vBar.getVisibleAmount();
            if (max > 0) {
                vBar.setValue((int) (max * ratio));
            }
        }
    }

    /**
     * Sets a listener that will be called with the scroll ratio (0.0-1.0)
     * when the user scrolls the preview pane.
     */
    public void setScrollListener(java.util.function.DoubleConsumer listener) {
        this.scrollListener = listener;

        // For the fallback JEditorPane, listen to its scroll bar
        if (!useWebView && scrollPane != null) {
            scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
                if (scrollListener == null) return;
                JScrollBar vBar = scrollPane.getVerticalScrollBar();
                int max = vBar.getMaximum() - vBar.getVisibleAmount();
                if (max > 0) {
                    scrollListener.accept((double) vBar.getValue() / max);
                }
            });
        }
    }

    /**
     * Sets a supplier that provides the current editor scroll ratio (0.0-1.0).
     * Used to restore the preview scroll position after content reloads.
     */
    public void setScrollRatioSupplier(java.util.function.DoubleSupplier supplier) {
        this.scrollRatioSupplier = supplier;
    }

    /**
     * Searches for and highlights text in the preview WebView using window.find().
     * Scrolls the found text into view.
     *
     * @param text the plain text to search for
     * @return true if found
     */
    public boolean findInPreview(String text) {
        if (!useWebView || webEngine == null) return false;
        // Escape single quotes for JavaScript string
        String escaped = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
        javafx.application.Platform.runLater(() -> {
            webEngine.executeScript("window.getSelection().removeAllRanges();");
            webEngine.executeScript("window.find('" + escaped + "', false, false, true);");
        });
        return true;
    }

    /**
     * Gets the currently selected text from the preview WebView.
     *
     * @return the selected text, or null if nothing is selected or WebView unavailable
     */
    public String getSelectedText() {
        if (!useWebView || webEngine == null) return null;
        try {
            Object result = webEngine.executeScript("window.getSelection().toString()");
            if (result instanceof String s && !s.isEmpty()) return s;
        } catch (Exception e) {
            // Silently fail
        }
        return null;
    }

    /**
     * Sets a callback for "Find in Source" requests from the preview context menu.
     */
    public void setFindInSourceCallback(java.util.function.Consumer<String> callback) {
        this.findInSourceCallback = callback;
    }

    private java.util.function.Consumer<String> findInSourceCallback;

    /**
     * Bridge object exposed to JavaScript as window.java for scroll event callbacks
     * and external link opening.
     */
    public class ScrollBridge {
        public void onScroll(double ratio) {
            if (scrollListener != null) {
                SwingUtilities.invokeLater(() -> scrollListener.accept(ratio));
            }
        }

        /**
         * Called from JavaScript when the user clicks an external link.
         * Opens the URL in the system browser without navigating the WebView.
         */
        public void openLink(String url) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception ex) {
                // Silently fail
            }
        }

        /**
         * Called from JavaScript when the user selects "Find in Source" from the context menu.
         */
        public void findInSource(String text) {
            if (findInSourceCallback != null && text != null && !text.isEmpty()) {
                SwingUtilities.invokeLater(() -> findInSourceCallback.accept(text));
            }
        }
    }

    /**
     * Adds class="url-link" to anchor tags where the link text matches the href URL.
     * This allows @media print CSS to skip appending the URL after such links,
     * since the URL is already visible as the link text.
     */
    static String markUrlLinks(String html) {
        // Match <a href="URL">TEXT</a> and add class if TEXT equals URL
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<a\\s+href=\"([^\"]+)\">(.*?)</a>");
        java.util.regex.Matcher matcher = pattern.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String href = matcher.group(1);
            String text = matcher.group(2);
            if (text.equals(href)) {
                matcher.appendReplacement(sb,
                        java.util.regex.Matcher.quoteReplacement(
                                "<a class=\"url-link\" href=\"" + href + "\">" + text + "</a>"));
            } else {
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Converts grid/Pandoc-style tables to GFM pipe tables for rendering.
     * Handles:
     * - Row separators: +------+------+ (removed)
     * - Header separators: +:=====+======:+ (converted to |:-----|------:|)
     * - Data rows: | cell | cell | (kept as-is)
     */
    private static String convertGridTables(String markdown) {
        String[] lines = markdown.split("\n", -1);
        StringBuilder result = new StringBuilder();
        boolean inTable = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Detect grid table lines starting with + and containing - or =
            if (line.matches("^\\+[-=:+]+\\+$")) {
                if (!inTable) {
                    inTable = true;
                }
                // Check if this is a header separator (contains = signs)
                if (line.contains("=")) {
                    // Convert +=====+ to |-----|
                    String converted = line.replace('+', '|');
                    // Replace = with - but preserve : for alignment
                    converted = converted.replace('=', '-');
                    result.append(converted).append('\n');
                }
                // Row separators with just - are dropped (not needed in GFM)
                continue;
            }

            // If we were in a table and hit a non-table line, end table mode
            if (inTable && !line.startsWith("|") && !line.matches("^\\+[-=:+]+\\+$")) {
                inTable = false;
            }

            result.append(lines[i]).append('\n');
        }

        // Remove trailing newline if original didn't have one
        if (result.length() > 0 && !markdown.endsWith("\n")) {
            result.setLength(result.length() - 1);
        }

        return result.toString();
    }

    private static String loadPreviewCss(boolean dark) {
        String path = dark ? "/preview_dark.css" : "/preview.css";
        try (var is = PreviewPanel.class.getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // Fall through
        }
        return "";
    }
}
