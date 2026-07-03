/*
 * (c) 2026 The Boeing Company
 */

/**
 * Main.java
 *
 * Entry point and primary UI class for the PurplePlatypus application.
 * Creates a Swing-based split-pane markdown editor with a live HTML preview,
 * line number gutter, file operations, undo/redo, clipboard support, and
 * find/replace functionality.
 */
package com.glowingcat;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Element;
import javax.swing.undo.UndoManager;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The main application class for PurplePlatypus.
 * <p>
 * Constructs the GUI on the Event Dispatch Thread and manages the editor state
 * including the current file, markdown parser, and undo history.
 */
public class Main {

    /** Tracks the number of open windows so the app exits when the last one closes. */
    private static final AtomicInteger windowCount = new AtomicInteger(0);

    /** Tracks all open Main instances for macOS Desktop handler routing. */
    private static final List<Main> openInstances = new ArrayList<>();

    private JFrame frame;
    private JTextArea editorPane;
    private WebEngine webEngine;
    private JFXPanel jfxPanel;
    private File currentFile;
    private final Parser parser;
    private final HtmlRenderer renderer;
    private final UndoManager undoManager = new UndoManager();
    private Preferences preferences;
    private boolean dirty = false;

    /**
     * Constructs the application, initializing the markdown parser and renderer,
     * loading user preferences, then building and displaying the GUI.
     */
    public Main() {
        List<Extension> extensions = Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create()
        );
        parser = Parser.builder().extensions(extensions).build();
        renderer = HtmlRenderer.builder().extensions(extensions).build();
        preferences = Preferences.load();
        createAndShowGUI();
    }

    /**
     * Builds and displays the main application window, including:
     * <ul>
     *   <li>Menu bar with File, Edit, and Search menus</li>
     *   <li>Split pane with editor (left) and HTML preview (right)</li>
     *   <li>Line number gutter on the editor pane</li>
     *   <li>Live preview via document change listener</li>
     * </ul>
     */
    private void createAndShowGUI() {
        frame = new JFrame("PurplePlatypus");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                windowCount.incrementAndGet();
                openInstances.add(Main.this);
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmClose()) {
                    frame.dispose();
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                openInstances.remove(Main.this);
                if (windowCount.decrementAndGet() == 0) {
                    System.exit(0);
                } else {
                    // Activate the next open window so macOS transfers the menu bar
                    for (Main instance : openInstances) {
                        if (instance.frame.isDisplayable()) {
                            instance.frame.toFront();
                            instance.frame.requestFocus();
                            break;
                        }
                    }
                }
            }
        });
        frame.setSize(1200, 700);

        // Application icon
        java.net.URL iconUrl = getClass().getClassLoader().getResource("app_icon_256.png");
        ImageIcon appIcon = null;
        if (iconUrl != null) {
            appIcon = new ImageIcon(iconUrl);
            frame.setIconImage(appIcon.getImage());
        }

        // Menu bar
        JMenuBar menuBar = new JMenuBar();
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        // File menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem newItem = new JMenuItem("New");
        newItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, shortcutMask));
        newItem.addActionListener(e -> newFile());

        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, shortcutMask));
        openItem.addActionListener(e -> openFile());

        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, shortcutMask));
        closeItem.addActionListener(e -> {
            if (confirmClose()) {
                frame.dispose();
            }
        });

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, shortcutMask));
        saveItem.addActionListener(e -> saveFile());

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        saveAsItem.addActionListener(e -> saveFileAs());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> exitApplication());

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(closeItem);
        fileMenu.addSeparator();
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // Edit menu
        JMenu editMenu = new JMenu("Edit");

        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, shortcutMask));
        undoItem.addActionListener(e -> {
            if (undoManager.canUndo()) {
                undoManager.undo();
            }
        });

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, shortcutMask));
        redoItem.addActionListener(e -> {
            if (undoManager.canRedo()) {
                undoManager.redo();
            }
        });

        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, shortcutMask));
        cutItem.addActionListener(e -> editorPane.cut());

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, shortcutMask));
        copyItem.addActionListener(e -> editorPane.copy());

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, shortcutMask));
        pasteItem.addActionListener(e -> editorPane.paste());

        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        menuBar.add(editMenu);

        // Search menu
        JMenu searchMenu = new JMenu("Search");

        JMenuItem findItem = new JMenuItem("Find...");
        findItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, shortcutMask));
        findItem.addActionListener(e -> showFindDialog());

        JMenuItem replaceItem = new JMenuItem("Replace...");
        replaceItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H, shortcutMask));
        replaceItem.addActionListener(e -> showReplaceDialog());

        searchMenu.add(findItem);
        searchMenu.add(replaceItem);
        menuBar.add(searchMenu);

        // Markdown menu
        JMenu markdownMenu = new JMenu("Markdown");

        JMenuItem boldItem = new JMenuItem("Bold");
        boldItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, shortcutMask));
        boldItem.setEnabled(false);
        boldItem.addActionListener(e -> wrapSelection("**", "**"));

        JMenuItem italicItem = new JMenuItem("Italic");
        italicItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I, shortcutMask));
        italicItem.setEnabled(false);
        italicItem.addActionListener(e -> wrapSelection("*", "*"));

        JMenuItem underlineItem = new JMenuItem("Underline");
        underlineItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_U, shortcutMask));
        underlineItem.setEnabled(false);
        underlineItem.addActionListener(e -> wrapSelection("<u>", "</u>"));

        JMenuItem strikethroughItem = new JMenuItem("Strikethrough");
        strikethroughItem.setEnabled(false);
        strikethroughItem.addActionListener(e -> wrapSelection("~~", "~~"));

        markdownMenu.add(boldItem);
        markdownMenu.add(italicItem);
        markdownMenu.add(underlineItem);
        markdownMenu.add(strikethroughItem);
        markdownMenu.addSeparator();

        JMenuItem linkItem = new JMenuItem("Link...");
        linkItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, shortcutMask));
        linkItem.addActionListener(e -> showLinkDialog());

        JMenuItem imageItem = new JMenuItem("Image...");
        imageItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, shortcutMask));
        imageItem.addActionListener(e -> showImageDialog());

        JMenuItem tableItem = new JMenuItem("Table...");
        tableItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, shortcutMask));
        tableItem.addActionListener(e -> showTableDialog());

        markdownMenu.add(linkItem);
        markdownMenu.add(imageItem);
        markdownMenu.add(tableItem);
        menuBar.add(markdownMenu);

        frame.setJMenuBar(menuBar);

        // Editor pane (left) - plain text area for markdown source
        editorPane = new JTextArea();
        editorPane.setFont(new Font(preferences.getEditorFontFamily(), Font.PLAIN, preferences.getEditorFontSize()));
        editorPane.setLineWrap(true);
        editorPane.setWrapStyleWord(true);
        editorPane.getDocument().addUndoableEditListener(undoManager);

        // Enable/disable Markdown menu items based on text selection
        editorPane.addCaretListener(e -> {
            boolean hasSelection = e.getDot() != e.getMark();
            boldItem.setEnabled(hasSelection);
            italicItem.setEnabled(hasSelection);
            underlineItem.setEnabled(hasSelection);
            strikethroughItem.setEnabled(hasSelection);
        });

        JScrollPane editorScroll = new JScrollPane(editorPane);
        editorScroll.setBorder(BorderFactory.createTitledBorder("Markdown Source"));

        // Line number panel
        LineNumberPanel lineNumbers = new LineNumberPanel(editorPane);
        editorScroll.setRowHeaderView(lineNumbers);

        // Preview pane (right) - JavaFX WebView for HTML5/CSS3 rendering
        jfxPanel = new JFXPanel();
        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();
            // Open links in the default browser
            webEngine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
                if (newUrl != null && !newUrl.isEmpty() && !newUrl.startsWith("data:")) {
                    Platform.runLater(() -> webEngine.loadContent(getStyledHtml("")));
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(newUrl));
                    } catch (Exception ex) {
                        // Silently fail
                    }
                }
            });
            Scene scene = new Scene(webView);
            jfxPanel.setScene(scene);
        });
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder("Preview"));
        previewPanel.add(jfxPanel, BorderLayout.CENTER);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorScroll, previewPanel);
        splitPane.setDividerLocation(600);
        splitPane.setResizeWeight(0.5);

        frame.add(splitPane, BorderLayout.CENTER);

        // Live preview: update HTML whenever the markdown source changes
        editorPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePreview();
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePreview();
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePreview();
                markDirty();
            }
        });

        // Set initial content
        editorPane.setText("# Welcome to PurplePlatypus\n\nStart typing your markdown here.\n\n"
                + "## Features\n\n"
                + "- **Live preview** as you type\n"
                + "- Open and save `.md` files\n"
                + "- Split pane editor\n");
        dirty = false;

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
        editorPane.requestFocusInWindow();
    }

    /**
     * Parses the current editor content as Markdown and renders it as styled HTML
     * in the preview pane using JavaFX WebView. Called automatically on every document change.
     */
    private void updatePreview() {
        String markdown = editorPane.getText();

        // Pre-process: encode spaces in markdown image/link URLs so CommonMark parses them
        // Matches ![alt](path with spaces) and [text](url with spaces)
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

        // Resolve relative image paths to absolute file:// URLs with proper encoding
        if (currentFile != null && currentFile.getParentFile() != null) {
            File baseDir = currentFile.getParentFile();
            java.util.regex.Pattern imgPattern = java.util.regex.Pattern.compile(
                    "(<img[^>]+src=\")([^\"]+)(\"[^>]*>)");
            java.util.regex.Matcher matcher = imgPattern.matcher(html);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String src = matcher.group(2);
                // Only resolve relative paths
                if (!src.startsWith("http://") && !src.startsWith("https://") && !src.startsWith("data:") && !src.startsWith("file://")) {
                    // Decode %20 back to space for File resolution, then let toURI() re-encode properly
                    String decodedSrc = src.replace("%20", " ");
                    File imgFile = new File(baseDir, decodedSrc);
                    src = imgFile.toURI().toString();
                }
                matcher.appendReplacement(sb,
                        java.util.regex.Matcher.quoteReplacement(matcher.group(1) + src + matcher.group(3)));
            }
            matcher.appendTail(sb);
            html = sb.toString();
        }

        String styledHtml = getStyledHtml(html);

        Platform.runLater(() -> {
            if (webEngine != null) {
                webEngine.loadContent(styledHtml);
            }
        });
    }

    /**
     * Builds a complete styled HTML document from the given body HTML.
     * Resolves relative image paths using the current file's location.
     */
    private String getStyledHtml(String bodyHtml) {
        String fontFamily = preferences.getPreviewFontFamily();
        int fontSize = preferences.getPreviewFontSize();

        String baseTag = "";
        if (currentFile != null && currentFile.getParentFile() != null) {
            try {
                baseTag = "<base href=\"" + currentFile.getParentFile().toURI().toURL() + "\">";
            } catch (Exception ex) {
                // Ignore
            }
        }

        return "<html><head>" + baseTag + "<style>"
                + "body { font-family: '" + fontFamily + "', sans-serif; font-size: " + fontSize + "pt; padding: 10px; line-height: 1.6; }"
                + "h1, h2, h3 { color: #333; }"
                + "code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-family: monospace; }"
                + "pre { background: #f4f4f4; padding: 10px; border-radius: 5px; overflow-x: auto; }"
                + "blockquote { border-left: 4px solid #ccc; margin-left: 0; padding-left: 16px; color: #666; }"
                + "table { border-collapse: collapse; margin: 12px auto; }"
                + "th, td { border: 1px solid #ddd; padding: 6px 12px; text-align: left; }"
                + "th { background-color: #f0f0f0; font-weight: bold; }"
                + "tr:nth-child(even) { background-color: #f9f9f9; }"
                + "img { max-width: 75%; display: block; margin: 12px auto; }"
                + "a { color: #0366d6; }"
                + "</style></head><body>" + bodyHtml + "</body></html>";
    }

    /**
     * Opens a new document window by creating a new application instance.
     */
    private void newFile() {
        SwingUtilities.invokeLater(Main::new);
    }

    /**
     * Displays a file chooser dialog and opens the selected markdown file
     * in a new document window. Supports .md, .markdown, and .txt extensions.
     */
    private long lastOpenTime = 0;

    private void openFile() {
        // Debounce: ignore if called again within 1 second
        long now = System.currentTimeMillis();
        if (now - lastOpenTime < 1000) return;
        lastOpenTime = now;

        // Prompt to save if current document has unsaved changes
        if (dirty) {
            if (!confirmClose()) {
                return;
            }
        }
        FileDialog dialog = new FileDialog(frame, "Open", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt");
        });
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File file = new File(dialog.getDirectory(), dialog.getFile());
            try {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                loadFileContent(file, content);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Error reading file: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        lastOpenTime = System.currentTimeMillis();
    }

    /**
     * Loads the given file content into this window's editor.
     */
    private void loadFileContent(File file, String content) {
        currentFile = file;
        editorPane.setText(content);
        editorPane.setCaretPosition(0);
        undoManager.discardAllEdits();
        dirty = false;
        updateTitle();
    }

    /**
     * Wraps the currently selected text with the given prefix and suffix.
     * For example, wrapSelection("**", "**") turns "hello" into "**hello**".
     *
     * @param prefix the text to insert before the selection
     * @param suffix the text to insert after the selection
     */
    private void wrapSelection(String prefix, String suffix) {
        int start = editorPane.getSelectionStart();
        int end = editorPane.getSelectionEnd();
        if (start == end) return;

        String selected = editorPane.getSelectedText();
        editorPane.replaceSelection(prefix + selected + suffix);
        // Re-select the wrapped text (including markers)
        editorPane.setSelectionStart(start);
        editorPane.setSelectionEnd(start + prefix.length() + selected.length() + suffix.length());
    }

    /**
     * Shows the Link dialog. If the selection is or overlaps an existing markdown link,
     * the dialog is pre-populated with that link's text and URI, and the entire link
     * is replaced on save. Otherwise uses selected text as link text for a new link.
     */
    private void showLinkDialog() {
        String selectedText = editorPane.getSelectedText();
        int selStart = editorPane.getSelectionStart();
        int selEnd = editorPane.getSelectionEnd();
        String fullText = editorPane.getText();

        // Try to find an existing markdown link that overlaps the selection
        String linkText = selectedText != null ? selectedText : "";
        String linkUri = "";
        int replaceStart = selStart;
        int replaceEnd = selEnd;

        // Search backwards from selection start to find '[' and forwards to find '](...)' 
        int searchFrom = Math.max(0, selStart - 200);
        int searchTo = Math.min(fullText.length(), selEnd + 200);
        String region = fullText.substring(searchFrom, searchTo);

        // Look for markdown link pattern [text](url) that overlaps the selection
        int idx = 0;
        while (idx < region.length()) {
            int bracketOpen = region.indexOf('[', idx);
            if (bracketOpen < 0) break;

            int bracketClose = region.indexOf(']', bracketOpen + 1);
            if (bracketClose < 0) break;

            // Check for ](
            if (bracketClose + 1 < region.length() && region.charAt(bracketClose + 1) == '(') {
                int parenClose = region.indexOf(')', bracketClose + 2);
                if (parenClose >= 0) {
                    int absStart = searchFrom + bracketOpen;
                    int absEnd = searchFrom + parenClose + 1;

                    // Check if selection overlaps this link
                    if (selStart < absEnd && selEnd > absStart) {
                        linkText = region.substring(bracketOpen + 1, bracketClose);
                        linkUri = region.substring(bracketClose + 2, parenClose);
                        replaceStart = absStart;
                        replaceEnd = absEnd;
                        break;
                    }
                }
            }
            idx = bracketOpen + 1;
        }

        LinkDialog dialog = new LinkDialog(frame, linkText, linkUri);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            String markdown = "[" + dialog.getLinkText() + "](" + dialog.getLinkUri() + ")";
            editorPane.setSelectionStart(replaceStart);
            editorPane.setSelectionEnd(replaceEnd);
            editorPane.replaceSelection(markdown);
        }
    }

    /**
     * Shows the Image dialog. If the selection is or overlaps an existing markdown image,
     * the dialog is pre-populated with that image's alt text and path, and the entire
     * image markdown is replaced on save. Otherwise uses selected text as alt text.
     */
    private void showImageDialog() {
        String selectedText = editorPane.getSelectedText();
        int selStart = editorPane.getSelectionStart();
        int selEnd = editorPane.getSelectionEnd();
        String fullText = editorPane.getText();

        String altText = selectedText != null ? selectedText : "";
        String imgPath = "";
        int replaceStart = selStart;
        int replaceEnd = selEnd;

        // Search for ![alt](path) pattern overlapping the selection
        int searchFrom = Math.max(0, selStart - 200);
        int searchTo = Math.min(fullText.length(), selEnd + 200);
        String region = fullText.substring(searchFrom, searchTo);

        int idx = 0;
        while (idx < region.length()) {
            int bangBracket = region.indexOf("![", idx);
            if (bangBracket < 0) break;

            int bracketClose = region.indexOf(']', bangBracket + 2);
            if (bracketClose < 0) break;

            if (bracketClose + 1 < region.length() && region.charAt(bracketClose + 1) == '(') {
                int parenClose = region.indexOf(')', bracketClose + 2);
                if (parenClose >= 0) {
                    int absStart = searchFrom + bangBracket;
                    int absEnd = searchFrom + parenClose + 1;

                    // Check if selection/cursor overlaps this image
                    if (selStart <= absEnd && selEnd >= absStart) {
                        altText = region.substring(bangBracket + 2, bracketClose);
                        imgPath = region.substring(bracketClose + 2, parenClose);
                        replaceStart = absStart;
                        replaceEnd = absEnd;
                        break;
                    }
                }
            }
            idx = bangBracket + 1;
        }

        ImageDialog dialog = new ImageDialog(frame, altText, imgPath, currentFile);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            String markdown = "![" + dialog.getAltText() + "](" + dialog.getImagePath() + ")";
            editorPane.setSelectionStart(replaceStart);
            editorPane.setSelectionEnd(replaceEnd);
            editorPane.replaceSelection(markdown);
        }
    }

    /**
     * Shows the Table dialog. If a markdown table (or part of one) is selected,
     * the table content is parsed and shown in the spreadsheet for editing.
     * On save, inserts or replaces the markdown table.
     */
    private void showTableDialog() {
        String selectedText = editorPane.getSelectedText();
        String fullText = editorPane.getText();
        int replaceStart = editorPane.getSelectionStart();
        int replaceEnd = editorPane.getSelectionEnd();
        String tableText = selectedText;

        // Check if the cursor/selection is within a table by examining the current line
        boolean inTable = false;
        if (selectedText != null && selectedText.contains("|")) {
            inTable = true;
        } else {
            // Check if the line at the cursor position contains a pipe
            int lineStart = fullText.lastIndexOf('\n', replaceStart - 1) + 1;
            int lineEnd = fullText.indexOf('\n', replaceStart);
            if (lineEnd < 0) lineEnd = fullText.length();
            String currentLine = fullText.substring(lineStart, lineEnd);
            if (currentLine.contains("|")) {
                inTable = true;
            }
        }

        if (inTable) {
            // Expand selection to cover the full table
            int lineStart = fullText.lastIndexOf('\n', replaceStart - 1) + 1;
            int lineEnd = replaceEnd;
            // Make sure lineEnd is at end of a line
            int eol = fullText.indexOf('\n', lineEnd);
            if (eol >= 0) lineEnd = eol;
            else lineEnd = fullText.length();

            // Expand backwards to find start of table
            while (lineStart > 0) {
                int prevLineStart = fullText.lastIndexOf('\n', lineStart - 2) + 1;
                String prevLine = fullText.substring(prevLineStart, lineStart - 1).trim();
                if (prevLine.contains("|")) {
                    lineStart = prevLineStart;
                } else {
                    break;
                }
            }
            // Expand forwards to find end of table
            while (lineEnd < fullText.length()) {
                int nextLineEnd = fullText.indexOf('\n', lineEnd + 1);
                if (nextLineEnd < 0) nextLineEnd = fullText.length();
                String nextLine = fullText.substring(lineEnd, nextLineEnd).trim();
                if (nextLine.contains("|")) {
                    lineEnd = nextLineEnd;
                } else {
                    break;
                }
            }
            tableText = fullText.substring(lineStart, lineEnd);
            replaceStart = lineStart;
            replaceEnd = lineEnd;
        }

        TableDialog dialog = new TableDialog(frame, tableText);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            String markdown = dialog.getMarkdownTable();
            editorPane.setSelectionStart(replaceStart);
            editorPane.setSelectionEnd(replaceEnd);
            editorPane.replaceSelection(markdown);
        }
    }

    /**
     * Saves the editor content to the current file. If no file has been set,
     * delegates to {@link #saveFileAs()}.
     */
    private void saveFile() {
        if (currentFile == null) {
            saveFileAs();
        } else {
            writeFile(currentFile);
        }
    }

    /**
     * Displays a file chooser dialog for the user to specify a save location.
     * Appends a .md extension if none is provided.
     */
    private void saveFileAs() {
        FileDialog dialog = new FileDialog(frame, "Save As", FileDialog.SAVE);
        if (currentFile != null) {
            dialog.setDirectory(currentFile.getParent());
            dialog.setFile(currentFile.getName());
        } else {
            dialog.setFile("untitled.md");
        }
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            currentFile = new File(dialog.getDirectory(), dialog.getFile());
            if (!currentFile.getName().contains(".")) {
                currentFile = new File(currentFile.getAbsolutePath() + ".md");
            }
            writeFile(currentFile);
            updateTitle();
        }
    }

    /**
     * Writes the current editor content to the specified file using UTF-8 encoding.
     *
     * @param file the target file to write to
     */
    private void writeFile(File file) {
        try {
            Files.write(file.toPath(), editorPane.getText().getBytes(StandardCharsets.UTF_8));
            dirty = false;
            updateTitle();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Error saving file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Marks the document as modified and updates the window title to show an indicator.
     */
    private void markDirty() {
        if (!dirty) {
            dirty = true;
            updateTitle();
        }
    }

    /**
     * Updates the window title, appending a bullet character when the document has unsaved changes.
     */
    private void updateTitle() {
        String title = "PurplePlatypus";
        if (currentFile != null) {
            title += " - " + currentFile.getName();
        }
        if (dirty) {
            title += " \u2022 Modified";
        }
        frame.setTitle(title);
    }

    /**
     * Prompts the user to save unsaved changes before closing. Returns {@code true}
     * if the window is safe to close (saved, discarded, or not dirty), or {@code false}
     * if the user cancelled.
     */
    private boolean confirmClose() {
        if (!dirty) return true;

        String filename = currentFile != null ? currentFile.getName() : "Untitled";
        int choice = JOptionPane.showOptionDialog(frame,
                "\"" + filename + "\" has unsaved changes. Do you want to save before closing?",
                "Unsaved Changes",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new String[]{"Save", "Don't Save", "Cancel"},
                "Save");

        if (choice == 0) {
            // Save
            saveFile();
            return !dirty; // returns false if save was cancelled (e.g. Save As cancelled)
        } else if (choice == 1) {
            // Don't Save
            return true;
        } else {
            // Cancel or closed dialog
            return false;
        }
    }

    /**
     * Attempts to exit the application, prompting each dirty window to save first.
     * If any window cancels, the exit is aborted.
     */
    private void exitApplication() {
        for (Main instance : new ArrayList<>(openInstances)) {
            if (!instance.confirmClose()) {
                return; // User cancelled, abort exit
            }
        }
        System.exit(0);
    }

    /**
     * Application entry point. Launches the GUI on the Swing Event Dispatch Thread.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "PurplePlatypus");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to default cross-platform L&F
        }

        // Initialize JavaFX toolkit and prevent it from exiting when windows close
        new JFXPanel(); // triggers JavaFX initialization
        Platform.setImplicitExit(false);

        // Register macOS application menu handlers once, targeting the active window
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler(e -> {
                    Main active = getActiveInstance();
                    if (active != null) active.showAboutDialog();
                });
            }
            if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
                desktop.setPreferencesHandler(e -> {
                    Main active = getActiveInstance();
                    if (active != null) active.showPreferencesDialog();
                });
            }
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                desktop.setQuitHandler((e, response) -> {
                    for (Main instance : new ArrayList<>(openInstances)) {
                        if (!instance.confirmClose()) {
                            response.cancelQuit();
                            return;
                        }
                    }
                    response.performQuit();
                });
            }
            if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
                desktop.setOpenFileHandler(e -> {
                    for (File file : e.getFiles()) {
                        SwingUtilities.invokeLater(() -> openFileInWindow(file));
                    }
                });
            }
        }

        // Open file from command-line argument, or create empty window
        SwingUtilities.invokeLater(() -> {
            if (args.length > 0) {
                File file = new File(args[0]);
                if (file.exists()) {
                    openFileInWindow(file);
                } else {
                    new Main();
                }
            } else {
                new Main();
            }
        });
    }

    /**
     * Opens a file in a new window or into the current empty window.
     * Used by command-line arguments and macOS open file handler.
     */
    private static void openFileInWindow(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            // Find an empty, unmodified window to reuse
            for (Main instance : openInstances) {
                if (!instance.dirty && instance.currentFile == null) {
                    instance.loadFileContent(file, content);
                    return;
                }
            }
            // Otherwise open a new window
            Main newWindow = new Main();
            newWindow.loadFileContent(file, content);
        } catch (IOException ex) {
            // Silently fail for open-file events
        }
    }

    /**
     * Returns the Main instance whose frame is currently focused,
     * or the first open instance if none is focused.
     */
    private static Main getActiveInstance() {
        for (Window w : Window.getWindows()) {
            if (w instanceof JFrame && w.isDisplayable()) {
                for (Main instance : openInstances) {
                    if (instance.frame == w) {
                        if (w.isFocused()) return instance;
                    }
                }
            }
        }
        // Fallback: return first open instance
        for (Main instance : openInstances) {
            if (instance.frame.isDisplayable()) return instance;
        }
        return null;
    }

    private FindDialog findDialog;
    private ReplaceDialog replaceDialog;

    /**
     * Shows the About dialog with version information and the application icon.
     */
    private void showAboutDialog() {
        ImageIcon icon = null;
        java.net.URL iconUrl = getClass().getClassLoader().getResource("app_icon_256.png");
        if (iconUrl != null) {
            // Scale to 64x64 for the about box
            ImageIcon fullIcon = new ImageIcon(iconUrl);
            Image scaled = fullIcon.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH);
            icon = new ImageIcon(scaled);
        }
        JOptionPane.showMessageDialog(frame,
                "PurplePlatypus\n"
                        + "Version 1.0\n\n"
                        + "A lightweight desktop Markdown editor\n"
                        + "with live preview.\n\n"
                        + "\u00a9 2026 The Boeing Company",
                "About PurplePlatypus",
                JOptionPane.INFORMATION_MESSAGE,
                icon);
    }

    /**
     * Shows the Preferences dialog. If the user confirms changes, applies the
     * new font settings to the editor and preview panes, saves the preferences
     * to disk, and refreshes the preview.
     */
    private void showPreferencesDialog() {
        PreferencesDialog dialog = new PreferencesDialog(frame, preferences);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            dialog.applyTo(preferences);
            preferences.save();
            applyPreferences();
        }
    }

    /**
     * Applies the current preferences to the editor and preview panes.
     * Updates the editor font and refreshes the preview HTML styling.
     */
    private void applyPreferences() {
        editorPane.setFont(new Font(preferences.getEditorFontFamily(), Font.PLAIN, preferences.getEditorFontSize()));
        updatePreview();
    }

    /**
     * Shows the Find dialog, creating it on first use. If already open,
     * brings it to the front.
     */
    private void showFindDialog() {
        if (findDialog == null) {
            findDialog = new FindDialog(frame, editorPane);
        }
        findDialog.setVisible(true);
        findDialog.toFront();
    }

    /**
     * Shows the Replace dialog, creating it on first use. If already open,
     * brings it to the front.
     */
    private void showReplaceDialog() {
        if (replaceDialog == null) {
            replaceDialog = new ReplaceDialog(frame, editorPane);
        }
        replaceDialog.setVisible(true);
        replaceDialog.toFront();
    }

    /**
     * A custom component that displays line numbers in a gutter alongside a JTextArea.
     * <p>
     * Automatically updates its width as the line count changes and repaints
     * when the document is modified or the font changes.
     */
    private static class LineNumberPanel extends JComponent implements DocumentListener, PropertyChangeListener {
        private final JTextArea textArea;
        private int lastDigits;

        /**
         * Creates a line number panel attached to the given text area.
         * Registers itself as a document listener and font property listener.
         *
         * @param textArea the text area to display line numbers for
         */
        public LineNumberPanel(JTextArea textArea) {
            this.textArea = textArea;
            setFont(textArea.getFont());
            setBackground(new Color(240, 240, 240));
            setForeground(new Color(128, 128, 128));
            textArea.getDocument().addDocumentListener(this);
            textArea.addPropertyChangeListener("font", this);
            updateWidth();
        }

        /**
         * Recalculates the preferred width of the panel based on the number of
         * digits needed to display the highest line number (minimum 3 digits).
         */
        private void updateWidth() {
            int lines = textArea.getLineCount();
            int digits = Math.max(String.valueOf(lines).length(), 3);
            if (digits != lastDigits) {
                lastDigits = digits;
                FontMetrics fm = getFontMetrics(getFont());
                int width = fm.charWidth('0') * digits + 16;
                setPreferredSize(new Dimension(width, 0));
                revalidate();
            }
        }

        /**
         * Paints the line numbers for all visible lines within the current clip bounds.
         * Numbers are right-aligned with anti-aliased text rendering.
         *
         * @param g the graphics context
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setFont(getFont());
            g2.setColor(getForeground());
            FontMetrics fm = g2.getFontMetrics();

            Rectangle clip = g2.getClipBounds();
            Element root = textArea.getDocument().getDefaultRootElement();
            int lineCount = root.getElementCount();

            int startOffset = textArea.viewToModel2D(new Point(0, clip.y));
            int endOffset = textArea.viewToModel2D(new Point(0, clip.y + clip.height));
            int startLine = root.getElementIndex(startOffset);
            int endLine = root.getElementIndex(endOffset);

            for (int line = startLine; line <= endLine && line < lineCount; line++) {
                try {
                    Rectangle2D rect = textArea.modelToView2D(root.getElement(line).getStartOffset());
                    if (rect == null) continue;
                    int y = (int) rect.getY() + fm.getAscent();
                    String lineNum = String.valueOf(line + 1);
                    int x = getWidth() - fm.stringWidth(lineNum) - 8;
                    g2.drawString(lineNum, x, y);
                } catch (Exception ex) {
                    // Skip lines that can't be mapped to a view position
                }
            }
            g2.dispose();
        }

        /** {@inheritDoc} */
        @Override
        public void insertUpdate(DocumentEvent e) {
            updateWidth();
            repaint();
        }

        /** {@inheritDoc} */
        @Override
        public void removeUpdate(DocumentEvent e) {
            updateWidth();
            repaint();
        }

        /** {@inheritDoc} */
        @Override
        public void changedUpdate(DocumentEvent e) {
            repaint();
        }

        /**
         * Responds to font property changes on the text area by updating
         * this panel's font and recalculating its width.
         *
         * @param evt the property change event
         */
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            setFont(textArea.getFont());
            updateWidth();
            repaint();
        }
    }
}
