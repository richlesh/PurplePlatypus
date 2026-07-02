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

public class Main {

    private JFrame frame;
    private JTextArea editorPane;
    private JEditorPane previewPane;
    private File currentFile;
    private final Parser parser;
    private final HtmlRenderer renderer;
    private final UndoManager undoManager = new UndoManager();

    public Main() {
        parser = Parser.builder().build();
        renderer = HtmlRenderer.builder().build();
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        frame = new JFrame("MarkdownPro");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 700);

        // Menu bar
        JMenuBar menuBar = new JMenuBar();
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
        editorPane.setFont(new Font("Monospaced", Font.PLAIN, 14));
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

    private void updatePreview() {
        String markdown = editorPane.getText();
        Node document = parser.parse(markdown);
        String html = renderer.render(document);

        // Wrap in basic HTML with some styling
        String styledHtml = "<html><head><style>"
                + "body { font-family: 'Segoe UI', Arial, sans-serif; padding: 10px; line-height: 1.6; }"
                + "h1, h2, h3 { color: #333; }"
                + "code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; }"
                + "pre { background: #f4f4f4; padding: 10px; border-radius: 5px; }"
                + "blockquote { border-left: 4px solid #ccc; margin-left: 0; padding-left: 16px; color: #666; }"
                + "</style></head><body>" + html + "</body></html>";

        previewPane.setText(styledHtml);
        previewPane.setCaretPosition(0);
    }

    private void newFile() {
        currentFile = null;
        editorPane.setText("");
        frame.setTitle("MarkdownPro");
    }

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

    private void saveFile() {
        if (currentFile == null) {
            saveFileAs();
        } else {
            writeFile(currentFile);
        }
    }

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

    private void writeFile(File file) {
        try {
            Files.write(file.toPath(), editorPane.getText().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Error saving file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }

    private FindDialog findDialog;
    private ReplaceDialog replaceDialog;

    private void showFindDialog() {
        if (findDialog == null) {
            findDialog = new FindDialog(frame, editorPane);
        }
        findDialog.setVisible(true);
        findDialog.toFront();
    }

    private void showReplaceDialog() {
        if (replaceDialog == null) {
            replaceDialog = new ReplaceDialog(frame, editorPane);
        }
        replaceDialog.setVisible(true);
        replaceDialog.toFront();
    }

    /**
     * A panel that displays line numbers alongside a JTextArea.
     */
    private static class LineNumberPanel extends JComponent implements DocumentListener, PropertyChangeListener {
        private final JTextArea textArea;
        private int lastDigits;

        public LineNumberPanel(JTextArea textArea) {
            this.textArea = textArea;
            setFont(textArea.getFont());
            setBackground(new Color(240, 240, 240));
            setForeground(new Color(128, 128, 128));
            textArea.getDocument().addDocumentListener(this);
            textArea.addPropertyChangeListener("font", this);
            updateWidth();
        }

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

        @Override
        public void insertUpdate(DocumentEvent e) {
            updateWidth();
            repaint();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            updateWidth();
            repaint();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            repaint();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            setFont(textArea.getFont());
            updateWidth();
            repaint();
        }
    }
}
