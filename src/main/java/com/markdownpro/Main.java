/*
 * (c) 2026 The Boeing Company
 */

/**
 * Main.java
 *
 * Entry point and primary UI class for the MarkdownPro application.
 * Creates a Swing-based split-pane markdown editor with a live HTML preview,
 * line number gutter, file operations, undo/redo, clipboard support, and
 * find/replace functionality.
 */
package com.markdownpro;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.Element;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The main application class for MarkdownPro.
 * <p>
 * Constructs the GUI on the Event Dispatch Thread and manages the editor state
 * including the current file, markdown parser, and undo history.
 */
public class Main {

    private JFrame frame;
    private JTextArea editorPane;
    private JEditorPane previewPane;
    private File currentFile;
    private final Parser parser;
    private final HtmlRenderer renderer;
    private final UndoManager undoManager = new UndoManager();
    private Preferences preferences;

    /**
     * Constructs the application, initializing the markdown parser and renderer,
     * loading user preferences, then building and displaying the GUI.
     */
    public Main() {
        parser = Parser.builder().build();
        renderer = HtmlRenderer.builder().build();
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
        frame = new JFrame("MarkdownPro");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 700);

        // Menu bar
        JMenuBar menuBar = new JMenuBar();

        // MarkdownPro menu
        JMenu appMenu = new JMenu("MarkdownPro");

        JMenuItem aboutItem = new JMenuItem("About MarkdownPro...");
        aboutItem.addActionListener(e -> showAboutDialog());

        JMenuItem prefsItem = new JMenuItem("Preferences...");
        prefsItem.addActionListener(e -> showPreferencesDialog());

        appMenu.add(aboutItem);
        appMenu.add(prefsItem);
        menuBar.add(appMenu);

        // File menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem newItem = new JMenuItem("New");
        newItem.setAccelerator(KeyStroke.getKeyStroke("control N"));
        newItem.addActionListener(e -> newFile());

        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke("control O"));
        openItem.addActionListener(e -> openFile());

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke("control S"));
        saveItem.addActionListener(e -> saveFile());

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke("control shift S"));
        saveAsItem.addActionListener(e -> saveFileAs());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // Edit menu
        JMenu editMenu = new JMenu("Edit");

        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke("control Z"));
        undoItem.addActionListener(e -> {
            if (undoManager.canUndo()) {
                undoManager.undo();
            }
        });

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke("control Y"));
        redoItem.addActionListener(e -> {
            if (undoManager.canRedo()) {
                undoManager.redo();
            }
        });

        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.setAccelerator(KeyStroke.getKeyStroke("control X"));
        cutItem.addActionListener(e -> editorPane.cut());

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setAccelerator(KeyStroke.getKeyStroke("control C"));
        copyItem.addActionListener(e -> editorPane.copy());

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.setAccelerator(KeyStroke.getKeyStroke("control V"));
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
        findItem.setAccelerator(KeyStroke.getKeyStroke("control F"));
        findItem.addActionListener(e -> showFindDialog());

        JMenuItem replaceItem = new JMenuItem("Replace...");
        replaceItem.setAccelerator(KeyStroke.getKeyStroke("control H"));
        replaceItem.addActionListener(e -> showReplaceDialog());

        searchMenu.add(findItem);
        searchMenu.add(replaceItem);
        menuBar.add(searchMenu);

        frame.setJMenuBar(menuBar);

        // Editor pane (left) - plain text area for markdown source
        editorPane = new JTextArea();
        editorPane.setFont(new Font(preferences.getEditorFontFamily(), Font.PLAIN, preferences.getEditorFontSize()));
        editorPane.setLineWrap(true);
        editorPane.setWrapStyleWord(true);
        editorPane.getDocument().addUndoableEditListener(undoManager);
        JScrollPane editorScroll = new JScrollPane(editorPane);
        editorScroll.setBorder(BorderFactory.createTitledBorder("Markdown Source"));

        // Line number panel
        LineNumberPanel lineNumbers = new LineNumberPanel(editorPane);
        editorScroll.setRowHeaderView(lineNumbers);

        // Preview pane (right) - HTML rendering of the markdown
        previewPane = new JEditorPane();
        previewPane.setContentType("text/html");
        previewPane.setEditable(false);
        JScrollPane previewScroll = new JScrollPane(previewPane);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Preview"));

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorScroll, previewScroll);
        splitPane.setDividerLocation(600);
        splitPane.setResizeWeight(0.5);

        frame.add(splitPane, BorderLayout.CENTER);

        // Live preview: update HTML whenever the markdown source changes
        editorPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePreview();
            }
        });

        // Set initial content
        editorPane.setText("# Welcome to MarkdownPro\n\nStart typing your markdown here.\n\n"
                + "## Features\n\n"
                + "- **Live preview** as you type\n"
                + "- Open and save `.md` files\n"
                + "- Split pane editor\n");

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * Parses the current editor content as Markdown and renders it as styled HTML
     * in the preview pane. Uses the current preview font preferences for styling.
     * Called automatically on every document change.
     */
    private void updatePreview() {
        String markdown = editorPane.getText();
        Node document = parser.parse(markdown);
        String html = renderer.render(document);

        String fontFamily = preferences.getPreviewFontFamily();
        int fontSize = preferences.getPreviewFontSize();

        String styledHtml = "<html><head><style>"
                + "body { font-family: '" + fontFamily + "', sans-serif; font-size: " + fontSize + "pt; padding: 10px; line-height: 1.6; }"
                + "h1, h2, h3 { color: #333; }"
                + "code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; }"
                + "pre { background: #f4f4f4; padding: 10px; border-radius: 5px; }"
                + "blockquote { border-left: 4px solid #ccc; margin-left: 0; padding-left: 16px; color: #666; }"
                + "</style></head><body>" + html + "</body></html>";

        previewPane.setText(styledHtml);
        previewPane.setCaretPosition(0);
    }

    /**
     * Resets the editor to an empty state for a new file.
     * Clears the current file reference and resets the window title.
     */
    private void newFile() {
        currentFile = null;
        editorPane.setText("");
        frame.setTitle("MarkdownPro");
    }

    /**
     * Displays a file chooser dialog and loads the selected markdown file
     * into the editor. Supports .md, .markdown, and .txt extensions.
     */
    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Markdown Files", "md", "markdown", "txt"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            currentFile = chooser.getSelectedFile();
            try {
                String content = new String(Files.readAllBytes(currentFile.toPath()), StandardCharsets.UTF_8);
                editorPane.setText(content);
                editorPane.setCaretPosition(0);
                frame.setTitle("MarkdownPro - " + currentFile.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Error reading file: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
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
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Markdown Files", "md", "markdown"));
        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            currentFile = chooser.getSelectedFile();
            if (!currentFile.getName().contains(".")) {
                currentFile = new File(currentFile.getAbsolutePath() + ".md");
            }
            writeFile(currentFile);
            frame.setTitle("MarkdownPro - " + currentFile.getName());
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
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Error saving file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Application entry point. Launches the GUI on the Swing Event Dispatch Thread.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }

    private FindDialog findDialog;
    private ReplaceDialog replaceDialog;

    /**
     * Shows the About dialog with version information about MarkdownPro.
     */
    private void showAboutDialog() {
        JOptionPane.showMessageDialog(frame,
                "MarkdownPro\n"
                        + "Version 1.0\n\n"
                        + "A lightweight desktop Markdown editor\n"
                        + "with live preview.\n\n"
                        + "\u00a9 2026 The Boeing Company",
                "About MarkdownPro",
                JOptionPane.INFORMATION_MESSAGE);
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
