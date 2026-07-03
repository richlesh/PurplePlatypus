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
    private AIChatPanel aiChatPanel;
    private JSplitPane editorPreviewSplit;
    private JSplitPane mainSplit;
    private JLabel filePathLabel;
    private JToggleButton previewToggle;
    private JToggleButton aiToggle;
    private boolean previewVisible = true;
    private boolean aiVisible = true;
    private int lastPreviewDivider = -1;
    private int lastAiDivider = -1;
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
                    saveWindowState();
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
        frame.setSize(preferences.getWindowWidth(), preferences.getWindowHeight());

        // Application icon
        java.net.URL iconUrl = getClass().getClassLoader().getResource("app_icon_256.png");
        if (iconUrl != null) {
            frame.setIconImage(new ImageIcon(iconUrl).getImage());
        }

        buildMenuBar();
        buildLayout();
        wireListeners();
        restoreWindowState();

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

        // On non-macOS, add a "PurplePlatypus" application menu with About, Preferences, License Key, Quit
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        if (!isMac) {
            JMenu appMenu = new JMenu("PurplePlatypus");
            JMenuItem aboutItem = new JMenuItem("About PurplePlatypus");
            aboutItem.addActionListener(e -> showAboutDialog());
            JMenuItem prefsItem = new JMenuItem("Settings...");
            prefsItem.addActionListener(e -> showPreferencesDialog());
            JMenuItem licenseItem = new JMenuItem("License Key...");
            licenseItem.addActionListener(e -> showLicenseDialog());
            JMenuItem quitItem = new JMenuItem("Quit PurplePlatypus");
            quitItem.addActionListener(e -> exitApplication());
            appMenu.add(aboutItem);
            appMenu.addSeparator();
            appMenu.add(prefsItem);
            appMenu.add(licenseItem);
            appMenu.addSeparator();
            appMenu.add(quitItem);
            menuBar.add(appMenu);
        }

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

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(closeItem);
        fileMenu.addSeparator();
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();

        JMenuItem pageSetupItem = new JMenuItem("Page Setup...");
        pageSetupItem.addActionListener(e -> showPageSetup());

        JMenuItem printItem = new JMenuItem("Print...");
        printItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, shortcutMask));
        printItem.addActionListener(e -> printPreview());

        fileMenu.add(pageSetupItem);
        fileMenu.add(printItem);
        fileMenu.addSeparator();

        JMenu exportMenu = new JMenu("Export");
        JMenuItem exportHtmlItem = new JMenuItem("HTML...");
        exportHtmlItem.addActionListener(e -> exportHtml());
        JMenuItem exportPdfItem = new JMenuItem("PDF...");
        exportPdfItem.addActionListener(e -> exportPdf());
        JMenuItem exportTextBundleItem = new JMenuItem("TextBundle...");
        exportTextBundleItem.addActionListener(e -> exportTextBundle());
        JMenuItem exportRtfItem = new JMenuItem("RTF...");
        exportRtfItem.addActionListener(e -> exportRtf());
        exportMenu.add(exportHtmlItem);
        exportMenu.add(exportPdfItem);
        exportMenu.add(exportTextBundleItem);
        exportMenu.add(exportRtfItem);
        fileMenu.add(exportMenu);
        if (isMac) {
            fileMenu.addSeparator();
            JMenuItem licenseItem = new JMenuItem("License Key...");
            licenseItem.addActionListener(e -> showLicenseDialog());
            fileMenu.add(licenseItem);
        }
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
        replaceItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, shortcutMask));
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

        JMenuItem superscriptItem = new JMenuItem("Superscript");
        superscriptItem.setEnabled(false);
        superscriptItem.addActionListener(e -> wrapSelection("<sup>", "</sup>"));

        JMenuItem subscriptItem = new JMenuItem("Subscript");
        subscriptItem.setEnabled(false);
        subscriptItem.addActionListener(e -> wrapSelection("<sub>", "</sub>"));

        JMenuItem insItem = new JMenuItem("Insert (Underline)");
        insItem.setEnabled(false);
        insItem.addActionListener(e -> wrapSelection("++", "++"));

        markdownMenu.add(boldItem);
        markdownMenu.add(italicItem);
        markdownMenu.add(underlineItem);
        markdownMenu.add(strikethroughItem);
        markdownMenu.add(superscriptItem);
        markdownMenu.add(subscriptItem);
        markdownMenu.add(insItem);
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

        JMenuItem footnoteItem = new JMenuItem("Footnote");
        footnoteItem.addActionListener(e -> insertFootnote());
        markdownMenu.add(footnoteItem);
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
        markdownMenu.addSeparator();

        JMenuItem h1Item = new JMenuItem("Heading 1");
        h1Item.addActionListener(e -> prefixCurrentLine("# "));
        JMenuItem h2Item = new JMenuItem("Heading 2");
        h2Item.addActionListener(e -> prefixCurrentLine("## "));
        JMenuItem h3Item = new JMenuItem("Heading 3");
        h3Item.addActionListener(e -> prefixCurrentLine("### "));
        JMenuItem h4Item = new JMenuItem("Heading 4");
        h4Item.addActionListener(e -> prefixCurrentLine("#### "));
        JMenuItem h5Item = new JMenuItem("Heading 5");
        h5Item.addActionListener(e -> prefixCurrentLine("##### "));
        JMenuItem h6Item = new JMenuItem("Heading 6");
        h6Item.addActionListener(e -> prefixCurrentLine("###### "));
        JMenuItem hrItem = new JMenuItem("Horizontal Rule");
        hrItem.addActionListener(e -> insertHorizontalRule());

        markdownMenu.add(h1Item);
        markdownMenu.add(h2Item);
        markdownMenu.add(h3Item);
        markdownMenu.add(h4Item);
        markdownMenu.add(h5Item);
        markdownMenu.add(h6Item);
        markdownMenu.addSeparator();
        markdownMenu.add(hrItem);

        menuBar.add(markdownMenu);

        frame.setJMenuBar(menuBar);

        // Enable/disable formatting items based on selection
        editorPane.addCaretListener(e -> {
            boolean hasSelection = e.getDot() != e.getMark();
            boldItem.setEnabled(hasSelection);
            italicItem.setEnabled(hasSelection);
            underlineItem.setEnabled(hasSelection);
            strikethroughItem.setEnabled(hasSelection);
            superscriptItem.setEnabled(hasSelection);
            subscriptItem.setEnabled(hasSelection);
            insItem.setEnabled(hasSelection);
        });
    }

    private void buildLayout() {
        // --- Toolbar / status bar at top ---
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        filePathLabel = new JLabel(" ");
        filePathLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolbar.add(filePathLabel, BorderLayout.CENTER);

        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        togglePanel.setOpaque(false);

        // Preview toggle button using eye.png
        ImageIcon eyeIconFull = null;
        var eyeUrl = getClass().getClassLoader().getResource("eye.png");
        if (eyeUrl != null) {
            eyeIconFull = new ImageIcon(new ImageIcon(eyeUrl).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        }
        previewToggle = new JToggleButton(eyeIconFull, true);
        previewToggle.setToolTipText("Show/Hide Preview");
        previewToggle.setFocusPainted(false);
        previewToggle.setBorderPainted(false);
        previewToggle.setContentAreaFilled(false);
        previewToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        previewToggle.addActionListener(e -> togglePreview());
        togglePanel.add(previewToggle);

        // AI toggle button using AI.png
        ImageIcon aiIconFull = null;
        var aiUrl = getClass().getClassLoader().getResource("AI.png");
        if (aiUrl != null) {
            aiIconFull = new ImageIcon(new ImageIcon(aiUrl).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        }
        aiToggle = new JToggleButton(aiIconFull, true);
        aiToggle.setToolTipText("Show/Hide AI Assistant");
        aiToggle.setFocusPainted(false);
        aiToggle.setBorderPainted(false);
        aiToggle.setContentAreaFilled(false);
        aiToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        aiToggle.addActionListener(e -> toggleAI());
        togglePanel.add(aiToggle);

        toolbar.add(togglePanel, BorderLayout.EAST);
        frame.add(toolbar, BorderLayout.NORTH);

        // --- Main content area ---
        editorPreviewSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPanel, previewPanel);
        editorPreviewSplit.setDividerLocation(600);
        editorPreviewSplit.setResizeWeight(0.5);

        aiChatPanel = new AIChatPanel(editorPane, preferences);
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPreviewSplit, aiChatPanel);
        mainSplit.setResizeWeight(1.0);
        mainSplit.setDividerLocation(frame.getWidth() - 400);

        frame.add(mainSplit, BorderLayout.CENTER);
    }

    private void togglePreview() {
        previewVisible = previewToggle.isSelected();
        if (previewVisible) {
            editorPreviewSplit.setRightComponent(previewPanel);
            editorPreviewSplit.setDividerSize(UIManager.getInt("SplitPane.dividerSize"));
            if (lastPreviewDivider > 0) {
                editorPreviewSplit.setDividerLocation(lastPreviewDivider);
            } else {
                editorPreviewSplit.setDividerLocation(editorPreviewSplit.getWidth() / 2);
            }
        } else {
            lastPreviewDivider = editorPreviewSplit.getDividerLocation();
            editorPreviewSplit.setRightComponent(null);
            editorPreviewSplit.setDividerSize(0);
        }
        editorPreviewSplit.revalidate();
        editorPreviewSplit.repaint();
    }

    private void toggleAI() {
        aiVisible = aiToggle.isSelected();
        if (aiVisible) {
            mainSplit.setRightComponent(aiChatPanel);
            mainSplit.setDividerSize(UIManager.getInt("SplitPane.dividerSize"));
            if (lastAiDivider > 0) {
                mainSplit.setDividerLocation(lastAiDivider);
            } else {
                mainSplit.setDividerLocation(mainSplit.getWidth() - 380);
            }
        } else {
            lastAiDivider = mainSplit.getDividerLocation();
            mainSplit.setRightComponent(null);
            mainSplit.setDividerSize(0);
        }
        mainSplit.revalidate();
        mainSplit.repaint();
    }

    private void saveWindowState() {
        preferences.setWindowWidth(frame.getWidth());
        preferences.setWindowHeight(frame.getHeight());
        if (previewVisible) {
            preferences.setEditorPreviewDivider(editorPreviewSplit.getDividerLocation());
        } else if (lastPreviewDivider > 0) {
            preferences.setEditorPreviewDivider(lastPreviewDivider);
        }
        if (aiVisible) {
            preferences.setMainDivider(mainSplit.getDividerLocation());
        } else if (lastAiDivider > 0) {
            preferences.setMainDivider(lastAiDivider);
        }
        preferences.setPreviewVisible(previewVisible);
        preferences.setAiVisible(aiVisible);
        preferences.save();
    }

    private void restoreWindowState() {
        // Restore divider positions after the frame is visible and laid out
        SwingUtilities.invokeLater(() -> {
            editorPreviewSplit.setDividerLocation(preferences.getEditorPreviewDivider());
            mainSplit.setDividerLocation(preferences.getMainDivider());

            // Restore pane visibility
            if (!preferences.isPreviewVisible()) {
                previewToggle.setSelected(false);
                previewVisible = false;
                lastPreviewDivider = preferences.getEditorPreviewDivider();
                editorPreviewSplit.setRightComponent(null);
                editorPreviewSplit.setDividerSize(0);
            }
            if (!preferences.isAiVisible()) {
                aiToggle.setSelected(false);
                aiVisible = false;
                lastAiDivider = preferences.getMainDivider();
                mainSplit.setRightComponent(null);
                mainSplit.setDividerSize(0);
            }
        });
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

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    // Show .textbundle directories as selectable, allow navigating other dirs
                    return true;
                }
                String lower = f.getName().toLowerCase();
                return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt");
            }
            @Override
            public String getDescription() {
                return "Markdown Files (*.md, *.markdown, *.txt, *.textbundle)";
            }
        });

        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            // Handle .textbundle directories
            if (file.isDirectory() && file.getName().toLowerCase().endsWith(".textbundle")) {
                File textMd = new File(file, "text.md");
                if (!textMd.exists()) textMd = new File(file, "text.markdown");
                if (textMd.exists()) {
                    try {
                        String content = new String(Files.readAllBytes(textMd.toPath()), StandardCharsets.UTF_8);
                        loadFileContent(textMd, content);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(frame, "Error reading TextBundle: " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else if (file.isFile()) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    loadFileContent(file, content);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Error reading file: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
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

    // --- Printing ---

    /** Shared PageFormat for page setup persistence within the session. */
    private static java.awt.print.PageFormat pageFormat;

    private void showPageSetup() {
        java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
        if (pageFormat == null) {
            pageFormat = job.defaultPage();
        }
        pageFormat = job.pageDialog(pageFormat);
    }

    private void printPreview() {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.web.WebEngine engine = previewPanel.getWebEngine();
            if (engine == null) return;
            javafx.print.PrinterJob fxJob = javafx.print.PrinterJob.createPrinterJob();
            if (fxJob != null && fxJob.showPrintDialog(null)) {
                engine.print(fxJob);
                fxJob.endJob();
            }
        });
    }

    private void exportHtml() {
        FileDialog dialog = new FileDialog(frame, "Export HTML", FileDialog.SAVE);
        if (currentFile != null) {
            dialog.setDirectory(currentFile.getParent());
            String name = currentFile.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            dialog.setFile(name + ".html");
        } else {
            dialog.setFile("untitled.html");
        }
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File outFile = new File(dialog.getDirectory(), dialog.getFile());
            if (!outFile.getName().contains(".")) {
                outFile = new File(outFile.getAbsolutePath() + ".html");
            }
            String html = previewPanel.getStyledHtml(getRenderedHtml(), null, preferences);
            try {
                Files.writeString(outFile.toPath(), html, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Error exporting HTML: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportPdf() {
        FileDialog dialog = new FileDialog(frame, "Export PDF", FileDialog.SAVE);
        if (currentFile != null) {
            dialog.setDirectory(currentFile.getParent());
            String name = currentFile.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            dialog.setFile(name + ".pdf");
        } else {
            dialog.setFile("untitled.pdf");
        }
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File outFile = new File(dialog.getDirectory(), dialog.getFile());
            if (!outFile.getName().contains(".")) {
                outFile = new File(outFile.getAbsolutePath() + ".pdf");
            }
            final File pdfFile = outFile;
            javafx.application.Platform.runLater(() -> {
                javafx.scene.web.WebEngine engine = previewPanel.getWebEngine();
                if (engine == null) return;
                javafx.print.PrinterJob fxJob = javafx.print.PrinterJob.createPrinterJob();
                if (fxJob != null) {
                    // Configure to print to PDF via the job attributes
                    javafx.print.JobSettings settings = fxJob.getJobSettings();
                    settings.setJobName(pdfFile.getName());
                    // Use a virtual PDF printer if available, otherwise use native print-to-file
                    fxJob.getJobSettings().setOutputFile(pdfFile.getAbsolutePath());
                    engine.print(fxJob);
                    fxJob.endJob();
                    SwingUtilities.invokeLater(() -> {
                        if (pdfFile.exists()) {
                            // Success - no message needed
                        } else {
                            JOptionPane.showMessageDialog(frame,
                                    "PDF export may require a PDF printer to be installed on your system.",
                                    "Export PDF", JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                }
            });
        }
    }

    private void exportTextBundle() {
        FileDialog dialog = new FileDialog(frame, "Export TextBundle", FileDialog.SAVE);
        if (currentFile != null) {
            dialog.setDirectory(currentFile.getParent());
            String name = currentFile.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            dialog.setFile(name + ".textbundle");
        } else {
            dialog.setFile("untitled.textbundle");
        }
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File bundleDir = new File(dialog.getDirectory(), dialog.getFile());
            if (!bundleDir.getName().contains(".")) {
                bundleDir = new File(bundleDir.getAbsolutePath() + ".textbundle");
            }
            try {
                // Create bundle directory
                Files.createDirectories(bundleDir.toPath());

                // Write info.json
                String info = "{\n  \"version\": 2,\n  \"type\": \"net.daringfireball.markdown\",\n"
                        + "  \"transient\": false,\n  \"creatorIdentifier\": \"com.glowingcat.purpleplatypus\"\n}";
                Files.writeString(bundleDir.toPath().resolve("info.json"), info, StandardCharsets.UTF_8);

                // Process markdown: copy images into assets/ and rewrite URLs
                String markdown = editorPane.getText();
                java.util.regex.Pattern imgPattern = java.util.regex.Pattern.compile(
                        "(!\\[[^\\]]*\\]\\()([^)]+)(\\))");
                java.util.regex.Matcher matcher = imgPattern.matcher(markdown);
                StringBuilder mdSb = new StringBuilder();
                java.nio.file.Path assetsDir = bundleDir.toPath().resolve("assets");
                boolean assetsCreated = false;

                while (matcher.find()) {
                    String imgPath = matcher.group(2).replace("%20", " ");
                    if (imgPath.startsWith("http://") || imgPath.startsWith("https://")) {
                        matcher.appendReplacement(mdSb, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
                        continue;
                    }
                    // Resolve source file
                    File srcFile = null;
                    if (currentFile != null && currentFile.getParentFile() != null) {
                        srcFile = new File(currentFile.getParentFile(), imgPath);
                        // Also check assets/ subfolder if we're in a TextBundle
                        if (!srcFile.exists() && currentFile.getParentFile().getName().toLowerCase().endsWith(".textbundle")) {
                            File inAssets = new File(currentFile.getParentFile(), "assets/" + imgPath);
                            if (inAssets.exists()) srcFile = inAssets;
                        }
                    }
                    if (srcFile != null && srcFile.exists()) {
                        if (!assetsCreated) {
                            Files.createDirectories(assetsDir);
                            assetsCreated = true;
                        }
                        // Strip leading "assets/" if present to avoid assets/assets/ nesting
                        String relPath = imgPath;
                        if (relPath.startsWith("assets/")) {
                            relPath = relPath.substring("assets/".length());
                        }
                        java.nio.file.Path relativePath = java.nio.file.Path.of(relPath);
                        java.nio.file.Path destPath = assetsDir.resolve(relativePath);
                        Files.createDirectories(destPath.getParent());
                        Files.copy(srcFile.toPath(), destPath,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        // Rewrite URL to assets/relative/path
                        String newUrl = "assets/" + relativePath.toString().replace(" ", "%20");
                        matcher.appendReplacement(mdSb,
                                java.util.regex.Matcher.quoteReplacement(matcher.group(1) + newUrl + matcher.group(3)));
                    } else {
                        matcher.appendReplacement(mdSb, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
                    }
                }
                matcher.appendTail(mdSb);

                // Write text.md with updated image URLs
                Files.writeString(bundleDir.toPath().resolve("text.md"), mdSb.toString(), StandardCharsets.UTF_8);

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Error exporting TextBundle: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportRtf() {
        FileDialog dialog = new FileDialog(frame, "Export RTF", FileDialog.SAVE);
        if (currentFile != null) {
            dialog.setDirectory(currentFile.getParent());
            String name = currentFile.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            dialog.setFile(name + ".rtf");
        } else {
            dialog.setFile("untitled.rtf");
        }
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File outFile = new File(dialog.getDirectory(), dialog.getFile());
            if (!outFile.getName().contains(".")) {
                outFile = new File(outFile.getAbsolutePath() + ".rtf");
            }
            try {
                String rtf = markdownToRtf(editorPane.getText());
                Files.writeString(outFile.toPath(), rtf, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Error exporting RTF: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Converts markdown text to RTF format with basic formatting support.
     */
    private String markdownToRtf(String markdown) {
        StringBuilder rtf = new StringBuilder();
        rtf.append("{\\rtf1\\ansi\\deff0\n");
        rtf.append("{\\fonttbl{\\f0\\fswiss Arial;}{\\f1\\fmodern Courier New;}}\n");
        rtf.append("{\\colortbl;\\red0\\green0\\blue0;\\red100\\green100\\blue100;}\n");
        rtf.append("\\f0\\fs28\n");

        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;

        for (String line : lines) {
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                rtf.append("{\\f1\\fs22 ").append(escapeRtf(line)).append("}\\par\n");
                continue;
            }
            if (line.startsWith("# ")) {
                rtf.append("{\\b\\fs48 ").append(escapeRtf(line.substring(2))).append("}\\par\\par\n");
            } else if (line.startsWith("## ")) {
                rtf.append("{\\b\\fs40 ").append(escapeRtf(line.substring(3))).append("}\\par\\par\n");
            } else if (line.startsWith("### ")) {
                rtf.append("{\\b\\fs32 ").append(escapeRtf(line.substring(4))).append("}\\par\\par\n");
            } else if (line.startsWith("#### ")) {
                rtf.append("{\\b\\fs28 ").append(escapeRtf(line.substring(5))).append("}\\par\\par\n");
            } else if (line.startsWith("##### ")) {
                rtf.append("{\\b\\fs24 ").append(escapeRtf(line.substring(6))).append("}\\par\\par\n");
            } else if (line.startsWith("###### ")) {
                rtf.append("{\\b\\fs22 ").append(escapeRtf(line.substring(7))).append("}\\par\\par\n");
            } else if (line.startsWith("---") && line.trim().matches("-{3,}")) {
                rtf.append("\\pard\\brdrb\\brdrs\\brdrw10\\brsp20\\par\\pard\n");
            } else if (line.startsWith("> ")) {
                rtf.append("{\\li720\\cf2 ").append(formatInlineRtf(line.substring(2))).append("}\\par\n");
            } else if (line.matches("^\\s*[-*+]\\s+.*")) {
                String content = line.replaceFirst("^\\s*[-*+]\\s+", "");
                rtf.append("{\\li360\\fi-360\\bullet\\tab ").append(formatInlineRtf(content)).append("}\\par\n");
            } else if (line.matches("^\\s*\\d+\\.\\s+.*")) {
                String content = line.replaceFirst("^\\s*\\d+\\.\\s+", "");
                String num = line.trim().substring(0, line.trim().indexOf('.'));
                rtf.append("{\\li360\\fi-360 ").append(num).append(".\\tab ").append(formatInlineRtf(content)).append("}\\par\n");
            } else if (line.trim().isEmpty()) {
                rtf.append("\\par\n");
            } else {
                rtf.append(formatInlineRtf(line)).append("\\par\n");
            }
        }
        rtf.append("}");
        return rtf.toString();
    }

    private String formatInlineRtf(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            // Bold: **...**
            if (i + 1 < text.length() && text.charAt(i) == '*' && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end > i) {
                    sb.append("{\\b ").append(escapeRtf(text.substring(i + 2, end))).append("}");
                    i = end + 2;
                    continue;
                }
            }
            // Italic: *...*
            if (text.charAt(i) == '*') {
                int end = text.indexOf('*', i + 1);
                if (end > i && !(i + 1 < text.length() && text.charAt(i + 1) == '*')) {
                    sb.append("{\\i ").append(escapeRtf(text.substring(i + 1, end))).append("}");
                    i = end + 1;
                    continue;
                }
            }
            // Inline code: `...`
            if (text.charAt(i) == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i) {
                    sb.append("{\\f1 ").append(escapeRtf(text.substring(i + 1, end))).append("}");
                    i = end + 1;
                    continue;
                }
            }
            // Strikethrough: ~~...~~
            if (i + 1 < text.length() && text.charAt(i) == '~' && text.charAt(i + 1) == '~') {
                int end = text.indexOf("~~", i + 2);
                if (end > i) {
                    sb.append("{\\strike ").append(escapeRtf(text.substring(i + 2, end))).append("}");
                    i = end + 2;
                    continue;
                }
            }
            // Link: [text](url) - just output the text
            if (text.charAt(i) == '[') {
                int bc = text.indexOf(']', i + 1);
                if (bc > i && bc + 1 < text.length() && text.charAt(bc + 1) == '(') {
                    int pc = text.indexOf(')', bc + 2);
                    if (pc > bc) {
                        sb.append("{\\ul ").append(escapeRtf(text.substring(i + 1, bc))).append("}");
                        i = pc + 1;
                        continue;
                    }
                }
            }
            sb.append(escapeRtf(String.valueOf(text.charAt(i))));
            i++;
        }
        return sb.toString();
    }

    private static String escapeRtf(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '{') sb.append("\\{");
            else if (c == '}') sb.append("\\}");
            else if (c > 127) sb.append("\\u").append((int) c).append("?");
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Renders the current editor content to HTML body (without styling wrapper).
     * Pre-processes markdown to encode spaces in URLs, then resolves relative
     * image paths to absolute file paths for export.
     */
    private String getRenderedHtml() {
        String markdown = editorPane.getText();

        // Encode spaces in image/link URLs (same as PreviewPanel)
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

        org.commonmark.Extension tablesExt = org.commonmark.ext.gfm.tables.TablesExtension.create();
        org.commonmark.Extension strikethroughExt = org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create();
        org.commonmark.Extension taskListExt = org.commonmark.ext.task.list.items.TaskListItemsExtension.create();
        org.commonmark.Extension autolinkExt = org.commonmark.ext.autolink.AutolinkExtension.create();
        org.commonmark.Extension footnotesExt = org.commonmark.ext.footnotes.FootnotesExtension.create();
        org.commonmark.Extension headingAnchorExt = org.commonmark.ext.heading.anchor.HeadingAnchorExtension.create();
        org.commonmark.Extension imageAttrExt = org.commonmark.ext.image.attributes.ImageAttributesExtension.create();
        org.commonmark.Extension insExt = org.commonmark.ext.ins.InsExtension.create();
        org.commonmark.Extension yamlExt = org.commonmark.ext.front.matter.YamlFrontMatterExtension.create();
        java.util.List<org.commonmark.Extension> extensions = java.util.Arrays.asList(
                tablesExt, strikethroughExt, taskListExt, autolinkExt, footnotesExt,
                headingAnchorExt, imageAttrExt, insExt, yamlExt);
        org.commonmark.parser.Parser parser = org.commonmark.parser.Parser.builder().extensions(extensions).build();
        org.commonmark.renderer.html.HtmlRenderer renderer = org.commonmark.renderer.html.HtmlRenderer.builder().extensions(extensions).build();
        org.commonmark.node.Node document = parser.parse(markdown);
        String html = renderer.render(document);

        // Resolve relative image paths - keep them relative since the exported
        // HTML will be placed alongside the markdown file. Just decode %20 back
        // to spaces for readable paths, but leave them relative.
        // No resolution needed - paths are already correct relative to the file.

        return html;
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
        // Update toolbar file path
        if (filePathLabel != null) {
            if (currentFile != null) {
                filePathLabel.setText(currentFile.getAbsolutePath());
            } else {
                filePathLabel.setText("Untitled");
            }
        }
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
     * Replaces the current line's content with a heading prefix followed by the line text
     * (stripping any existing heading prefix first).
     */
    private void prefixCurrentLine(String prefix) {
        String fullText = editorPane.getText();
        int caret = editorPane.getCaretPosition();
        int lineStart = fullText.lastIndexOf('\n', caret - 1) + 1;
        int lineEnd = fullText.indexOf('\n', caret);
        if (lineEnd < 0) lineEnd = fullText.length();

        String line = fullText.substring(lineStart, lineEnd);
        // Strip existing heading prefix
        String stripped = line.replaceFirst("^#{1,6}\\s*", "");
        String result = prefix + stripped;

        editorPane.setSelectionStart(lineStart);
        editorPane.setSelectionEnd(lineEnd);
        editorPane.replaceSelection(result);
        editorPane.setCaretPosition(lineStart + result.length());
    }

    /**
     * Inserts a horizontal rule (---) on its own line below the current line.
     */
    private void insertHorizontalRule() {
        String fullText = editorPane.getText();
        int caret = editorPane.getCaretPosition();
        int lineEnd = fullText.indexOf('\n', caret);
        if (lineEnd < 0) lineEnd = fullText.length();

        editorPane.setCaretPosition(lineEnd);
        editorPane.replaceSelection("\n\n---\n");
    }

    /**
     * Inserts a footnote reference at the caret and appends the footnote definition
     * at the end of the document.
     */
    private void insertFootnote() {
        String fullText = editorPane.getText();
        // Find next available footnote number
        int num = 1;
        while (fullText.contains("[^" + num + "]")) num++;

        String ref = "[^" + num + "]";
        int caret = editorPane.getCaretPosition();
        editorPane.insert(ref, caret);

        // Append definition at end of document
        String def = "\n\n" + ref + ": ";
        editorPane.append(def);
        editorPane.setCaretPosition(editorPane.getText().length());
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
        AboutDialog.show(frame, preferences);
    }

    public void showPreferencesDialog() {
        PreferencesDialog dialog = new PreferencesDialog(frame, preferences);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            dialog.applyTo(preferences);
            preferences.save();
            editorPanel.applyPreferences(preferences);
            if (aiChatPanel != null) aiChatPanel.updateFont();
            updatePreview();
        }
    }

    public void showLicenseDialog() {
        LicenseDialog.show(frame, preferences);
    }

    // --- Static helpers ---

    public static void openFileInWindow(File file) {
        try {
            File actualFile = file;
            // If it's a .textbundle directory, open text.md inside it
            if (file.isDirectory() && file.getName().toLowerCase().endsWith(".textbundle")) {
                actualFile = new File(file, "text.md");
                if (!actualFile.exists()) {
                    // Try text.markdown as fallback
                    actualFile = new File(file, "text.markdown");
                }
                if (!actualFile.exists()) return;
            }
            String content = new String(Files.readAllBytes(actualFile.toPath()), StandardCharsets.UTF_8);
            final File fileToOpen = actualFile;
            for (EditorWindow instance : openInstances) {
                if (!instance.dirty && instance.currentFile == null) {
                    instance.loadFileContent(fileToOpen, content);
                    return;
                }
            }
            EditorWindow newWindow = new EditorWindow();
            newWindow.loadFileContent(fileToOpen, content);
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
