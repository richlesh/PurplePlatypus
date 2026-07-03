/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a single editor window with a split pane containing
 * an EditorPanel and a PreviewPanel.
 */
public class EditorWindow {

    /** Tracks the number of open windows so the app exits when the last one closes. */
    static final AtomicInteger windowCount = new AtomicInteger(0);

    /** Tracks all open EditorWindow instances. */
    static final List<EditorWindow> openInstances = new ArrayList<>();

    private final JFrame frame;
    private final EditorPanel editorPanel;
    private final PreviewPanel previewPanel;
    private final RSyntaxTextArea editorPane;
    private final UndoManager undoManager = new UndoManager();
    private Preferences preferences;
    private File currentFile;
    private boolean dirty = false;
    private long lastOpenTime = 0;

    private FindDialog findDialog;
    private ReplaceDialog replaceDialog;

    public EditorWindow() {
        preferences = Preferences.load();
        editorPanel = new EditorPanel(preferences);
        previewPanel = new PreviewPanel();
        editorPane = editorPanel.getTextArea();

        frame = new JFrame("PurplePlatypus");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                windowCount.incrementAndGet();
                openInstances.add(EditorWindow.this);
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmClose()) {
                    frame.dispose();
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                openInstances.remove(EditorWindow.this);
                if (windowCount.decrementAndGet() == 0) {
                    System.exit(0);
                } else {
                    for (EditorWindow instance : openInstances) {
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
        if (iconUrl != null) {
            frame.setIconImage(new ImageIcon(iconUrl).getImage());
        }

        buildMenuBar();
        buildLayout();
        wireListeners();

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

    public JFrame getFrame() { return frame; }
    public boolean isDirty() { return dirty; }
    public File getCurrentFile() { return currentFile; }

    private void buildMenuBar() {
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
        closeItem.addActionListener(e -> { if (confirmClose()) frame.dispose(); });

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
        undoItem.addActionListener(e -> { if (undoManager.canUndo()) undoManager.undo(); });

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, shortcutMask));
        redoItem.addActionListener(e -> { if (undoManager.canRedo()) undoManager.redo(); });

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
        markdownMenu.addSeparator();

        JMenuItem orderedListItem = new JMenuItem("Ordered List");
        orderedListItem.addActionListener(e -> convertToList("ordered"));
        JMenuItem unorderedListItem = new JMenuItem("Unordered List");
        unorderedListItem.addActionListener(e -> convertToList("unordered"));
        JMenuItem taskListItem = new JMenuItem("Task List");
        taskListItem.addActionListener(e -> convertToList("task"));

        markdownMenu.add(orderedListItem);
        markdownMenu.add(unorderedListItem);
        markdownMenu.add(taskListItem);
        markdownMenu.addSeparator();

        JMenuItem blockQuoteItem = new JMenuItem("Block Quote");
        blockQuoteItem.addActionListener(e -> prefixLines("> "));

        JMenuItem inlineCodeItem = new JMenuItem("Inline Code");
        inlineCodeItem.addActionListener(e -> wrapSelection("`", "`"));

        JMenuItem blockCodeItem = new JMenuItem("Block Code");
        blockCodeItem.addActionListener(e -> wrapBlock("```\n", "\n```"));

        JMenuItem inlineMathItem = new JMenuItem("Inline Math");
        inlineMathItem.addActionListener(e -> wrapSelection("$", "$"));

        JMenuItem blockMathItem = new JMenuItem("Block Math");
        blockMathItem.addActionListener(e -> wrapBlock("$$\n", "\n$$"));

        markdownMenu.add(blockQuoteItem);
        markdownMenu.add(inlineCodeItem);
        markdownMenu.add(blockCodeItem);
        markdownMenu.add(inlineMathItem);
        markdownMenu.add(blockMathItem);
        menuBar.add(markdownMenu);

        frame.setJMenuBar(menuBar);

        // Enable/disable formatting items based on selection
        editorPane.addCaretListener(e -> {
            boolean hasSelection = e.getDot() != e.getMark();
            boldItem.setEnabled(hasSelection);
            italicItem.setEnabled(hasSelection);
            underlineItem.setEnabled(hasSelection);
            strikethroughItem.setEnabled(hasSelection);
        });
    }

    private void buildLayout() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPanel, previewPanel);
        splitPane.setDividerLocation(600);
        splitPane.setResizeWeight(0.5);
        frame.add(splitPane, BorderLayout.CENTER);
    }

    private void wireListeners() {
        editorPane.getDocument().addUndoableEditListener(undoManager);
        editorPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updatePreview(); markDirty(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updatePreview(); markDirty(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updatePreview(); markDirty(); }
        });
    }

    private void updatePreview() {
        previewPanel.updatePreview(editorPane.getText(), currentFile, preferences);
    }

    // --- File operations ---

    private void newFile() {
        SwingUtilities.invokeLater(EditorWindow::new);
    }

    private void openFile() {
        long now = System.currentTimeMillis();
        if (now - lastOpenTime < 1000) return;
        lastOpenTime = now;

        if (dirty && !confirmClose()) return;

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

    public void loadFileContent(File file, String content) {
        currentFile = file;
        editorPane.setText(content);
        editorPane.setCaretPosition(0);
        undoManager.discardAllEdits();
        dirty = false;
        updateTitle();
    }

    private void saveFile() {
        if (currentFile == null) saveFileAs();
        else writeFile(currentFile);
    }

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

    // --- Dirty tracking ---

    private void markDirty() {
        if (!dirty) { dirty = true; updateTitle(); }
    }

    private void updateTitle() {
        String title = "PurplePlatypus";
        if (currentFile != null) title += " - " + currentFile.getName();
        if (dirty) title += " \u2022 Modified";
        frame.setTitle(title);
    }

    public boolean confirmClose() {
        if (!dirty) return true;
        String filename = currentFile != null ? currentFile.getName() : "Untitled";
        int choice = JOptionPane.showOptionDialog(frame,
                "\"" + filename + "\" has unsaved changes. Do you want to save before closing?",
                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new String[]{"Save", "Don't Save", "Cancel"}, "Save");
        if (choice == 0) { saveFile(); return !dirty; }
        else if (choice == 1) return true;
        else return false;
    }

    static void exitApplication() {
        for (EditorWindow instance : new ArrayList<>(openInstances)) {
            if (!instance.confirmClose()) return;
        }
        System.exit(0);
    }

    // --- Markdown operations ---

    private void wrapSelection(String prefix, String suffix) {
        int start = editorPane.getSelectionStart();
        int end = editorPane.getSelectionEnd();
        if (start == end) return;
        String selected = editorPane.getSelectedText();
        editorPane.replaceSelection(prefix + selected + suffix);
        editorPane.setSelectionStart(start);
        editorPane.setSelectionEnd(start + prefix.length() + selected.length() + suffix.length());
    }

    /**
     * Prefixes each selected line with the given string (e.g., "> " for block quotes).
     */
    private void prefixLines(String prefix) {
        int selStart = editorPane.getSelectionStart();
        int selEnd = editorPane.getSelectionEnd();
        String fullText = editorPane.getText();

        int lineStart = fullText.lastIndexOf('\n', selStart - 1) + 1;
        int lineEnd = fullText.indexOf('\n', selEnd);
        if (lineEnd < 0) lineEnd = fullText.length();

        String selectedBlock = fullText.substring(lineStart, lineEnd);
        String[] lines = selectedBlock.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(prefix).append(line).append("\n");
        }
        String result = sb.toString();
        if (!selectedBlock.endsWith("\n") && result.endsWith("\n")) {
            result = result.substring(0, result.length() - 1);
        }

        editorPane.setSelectionStart(lineStart);
        editorPane.setSelectionEnd(lineEnd);
        editorPane.replaceSelection(result);
    }

    /**
     * Wraps the selected text (or current line) in block delimiters on their own lines.
     * E.g., wrapping with "```\n" and "\n```" for fenced code blocks.
     */
    private void wrapBlock(String prefix, String suffix) {
        int start = editorPane.getSelectionStart();
        int end = editorPane.getSelectionEnd();
        String fullText = editorPane.getText();

        // Expand to full lines
        int lineStart = fullText.lastIndexOf('\n', start - 1) + 1;
        int lineEnd = fullText.indexOf('\n', end);
        if (lineEnd < 0) lineEnd = fullText.length();

        String selected = fullText.substring(lineStart, lineEnd);
        String result = prefix + selected + suffix;

        editorPane.setSelectionStart(lineStart);
        editorPane.setSelectionEnd(lineEnd);
        editorPane.replaceSelection(result);
    }

    private void convertToList(String type) {
        int selStart = editorPane.getSelectionStart();
        int selEnd = editorPane.getSelectionEnd();
        String fullText = editorPane.getText();
        int lineStart = fullText.lastIndexOf('\n', selStart - 1) + 1;
        int lineEnd = fullText.indexOf('\n', selEnd);
        if (lineEnd < 0) lineEnd = fullText.length();

        String selectedBlock = fullText.substring(lineStart, lineEnd);
        String[] lines = selectedBlock.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int counter = 1;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) { sb.append("\n"); continue; }

            String content;
            String indent;
            java.util.regex.Matcher taskMatcher = java.util.regex.Pattern.compile("^(\\s*)[-*+]\\s+\\[[ xX]\\]\\s+(.*)$").matcher(line);
            java.util.regex.Matcher orderedMatcher = java.util.regex.Pattern.compile("^(\\s*)\\d+\\.\\s+(.*)$").matcher(line);
            java.util.regex.Matcher unorderedMatcher = java.util.regex.Pattern.compile("^(\\s*)[-*+]\\s+(.*)$").matcher(line);

            if (taskMatcher.matches()) { indent = taskMatcher.group(1); content = taskMatcher.group(2); }
            else if (orderedMatcher.matches()) { indent = orderedMatcher.group(1); content = orderedMatcher.group(2); }
            else if (unorderedMatcher.matches()) { indent = unorderedMatcher.group(1); content = unorderedMatcher.group(2); }
            else { content = trimmed; indent = " ".repeat(line.length() - line.stripLeading().length()); }

            switch (type) {
                case "ordered" -> sb.append(indent).append(counter++).append(". ").append(content).append("\n");
                case "unordered" -> sb.append(indent).append("- ").append(content).append("\n");
                case "task" -> sb.append(indent).append("- [ ] ").append(content).append("\n");
            }
        }

        String result = sb.toString();
        if (!selectedBlock.endsWith("\n") && result.endsWith("\n"))
            result = result.substring(0, result.length() - 1);
        editorPane.setSelectionStart(lineStart);
        editorPane.setSelectionEnd(lineEnd);
        editorPane.replaceSelection(result);
    }

    private void showLinkDialog() {
        String selectedText = editorPane.getSelectedText();
        int selStart = editorPane.getSelectionStart();
        int selEnd = editorPane.getSelectionEnd();
        String fullText = editorPane.getText();

        String linkText = selectedText != null ? selectedText : "";
        String linkUri = "";
        int replaceStart = selStart, replaceEnd = selEnd;

        int searchFrom = Math.max(0, selStart - 200);
        int searchTo = Math.min(fullText.length(), selEnd + 200);
        String region = fullText.substring(searchFrom, searchTo);

        int idx = 0;
        while (idx < region.length()) {
            int bo = region.indexOf('[', idx);
            if (bo < 0) break;
            int bc = region.indexOf(']', bo + 1);
            if (bc < 0) break;
            if (bc + 1 < region.length() && region.charAt(bc + 1) == '(') {
                int pc = region.indexOf(')', bc + 2);
                if (pc >= 0) {
                    int absStart = searchFrom + bo, absEnd = searchFrom + pc + 1;
                    if (selStart < absEnd && selEnd > absStart) {
                        linkText = region.substring(bo + 1, bc);
                        linkUri = region.substring(bc + 2, pc);
                        replaceStart = absStart; replaceEnd = absEnd;
                        break;
                    }
                }
            }
            idx = bo + 1;
        }

        LinkDialog dialog = new LinkDialog(frame, linkText, linkUri);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            editorPane.setSelectionStart(replaceStart);
            editorPane.setSelectionEnd(replaceEnd);
            editorPane.replaceSelection("[" + dialog.getLinkText() + "](" + dialog.getLinkUri() + ")");
        }
    }

    private void showImageDialog() {
        String selectedText = editorPane.getSelectedText();
        int selStart = editorPane.getSelectionStart();
        int selEnd = editorPane.getSelectionEnd();
        String fullText = editorPane.getText();

        String altText = selectedText != null ? selectedText : "";
        String imgPath = "";
        int replaceStart = selStart, replaceEnd = selEnd;

        int searchFrom = Math.max(0, selStart - 200);
        int searchTo = Math.min(fullText.length(), selEnd + 200);
        String region = fullText.substring(searchFrom, searchTo);

        int idx = 0;
        while (idx < region.length()) {
            int bb = region.indexOf("![", idx);
            if (bb < 0) break;
            int bc = region.indexOf(']', bb + 2);
            if (bc < 0) break;
            if (bc + 1 < region.length() && region.charAt(bc + 1) == '(') {
                int pc = region.indexOf(')', bc + 2);
                if (pc >= 0) {
                    int absStart = searchFrom + bb, absEnd = searchFrom + pc + 1;
                    if (selStart <= absEnd && selEnd >= absStart) {
                        altText = region.substring(bb + 2, bc);
                        imgPath = region.substring(bc + 2, pc);
                        replaceStart = absStart; replaceEnd = absEnd;
                        break;
                    }
                }
            }
            idx = bb + 1;
        }

        ImageDialog dialog = new ImageDialog(frame, altText, imgPath, currentFile);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            editorPane.setSelectionStart(replaceStart);
            editorPane.setSelectionEnd(replaceEnd);
            editorPane.replaceSelection("![" + dialog.getAltText() + "](" + dialog.getImagePath() + ")");
        }
    }

    private void showTableDialog() {
        String selectedText = editorPane.getSelectedText();
        String fullText = editorPane.getText();
        int replaceStart = editorPane.getSelectionStart();
        int replaceEnd = editorPane.getSelectionEnd();
        String tableText = selectedText;

        boolean inTable = false;
        if (selectedText != null && selectedText.contains("|")) {
            inTable = true;
        } else {
            int ls = fullText.lastIndexOf('\n', replaceStart - 1) + 1;
            int le = fullText.indexOf('\n', replaceStart);
            if (le < 0) le = fullText.length();
            if (fullText.substring(ls, le).contains("|")) inTable = true;
        }

        if (inTable) {
            int lineStart = fullText.lastIndexOf('\n', replaceStart - 1) + 1;
            int lineEnd = replaceEnd;
            int eol = fullText.indexOf('\n', lineEnd);
            if (eol >= 0) lineEnd = eol; else lineEnd = fullText.length();

            while (lineStart > 0) {
                int pls = fullText.lastIndexOf('\n', lineStart - 2) + 1;
                if (fullText.substring(pls, lineStart - 1).trim().contains("|")) lineStart = pls;
                else break;
            }
            while (lineEnd < fullText.length()) {
                int nle = fullText.indexOf('\n', lineEnd + 1);
                if (nle < 0) nle = fullText.length();
                if (fullText.substring(lineEnd, nle).trim().contains("|")) lineEnd = nle;
                else break;
            }
            tableText = fullText.substring(lineStart, lineEnd);
            replaceStart = lineStart; replaceEnd = lineEnd;
        }

        TableDialog dialog = new TableDialog(frame, tableText);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            String markdown = dialog.getMarkdownTable();
            if (replaceEnd < fullText.length()) {
                String after = fullText.substring(replaceEnd);
                if (after.startsWith("\n") || after.startsWith("\r\n")) {
                    if (markdown.endsWith("\n")) markdown = markdown.substring(0, markdown.length() - 1);
                }
            }
            editorPane.setSelectionStart(replaceStart);
            editorPane.setSelectionEnd(replaceEnd);
            editorPane.replaceSelection(markdown);
        }
    }

    // --- Dialogs ---

    private void showFindDialog() {
        if (findDialog == null) findDialog = new FindDialog(frame, editorPane);
        findDialog.setVisible(true);
        findDialog.toFront();
    }

    private void showReplaceDialog() {
        if (replaceDialog == null) replaceDialog = new ReplaceDialog(frame, editorPane);
        replaceDialog.setVisible(true);
        replaceDialog.toFront();
    }

    public void showAboutDialog() {
        ImageIcon icon = null;
        java.net.URL iconUrl = getClass().getClassLoader().getResource("app_icon_256.png");
        if (iconUrl != null) {
            ImageIcon fullIcon = new ImageIcon(iconUrl);
            Image scaled = fullIcon.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH);
            icon = new ImageIcon(scaled);
        }
        JOptionPane.showMessageDialog(frame,
                "PurplePlatypus\nVersion 1.0\n\nA lightweight desktop Markdown editor\nwith live preview.\n\n\u00a9 2026 Glowing Cat Software",
                "About PurplePlatypus", JOptionPane.INFORMATION_MESSAGE, icon);
    }

    public void showPreferencesDialog() {
        PreferencesDialog dialog = new PreferencesDialog(frame, preferences);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            dialog.applyTo(preferences);
            preferences.save();
            editorPanel.applyPreferences(preferences);
            updatePreview();
        }
    }

    // --- Static helpers ---

    public static void openFileInWindow(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            for (EditorWindow instance : openInstances) {
                if (!instance.dirty && instance.currentFile == null) {
                    instance.loadFileContent(file, content);
                    return;
                }
            }
            EditorWindow newWindow = new EditorWindow();
            newWindow.loadFileContent(file, content);
        } catch (IOException ex) {
            // Silently fail
        }
    }

    public static EditorWindow getActiveInstance() {
        for (Window w : Window.getWindows()) {
            if (w instanceof JFrame && w.isDisplayable()) {
                for (EditorWindow instance : openInstances) {
                    if (instance.frame == w && w.isFocused()) return instance;
                }
            }
        }
        for (EditorWindow instance : openInstances) {
            if (instance.frame.isDisplayable()) return instance;
        }
        return null;
    }
}
