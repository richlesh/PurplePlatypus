/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import com.glowingcat.aichat.AIChatPanel;
import com.glowingcat.aichat.AIChatPreferences;
import com.glowingcat.aichat.AIChatPreferencesDialog;
import com.glowingcat.aichat.LLMClientFactory;
import com.glowingcat.aichat.DocumentEditor;
import com.glowingcat.spellcheck.SpellCheckController;
import com.glowingcat.spellcheck.LanguageDownloader;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private AIChatPreferences aiPreferences;
    private JSplitPane editorPreviewSplit;
    private JSplitPane mainSplit;
    private JLabel filePathLabel;
    private JToggleButton previewToggle;
    private JToggleButton aiToggle;
    private JToggleButton syncScrollToggle;
    private JToggleButton hiddenCharsToggle;
    private JToggleButton wordWrapToggle;
    private JToggleButton darkModeToggle;
    private JToggleButton spellCheckToggle;
    private JPanel toolbar;
    private SpellCheckController spellCheckController;
    private boolean previewVisible = true;
    private boolean aiVisible = true;
    private boolean syncScrollEnabled = false;
    private boolean syncScrolling = false;
    private boolean hiddenCharsVisible = false;
    private int lastPreviewDivider = -1;
    private int lastAiDivider = -1;
    private Preferences preferences;
    private File currentFile;
    private boolean dirty = false;
    private boolean windowsLineEndings = false;
    private boolean textPackSource = false;
    private long lastModifiedOnDisk = 0;
    private long lastOpenTime = 0;

    // Debounce timer for preview updates (avoids re-rendering on every keystroke for large files)
    private javax.swing.Timer previewDebounceTimer;
    private static final int PREVIEW_DEBOUNCE_MS = 500;
    // Threshold above which syntax highlighting is disabled (bytes)
    private static final long LARGE_FILE_THRESHOLD = 1_000_000; // 1 MB

    private FindDialog findDialog;
    private ReplaceDialog replaceDialog;
    private JMenu recentsMenu;
    private JMenuItem convertLineEndingsItem;
    private JMenuItem saveItem;
    private JMenuItem saveAsItem;
    private JLabel statsLabel;

    /** Shared preferences instance across all windows. */
    private static Preferences sharedPreferences;

    public EditorWindow() {
        if (sharedPreferences == null) {
            sharedPreferences = Preferences.load();
        }
        preferences = sharedPreferences;
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
            public void windowActivated(WindowEvent e) {
                checkFileChangedOnDisk();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmClose()) {
                    saveWindowState();
                    if (spellCheckController != null) {
                        spellCheckController.dispose();
                    }
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

        // Apply editor/preview/AI dark theme if saved in preferences
        if (preferences.isDarkMode()) {
            applyTheme(Theme.DARK);
        }

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
            JMenu appMenu = new JMenu(Messages.get("app.name"));
            JMenuItem aboutItem = new JMenuItem(Messages.get("menu.app.about"));
            aboutItem.addActionListener(e -> showAboutDialog());
            JMenuItem prefsItem = new JMenuItem(Messages.get("menu.app.settings"));
            prefsItem.addActionListener(e -> showPreferencesDialog());
            JMenuItem aiSettingsItem = new JMenuItem(Messages.get("menu.app.aiSettings"));
            aiSettingsItem.addActionListener(e -> showAiSettingsDialog());
            JMenuItem licenseItem = new JMenuItem(Messages.get("menu.file.licenseKey"));
            licenseItem.addActionListener(e -> showLicenseDialog());
            JMenuItem quitItem = new JMenuItem(Messages.get("menu.app.quit"));
            quitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
            quitItem.addActionListener(e -> exitApplication());
            appMenu.add(aboutItem);
            appMenu.addSeparator();
            appMenu.add(prefsItem);
            appMenu.add(aiSettingsItem);
            appMenu.add(licenseItem);
            appMenu.addSeparator();
            appMenu.add(quitItem);
            menuBar.add(appMenu);
        }

        // File menu
        JMenu fileMenu = new JMenu(Messages.get("menu.file"));
        JMenuItem newItem = new JMenuItem(Messages.get("menu.file.new"));
        newItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, shortcutMask));
        newItem.addActionListener(e -> newFile());

        JMenuItem openItem = new JMenuItem(Messages.get("menu.file.open"));
        openItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, shortcutMask));
        openItem.addActionListener(e -> openFile());

        JMenuItem closeItem = new JMenuItem(Messages.get("menu.file.close"));
        closeItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, shortcutMask));
        closeItem.addActionListener(e -> { if (confirmClose()) frame.dispose(); });

        JMenuItem saveItem = new JMenuItem(Messages.get("menu.file.save"));
        saveItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, shortcutMask));
        saveItem.addActionListener(e -> saveFile());
        this.saveItem = saveItem;

        saveAsItem = new JMenuItem(Messages.get("menu.file.saveAs"));
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        saveAsItem.addActionListener(e -> saveFileAs());

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        recentsMenu = new JMenu(Messages.get("menu.file.recents"));
        rebuildRecentsMenu();
        fileMenu.add(recentsMenu);
        fileMenu.addSeparator();
        fileMenu.add(closeItem);
        fileMenu.addSeparator();
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();

        JMenuItem pageSetupItem = new JMenuItem(Messages.get("menu.file.pageSetup"));
        pageSetupItem.addActionListener(e -> showPageSetup());

        JMenuItem printItem = new JMenuItem(Messages.get("menu.file.print"));
        printItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, shortcutMask));
        printItem.addActionListener(e -> printPreview());

        fileMenu.add(pageSetupItem);
        fileMenu.add(printItem);
        fileMenu.addSeparator();

        JMenu importMenu = new JMenu(Messages.get("menu.file.import"));
        JMenuItem importHtmlItem = new JMenuItem(Messages.get("menu.file.import.html"));
        importHtmlItem.addActionListener(e -> importHtml());
        JMenuItem importPlainTextItem = new JMenuItem(Messages.get("menu.file.import.plainText"));
        importPlainTextItem.addActionListener(e -> importPlainText());
        JMenuItem importRtfItem = new JMenuItem(Messages.get("menu.file.import.rtf"));
        importRtfItem.addActionListener(e -> importRtf());
        JMenuItem importDocxItem = new JMenuItem(Messages.get("menu.file.import.word"));
        importDocxItem.addActionListener(e -> importDocx());
        importMenu.add(importHtmlItem);
        importMenu.add(importPlainTextItem);
        importMenu.add(importRtfItem);
        importMenu.add(importDocxItem);
        fileMenu.add(importMenu);

        JMenu exportMenu = new JMenu(Messages.get("menu.file.export"));
        JMenuItem exportHtmlItem = new JMenuItem(Messages.get("menu.file.export.html"));
        exportHtmlItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, shortcutMask | java.awt.event.InputEvent.ALT_DOWN_MASK));
        exportHtmlItem.addActionListener(e -> exportHtml());
        JMenuItem exportPdfItem = new JMenuItem(Messages.get("menu.file.export.pdf"));
        exportPdfItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, shortcutMask | java.awt.event.InputEvent.ALT_DOWN_MASK));
        exportPdfItem.addActionListener(e -> exportPdf());
        JMenuItem exportTextItem = new JMenuItem(Messages.get("menu.file.export.plainText"));
        exportTextItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, shortcutMask | java.awt.event.InputEvent.ALT_DOWN_MASK));
        exportTextItem.addActionListener(e -> exportPlainText());
        JMenuItem exportRtfItem = new JMenuItem(Messages.get("menu.file.export.rtf"));
        exportRtfItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, shortcutMask | java.awt.event.InputEvent.ALT_DOWN_MASK));
        exportRtfItem.addActionListener(e -> exportRtf());
        JMenuItem exportTextBundleItem = new JMenuItem(Messages.get("menu.file.export.textBundle"));
        exportTextBundleItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, shortcutMask | java.awt.event.InputEvent.ALT_DOWN_MASK));
        exportTextBundleItem.addActionListener(e -> exportTextBundle());
        JMenuItem exportTextPackItem = new JMenuItem(Messages.get("menu.file.export.textPack"));
        exportTextPackItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, shortcutMask | java.awt.event.InputEvent.ALT_DOWN_MASK));
        exportTextPackItem.addActionListener(e -> exportTextPack());
        JMenuItem exportDocxItem = new JMenuItem(Messages.get("menu.file.export.word"));
        exportDocxItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, shortcutMask | java.awt.event.InputEvent.ALT_DOWN_MASK));
        exportDocxItem.addActionListener(e -> exportDocx());
        exportMenu.add(exportHtmlItem);
        exportMenu.add(exportPdfItem);
        exportMenu.add(exportTextBundleItem);
        exportMenu.add(exportTextPackItem);
        exportMenu.add(exportRtfItem);
        exportMenu.add(exportDocxItem);
        exportMenu.add(exportTextItem);
        fileMenu.add(exportMenu);
        if (isMac) {
            fileMenu.addSeparator();
            JMenuItem licenseItem = new JMenuItem(Messages.get("menu.file.licenseKey"));
            licenseItem.addActionListener(e -> showLicenseDialog());
            fileMenu.add(licenseItem);
        }
        menuBar.add(fileMenu);

        // Edit menu
        JMenu editMenu = new JMenu(Messages.get("menu.edit"));
        JMenuItem undoItem = new JMenuItem(Messages.get("menu.edit.undo"));
        undoItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, shortcutMask));
        undoItem.addActionListener(e -> { if (undoManager.canUndo()) undoManager.undo(); });

        JMenuItem redoItem = new JMenuItem(Messages.get("menu.edit.redo"));
        redoItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, shortcutMask));
        redoItem.addActionListener(e -> { if (undoManager.canRedo()) undoManager.redo(); });

        JMenuItem cutItem = new JMenuItem(Messages.get("menu.edit.cut"));
        cutItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, shortcutMask));
        cutItem.addActionListener(e -> editorPane.cut());

        JMenuItem copyItem = new JMenuItem(Messages.get("menu.edit.copy"));
        copyItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, shortcutMask));
        copyItem.addActionListener(e -> editorPane.copy());

        JMenuItem pasteItem = new JMenuItem(Messages.get("menu.edit.paste"));
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, shortcutMask));
        pasteItem.addActionListener(e -> editorPane.paste());

        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.addSeparator();
        convertLineEndingsItem = new JMenuItem(Messages.get("menu.edit.convertLineEndings"));
        convertLineEndingsItem.addActionListener(e -> convertLineEndings());
        editMenu.add(convertLineEndingsItem);

        JMenuItem cleanupTablesItem = new JMenuItem(Messages.get("menu.edit.convertPandocTable"));
        cleanupTablesItem.addActionListener(e -> cleanupPandocTables());
        editMenu.add(cleanupTablesItem);

        JMenuItem formatTableItem = new JMenuItem(Messages.get("menu.edit.formatTable"));
        formatTableItem.addActionListener(e -> formatTable());
        editMenu.add(formatTableItem);

        JMenuItem htmlEncodeItem = new JMenuItem(Messages.get("menu.edit.htmlEncode"));
        htmlEncodeItem.addActionListener(e -> htmlEncodeNonAscii());
        editMenu.add(htmlEncodeItem);

        JMenuItem zapGremlinsItem = new JMenuItem(Messages.get("menu.edit.zapGremlins"));
        zapGremlinsItem.addActionListener(e -> zapGremlins());
        editMenu.add(zapGremlinsItem);

        // Update menu item text based on selection when Edit menu opens
        editMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                boolean hasSel = editorPane.getSelectionStart() != editorPane.getSelectionEnd();
                cleanupTablesItem.setText(hasSel ? Messages.get("menu.edit.convertPandocTable.selection") : Messages.get("menu.edit.convertPandocTable"));
                zapGremlinsItem.setText(hasSel ? Messages.get("menu.edit.zapGremlins.selection") : Messages.get("menu.edit.zapGremlins"));
                htmlEncodeItem.setText(hasSel ? Messages.get("menu.edit.htmlEncode.selection") : Messages.get("menu.edit.htmlEncode"));
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });

        if (isMac) {
            editMenu.addSeparator();
            JMenuItem aiSettingsMenuItem = new JMenuItem(Messages.get("menu.app.aiSettings"));
            aiSettingsMenuItem.addActionListener(e -> showAiSettingsDialog());
            editMenu.add(aiSettingsMenuItem);
        }
        menuBar.add(editMenu);

        // Search menu
        JMenu searchMenu = new JMenu(Messages.get("menu.search"));
        JMenuItem findItem = new JMenuItem(Messages.get("menu.search.find"));
        findItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, shortcutMask));
        findItem.addActionListener(e -> showFindDialog());

        JMenuItem replaceItem = new JMenuItem(Messages.get("menu.search.replace"));
        replaceItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, shortcutMask));
        replaceItem.addActionListener(e -> showReplaceDialog());

        searchMenu.add(findItem);
        searchMenu.add(replaceItem);
        searchMenu.addSeparator();
        JMenuItem findInPreviewItem = new JMenuItem(Messages.get("menu.search.findInPreview"));
        findInPreviewItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        findInPreviewItem.addActionListener(e -> findInPreview());
        searchMenu.add(findInPreviewItem);
        searchMenu.addSeparator();
        JMenuItem gotoLineItem = new JMenuItem(Messages.get("menu.search.goToLine"));
        gotoLineItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_J, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        gotoLineItem.addActionListener(e -> gotoLine());
        searchMenu.add(gotoLineItem);
        menuBar.add(searchMenu);

        // Markdown menu
        JMenu markdownMenu = new JMenu(Messages.get("menu.markdown"));

        JMenuItem boldItem = new JMenuItem(Messages.get("menu.markdown.bold"));
        boldItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, shortcutMask));
        boldItem.setEnabled(false);
        boldItem.addActionListener(e -> wrapSelection("**", "**"));

        JMenuItem italicItem = new JMenuItem(Messages.get("menu.markdown.italic"));
        italicItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I, shortcutMask));
        italicItem.setEnabled(false);
        italicItem.addActionListener(e -> wrapSelection("*", "*"));

        JMenuItem strikethroughItem = new JMenuItem(Messages.get("menu.markdown.strikethrough"));
        strikethroughItem.setEnabled(false);
        strikethroughItem.addActionListener(e -> wrapSelection("~~", "~~"));

        JMenuItem superscriptItem = new JMenuItem(Messages.get("menu.markdown.superscript"));
        superscriptItem.setEnabled(false);
        superscriptItem.addActionListener(e -> wrapSelection("<sup>", "</sup>"));

        JMenuItem subscriptItem = new JMenuItem(Messages.get("menu.markdown.subscript"));
        subscriptItem.setEnabled(false);
        subscriptItem.addActionListener(e -> wrapSelection("<sub>", "</sub>"));

        JMenuItem insItem = new JMenuItem(Messages.get("menu.markdown.underline"));
        insItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_U, shortcutMask));
        insItem.setEnabled(false);
        insItem.addActionListener(e -> wrapSelection("++", "++"));

        markdownMenu.add(boldItem);

        JMenuItem centerItem = new JMenuItem(Messages.get("menu.markdown.center"));
        centerItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        centerItem.addActionListener(e -> wrapBlock("<div style=\"text-align: center;\">\n\n", "\n\n</div>"));
        markdownMenu.add(centerItem);

        markdownMenu.add(italicItem);
        markdownMenu.add(strikethroughItem);
        markdownMenu.add(subscriptItem);
        markdownMenu.add(superscriptItem);
        markdownMenu.add(insItem);
        markdownMenu.addSeparator();

        JMenuItem linkItem = new JMenuItem(Messages.get("menu.markdown.link"));
        linkItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, shortcutMask));
        linkItem.addActionListener(e -> showLinkDialog());

        JMenuItem imageItem = new JMenuItem(Messages.get("menu.markdown.image"));
        imageItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, shortcutMask));
        imageItem.addActionListener(e -> showImageDialog());

        JMenuItem tableItem = new JMenuItem(Messages.get("menu.markdown.table"));
        tableItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, shortcutMask));
        tableItem.addActionListener(e -> showTableDialog());

        markdownMenu.add(linkItem);
        markdownMenu.add(imageItem);
        markdownMenu.add(tableItem);

        JMenuItem footnoteItem = new JMenuItem(Messages.get("menu.markdown.footnote"));
        footnoteItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        footnoteItem.addActionListener(e -> insertFootnote());
        markdownMenu.add(footnoteItem);
        markdownMenu.addSeparator();

        JMenuItem orderedListItem = new JMenuItem(Messages.get("menu.markdown.orderedList"));
        orderedListItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        orderedListItem.addActionListener(e -> convertToList("ordered"));
        JMenuItem unorderedListItem = new JMenuItem(Messages.get("menu.markdown.unorderedList"));
        unorderedListItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_U, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        unorderedListItem.addActionListener(e -> convertToList("unordered"));
        JMenuItem taskListItem = new JMenuItem(Messages.get("menu.markdown.taskList"));
        taskListItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        taskListItem.addActionListener(e -> convertToList("task"));

        markdownMenu.add(orderedListItem);
        markdownMenu.add(unorderedListItem);
        markdownMenu.add(taskListItem);
        markdownMenu.addSeparator();

        JMenuItem blockQuoteItem = new JMenuItem(Messages.get("menu.markdown.blockQuote"));
        blockQuoteItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        blockQuoteItem.addActionListener(e -> prefixLines("> "));

        JMenuItem inlineCodeItem = new JMenuItem(Messages.get("menu.markdown.inlineCode"));
        inlineCodeItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_QUOTE, shortcutMask));
        inlineCodeItem.addActionListener(e -> wrapSelection("`", "`"));

        JMenuItem blockCodeItem = new JMenuItem(Messages.get("menu.markdown.blockCode"));
        blockCodeItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_QUOTE, shortcutMask | java.awt.event.InputEvent.ALT_DOWN_MASK));
        blockCodeItem.addActionListener(e -> wrapBlock("```\n", "\n```"));

        JMenuItem inlineMathItem = new JMenuItem(Messages.get("menu.markdown.inlineMath"));
        inlineMathItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        inlineMathItem.addActionListener(e -> wrapSelection("$", "$"));

        JMenuItem blockMathItem = new JMenuItem(Messages.get("menu.markdown.blockMath"));
        blockMathItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, shortcutMask | java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        blockMathItem.addActionListener(e -> wrapBlock("$$\n", "\n$$"));

        markdownMenu.add(blockQuoteItem);
        markdownMenu.add(inlineCodeItem);
        markdownMenu.add(blockCodeItem);
        markdownMenu.add(inlineMathItem);
        markdownMenu.add(blockMathItem);

        JMenuItem mermaidItem = new JMenuItem(Messages.get("menu.markdown.mermaid"));
        mermaidItem.addActionListener(e -> wrapBlock("```mermaid\n", "\n```"));
        markdownMenu.add(mermaidItem);
        markdownMenu.addSeparator();

        JMenuItem h1Item = new JMenuItem(Messages.get("menu.markdown.heading1"));
        h1Item.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, shortcutMask));
        h1Item.addActionListener(e -> prefixCurrentLine("# "));
        JMenuItem h2Item = new JMenuItem(Messages.get("menu.markdown.heading2"));
        h2Item.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, shortcutMask));
        h2Item.addActionListener(e -> prefixCurrentLine("## "));
        JMenuItem h3Item = new JMenuItem(Messages.get("menu.markdown.heading3"));
        h3Item.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, shortcutMask));
        h3Item.addActionListener(e -> prefixCurrentLine("### "));
        JMenuItem h4Item = new JMenuItem(Messages.get("menu.markdown.heading4"));
        h4Item.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_4, shortcutMask));
        h4Item.addActionListener(e -> prefixCurrentLine("#### "));
        JMenuItem h5Item = new JMenuItem(Messages.get("menu.markdown.heading5"));
        h5Item.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_5, shortcutMask));
        h5Item.addActionListener(e -> prefixCurrentLine("##### "));
        JMenuItem h6Item = new JMenuItem(Messages.get("menu.markdown.heading6"));
        h6Item.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_6, shortcutMask));
        h6Item.addActionListener(e -> prefixCurrentLine("###### "));
        JMenuItem hrItem = new JMenuItem(Messages.get("menu.markdown.horizontalRule"));
        hrItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, shortcutMask));
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

        // Window menu
        JMenu windowMenu = new JMenu(Messages.get("menu.window"));
        windowMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                windowMenu.removeAll();

                JMenuItem minimizeItem = new JMenuItem(Messages.get("menu.window.minimize"));
                minimizeItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, shortcutMask));
                minimizeItem.addActionListener(ev -> frame.setState(Frame.ICONIFIED));
                windowMenu.add(minimizeItem);

                JMenuItem zoomItem = new JMenuItem(Messages.get("menu.window.zoom"));
                zoomItem.addActionListener(ev -> {
                    if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                        frame.setExtendedState(Frame.NORMAL);
                    } else {
                        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
                    }
                });
                windowMenu.add(zoomItem);

                windowMenu.addSeparator();

                JMenuItem previousItem = new JMenuItem(Messages.get("menu.window.previous"));
                previousItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_COMMA, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
                previousItem.addActionListener(ev -> {
                    int idx = openInstances.indexOf(EditorWindow.this);
                    if (idx >= 0 && openInstances.size() > 1) {
                        int prev = (idx - 1 + openInstances.size()) % openInstances.size();
                        EditorWindow target = openInstances.get(prev);
                        target.frame.toFront();
                        target.frame.requestFocus();
                    }
                });
                previousItem.setEnabled(openInstances.size() > 1);
                windowMenu.add(previousItem);

                JMenuItem nextItem = new JMenuItem(Messages.get("menu.window.next"));
                nextItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PERIOD, shortcutMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
                nextItem.addActionListener(ev -> {
                    int idx = openInstances.indexOf(EditorWindow.this);
                    if (idx >= 0 && openInstances.size() > 1) {
                        int next = (idx + 1) % openInstances.size();
                        EditorWindow target = openInstances.get(next);
                        target.frame.toFront();
                        target.frame.requestFocus();
                    }
                });
                nextItem.setEnabled(openInstances.size() > 1);
                windowMenu.add(nextItem);

                windowMenu.addSeparator();

                JMenuItem cascadeItem = new JMenuItem(Messages.get("menu.window.cascadeAll"));
                cascadeItem.addActionListener(ev -> {
                    int x = 20, y = 20;
                    for (EditorWindow instance : openInstances) {
                        instance.frame.setExtendedState(Frame.NORMAL);
                        instance.frame.setLocation(x, y);
                        instance.frame.toFront();
                        x += 30;
                        y += 30;
                    }
                    frame.toFront();
                    frame.requestFocus();
                });
                cascadeItem.setEnabled(openInstances.size() > 1);
                windowMenu.add(cascadeItem);

                JMenuItem tileItem = new JMenuItem(Messages.get("menu.window.tileAll"));
                tileItem.addActionListener(ev -> {
                    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    Rectangle screenBounds = ge.getMaximumWindowBounds();
                    int count = openInstances.size();
                    if (count == 0) return;
                    int cols = (int) Math.ceil(Math.sqrt(count));
                    int rows = (int) Math.ceil((double) count / cols);
                    int tileWidth = screenBounds.width / cols;
                    int tileHeight = screenBounds.height / rows;
                    for (int i = 0; i < count; i++) {
                        EditorWindow instance = openInstances.get(i);
                        instance.frame.setExtendedState(Frame.NORMAL);
                        int col = i % cols;
                        int row = i / cols;
                        instance.frame.setBounds(
                            screenBounds.x + col * tileWidth,
                            screenBounds.y + row * tileHeight,
                            tileWidth, tileHeight
                        );
                    }
                });
                tileItem.setEnabled(openInstances.size() > 1);
                windowMenu.add(tileItem);

                windowMenu.addSeparator();

                // Window list
                for (EditorWindow instance : openInstances) {
                    String title = instance.frame.getTitle();
                    JCheckBoxMenuItem windowItem = new JCheckBoxMenuItem(title);
                    windowItem.setSelected(instance == EditorWindow.this && frame.isFocused());
                    windowItem.addActionListener(ev -> {
                        instance.frame.toFront();
                        instance.frame.requestFocus();
                    });
                    windowMenu.add(windowItem);
                }
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });
        menuBar.add(windowMenu);

        frame.setJMenuBar(menuBar);

        // Enable/disable formatting items based on selection
        editorPane.addCaretListener(e -> {
            boolean hasSelection = e.getDot() != e.getMark();
            boldItem.setEnabled(hasSelection);
            italicItem.setEnabled(hasSelection);
            strikethroughItem.setEnabled(hasSelection);
            superscriptItem.setEnabled(hasSelection);
            subscriptItem.setEnabled(hasSelection);
            insItem.setEnabled(hasSelection);
        });
    }

    private void buildLayout() {
        // --- Toolbar / status bar at top ---
        toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        filePathLabel = new JLabel(" ");
        filePathLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        toolbar.add(filePathLabel, BorderLayout.CENTER);

        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        togglePanel.setOpaque(false);

        // Document statistics label
        statsLabel = new JLabel("L: 0  W: 0  C: 0");
        statsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statsLabel.setForeground(Color.GRAY);
        statsLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        togglePanel.add(statsLabel);

        // Word wrap toggle button
        wordWrapToggle = new JToggleButton();
        wordWrapToggle.setUI(new BasicToggleButtonUI());
        wordWrapToggle.setToolTipText(Messages.get("toolbar.wordWrap"));
        wordWrapToggle.setIcon(new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getForeground());
                g2.setStroke(new BasicStroke(1.5f));
                // Three horizontal lines with a wrap arrow on the last
                g2.drawLine(x + 3, y + 5, x + 17, y + 5);
                g2.drawLine(x + 3, y + 10, x + 17, y + 10);
                g2.drawLine(x + 3, y + 15, x + 13, y + 15);
                // Wrap arrow curving back
                g2.drawArc(x + 13, y + 10, 8, 10, 270, 180);
                // Arrow head
                g2.drawLine(x + 13, y + 15, x + 13, y + 12);
                g2.drawLine(x + 13, y + 15, x + 16, y + 15);
                g2.dispose();
            }
            @Override public int getIconWidth() { return 20; }
            @Override public int getIconHeight() { return 20; }
        });
        wordWrapToggle.setSelected(true);
        wordWrapToggle.setFocusPainted(false);
        wordWrapToggle.setBorderPainted(false);
        wordWrapToggle.setContentAreaFilled(true);
        wordWrapToggle.setOpaque(true);
        wordWrapToggle.setBackground(preferences.getButtonHighlightColorObj());
        wordWrapToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        wordWrapToggle.setPreferredSize(new Dimension(28, 28));
        wordWrapToggle.addActionListener(e -> {
            boolean wrap = wordWrapToggle.isSelected();
            wordWrapToggle.setBackground(wrap ? preferences.getButtonHighlightColorObj() : null);
            wordWrapToggle.setContentAreaFilled(wrap);
            wordWrapToggle.setOpaque(wrap);
            editorPane.setLineWrap(wrap);
            editorPane.setWrapStyleWord(wrap);
        });
        togglePanel.add(wordWrapToggle);

        // Hidden characters toggle button
        ImageIcon hiddenCharsIconFull = null;
        var hiddenCharsUrl = getClass().getClassLoader().getResource("hidden_chars.png");
        if (hiddenCharsUrl != null) {
            hiddenCharsIconFull = new ImageIcon(new ImageIcon(hiddenCharsUrl).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        }
        hiddenCharsToggle = new JToggleButton(hiddenCharsIconFull, false);
        hiddenCharsToggle.setUI(new BasicToggleButtonUI());
        hiddenCharsToggle.setToolTipText(Messages.get("toolbar.hiddenChars"));
        hiddenCharsToggle.setFocusPainted(false);
        hiddenCharsToggle.setBorderPainted(false);
        hiddenCharsToggle.setContentAreaFilled(false);
        hiddenCharsToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hiddenCharsToggle.setPreferredSize(new Dimension(28, 28));
        hiddenCharsToggle.addActionListener(e -> {
            hiddenCharsVisible = hiddenCharsToggle.isSelected();
            hiddenCharsToggle.setBackground(hiddenCharsVisible ? preferences.getButtonHighlightColorObj() : null);
            hiddenCharsToggle.setContentAreaFilled(hiddenCharsVisible);
            hiddenCharsToggle.setOpaque(hiddenCharsVisible);
            editorPane.setWhitespaceVisible(hiddenCharsVisible);
            editorPane.setEOLMarkersVisible(hiddenCharsVisible);
        });
        togglePanel.add(hiddenCharsToggle);

        // Spell check toggle button
        spellCheckToggle = new JToggleButton();
        spellCheckToggle.setUI(new BasicToggleButtonUI());
        spellCheckToggle.setToolTipText(Messages.get("toolbar.spellCheck"));
        spellCheckToggle.setIcon(new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getForeground());
                // Draw "ABC" with a checkmark
                g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
                g2.drawString("AB", x + 2, y + 12);
                // Red squiggly under it
                g2.setColor(new Color(255, 0, 0, 200));
                g2.setStroke(new BasicStroke(1.0f));
                int baseY = y + 15;
                for (int wx = x + 2; wx < x + 16; wx += 4) {
                    int nx = Math.min(wx + 2, x + 16);
                    g2.drawLine(wx, baseY, nx, baseY - 2);
                    int nx2 = Math.min(nx + 2, x + 16);
                    g2.drawLine(nx, baseY - 2, nx2, baseY);
                }
                // Checkmark in green
                g2.setColor(new Color(0, 160, 0));
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawLine(x + 13, y + 6, x + 15, y + 9);
                g2.drawLine(x + 15, y + 9, x + 19, y + 3);
                g2.dispose();
            }
            @Override public int getIconWidth() { return 20; }
            @Override public int getIconHeight() { return 20; }
        });
        spellCheckToggle.setSelected(true);
        spellCheckToggle.setFocusPainted(false);
        spellCheckToggle.setBorderPainted(false);
        spellCheckToggle.setContentAreaFilled(true);
        spellCheckToggle.setOpaque(true);
        spellCheckToggle.setBackground(preferences.getButtonHighlightColorObj());
        spellCheckToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        spellCheckToggle.setPreferredSize(new Dimension(28, 28));
        spellCheckToggle.addActionListener(e -> {
            boolean active = spellCheckToggle.isSelected();
            spellCheckToggle.setBackground(active ? preferences.getButtonHighlightColorObj() : null);
            spellCheckToggle.setContentAreaFilled(active);
            spellCheckToggle.setOpaque(active);
            if (spellCheckController != null) {
                spellCheckController.setEnabled(active);
            }
        });
        togglePanel.add(spellCheckToggle);

        // Synchronized scrolling toggle button
        ImageIcon syncIconFull = null;
        var syncUrl = getClass().getClassLoader().getResource("sync_scrolling.png");
        if (syncUrl != null) {
            syncIconFull = new ImageIcon(new ImageIcon(syncUrl).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        }
        syncScrollToggle = new JToggleButton(syncIconFull, false);
        syncScrollToggle.setUI(new BasicToggleButtonUI());
        syncScrollToggle.setToolTipText(Messages.get("toolbar.syncScroll"));
        syncScrollToggle.setFocusPainted(false);
        syncScrollToggle.setBorderPainted(false);
        syncScrollToggle.setContentAreaFilled(false);
        syncScrollToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        syncScrollToggle.setPreferredSize(new Dimension(28, 28));
        syncScrollToggle.addActionListener(e -> {
            syncScrollEnabled = syncScrollToggle.isSelected();
            syncScrollToggle.setBackground(syncScrollEnabled ? preferences.getButtonHighlightColorObj() : null);
            syncScrollToggle.setContentAreaFilled(syncScrollEnabled);
            syncScrollToggle.setOpaque(syncScrollEnabled);
        });
        togglePanel.add(syncScrollToggle);

        // Preview toggle button using eye.png
        ImageIcon eyeIconFull = null;
        var eyeUrl = getClass().getClassLoader().getResource("eye.png");
        if (eyeUrl != null) {
            eyeIconFull = new ImageIcon(new ImageIcon(eyeUrl).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        }
        previewToggle = new JToggleButton(eyeIconFull, true);
        previewToggle.setUI(new BasicToggleButtonUI());
        previewToggle.setToolTipText(Messages.get("toolbar.preview"));
        previewToggle.setFocusPainted(false);
        previewToggle.setBorderPainted(false);
        previewToggle.setContentAreaFilled(true);
        previewToggle.setOpaque(true);
        previewToggle.setBackground(preferences.getButtonHighlightColorObj());
        previewToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        previewToggle.setPreferredSize(new Dimension(28, 28));
        previewToggle.addActionListener(e -> togglePreview());
        togglePanel.add(previewToggle);

        // AI toggle button using AI.png
        ImageIcon aiIconFull = null;
        var aiUrl = getClass().getClassLoader().getResource("AI.png");
        if (aiUrl != null) {
            aiIconFull = new ImageIcon(new ImageIcon(aiUrl).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        }
        aiToggle = new JToggleButton(aiIconFull, true);
        aiToggle.setUI(new BasicToggleButtonUI());
        aiToggle.setToolTipText(Messages.get("toolbar.ai"));
        aiToggle.setFocusPainted(false);
        aiToggle.setBorderPainted(false);
        aiToggle.setContentAreaFilled(true);
        aiToggle.setOpaque(true);
        aiToggle.setBackground(preferences.getButtonHighlightColorObj());
        aiToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        aiToggle.setPreferredSize(new Dimension(28, 28));
        aiToggle.addActionListener(e -> toggleAI());
        togglePanel.add(aiToggle);

        // Dark mode toggle button (moon/sun)
        darkModeToggle = new JToggleButton();
        darkModeToggle.setUI(new BasicToggleButtonUI());
        darkModeToggle.setToolTipText(Messages.get("toolbar.darkMode"));
        darkModeToggle.setSelected(preferences.isDarkMode());
        darkModeToggle.setIcon(new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getForeground());
                if (darkModeToggle.isSelected()) {
                    // Sun icon (light mode switch) — yellow
                    g2.setColor(new Color(255, 200, 0));
                    int cx = x + 10, cy = y + 10;
                    g2.fillOval(cx - 4, cy - 4, 8, 8);
                    g2.setStroke(new BasicStroke(1.5f));
                    for (int i = 0; i < 8; i++) {
                        double angle = Math.toRadians(i * 45);
                        int x1 = cx + (int)(6 * Math.cos(angle));
                        int y1 = cy + (int)(6 * Math.sin(angle));
                        int x2 = cx + (int)(8 * Math.cos(angle));
                        int y2 = cy + (int)(8 * Math.sin(angle));
                        g2.drawLine(x1, y1, x2, y2);
                    }
                } else {
                    // Crescent moon icon (dark mode switch)
                    g2.fillOval(x + 5, y + 3, 12, 12);
                    g2.setColor(darkModeToggle.getBackground() != null ? darkModeToggle.getBackground() : c.getBackground());
                    g2.fillOval(x + 9, y + 2, 10, 10);
                }
                g2.dispose();
            }
            @Override public int getIconWidth() { return 20; }
            @Override public int getIconHeight() { return 20; }
        });
        darkModeToggle.setFocusPainted(false);
        darkModeToggle.setBorderPainted(false);
        darkModeToggle.setContentAreaFilled(false);
        darkModeToggle.setOpaque(false);
        darkModeToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        darkModeToggle.setPreferredSize(new Dimension(28, 28));
        darkModeToggle.addActionListener(e -> toggleDarkMode());
        togglePanel.add(darkModeToggle);

        toolbar.add(togglePanel, BorderLayout.EAST);
        frame.add(toolbar, BorderLayout.NORTH);

        // --- Main content area ---
        editorPreviewSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPanel, previewPanel);
        editorPreviewSplit.setDividerLocation(600);
        editorPreviewSplit.setResizeWeight(0.5);

        aiPreferences = AIChatPreferences.load();
        aiChatPanel = AIChatPanel.builder()
            .editor(new DocumentEditor() {
                @Override public String getText() { return editorPane.getText(); }
                private boolean truncationWarningShown = false;
                @Override public String getContextText() {
                    String fullText = editorPane.getText();
                    if (fullText.length() <= 20_000) {
                        truncationWarningShown = false;
                        return fullText;
                    }
                    // For large documents, return 10K chars before and after the visible area
                    int caretPos = editorPane.getCaretPosition();
                    int start = Math.max(0, caretPos - 10_000);
                    int end = Math.min(fullText.length(), caretPos + 10_000);
                    if (!truncationWarningShown) {
                        truncationWarningShown = true;
                        JOptionPane.showMessageDialog(frame,
                                Messages.get("msg.documentTruncated"),
                                Messages.get("msg.documentTruncatedTitle"), JOptionPane.INFORMATION_MESSAGE);
                    }
                    StringBuilder sb = new StringBuilder();
                    if (start > 0) {
                        // Count lines and characters omitted from the beginning
                        String omittedPrefix = fullText.substring(0, start);
                        long omittedLines = omittedPrefix.chars().filter(c -> c == '\n').count();
                        sb.append("[... ").append(omittedLines).append(" lines, ")
                          .append(start).append(" characters omitted from beginning ...]\n");
                    }
                    sb.append(fullText, start, end);
                    if (end < fullText.length()) {
                        String omittedSuffix = fullText.substring(end);
                        long omittedLines = omittedSuffix.chars().filter(c -> c == '\n').count();
                        sb.append("\n[... ").append(omittedLines).append(" lines, ")
                          .append(fullText.length() - end).append(" characters omitted from end ...]");
                    }
                    return sb.toString();
                }
                @Override public void setText(String text) {
                    editorPane.setText(text);
                    editorPane.setCaretPosition(0);
                }
            })
            .preferences(aiPreferences)
            .onPromptNag(() -> {
                if (!LicenseDialog.isLicensed(preferences)) SplashScreen.show();
            })
            .build();
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPreviewSplit, aiChatPanel);
        mainSplit.setResizeWeight(1.0);
        mainSplit.setDividerLocation(frame.getWidth() - 400);

        frame.add(mainSplit, BorderLayout.CENTER);
    }

    private void toggleDarkMode() {
        boolean dark = darkModeToggle.isSelected();
        preferences.setDarkMode(dark);
        preferences.save();

        // Switch FlatLaf theme
        try {
            if (dark) {
                UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
            }
            // Update all open windows and their dialogs
            for (EditorWindow w : openInstances) {
                SwingUtilities.updateComponentTreeUI(w.frame);
                if (w.findDialog != null) SwingUtilities.updateComponentTreeUI(w.findDialog);
                if (w.replaceDialog != null) SwingUtilities.updateComponentTreeUI(w.replaceDialog);
                w.frame.repaint();
            }
        } catch (Exception e) {
            // Fall back to manual theme
        }

        applyTheme(dark ? Theme.DARK : Theme.LIGHT);
    }

    private void applyTheme(Theme theme) {
        // Editor — apply RSyntaxTextArea theme
        try {
            String themePath = "dark".equals(theme.rsyntaxTheme)
                ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
            var is = getClass().getResourceAsStream(themePath);
            if (is != null) {
                org.fife.ui.rsyntaxtextarea.Theme rstaTheme =
                    org.fife.ui.rsyntaxtextarea.Theme.load(is);
                rstaTheme.apply(editorPane);
            }
        } catch (Exception e) {
            // Fall back to manual colors
        }
        // Re-apply user's font preferences (RSTA theme overrides font)
        editorPanel.applyPreferences(preferences);
        // Override editor background to match theme
        editorPane.setBackground(theme.editorBackground);

        // Line numbers
        var scrollPane = editorPanel.getScrollPane();
        scrollPane.getGutter().setBackground(theme.lineNumberBackground);
        scrollPane.getGutter().setLineNumberColor(theme.lineNumberForeground);

        // Dark mode toggle icon needs repaint
        darkModeToggle.repaint();

        // Preview panel — force full reload to apply CSS
        previewPanel.forceFullReload();
        updatePreview();

        // AI chat panel
        if (aiChatPanel != null) {
            aiChatPanel.setDarkMode(preferences.isDarkMode());
        }
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
        previewToggle.setBackground(previewVisible ? preferences.getButtonHighlightColorObj() : null);
        previewToggle.setContentAreaFilled(previewVisible);
        previewToggle.setOpaque(previewVisible);
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
        aiToggle.setBackground(aiVisible ? preferences.getButtonHighlightColorObj() : null);
        aiToggle.setContentAreaFilled(aiVisible);
        aiToggle.setOpaque(aiVisible);
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
                previewToggle.setBackground(null);
                previewToggle.setContentAreaFilled(false);
                previewToggle.setOpaque(false);
                previewVisible = false;
                lastPreviewDivider = preferences.getEditorPreviewDivider();
                editorPreviewSplit.setRightComponent(null);
                editorPreviewSplit.setDividerSize(0);
            }
            if (!preferences.isAiVisible()) {
                aiToggle.setSelected(false);
                aiToggle.setBackground(null);
                aiToggle.setContentAreaFilled(false);
                aiToggle.setOpaque(false);
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
            public void insertUpdate(DocumentEvent e) { updatePreview(); markDirty(); updateStats(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updatePreview(); markDirty(); updateStats(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updatePreview(); markDirty(); updateStats(); }
        });

        // Synchronized scrolling: editor scroll drives preview scroll
        JScrollBar editorVScroll = editorPanel.getScrollPane().getVerticalScrollBar();
        editorVScroll.addAdjustmentListener(e -> {
            if (!syncScrollEnabled || !previewVisible || syncScrolling) return;
            if (editorVScroll.getMaximum() <= editorVScroll.getVisibleAmount()) return;
            double ratio = (double) e.getValue() / (editorVScroll.getMaximum() - editorVScroll.getVisibleAmount());
            syncScrolling = true;
            previewPanel.scrollToRatio(ratio);
            SwingUtilities.invokeLater(() -> syncScrolling = false);
        });

        // Provide the editor's scroll ratio to the preview panel so it can restore
        // position after content reloads during synchronized scrolling
        previewPanel.setScrollRatioSupplier(() -> {
            if (!syncScrollEnabled) return -1;
            int max = editorVScroll.getMaximum() - editorVScroll.getVisibleAmount();
            if (max <= 0) return 0;
            return (double) editorVScroll.getValue() / max;
        });

        // Synchronized scrolling: preview scroll drives editor scroll
        previewPanel.setScrollListener(ratio -> {
            if (!syncScrollEnabled || syncScrolling) return;
            syncScrolling = true;
            int max = editorVScroll.getMaximum() - editorVScroll.getVisibleAmount();
            if (max > 0) {
                editorVScroll.setValue((int) (max * ratio));
            }
            SwingUtilities.invokeLater(() -> syncScrolling = false);
        });

        // "Find in Source" callback from preview right-click
        previewPanel.setFindInSourceCallback(text -> findInSource(text));

        // Editor right-click context menu
        JPopupMenu editorContextMenu = new JPopupMenu();
        JMenuItem ctxCut = new JMenuItem(Messages.get("context.cut"));
        ctxCut.addActionListener(e -> editorPane.cut());
        JMenuItem ctxCopy = new JMenuItem(Messages.get("context.copy"));
        ctxCopy.addActionListener(e -> editorPane.copy());
        JMenuItem ctxPaste = new JMenuItem(Messages.get("context.paste"));
        ctxPaste.addActionListener(e -> editorPane.paste());
        JMenuItem ctxFindInPreview = new JMenuItem(Messages.get("context.findInPreview"));
        ctxFindInPreview.addActionListener(e -> findInPreview());
        editorContextMenu.add(ctxCut);
        editorContextMenu.add(ctxCopy);
        editorContextMenu.add(ctxPaste);
        editorContextMenu.addSeparator();
        editorContextMenu.add(ctxFindInPreview);
        editorPane.setPopupMenu(editorContextMenu);

        // Spell checking — initialize controller and enable by default
        Path spellCheckConfigDir = Paths.get(System.getProperty("user.home"), ".purpleplatypus");
        spellCheckController = new SpellCheckController(editorPane, spellCheckConfigDir, preferences.getSpellCheckLanguage());
        spellCheckController.setOnLanguageReady(() -> {
            String langName = LanguageDownloader.getDisplayName(spellCheckController.getLanguage());
            JOptionPane.showMessageDialog(frame,
                    Messages.get("msg.spellCheckReady", langName),
                    Messages.get("msg.spellCheckReadyTitle"), JOptionPane.INFORMATION_MESSAGE);
        });
        spellCheckController.setEnabled(true);

        // Drag-and-drop: insert markdown image link when an image file is dropped onto the editor
        new DropTarget(editorPane, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    // Move caret to follow the pointer for precise placement
                    Point pt = dtde.getLocation();
                    int offset = editorPane.viewToModel2D(pt);
                    if (offset >= 0) {
                        editorPane.setCaretPosition(offset);
                    }
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.rejectDrop();
                    return;
                }
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                try {
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    StringBuilder markdown = new StringBuilder();
                    for (File file : files) {
                        String name = file.getName().toLowerCase();
                        if (name.endsWith(".gif") || name.endsWith(".jpg")
                                || name.endsWith(".jpeg") || name.endsWith(".png")) {
                            String imgPath = computeImageRelativePath(file);
                            String altText = file.getName().replaceFirst("\\.[^.]+$", "");
                            markdown.append("![").append(altText).append("](").append(imgPath).append(")\n");
                        }
                    }
                    if (markdown.length() > 0) {
                        // Position caret at drop location
                        Point dropPoint = dtde.getLocation();
                        int offset = editorPane.viewToModel2D(dropPoint);
                        if (offset >= 0) {
                            editorPane.setCaretPosition(offset);
                        }
                        editorPane.replaceSelection(markdown.toString());
                    }
                    dtde.dropComplete(true);
                } catch (UnsupportedFlavorException | IOException ex) {
                    dtde.dropComplete(false);
                }
            }
        }, true);
    }

    /**
     * Computes the relative path from the current document's directory to the given image file.
     * Falls back to the absolute path if no document has been saved yet or paths are on different roots.
     */
    private String computeImageRelativePath(File imageFile) {
        if (currentFile != null && currentFile.getParentFile() != null) {
            Path docDir = currentFile.getParentFile().toPath();
            Path imgPath = imageFile.toPath();
            try {
                return docDir.relativize(imgPath).toString().replace('\\', '/');
            } catch (IllegalArgumentException e) {
                // Different roots (e.g., different drives on Windows)
                return imageFile.getAbsolutePath().replace('\\', '/');
            }
        }
        return imageFile.getAbsolutePath().replace('\\', '/');
    }

    private void updatePreview() {
        if (previewDebounceTimer != null) {
            previewDebounceTimer.restart();
        } else {
            previewDebounceTimer = new javax.swing.Timer(PREVIEW_DEBOUNCE_MS, e -> {
                previewPanel.updatePreview(editorPane.getText(), currentFile, preferences);
            });
            previewDebounceTimer.setRepeats(false);
            previewDebounceTimer.start();
        }
    }

    // --- File operations ---

    private void rebuildRecentsMenu() {
        recentsMenu.removeAll();
        java.util.List<String> recentFiles = preferences.getRecentFiles();
        if (recentFiles.isEmpty()) {
            JMenuItem emptyItem = new JMenuItem(Messages.get("menu.file.recents.none"));
            emptyItem.setEnabled(false);
            recentsMenu.add(emptyItem);
        } else {
            for (String path : recentFiles) {
                File file = new File(path);
                JMenuItem item = new JMenuItem(file.getName());
                item.setToolTipText(path);
                item.addActionListener(e -> openRecentFile(file));
                recentsMenu.add(item);
            }
            recentsMenu.addSeparator();
            JMenuItem clearItem = new JMenuItem(Messages.get("menu.file.recents.clearAll"));
            clearItem.addActionListener(e -> {
                preferences.clearRecentFiles();
                preferences.save();
                rebuildRecentsMenuAllWindows();
            });
            recentsMenu.add(clearItem);
        }
    }

    private void addToRecents(File file) {
        if (file == null) return;
        preferences.addRecentFile(file.getAbsolutePath());
        preferences.save();
        rebuildRecentsMenuAllWindows();
    }

    private static void rebuildRecentsMenuAllWindows() {
        for (EditorWindow w : openInstances) {
            w.rebuildRecentsMenu();
        }
    }

    private void openRecentFile(File file) {
        if (!file.exists()) {
            JOptionPane.showMessageDialog(frame,
                Messages.get("msg.error.fileNotFound"),
                Messages.get("msg.error.fileNotFoundTitle"), JOptionPane.WARNING_MESSAGE);
            // Remove from recents
            java.util.List<String> recents = new java.util.ArrayList<>(preferences.getRecentFiles());
            recents.remove(file.getAbsolutePath());
            preferences.clearRecentFiles();
            for (int i = recents.size() - 1; i >= 0; i--) {
                preferences.addRecentFile(recents.get(i));
            }
            preferences.save();
            rebuildRecentsMenuAllWindows();
            return;
        }
        // Check if file is already open in another window — bring it to front
        for (EditorWindow w : openInstances) {
            if (w.currentFile != null && w.currentFile.getAbsolutePath().equals(file.getAbsolutePath())) {
                w.frame.toFront();
                w.frame.setState(java.awt.Frame.NORMAL);
                w.frame.requestFocus();
                return;
            }
        }
        try {
            String content = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            openFileInTarget(file, content);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame,
                Messages.get("msg.error.readFile", ex.getMessage()),
                Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void newFile() {
        SwingUtilities.invokeLater(EditorWindow::new);
    }

    private void openFile() {
        long now = System.currentTimeMillis();
        if (now - lastOpenTime < 1000) return;
        lastOpenTime = now;

        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.open"), FileDialog.LOAD);
        dialog.setMultipleMode(true);
        dialog.setFilenameFilter((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".md") || lower.endsWith(".markdown")
                    || lower.endsWith(".txt") || lower.endsWith(".textbundle")
                    || lower.endsWith(".textpack");
        });
        dialog.setVisible(true);
        File[] files = dialog.getFiles();
        if (files != null && files.length > 0) {
            boolean first = true;
            for (File file : files) {
                // Handle .textbundle directories
                if (file.isDirectory() && file.getName().toLowerCase().endsWith(".textbundle")) {
                    File textMd = new File(file, "text.md");
                    if (!textMd.exists()) textMd = new File(file, "text.markdown");
                    if (textMd.exists()) {
                        try {
                            String content = new String(Files.readAllBytes(textMd.toPath()), StandardCharsets.UTF_8);
                            if (first) {
                                openFileInTarget(textMd, content);
                                first = false;
                            } else {
                                EditorWindow newWindow = new EditorWindow();
                                newWindow.loadFileContent(textMd, content);
                            }
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog(frame, Messages.get("msg.error.readTextBundle", ex.getMessage()),
                                    Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } else if (file.isFile() && file.getName().toLowerCase().endsWith(".textpack")) {
                    // Handle .textpack (zipped TextBundle)
                    try {
                        java.nio.file.Path tempDir = Files.createTempDirectory("textpack_");
                        unzipTextPack(file.toPath(), tempDir);
                        File textMd = tempDir.resolve("text.md").toFile();
                        if (!textMd.exists()) textMd = tempDir.resolve("text.markdown").toFile();
                        if (textMd.exists()) {
                            String content = new String(Files.readAllBytes(textMd.toPath()), StandardCharsets.UTF_8);
                            if (first) {
                                openFileInTarget(textMd, content);
                                textPackSource = true;
                                saveItem.setEnabled(false);
                                first = false;
                            } else {
                                EditorWindow newWindow = new EditorWindow();
                                newWindow.loadFileContent(textMd, content);
                                newWindow.textPackSource = true;
                                newWindow.saveItem.setEnabled(false);
                            }
                        } else {
                            JOptionPane.showMessageDialog(frame, Messages.get("msg.error.textPackNoFile"),
                                    Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(frame, Messages.get("msg.error.readTextPack", ex.getMessage()),
                                Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
                    }
                } else if (file.isFile()) {
                    // Warn if file is larger than 2MB
                    if (file.length() > 2_000_000) {
                        int choice = JOptionPane.showOptionDialog(frame,
                                Messages.get("msg.largeFile", file.length() / 1_000_000),
                                Messages.get("msg.largeFileTitle"), JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                                null, new String[]{Messages.get("menu.file.open"), Messages.get("msg.cancel")}, Messages.get("msg.cancel"));
                        if (choice != 0) continue;
                    }
                    try {
                        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                        if (first) {
                            openFileInTarget(file, content);
                            first = false;
                        } else {
                            EditorWindow newWindow = new EditorWindow();
                            newWindow.loadFileContent(file, content);
                        }
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(frame, Messages.get("msg.error.readFile", ex.getMessage()),
                                Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
        lastOpenTime = System.currentTimeMillis();
    }

    /**
     * Opens the file in this window if it has no document, otherwise in a new window.
     */
    private void openFileInTarget(File file, String content) {
        if (currentFile == null && !dirty) {
            loadFileContent(file, content);
        } else {
            EditorWindow newWindow = new EditorWindow();
            newWindow.loadFileContent(file, content);
        }
    }

    public void loadFileContent(File file, String content) {
        currentFile = file;
        windowsLineEndings = content.contains("\r\n");
        // Normalize to Unix line endings for the editor; restore on save if needed
        content = content.replace("\r\n", "\n").replace("\r", "\n");
        lastModifiedOnDisk = file.lastModified();
        // Disable syntax highlighting for large files to improve performance
        if (file.length() > LARGE_FILE_THRESHOLD) {
            editorPane.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE);
            JOptionPane.showMessageDialog(frame,
                    Messages.get("msg.syntaxDisabled"),
                    Messages.get("msg.syntaxDisabledTitle"), JOptionPane.INFORMATION_MESSAGE);
        } else {
            editorPane.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        }
        previewPanel.forceFullReload();
        editorPane.setText(content);
        editorPane.setCaretPosition(0);
        undoManager.discardAllEdits();
        dirty = false;
        updateTitle();
        updateLineEndingsMenuItem();
        updateStats();
        addToRecents(file);
    }

    private void saveFile() {
        if (textPackSource) {
            JOptionPane.showMessageDialog(frame,
                    Messages.get("msg.saveDisabled"),
                    Messages.get("msg.saveDisabledTitle"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (currentFile == null) saveFileAs();
        else writeFile(currentFile);
    }

    private void saveFileAs() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.saveAs"), FileDialog.SAVE);
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
            // Clear textpack restriction since we now have a real file
            if (textPackSource) {
                textPackSource = false;
                saveItem.setEnabled(true);
            }
            updateTitle();
        }
    }

    private void writeFile(File file) {
        try {
            String content = editorPane.getText();
            if (windowsLineEndings) {
                // JTextArea normalizes to \n; convert back to \r\n for Windows format
                content = content.replace("\n", "\r\n");
            }
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            lastModifiedOnDisk = file.lastModified();
            dirty = false;
            updateTitle();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, Messages.get("msg.error.saveFile", ex.getMessage()),
                    Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Converts the document's line endings between Windows (\r\n) and Unix (\n) format.
     * Toggles the internal flag and marks the document as dirty since the on-disk
     * representation will change on next save.
     */
    private void convertLineEndings() {
        windowsLineEndings = !windowsLineEndings;
        // Ensure editor text is clean (no stray \r characters)
        String text = editorPane.getText();
        if (text.contains("\r")) {
            int caretPos = editorPane.getCaretPosition();
            text = text.replace("\r\n", "\n").replace("\r", "\n");
            editorPane.setText(text);
            editorPane.setCaretPosition(Math.min(caretPos, text.length()));
        }
        dirty = true;
        updateTitle();
        updateLineEndingsMenuItem();
    }

    /**
     * Converts grid/Pandoc-style tables in the document to standard GFM pipe tables.
     * Removes +---+ row separators and converts +:===+: header separators to |:---|.
     */
    private void cleanupPandocTables() {
        int selStart = editorPane.getSelectionStart();
        int selEnd = editorPane.getSelectionEnd();
        boolean hasSel = selStart != selEnd;

        String text = hasSel ? editorPane.getSelectedText() : editorPane.getText();
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        boolean changed = false;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            if (trimmed.matches("^\\+[-=:+]+\\+$")) {
                changed = true;
                if (trimmed.contains("=")) {
                    String converted = trimmed.replace('+', '|').replace('=', '-');
                    result.append(converted).append('\n');
                }
                continue;
            }

            result.append(lines[i]);
            if (i < lines.length - 1) result.append('\n');
        }

        if (changed) {
            String newText = result.toString();
            if (hasSel) {
                editorPane.replaceSelection(newText);
            } else {
                int caretPos = editorPane.getCaretPosition();
                editorPane.setText(newText);
                editorPane.setCaretPosition(Math.min(caretPos, newText.length()));
            }
            dirty = true;
            updateTitle();
            updatePreview();
        } else {
            JOptionPane.showMessageDialog(frame, Messages.get("msg.noPandocTables"),
                Messages.get("msg.noPandocTablesTitle"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Formats the GFM table at the current caret position by padding columns to
     * uniform width, respecting the alignment indicated by the separator row.
     */
    private void formatTable() {
        String fullText = editorPane.getText();
        int caretPos = editorPane.getCaretPosition();

        // Find the table surrounding the caret position
        String[] allLines = fullText.split("\n", -1);
        int caretLine = 0;
        int charCount = 0;
        for (int i = 0; i < allLines.length; i++) {
            charCount += allLines[i].length() + 1; // +1 for \n
            if (charCount > caretPos) {
                caretLine = i;
                break;
            }
        }

        // Check if the current line is part of a table (contains |)
        if (caretLine >= allLines.length || !allLines[caretLine].contains("|")) {
            JOptionPane.showMessageDialog(frame, Messages.get("msg.noTableFound"),
                    Messages.get("msg.noTableFoundTitle"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Expand to find full table (consecutive lines containing |)
        int tableStart = caretLine;
        while (tableStart > 0 && allLines[tableStart - 1].contains("|")) {
            tableStart--;
        }
        int tableEnd = caretLine;
        while (tableEnd < allLines.length - 1 && allLines[tableEnd + 1].contains("|")) {
            tableEnd++;
        }

        // Need at least 2 rows (header + separator)
        if (tableEnd - tableStart < 1) {
            JOptionPane.showMessageDialog(frame, Messages.get("msg.noValidTable"),
                    Messages.get("msg.noTableFoundTitle"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Parse table cells
        List<String[]> rows = new ArrayList<>();
        for (int i = tableStart; i <= tableEnd; i++) {
            rows.add(parseTableRow(allLines[i]));
        }

        // Determine column count (max across all rows)
        int colCount = 0;
        for (String[] row : rows) {
            colCount = Math.max(colCount, row.length);
        }
        if (colCount == 0) return;

        // Detect alignment from separator row (row index 1 if it matches ---/:::/etc.)
        int sepRowIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (isSeparatorRow(rows.get(i))) {
                sepRowIdx = i;
                break;
            }
        }

        // Determine alignment per column: 0=left, 1=center, 2=right
        int[] alignments = new int[colCount];
        if (sepRowIdx >= 0) {
            String[] sepCells = rows.get(sepRowIdx);
            for (int c = 0; c < colCount; c++) {
                if (c < sepCells.length) {
                    alignments[c] = parseAlignment(sepCells[c].trim());
                }
            }
        }

        // Calculate max width per column (excluding separator row)
        int[] colWidths = new int[colCount];
        for (int i = 0; i < rows.size(); i++) {
            if (i == sepRowIdx) continue;
            String[] cells = rows.get(i);
            for (int c = 0; c < cells.length; c++) {
                colWidths[c] = Math.max(colWidths[c], cells[c].trim().length());
            }
        }
        // Ensure minimum width of 3 (for separator dashes)
        for (int c = 0; c < colCount; c++) {
            colWidths[c] = Math.max(colWidths[c], 3);
        }

        // Rebuild the table with padding
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            String[] cells = rows.get(i);
            formatted.append("|");
            for (int c = 0; c < colCount; c++) {
                String cell = c < cells.length ? cells[c].trim() : "";
                if (i == sepRowIdx) {
                    // Rebuild separator with alignment markers
                    formatted.append(" ").append(buildSeparatorCell(alignments[c], colWidths[c])).append(" |");
                } else {
                    // Pad cell according to alignment
                    formatted.append(" ").append(padCell(cell, colWidths[c], alignments[c])).append(" |");
                }
            }
            if (i < rows.size() - 1) formatted.append("\n");
        }

        // Calculate the text range to replace
        int startOffset = 0;
        for (int i = 0; i < tableStart; i++) {
            startOffset += allLines[i].length() + 1;
        }
        int endOffset = startOffset;
        for (int i = tableStart; i <= tableEnd; i++) {
            endOffset += allLines[i].length() + (i < tableEnd ? 1 : 0);
        }

        // Replace the table text
        String formattedText = formatted.toString();
        try {
            editorPane.getDocument().remove(startOffset, endOffset - startOffset);
            editorPane.getDocument().insertString(startOffset, formattedText, null);
            editorPane.setCaretPosition(Math.min(caretPos, editorPane.getDocument().getLength()));
        } catch (javax.swing.text.BadLocationException e) {
            // Fallback: just set full text
            editorPane.select(startOffset, endOffset);
            editorPane.replaceSelection(formattedText);
        }
        dirty = true;
        updateTitle();
        updatePreview();
    }

    /** Splits a table row by | delimiters, ignoring leading/trailing pipes. */
    private String[] parseTableRow(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed.split("\\|", -1);
    }

    /** Returns true if all cells in the row match a separator pattern (dashes with optional colons). */
    private boolean isSeparatorRow(String[] cells) {
        if (cells.length == 0) return false;
        for (String cell : cells) {
            if (!cell.trim().matches(":?-{1,}:?")) return false;
        }
        return true;
    }

    /** Parses alignment from a separator cell: :--- = left, :---: = center, ---: = right. */
    private int parseAlignment(String sep) {
        boolean left = sep.startsWith(":");
        boolean right = sep.endsWith(":");
        if (left && right) return 1; // center
        if (right) return 2;         // right
        return 0;                    // left (default)
    }

    /** Pads a cell value to the given width according to alignment. */
    private String padCell(String value, int width, int alignment) {
        int padding = width - value.length();
        if (padding <= 0) return value;
        return switch (alignment) {
            case 1 -> { // center
                int left = padding / 2;
                int right = padding - left;
                yield " ".repeat(left) + value + " ".repeat(right);
            }
            case 2 -> // right
                " ".repeat(padding) + value;
            default -> // left
                value + " ".repeat(padding);
        };
    }

    /** Builds a separator cell (e.g., :---:, ---:, ---) of the given width. */
    private String buildSeparatorCell(int alignment, int width) {
        return switch (alignment) {
            case 1 -> ":" + "-".repeat(width - 2) + ":"; // center
            case 2 -> "-".repeat(width - 1) + ":";       // right
            default -> "-".repeat(width);                 // left
        };
    }

    /**
     * Shows the Zap Gremlins dialog and applies checked substitutions if the user clicks Zap.
     */
    private void zapGremlins() {
        ZapGremlinsDialog dialog = new ZapGremlinsDialog(frame, preferences);
        dialog.setVisible(true);
        if (dialog.isZapped()) {
            int selStart = editorPane.getSelectionStart();
            int selEnd = editorPane.getSelectionEnd();
            boolean hasSel = selStart != selEnd;

            String text = hasSel ? editorPane.getSelectedText() : editorPane.getText();
            int count = 0;
            for (String[] rule : preferences.getGremlins()) {
                if ("true".equals(rule[0]) && !rule[1].isEmpty()) {
                    String before = text;
                    text = text.replace(rule[1], rule[2]);
                    if (!text.equals(before)) {
                        count += countOccurrences(before, rule[1]);
                    }
                }
            }
            if (count > 0) {
                if (hasSel) {
                    editorPane.replaceSelection(text);
                } else {
                    int caretPos = editorPane.getCaretPosition();
                    editorPane.setText(text);
                    editorPane.setCaretPosition(Math.min(caretPos, text.length()));
                }
                dirty = true;
                updateTitle();
                updatePreview();
                JOptionPane.showMessageDialog(frame,
                    count + " substitution" + (count != 1 ? "s" : "") + " made.",
                    Messages.get("msg.zapGremlinsTitle"), JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, Messages.get("msg.noGremlinsFound"),
                    Messages.get("msg.zapGremlinsTitle"), JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private static int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) >= 0) {
            count++;
            idx += search.length();
        }
        return count;
    }

    /** Named HTML5 entities for common non-ASCII characters. */
    private static final java.util.Map<Character, String> HTML_ENTITIES = new java.util.LinkedHashMap<>();
    static {
        HTML_ENTITIES.put('\u00A0', "&nbsp;");
        HTML_ENTITIES.put('\u00A1', "&iexcl;");
        HTML_ENTITIES.put('\u00A2', "&cent;");
        HTML_ENTITIES.put('\u00A3', "&pound;");
        HTML_ENTITIES.put('\u00A4', "&curren;");
        HTML_ENTITIES.put('\u00A5', "&yen;");
        HTML_ENTITIES.put('\u00A6', "&brvbar;");
        HTML_ENTITIES.put('\u00A7', "&sect;");
        HTML_ENTITIES.put('\u00A8', "&uml;");
        HTML_ENTITIES.put('\u00A9', "&copy;");
        HTML_ENTITIES.put('\u00AA', "&ordf;");
        HTML_ENTITIES.put('\u00AB', "&laquo;");
        HTML_ENTITIES.put('\u00AC', "&not;");
        HTML_ENTITIES.put('\u00AD', "&shy;");
        HTML_ENTITIES.put('\u00AE', "&reg;");
        HTML_ENTITIES.put('\u00AF', "&macr;");
        HTML_ENTITIES.put('\u00B0', "&deg;");
        HTML_ENTITIES.put('\u00B1', "&plusmn;");
        HTML_ENTITIES.put('\u00B2', "&sup2;");
        HTML_ENTITIES.put('\u00B3', "&sup3;");
        HTML_ENTITIES.put('\u00B4', "&acute;");
        HTML_ENTITIES.put('\u00B5', "&micro;");
        HTML_ENTITIES.put('\u00B6', "&para;");
        HTML_ENTITIES.put('\u00B7', "&middot;");
        HTML_ENTITIES.put('\u00B8', "&cedil;");
        HTML_ENTITIES.put('\u00B9', "&sup1;");
        HTML_ENTITIES.put('\u00BA', "&ordm;");
        HTML_ENTITIES.put('\u00BB', "&raquo;");
        HTML_ENTITIES.put('\u00BC', "&frac14;");
        HTML_ENTITIES.put('\u00BD', "&frac12;");
        HTML_ENTITIES.put('\u00BE', "&frac34;");
        HTML_ENTITIES.put('\u00BF', "&iquest;");
        HTML_ENTITIES.put('\u00D7', "&times;");
        HTML_ENTITIES.put('\u00F7', "&divide;");
        HTML_ENTITIES.put('\u0192', "&fnof;");
        HTML_ENTITIES.put('\u2013', "&ndash;");
        HTML_ENTITIES.put('\u2014', "&mdash;");
        HTML_ENTITIES.put('\u2018', "&lsquo;");
        HTML_ENTITIES.put('\u2019', "&rsquo;");
        HTML_ENTITIES.put('\u201A', "&sbquo;");
        HTML_ENTITIES.put('\u201C', "&ldquo;");
        HTML_ENTITIES.put('\u201D', "&rdquo;");
        HTML_ENTITIES.put('\u201E', "&bdquo;");
        HTML_ENTITIES.put('\u2020', "&dagger;");
        HTML_ENTITIES.put('\u2021', "&Dagger;");
        HTML_ENTITIES.put('\u2022', "&bull;");
        HTML_ENTITIES.put('\u2026', "&hellip;");
        HTML_ENTITIES.put('\u2030', "&permil;");
        HTML_ENTITIES.put('\u2032', "&prime;");
        HTML_ENTITIES.put('\u2033', "&Prime;");
        HTML_ENTITIES.put('\u2039', "&lsaquo;");
        HTML_ENTITIES.put('\u203A', "&rsaquo;");
        HTML_ENTITIES.put('\u2044', "&frasl;");
        HTML_ENTITIES.put('\u20AC', "&euro;");
        HTML_ENTITIES.put('\u2122', "&trade;");
        HTML_ENTITIES.put('\u2190', "&larr;");
        HTML_ENTITIES.put('\u2191', "&uarr;");
        HTML_ENTITIES.put('\u2192', "&rarr;");
        HTML_ENTITIES.put('\u2193', "&darr;");
        HTML_ENTITIES.put('\u2194', "&harr;");
        HTML_ENTITIES.put('\u21B5', "&crarr;");
        HTML_ENTITIES.put('\u2200', "&forall;");
        HTML_ENTITIES.put('\u2202', "&part;");
        HTML_ENTITIES.put('\u2203', "&exist;");
        HTML_ENTITIES.put('\u2205', "&empty;");
        HTML_ENTITIES.put('\u2207', "&nabla;");
        HTML_ENTITIES.put('\u2208', "&isin;");
        HTML_ENTITIES.put('\u2209', "&notin;");
        HTML_ENTITIES.put('\u220B', "&ni;");
        HTML_ENTITIES.put('\u220F', "&prod;");
        HTML_ENTITIES.put('\u2211', "&sum;");
        HTML_ENTITIES.put('\u2212', "&minus;");
        HTML_ENTITIES.put('\u221A', "&radic;");
        HTML_ENTITIES.put('\u221E', "&infin;");
        HTML_ENTITIES.put('\u2220', "&ang;");
        HTML_ENTITIES.put('\u2227', "&and;");
        HTML_ENTITIES.put('\u2228', "&or;");
        HTML_ENTITIES.put('\u2229', "&cap;");
        HTML_ENTITIES.put('\u222A', "&cup;");
        HTML_ENTITIES.put('\u222B', "&int;");
        HTML_ENTITIES.put('\u2248', "&asymp;");
        HTML_ENTITIES.put('\u2260', "&ne;");
        HTML_ENTITIES.put('\u2261', "&equiv;");
        HTML_ENTITIES.put('\u2264', "&le;");
        HTML_ENTITIES.put('\u2265', "&ge;");
        HTML_ENTITIES.put('\u25CA', "&loz;");
        HTML_ENTITIES.put('\u2660', "&spades;");
        HTML_ENTITIES.put('\u2663', "&clubs;");
        HTML_ENTITIES.put('\u2665', "&hearts;");
        HTML_ENTITIES.put('\u2666', "&diams;");
    }

    /**
     * Encodes non-ASCII characters in the document as HTML entities.
     * Uses named entities where available (HTML5 standard), otherwise numeric code points.
     */
    private void htmlEncodeNonAscii() {
        int selStart = editorPane.getSelectionStart();
        int selEnd = editorPane.getSelectionEnd();
        boolean hasSel = selStart != selEnd;

        String text = hasSel ? editorPane.getSelectedText() : editorPane.getText();
        StringBuilder result = new StringBuilder(text.length());
        int count = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 127) {
                String entity = HTML_ENTITIES.get(c);
                if (entity != null) {
                    result.append(entity);
                } else {
                    result.append("&#x").append(String.format("%04X", (int) c)).append(";");
                }
                count++;
            } else {
                result.append(c);
            }
        }

        if (count > 0) {
            String newText = result.toString();
            if (hasSel) {
                editorPane.replaceSelection(newText);
            } else {
                int caretPos = editorPane.getCaretPosition();
                editorPane.setText(newText);
                editorPane.setCaretPosition(Math.min(caretPos, newText.length()));
            }
            dirty = true;
            updateTitle();
            updatePreview();
            JOptionPane.showMessageDialog(frame,
                count + " character" + (count != 1 ? "s" : "") + " encoded.",
                "HTML Encode", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(frame, Messages.get("msg.noGremlinsFound"),
                "HTML Encode", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Updates the Convert Line Endings menu item text to reflect the current format.
     * Shows Messages.get("menu.edit.convertLineEndings") when the document uses Unix format,
     * and Messages.get("menu.edit.convertLineEndings.unix") when it uses Windows format.
     */
    private void updateLineEndingsMenuItem() {
        if (windowsLineEndings) {
            convertLineEndingsItem.setText(Messages.get("menu.edit.convertLineEndings.unix"));
        } else {
            convertLineEndingsItem.setText(Messages.get("menu.edit.convertLineEndings"));
        }
    }

    /**
     * Checks whether the current file has been modified on disk by another program
     * since the last load or save. If a change is detected, prompts the user to
     * either reload the file (losing in-memory changes) or keep the current content.
     */
    private void checkFileChangedOnDisk() {
        if (currentFile == null || !currentFile.exists()) return;

        long diskModified = currentFile.lastModified();
        if (diskModified == 0 || diskModified == lastModifiedOnDisk) return;

        Object[] options = {Messages.get("msg.reload"), Messages.get("msg.keep")};
        int choice = JOptionPane.showOptionDialog(frame,
                Messages.get("msg.fileModified", currentFile.getName()),
                Messages.get("msg.fileModifiedTitle"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]);

        if (choice == 0) { // Reload
            try {
                String content = new String(Files.readAllBytes(currentFile.toPath()), StandardCharsets.UTF_8);
                windowsLineEndings = content.contains("\r\n");
                content = content.replace("\r\n", "\n").replace("\r", "\n");
                lastModifiedOnDisk = currentFile.lastModified();
                previewPanel.forceFullReload();
                editorPane.setText(content);
                editorPane.setCaretPosition(0);
                undoManager.discardAllEdits();
                dirty = false;
                updateTitle();
                updateLineEndingsMenuItem();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, Messages.get("msg.error.readFile", ex.getMessage()),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        } else {
            // User chose to keep current changes; update timestamp to avoid repeated prompts
            lastModifiedOnDisk = diskModified;
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
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.export") + " HTML", FileDialog.SAVE);
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
            String html = previewPanel.getStyledHtml(getRenderedHtml(), currentFile, preferences, true, editorPane.getText());
            try {
                Files.writeString(outFile.toPath(), html, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, Messages.get("msg.error", ex.getMessage()),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportPdf() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.export") + " PDF", FileDialog.SAVE);
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
                                    Messages.get("menu.file.export.pdf"), JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                }
            });
        }
    }

    private void exportTextBundle() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.export") + " TextBundle", FileDialog.SAVE);
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
                JOptionPane.showMessageDialog(frame, Messages.get("msg.error", ex.getMessage()),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Exports the current document as a TextPack (.textpack) file, which is a
     * zipped TextBundle containing text.md, info.json, and an assets/ folder.
     */
    private void exportTextPack() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.export") + " TextPack", FileDialog.SAVE);
        if (currentFile != null) {
            dialog.setDirectory(currentFile.getParent());
            String name = currentFile.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            dialog.setFile(name + ".textpack");
        } else {
            dialog.setFile("untitled.textpack");
        }
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File packFile = new File(dialog.getDirectory(), dialog.getFile());
            if (!packFile.getName().contains(".")) {
                packFile = new File(packFile.getAbsolutePath() + ".textpack");
            }
            try {
                // Create a temporary TextBundle directory, then zip it
                java.nio.file.Path tempDir = Files.createTempDirectory("textpack_export_");
                java.nio.file.Path bundleDir = tempDir;

                // Write info.json
                String info = "{\n  \"version\": 2,\n  \"type\": \"net.daringfireball.markdown\",\n"
                        + "  \"transient\": false,\n  \"creatorIdentifier\": \"com.glowingcat.purpleplatypus\"\n}";
                Files.writeString(bundleDir.resolve("info.json"), info, StandardCharsets.UTF_8);

                // Process markdown: copy images into assets/ and rewrite URLs
                String markdown = editorPane.getText();
                java.util.regex.Pattern imgPattern = java.util.regex.Pattern.compile(
                        "(!\\[[^\\]]*\\]\\()([^)]+)(\\))");
                java.util.regex.Matcher matcher = imgPattern.matcher(markdown);
                StringBuilder mdSb = new StringBuilder();
                java.nio.file.Path assetsDir = bundleDir.resolve("assets");
                boolean assetsCreated = false;

                while (matcher.find()) {
                    String imgPath = matcher.group(2).replace("%20", " ");
                    if (imgPath.startsWith("http://") || imgPath.startsWith("https://")) {
                        matcher.appendReplacement(mdSb, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
                        continue;
                    }
                    File srcFile = null;
                    if (currentFile != null && currentFile.getParentFile() != null) {
                        srcFile = new File(currentFile.getParentFile(), imgPath);
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
                        String relPath = imgPath;
                        if (relPath.startsWith("assets/")) {
                            relPath = relPath.substring("assets/".length());
                        }
                        java.nio.file.Path relativePath = java.nio.file.Path.of(relPath);
                        java.nio.file.Path destPath = assetsDir.resolve(relativePath);
                        Files.createDirectories(destPath.getParent());
                        Files.copy(srcFile.toPath(), destPath,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        String newUrl = "assets/" + relativePath.toString().replace(" ", "%20");
                        matcher.appendReplacement(mdSb,
                                java.util.regex.Matcher.quoteReplacement(matcher.group(1) + newUrl + matcher.group(3)));
                    } else {
                        matcher.appendReplacement(mdSb, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
                    }
                }
                matcher.appendTail(mdSb);

                // Write text.md
                Files.writeString(bundleDir.resolve("text.md"), mdSb.toString(), StandardCharsets.UTF_8);

                // Zip everything into the .textpack file
                zipDirectory(bundleDir, packFile.toPath());

                // Clean up temp directory
                deleteRecursive(tempDir);

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, Messages.get("msg.error", ex.getMessage()),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Unzips a TextPack (.textpack) file into the specified target directory.
     *
     * @param zipFile   the .textpack file to unzip
     * @param targetDir the directory to extract into
     * @throws IOException if an I/O error occurs
     */
    private void unzipTextPack(java.nio.file.Path zipFile, java.nio.file.Path targetDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                java.nio.file.Path destPath = targetDir.resolve(entry.getName()).normalize();
                // Guard against zip slip
                if (!destPath.startsWith(targetDir)) {
                    throw new IOException("Zip entry outside target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destPath);
                } else {
                    Files.createDirectories(destPath.getParent());
                    Files.copy(zis, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Zips a directory's contents into a ZIP file (for TextPack export).
     *
     * @param sourceDir the directory to zip
     * @param zipFile   the output ZIP file path
     * @throws IOException if an I/O error occurs
     */
    private void zipDirectory(java.nio.file.Path sourceDir, java.nio.file.Path zipFile) throws IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                Files.newOutputStream(zipFile))) {
            Files.walk(sourceDir)
                    .filter(path -> !path.equals(sourceDir))
                    .forEach(path -> {
                        try {
                            String entryName = sourceDir.relativize(path).toString();
                            if (Files.isDirectory(path)) {
                                zos.putNextEntry(new java.util.zip.ZipEntry(entryName + "/"));
                                zos.closeEntry();
                            } else {
                                zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                                Files.copy(path, zos);
                                zos.closeEntry();
                            }
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param dir the directory to delete
     * @throws IOException if an I/O error occurs
     */
    private void deleteRecursive(java.nio.file.Path dir) throws IOException {
        Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Best effort cleanup
                    }
                });
    }

    private void exportRtf() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.export") + " RTF", FileDialog.SAVE);
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
                JOptionPane.showMessageDialog(frame, Messages.get("msg.error", ex.getMessage()),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportDocx() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.export") + " Word", FileDialog.SAVE);
        if (currentFile != null) {
            dialog.setDirectory(currentFile.getParent());
            String name = currentFile.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            dialog.setFile(name + ".docx");
        } else {
            dialog.setFile("untitled.docx");
        }
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File outFile = new File(dialog.getDirectory(), dialog.getFile());
            if (!outFile.getName().contains(".")) {
                outFile = new File(outFile.getAbsolutePath() + ".docx");
            }
            try {
                String markdown = editorPane.getText();

                // Parse markdown using the same extensions as preview
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
                org.commonmark.node.Node document = parser.parse(markdown);

                DocxExporter exporter = new DocxExporter(currentFile);
                exporter.export(document, outFile);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, Messages.get("msg.error", ex.getMessage()),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportPlainText() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.export") + " Text", FileDialog.SAVE);
        if (currentFile != null) {
            dialog.setDirectory(currentFile.getParent());
            String name = currentFile.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            dialog.setFile(name + ".txt");
        } else {
            dialog.setFile("untitled.txt");
        }
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File outFile = new File(dialog.getDirectory(), dialog.getFile());
            if (!outFile.getName().contains(".")) {
                outFile = new File(outFile.getAbsolutePath() + ".txt");
            }
            try {
                // Strip markdown formatting to produce plain text
                String plainText = markdownToPlainText(editorPane.getText());
                Files.writeString(outFile.toPath(), plainText, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, Messages.get("msg.error", ex.getMessage()),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- Import methods ---

    private void importHtml() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.import") + " HTML", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".html") || name.toLowerCase().endsWith(".htm"));
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File inFile = new File(dialog.getDirectory(), dialog.getFile());
            try {
                String html = Files.readString(inFile.toPath(), StandardCharsets.UTF_8);
                String markdown = htmlToMarkdown(html);
                insertImportedText(markdown);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Error importing HTML: " + ex.getMessage(),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importPlainText() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.import") + " Text", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".txt"));
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File inFile = new File(dialog.getDirectory(), dialog.getFile());
            try {
                String text = Files.readString(inFile.toPath(), StandardCharsets.UTF_8);
                insertImportedText(text);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Error importing plain text: " + ex.getMessage(),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importRtf() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.import") + " RTF", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".rtf"));
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File inFile = new File(dialog.getDirectory(), dialog.getFile());
            try {
                String rtf = Files.readString(inFile.toPath(), StandardCharsets.UTF_8);
                String markdown = rtfToMarkdown(rtf);
                insertImportedText(markdown);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Error importing RTF: " + ex.getMessage(),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importDocx() {
        FileDialog dialog = new FileDialog(frame, Messages.get("menu.file.import") + " Word", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".docx"));
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File inFile = new File(dialog.getDirectory(), dialog.getFile());
            try {
                String markdown = docxToMarkdown(inFile);
                insertImportedText(markdown);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Error importing Word document: " + ex.getMessage(),
                        Messages.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Inserts imported text at the current caret position, or replaces
     * the document content if the document is empty.
     */
    private void insertImportedText(String text) {
        if (text == null || text.isEmpty()) return;
        if (editorPane.getText().trim().isEmpty()) {
            editorPane.setText(text);
            editorPane.setCaretPosition(0);
        } else {
            editorPane.replaceSelection(text);
        }
        markDirty();
    }

    /**
     * Converts HTML to Markdown by extracting text content with basic formatting.
     */
    private String htmlToMarkdown(String html) {
        // Remove DOCTYPE and head section
        html = html.replaceAll("(?si)<!DOCTYPE[^>]*>", "");
        html = html.replaceAll("(?si)<head>.*?</head>", "");
        html = html.replaceAll("(?si)<style[^>]*>.*?</style>", "");
        html = html.replaceAll("(?si)<script[^>]*>.*?</script>", "");

        // Convert headings
        html = html.replaceAll("(?i)<h1[^>]*>(.*?)</h1>", "\n# $1\n");
        html = html.replaceAll("(?i)<h2[^>]*>(.*?)</h2>", "\n## $1\n");
        html = html.replaceAll("(?i)<h3[^>]*>(.*?)</h3>", "\n### $1\n");
        html = html.replaceAll("(?i)<h4[^>]*>(.*?)</h4>", "\n#### $1\n");
        html = html.replaceAll("(?i)<h5[^>]*>(.*?)</h5>", "\n##### $1\n");
        html = html.replaceAll("(?i)<h6[^>]*>(.*?)</h6>", "\n###### $1\n");

        // Convert emphasis
        html = html.replaceAll("(?i)<strong[^>]*>(.*?)</strong>", "**$1**");
        html = html.replaceAll("(?i)<b[^>]*>(.*?)</b>", "**$1**");
        html = html.replaceAll("(?i)<em[^>]*>(.*?)</em>", "*$1*");
        html = html.replaceAll("(?i)<i[^>]*>(.*?)</i>", "*$1*");
        html = html.replaceAll("(?i)<del[^>]*>(.*?)</del>", "~~$1~~");
        html = html.replaceAll("(?i)<s[^>]*>(.*?)</s>", "~~$1~~");
        html = html.replaceAll("(?i)<u[^>]*>(.*?)</u>", "++$1++");
        html = html.replaceAll("(?i)<ins[^>]*>(.*?)</ins>", "++$1++");

        // Convert code
        html = html.replaceAll("(?i)<code[^>]*>(.*?)</code>", "`$1`");
        html = html.replaceAll("(?si)<pre[^>]*>(.*?)</pre>", "\n```\n$1\n```\n");

        // Convert links
        html = html.replaceAll("(?i)<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", "[$2]($1)");

        // Convert images
        html = html.replaceAll("(?i)<img[^>]*src=\"([^\"]+)\"[^>]*alt=\"([^\"]*?)\"[^>]*/?>", "![$2]($1)");
        html = html.replaceAll("(?i)<img[^>]*src=\"([^\"]+)\"[^>]*/?>", "![]($1)");

        // Convert line breaks and paragraphs
        html = html.replaceAll("(?i)<br\\s*/?>", "\n");
        html = html.replaceAll("(?i)<p[^>]*>", "\n");
        html = html.replaceAll("(?i)</p>", "\n");
        html = html.replaceAll("(?i)<hr[^>]*/?>", "\n---\n");

        // Convert lists
        html = html.replaceAll("(?i)<li[^>]*>", "- ");
        html = html.replaceAll("(?i)</li>", "\n");
        html = html.replaceAll("(?i)</?[ou]l[^>]*>", "\n");

        // Convert blockquotes
        html = html.replaceAll("(?i)<blockquote[^>]*>", "\n> ");
        html = html.replaceAll("(?i)</blockquote>", "\n");

        // Strip remaining HTML tags
        html = html.replaceAll("<[^>]+>", "");

        // Decode common HTML entities
        html = html.replace("&amp;", "&");
        html = html.replace("&lt;", "<");
        html = html.replace("&gt;", ">");
        html = html.replace("&quot;", "\"");
        html = html.replace("&apos;", "'");
        html = html.replace("&nbsp;", " ");
        html = html.replaceAll("&#(\\d+);", ""); // Strip numeric entities as fallback

        // Clean up excessive newlines
        html = html.replaceAll("\n{3,}", "\n\n");
        return html.trim();
    }

    /**
     * Converts RTF to Markdown by extracting text content with basic formatting.
     */
    private String rtfToMarkdown(String rtf) {
        StringBuilder sb = new StringBuilder();

        // Remove RTF header and font tables
        rtf = rtf.replaceAll("\\{\\\\fonttbl[^}]*\\}", "");
        rtf = rtf.replaceAll("\\{\\\\colortbl[^}]*\\}", "");
        rtf = rtf.replaceAll("\\{\\\\stylesheet[^}]*\\}", "");

        // Process the RTF content
        int i = 0;
        int len = rtf.length();
        boolean bold = false;
        boolean italic = false;

        while (i < len) {
            char c = rtf.charAt(i);

            if (c == '\\') {
                // RTF control word
                int start = i + 1;
                if (start >= len) { i++; continue; }

                // Check for escaped characters: \{, \}, \\
                char next = rtf.charAt(start);
                if (next == '{') { sb.append('{'); i = start + 1; continue; }
                if (next == '}') { sb.append('}'); i = start + 1; continue; }
                if (next == '\\') { sb.append('\\'); i = start + 1; continue; }

                while (start < len && Character.isLetter(rtf.charAt(start))) {
                    start++;
                }
                String word = rtf.substring(i + 1, start);

                // Skip optional numeric parameter
                int paramEnd = start;
                if (paramEnd < len && (rtf.charAt(paramEnd) == '-' || Character.isDigit(rtf.charAt(paramEnd)))) {
                    paramEnd++;
                    while (paramEnd < len && Character.isDigit(rtf.charAt(paramEnd))) {
                        paramEnd++;
                    }
                }
                // Skip optional space delimiter
                if (paramEnd < len && rtf.charAt(paramEnd) == ' ') {
                    paramEnd++;
                }

                switch (word) {
                    case "par" -> sb.append("\n");
                    case "tab" -> sb.append("\t");
                    case "b" -> {
                        if (!bold) { sb.append("**"); bold = true; }
                    }
                    case "b0" -> {
                        if (bold) { sb.append("**"); bold = false; }
                    }
                    case "i" -> {
                        if (!italic) { sb.append("*"); italic = true; }
                    }
                    case "i0" -> {
                        if (italic) { sb.append("*"); italic = false; }
                    }
                    case "line" -> sb.append("\n");
                    default -> {
                        // Skip unknown control words
                    }
                }
                i = paramEnd;
            } else if (c == '{' || c == '}') {
                i++;
            } else if (c == '\n' || c == '\r') {
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }

        // Close unclosed formatting
        if (bold) sb.append("**");
        if (italic) sb.append("*");

        // Clean up excessive newlines
        String result = sb.toString().replaceAll("\n{3,}", "\n\n");
        return result.trim();
    }

    /**
     * Converts a DOCX file to Markdown using Apache POI.
     */
    private String docxToMarkdown(File docxFile) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument(
                             new java.io.FileInputStream(docxFile))) {

            for (org.apache.poi.xwpf.usermodel.IBodyElement element : doc.getBodyElements()) {
                if (element instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph para) {
                    String style = para.getStyle();
                    String text = convertParagraphToMarkdown(para);

                    if (text.trim().isEmpty()) {
                        sb.append("\n");
                        continue;
                    }

                    // Apply heading style
                    if (style != null) {
                        switch (style) {
                            case "Heading1", "heading 1" -> text = "# " + text;
                            case "Heading2", "heading 2" -> text = "## " + text;
                            case "Heading3", "heading 3" -> text = "### " + text;
                            case "Heading4", "heading 4" -> text = "#### " + text;
                            case "Heading5", "heading 5" -> text = "##### " + text;
                            case "Heading6", "heading 6" -> text = "###### " + text;
                        }
                    }

                    sb.append(text).append("\n\n");
                } else if (element instanceof org.apache.poi.xwpf.usermodel.XWPFTable table) {
                    sb.append(convertTableToMarkdown(table));
                    sb.append("\n");
                }
            }
        }

        // Clean up excessive newlines
        String result = sb.toString().replaceAll("\n{3,}", "\n\n");
        return result.trim();
    }

    private String convertParagraphToMarkdown(org.apache.poi.xwpf.usermodel.XWPFParagraph para) {
        StringBuilder sb = new StringBuilder();
        for (org.apache.poi.xwpf.usermodel.XWPFRun run : para.getRuns()) {
            String text = run.getText(0);
            if (text == null) continue;

            boolean isBold = run.isBold();
            boolean isItalic = run.isItalic();
            boolean isStrike = run.isStrikeThrough();
            String fontFamily = run.getFontFamily();
            boolean isCode = fontFamily != null &&
                    (fontFamily.contains("Courier") || fontFamily.contains("Mono") || fontFamily.contains("Consolas"));

            if (isCode) {
                sb.append("`").append(text).append("`");
            } else {
                if (isBold && isItalic) sb.append("***");
                else if (isBold) sb.append("**");
                else if (isItalic) sb.append("*");
                if (isStrike) sb.append("~~");

                sb.append(text);

                if (isStrike) sb.append("~~");
                if (isBold && isItalic) sb.append("***");
                else if (isBold) sb.append("**");
                else if (isItalic) sb.append("*");
            }
        }
        return sb.toString();
    }

    private String convertTableToMarkdown(org.apache.poi.xwpf.usermodel.XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        java.util.List<org.apache.poi.xwpf.usermodel.XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return "";

        // First row as header
        org.apache.poi.xwpf.usermodel.XWPFTableRow headerRow = rows.get(0);
        sb.append("|");
        for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : headerRow.getTableCells()) {
            sb.append(" ").append(cell.getText().trim()).append(" |");
        }
        sb.append("\n|");
        for (int i = 0; i < headerRow.getTableCells().size(); i++) {
            sb.append("------|");
        }
        sb.append("\n");

        // Remaining rows as body
        for (int r = 1; r < rows.size(); r++) {
            org.apache.poi.xwpf.usermodel.XWPFTableRow row = rows.get(r);
            sb.append("|");
            for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                sb.append(" ").append(cell.getText().trim()).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Converts markdown text to plain text by stripping formatting markers.
     */
    private String markdownToPlainText(String markdown) {
        String text = markdown;
        // Remove heading markers
        text = text.replaceAll("(?m)^#{1,6}\\s+", "");
        // Remove bold/italic markers
        text = text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "$1");
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("\\*(.+?)\\*", "$1");
        text = text.replaceAll("__(.+?)__", "$1");
        text = text.replaceAll("_(.+?)_", "$1");
        // Remove strikethrough
        text = text.replaceAll("~~(.+?)~~", "$1");
        // Remove inline code backticks
        text = text.replaceAll("`([^`]+)`", "$1");
        // Remove links: [text](url) -> text
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        // Remove images: ![alt](url) -> alt
        text = text.replaceAll("!\\[([^\\]]*?)\\]\\([^)]+\\)", "$1");
        // Remove fenced code block markers
        text = text.replaceAll("(?m)^```.*$", "");
        // Remove horizontal rules
        text = text.replaceAll("(?m)^(---+|\\*\\*\\*+|___+)\\s*$", "");
        // Remove HTML tags
        text = text.replaceAll("<[^>]+>", "");
        // Remove task list markers
        text = text.replaceAll("(?m)^(\\s*)- \\[[xX ]\\] ", "$1");
        // Remove unordered list markers
        text = text.replaceAll("(?m)^(\\s*)[-*+] ", "$1");
        // Remove blockquote markers
        text = text.replaceAll("(?m)^>\\s?", "");
        return text;
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
        org.commonmark.renderer.html.HtmlRenderer renderer = org.commonmark.renderer.html.HtmlRenderer.builder()
                .extensions(extensions)
                .nodeRendererFactory(new FigureNodeRenderer.Factory())
                .build();
        org.commonmark.node.Node document = parser.parse(markdown);
        String html = renderer.render(document);

        // Mark links where the display text matches the href URL
        html = PreviewPanel.markUrlLinks(html);

        // Replace non-BMP characters (emoji) with Twemoji SVG images
        html = com.glowingcat.aichat.EmojiReplacer.replaceEmoji(html);

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
                filePathLabel.setText(Messages.get("msg.untitled"));
            }
        }
    }

    /**
     * Updates the stats label in the toolbar with the current document's
     * line count, word count, and character count.
     */
    // Debounce timer for stats updates
    private javax.swing.Timer statsDebounceTimer;
    private static final int STATS_DEBOUNCE_MS = 500;

    private void updateStats() {
        if (statsLabel == null) return;
        if (statsDebounceTimer != null) {
            statsDebounceTimer.restart();
        } else {
            statsDebounceTimer = new javax.swing.Timer(STATS_DEBOUNCE_MS, e -> {
                String text = editorPane.getText();
                int lines = editorPane.getLineCount();
                int chars = text.length();
                int words = 0;
                if (!text.isEmpty()) {
                    String trimmed = text.trim();
                    if (!trimmed.isEmpty()) {
                        words = trimmed.split("\\s+").length;
                    }
                }
                statsLabel.setText("L: " + lines + "  W: " + words + "  C: " + chars);
            });
            statsDebounceTimer.setRepeats(false);
            statsDebounceTimer.start();
        }
    }

    public boolean confirmClose() {
        if (!dirty) return true;
        String filename = currentFile != null ? currentFile.getName() : Messages.get("msg.untitled");
        int choice = JOptionPane.showOptionDialog(frame,
                "\"" + filename + "\" has unsaved changes. Do you want to save before closing?",
                Messages.get("msg.unsavedTitle"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new String[]{Messages.get("msg.save"), Messages.get("msg.dontSave"), Messages.get("msg.cancel")}, "Save");
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

        // If selection includes a trailing newline, back up so we don't
        // expand into the next line
        if (end > start && end <= fullText.length() && fullText.charAt(end - 1) == '\n') {
            end--;
        }

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
        String imgWidth = "";
        boolean center = false;
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
                    // Check for {width=...} attribute after the closing paren
                    int attrEnd = absEnd;
                    int regionAttrStart = pc + 1;
                    if (regionAttrStart < region.length() && region.charAt(regionAttrStart) == '{') {
                        int braceClose = region.indexOf('}', regionAttrStart);
                        if (braceClose >= 0) {
                            String attrs = region.substring(regionAttrStart + 1, braceClose);
                            // Parse width=... from attributes
                            for (String attr : attrs.split("\\s+")) {
                                if (attr.startsWith("width=")) {
                                    imgWidth = attr.substring(6);
                                }
                            }
                            attrEnd = searchFrom + braceClose + 1;
                        }
                    }
                    // Check if wrapped in <div ...>...</div>
                    int divStart = absStart;
                    int divEnd = attrEnd;
                    // Look backwards for <div
                    String before = fullText.substring(Math.max(0, absStart - 100), absStart);
                    int divOpen = before.lastIndexOf("<div");
                    if (divOpen >= 0) {
                        int divOpenAbs = Math.max(0, absStart - 100) + divOpen;
                        int gtPos = fullText.indexOf('>', divOpenAbs);
                        if (gtPos >= 0 && gtPos < absStart) {
                            // Check for style="text-align: center;"
                            String divTag = fullText.substring(divOpenAbs, gtPos + 1);
                            if (divTag.contains("text-align: center") || divTag.contains("text-align:center")) {
                                center = true;
                            }
                            divStart = divOpenAbs;
                        }
                    }
                    // Look forwards for </div>
                    String after = fullText.substring(attrEnd, Math.min(fullText.length(), attrEnd + 50));
                    int divClose = after.indexOf("</div>");
                    if (divClose >= 0 && divStart < absStart) {
                        divEnd = attrEnd + divClose + 6;
                    }

                    if (selStart <= divEnd && selEnd >= divStart) {
                        altText = region.substring(bb + 2, bc);
                        imgPath = region.substring(bc + 2, pc);
                        replaceStart = divStart;
                        replaceEnd = divEnd;
                        break;
                    }
                }
            }
            idx = bb + 1;
        }

        ImageDialog dialog = new ImageDialog(frame, altText, imgPath, imgWidth, center, currentFile);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            String imgMarkdown = "![" + dialog.getAltText() + "](" + dialog.getImagePath() + ")";
            String width = dialog.getImageWidth();
            if (!width.isEmpty()) {
                imgMarkdown += "{width=" + width + "}";
            }
            boolean dialogCenter = dialog.isCenter();

            String markdown;
            if (dialogCenter) {
                // Center: wrap in div
                markdown = "<div style=\"text-align: center;\">\n\n" + imgMarkdown + "\n\n</div>";
            } else {
                markdown = imgMarkdown;
            }
            editorPane.setSelectionStart(replaceStart);
            editorPane.setSelectionEnd(replaceEnd);
            editorPane.replaceSelection(markdown);
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

    private void gotoLine() {
        int totalLines = editorPane.getLineCount();
        String input = JOptionPane.showInputDialog(frame,
            Messages.get("msg.goToLine.prompt", totalLines), Messages.get("msg.goToLine.title"), JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.trim().isEmpty()) return;
        try {
            int line = Integer.parseInt(input.trim());
            if (line < 1) line = 1;
            if (line > totalLines) line = totalLines;
            int offset = editorPane.getLineStartOffset(line - 1);
            editorPane.setCaretPosition(offset);
            editorPane.requestFocusInWindow();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, Messages.get("msg.invalidLineNumber"), Messages.get("menu.search.goToLine"), JOptionPane.WARNING_MESSAGE);
        } catch (javax.swing.text.BadLocationException ex) {
            // Silently fail
        }
    }

    private void findInPreview() {
        String selected = editorPane.getSelectedText();
        if (selected == null || selected.trim().isEmpty()) return;
        // Strip markdown markup for plain text search
        String plainText = stripMarkup(selected.trim());
        previewPanel.findInPreview(plainText);
    }

    private void findInSource(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String searchText = text.trim();
        String content = editorPane.getText();
        // Search for the plain text in the source
        int idx = content.indexOf(searchText);
        if (idx < 0) {
            // Try case-insensitive
            idx = content.toLowerCase().indexOf(searchText.toLowerCase());
        }
        if (idx >= 0) {
            editorPane.setCaretPosition(idx);
            editorPane.moveCaretPosition(idx + searchText.length());
            editorPane.requestFocusInWindow();
        }
    }

    /** Strips common markdown markup from text for plain text matching. */
    private static String stripMarkup(String text) {
        // Remove bold/italic markers
        String result = text.replaceAll("\\*{1,3}([^*]+)\\*{1,3}", "$1");
        // Remove strikethrough
        result = result.replaceAll("~~([^~]+)~~", "$1");
        // Remove inline code
        result = result.replaceAll("`([^`]+)`", "$1");
        // Remove link syntax [text](url)
        result = result.replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1");
        // Remove image syntax ![alt](url)
        result = result.replaceAll("!\\[([^]]*)]\\([^)]+\\)", "$1");
        // Remove heading markers
        result = result.replaceAll("^#{1,6}\\s+", "");
        return result.trim();
    }

    private void showFindDialog() {
        if (findDialog == null) findDialog = new FindDialog(frame, editorPane, preferences);
        findDialog.setVisible(true);
        findDialog.toFront();
        findDialog.focusSearchField();
    }

    private void showReplaceDialog() {
        if (replaceDialog == null) replaceDialog = new ReplaceDialog(frame, editorPane, preferences);
        replaceDialog.setVisible(true);
        replaceDialog.toFront();
        replaceDialog.focusSearchField();
    }

    public void showAboutDialog() {
        AboutDialog.show(frame, preferences);
    }

    public void showPreferencesDialog() {
        PreferencesDialog dialog = new PreferencesDialog(frame, preferences);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            String oldUiLang = preferences.getUiLanguage();
            dialog.applyTo(preferences);
            preferences.save();
            editorPanel.applyPreferences(preferences);
            previewPanel.forceFullReload();
            updatePreview();
            // Notify user if UI language changed (requires restart)
            String newUiLang = preferences.getUiLanguage();
            if (!oldUiLang.equals(newUiLang)) {
                JOptionPane.showMessageDialog(frame,
                        Messages.get("dialog.prefs.restartRequired"),
                        Messages.get("dialog.prefs.title"),
                        JOptionPane.INFORMATION_MESSAGE);
            }
            // Update spell check language if changed
            if (spellCheckController != null) {
                String newLang = preferences.getSpellCheckLanguage();
                if (!newLang.equals(spellCheckController.getLanguage())) {
                    spellCheckController.setLanguage(newLang);
                }
            }
            // Update toolbar toggle button highlight colors
            Color hlColor = preferences.getButtonHighlightColorObj();
            if (wordWrapToggle.isSelected()) wordWrapToggle.setBackground(hlColor);
            if (hiddenCharsToggle.isSelected()) hiddenCharsToggle.setBackground(hlColor);
            if (spellCheckToggle.isSelected()) spellCheckToggle.setBackground(hlColor);
            if (syncScrollToggle.isSelected()) syncScrollToggle.setBackground(hlColor);
            if (previewToggle.isSelected()) previewToggle.setBackground(hlColor);
            if (aiToggle.isSelected()) aiToggle.setBackground(hlColor);
        }
    }

    public void showAiSettingsDialog() {
        AIChatPreferencesDialog dialog = new AIChatPreferencesDialog(frame, aiPreferences);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            dialog.applyTo(aiPreferences);
            aiPreferences.setUserPromptColor(dialog.getSelectedUserPromptColor());
            aiPreferences.setUserTextColor(dialog.getSelectedUserTextColor());
            aiPreferences.setAiResponseColor(dialog.getSelectedAiResponseColor());
            aiPreferences.setAiTextColor(dialog.getSelectedAiTextColor());
            aiPreferences.save();
            if (aiChatPanel != null) {
                aiChatPanel.setLlmClient(LLMClientFactory.create(aiPreferences));
                aiChatPanel.updateFont();
            }
        }
    }

    public void showLicenseDialog() {
        LicenseDialog.show(frame, preferences);
    }

    // --- Static helpers ---

    public static void openFileInWindow(File file) {
        try {
            File actualFile = file;
            boolean isTextPack = false;
            // If it's a .textbundle directory, open text.md inside it
            if (file.isDirectory() && file.getName().toLowerCase().endsWith(".textbundle")) {
                actualFile = new File(file, "text.md");
                if (!actualFile.exists()) {
                    // Try text.markdown as fallback
                    actualFile = new File(file, "text.markdown");
                }
                if (!actualFile.exists()) return;
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".textpack")) {
                // Unzip TextPack to temp directory
                java.nio.file.Path tempDir = Files.createTempDirectory("textpack_");
                try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                        Files.newInputStream(file.toPath()))) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        java.nio.file.Path destPath = tempDir.resolve(entry.getName()).normalize();
                        if (!destPath.startsWith(tempDir)) continue; // zip slip guard
                        if (entry.isDirectory()) {
                            Files.createDirectories(destPath);
                        } else {
                            Files.createDirectories(destPath.getParent());
                            Files.copy(zis, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        zis.closeEntry();
                    }
                }
                actualFile = tempDir.resolve("text.md").toFile();
                if (!actualFile.exists()) actualFile = tempDir.resolve("text.markdown").toFile();
                if (!actualFile.exists()) return;
                isTextPack = true;
            }
            String content = new String(Files.readAllBytes(actualFile.toPath()), StandardCharsets.UTF_8);
            final File fileToOpen = actualFile;
            final boolean textPack = isTextPack;
            // Always open in a new window unless an empty untitled window exists
            for (EditorWindow instance : openInstances) {
                if (!instance.dirty && instance.currentFile == null) {
                    instance.loadFileContent(fileToOpen, content);
                    if (textPack) {
                        instance.textPackSource = true;
                        instance.saveItem.setEnabled(false);
                    }
                    return;
                }
            }
            EditorWindow newWindow = new EditorWindow();
            newWindow.loadFileContent(fileToOpen, content);
            if (textPack) {
                newWindow.textPackSource = true;
                newWindow.saveItem.setEnabled(false);
            }
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
