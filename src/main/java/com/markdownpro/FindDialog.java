/*
 * (c) 2026 The Boeing Company
 */

/**
 * FindDialog.java
 *
 * Provides a non-modal Find dialog for the MarkdownPro editor.
 * Supports Find Next, Find All (with a clickable results window), and Count
 * operations with options for case sensitivity, wrap-around, search direction,
 * and searching within a remembered selection.
 * <p>
 * Designed as a base class that can be extended (see {@link ReplaceDialog})
 * via template methods for the top panel, options panel, and button panel.
 */
package com.markdownpro;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A non-modal Find dialog with Find Next, Find All, Count,
 * and search option checkboxes.
 * <p>
 * Subclasses can override {@link #createTopPanel()}, {@link #createOptionsPanel()},
 * and {@link #createButtonPanel()} to customize the dialog layout while reusing
 * the core search logic.
 */
public class FindDialog extends JDialog {

    /** The parent frame, used for positioning and bringing to front. */
    protected final JFrame ownerFrame;

    /** The text area being searched. */
    protected final JTextArea textArea;

    /** Text field where the user enters the search query. */
    protected JTextField searchField;

    /** Checkbox to restrict search to the remembered selection range. */
    protected JCheckBox findInSelectionBox;

    /** Checkbox to reverse the search direction (search backwards from caret). */
    protected JCheckBox searchBackwardsBox;

    /** Checkbox for case-sensitive matching. */
    protected JCheckBox matchCaseBox;

    /** Checkbox to wrap around to the start/end of the search region. */
    protected JCheckBox wrapAroundBox;

    /** Start offset of the remembered selection for "Find in selection". */
    protected int selectionStart = -1;

    /** End offset of the remembered selection for "Find in selection". */
    protected int selectionEnd = -1;

    /**
     * Creates a Find dialog with the default title "Find".
     *
     * @param owner    the parent frame
     * @param textArea the text area to search within
     */
    public FindDialog(JFrame owner, JTextArea textArea) {
        this(owner, textArea, "Find");
    }

    /**
     * Creates a Find dialog with a custom title. This constructor is used by subclasses
     * to provide their own dialog title (e.g., "Replace").
     *
     * @param owner    the parent frame
     * @param textArea the text area to search within
     * @param title    the dialog window title
     */
    protected FindDialog(JFrame owner, JTextArea textArea, String title) {
        super(owner, title, false);
        this.ownerFrame = owner;
        this.textArea = textArea;

        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: search field(s)
        add(createTopPanel(), BorderLayout.NORTH);

        // Left: checkboxes
        add(createOptionsPanel(), BorderLayout.CENTER);

        // Right: buttons
        add(createButtonPanel(), BorderLayout.EAST);

        // Enter key triggers Find Next
        searchField.addActionListener(e -> findNext());

        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    /**
     * Creates the top panel containing the search text field.
     * Subclasses can override this to add additional fields (e.g., a replace field).
     *
     * @return the configured top panel
     */
    protected JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.add(new JLabel("Find:"), BorderLayout.WEST);
        topPanel.add(searchField = new JTextField(24), BorderLayout.CENTER);
        return topPanel;
    }

    /**
     * Creates the options panel with search modifier checkboxes:
     * Find in Selection, Search Backwards, Match Case, and Wrap Around.
     * <p>
     * When "Find in selection" is checked, the current editor selection boundaries
     * are captured and remembered for subsequent searches.
     *
     * @return the configured options panel
     */
    protected JPanel createOptionsPanel() {
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Options"));

        findInSelectionBox = new JCheckBox("Find in selection");
        searchBackwardsBox = new JCheckBox("Search Backwards");
        matchCaseBox = new JCheckBox("Match Case");
        wrapAroundBox = new JCheckBox("Wrap Around");
        wrapAroundBox.setSelected(true);

        // When "Find in selection" is checked, capture the current selection
        findInSelectionBox.addActionListener(e -> {
            if (findInSelectionBox.isSelected()) {
                int start = textArea.getSelectionStart();
                int end = textArea.getSelectionEnd();
                if (start != end) {
                    selectionStart = start;
                    selectionEnd = end;
                } else {
                    findInSelectionBox.setSelected(false);
                    JOptionPane.showMessageDialog(this,
                            "Please select text in the editor first.",
                            "Find in Selection", JOptionPane.WARNING_MESSAGE);
                }
            } else {
                selectionStart = -1;
                selectionEnd = -1;
            }
        });

        optionsPanel.add(findInSelectionBox);
        optionsPanel.add(searchBackwardsBox);
        optionsPanel.add(matchCaseBox);
        optionsPanel.add(wrapAroundBox);
        return optionsPanel;
    }

    /**
     * Creates the button panel with Find Next, Find All, and Count buttons.
     * Subclasses can override this to provide different buttons (e.g., Replace).
     *
     * @return the configured button panel
     */
    protected JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        JButton findNextBtn = createButton("Find Next");
        JButton findAllBtn = createButton("Find All");
        JButton countBtn = createButton("Count");

        findNextBtn.addActionListener(e -> findNext());
        findAllBtn.addActionListener(e -> findAll());
        countBtn.addActionListener(e -> count());

        buttonPanel.add(findNextBtn);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(findAllBtn);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(countBtn);
        return buttonPanel;
    }

    /**
     * Creates a button with a standardized size for consistent dialog layout.
     *
     * @param text the button label
     * @return the configured button
     */
    protected JButton createButton(String text) {
        JButton btn = new JButton(text);
        Dimension btnSize = new Dimension(130, 28);
        btn.setMaximumSize(btnSize);
        btn.setPreferredSize(btnSize);
        return btn;
    }

    /**
     * Populates the bounds array with the start and end offsets of the search region.
     * If "Find in selection" is active with a valid remembered range, uses that range;
     * otherwise uses the full document.
     *
     * @param bounds a two-element array where bounds[0] receives the start offset
     *              and bounds[1] receives the end offset
     */
    protected void getSearchBounds(int[] bounds) {
        if (findInSelectionBox.isSelected() && selectionStart >= 0 && selectionEnd > selectionStart) {
            bounds[0] = selectionStart;
            bounds[1] = selectionEnd;
        } else {
            bounds[0] = 0;
            bounds[1] = textArea.getText().length();
        }
    }

    /**
     * Finds and selects the next occurrence of the search text from the current
     * caret position. Respects all search options (case, direction, wrap, selection).
     * Displays a message if no match is found.
     */
    public void findNext() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;

        String content = textArea.getText();
        boolean matchCase = matchCaseBox.isSelected();
        boolean backwards = searchBackwardsBox.isSelected();
        boolean wrapAround = wrapAroundBox.isSelected();

        int[] bounds = new int[2];
        getSearchBounds(bounds);
        int regionStart = bounds[0];
        int regionEnd = bounds[1];

        String searchIn = content.substring(regionStart, regionEnd);
        String compareIn = matchCase ? searchIn : searchIn.toLowerCase();
        String compareText = matchCase ? searchText : searchText.toLowerCase();

        int caretPos = textArea.getCaretPosition() - regionStart;
        int index;

        if (backwards) {
            int fromIndex = caretPos - 1;
            if (textArea.getSelectionStart() != textArea.getSelectionEnd()) {
                fromIndex = textArea.getSelectionStart() - regionStart - 1;
            }
            fromIndex = Math.max(0, fromIndex);
            index = compareIn.lastIndexOf(compareText, fromIndex);
            if (index < 0 && wrapAround) {
                index = compareIn.lastIndexOf(compareText);
            }
        } else {
            int fromIndex = caretPos;
            if (textArea.getSelectionStart() != textArea.getSelectionEnd()) {
                fromIndex = textArea.getSelectionEnd() - regionStart;
            }
            fromIndex = Math.max(0, Math.min(fromIndex, compareIn.length()));
            index = compareIn.indexOf(compareText, fromIndex);
            if (index < 0 && wrapAround) {
                index = compareIn.indexOf(compareText);
            }
        }

        if (index >= 0) {
            int start = index + regionStart;
            int end = start + searchText.length();
            textArea.setSelectionStart(start);
            textArea.setSelectionEnd(end);
            textArea.requestFocusInWindow();
        } else {
            JOptionPane.showMessageDialog(this, "Text not found.",
                    "Find", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Finds all occurrences of the search text within the search region and
     * displays them in a new results window. Each matching line is shown with
     * its line number and the matched text highlighted in yellow.
     */
    protected void findAll() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;

        String content = textArea.getText();
        boolean matchCase = matchCaseBox.isSelected();

        int[] bounds = new int[2];
        getSearchBounds(bounds);
        int regionStart = bounds[0];
        int regionEnd = bounds[1];

        String searchIn = content.substring(regionStart, regionEnd);
        String compareIn = matchCase ? searchIn : searchIn.toLowerCase();
        String compareText = matchCase ? searchText : searchText.toLowerCase();

        List<int[]> matches = new ArrayList<>();
        int idx = 0;
        while ((idx = compareIn.indexOf(compareText, idx)) >= 0) {
            matches.add(new int[]{idx + regionStart, idx + regionStart + searchText.length()});
            idx += compareText.length();
        }

        if (matches.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Text not found.",
                    "Find All", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        showFindAllResults(searchText, matches, content);
    }

    /**
     * Displays a results window containing all matching lines with highlighted
     * match text. Clicking a highlighted match selects the corresponding text
     * in the editor and brings the main window to the front.
     *
     * @param searchText the search query (used in the window title)
     * @param matches    list of [start, end] offset pairs for each match
     * @param content    the full document text (used for line extraction)
     */
    protected void showFindAllResults(String searchText, List<int[]> matches, String content) {
        JFrame resultsFrame = new JFrame("Find All Results - \"" + searchText + "\" (" + matches.size() + " matches)");
        resultsFrame.setSize(600, 400);

        JTextPane resultsPane = new JTextPane();
        resultsPane.setEditable(false);
        resultsPane.setFont(new Font("Monospaced", Font.PLAIN, 13));

        StyledDocument doc = resultsPane.getStyledDocument();

        Style normalStyle = doc.addStyle("normal", null);
        StyleConstants.setFontFamily(normalStyle, "Monospaced");
        StyleConstants.setFontSize(normalStyle, 13);

        Style lineNumStyle = doc.addStyle("lineNum", null);
        StyleConstants.setFontFamily(lineNumStyle, "Monospaced");
        StyleConstants.setFontSize(lineNumStyle, 13);
        StyleConstants.setForeground(lineNumStyle, new Color(100, 100, 100));

        Style highlightStyle = doc.addStyle("highlight", null);
        StyleConstants.setFontFamily(highlightStyle, "Monospaced");
        StyleConstants.setFontSize(highlightStyle, 13);
        StyleConstants.setBackground(highlightStyle, new Color(255, 255, 0));
        StyleConstants.setForeground(highlightStyle, Color.BLACK);

        String[] lines = content.split("\n", -1);
        int[] lineStartOffsets = new int[lines.length];
        lineStartOffsets[0] = 0;
        for (int i = 1; i < lines.length; i++) {
            lineStartOffsets[i] = lineStartOffsets[i - 1] + lines[i - 1].length() + 1;
        }

        List<int[]> clickableRanges = new ArrayList<>();

        Map<Integer, List<int[]>> matchesByLine = new LinkedHashMap<>();
        for (int[] match : matches) {
            int lineIdx = getLineForOffset(lineStartOffsets, match[0]);
            matchesByLine.computeIfAbsent(lineIdx, k -> new ArrayList<>()).add(match);
        }

        try {
            for (Map.Entry<Integer, List<int[]>> entry : matchesByLine.entrySet()) {
                int lineIdx = entry.getKey();
                List<int[]> lineMatches = entry.getValue();
                String line = lines[lineIdx];
                int lineStart = lineStartOffsets[lineIdx];

                String prefix = String.format("%4d: ", lineIdx + 1);
                doc.insertString(doc.getLength(), prefix, lineNumStyle);

                int pos = 0;
                for (int[] match : lineMatches) {
                    int matchStartInLine = match[0] - lineStart;
                    int matchEndInLine = match[1] - lineStart;

                    if (matchStartInLine > pos) {
                        doc.insertString(doc.getLength(), line.substring(pos, matchStartInLine), normalStyle);
                    }

                    int highlightStart = doc.getLength();
                    doc.insertString(doc.getLength(), line.substring(matchStartInLine, matchEndInLine), highlightStyle);
                    int highlightEnd = doc.getLength();

                    clickableRanges.add(new int[]{highlightStart, highlightEnd, match[0], match[1]});
                    pos = matchEndInLine;
                }

                if (pos < line.length()) {
                    doc.insertString(doc.getLength(), line.substring(pos), normalStyle);
                }
                doc.insertString(doc.getLength(), "\n", normalStyle);
            }
        } catch (BadLocationException ex) {
            // Should not happen
        }

        resultsPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int clickPos = resultsPane.viewToModel2D(e.getPoint());
                for (int[] range : clickableRanges) {
                    if (clickPos >= range[0] && clickPos < range[1]) {
                        textArea.setSelectionStart(range[2]);
                        textArea.setSelectionEnd(range[3]);
                        textArea.requestFocusInWindow();
                        ownerFrame.toFront();
                        break;
                    }
                }
            }
        });

        resultsPane.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int hoverPos = resultsPane.viewToModel2D(e.getPoint());
                boolean overMatch = false;
                for (int[] range : clickableRanges) {
                    if (hoverPos >= range[0] && hoverPos < range[1]) {
                        overMatch = true;
                        break;
                    }
                }
                resultsPane.setCursor(overMatch
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsPane);
        resultsFrame.add(scrollPane);
        resultsFrame.setLocationRelativeTo(ownerFrame);
        resultsFrame.setVisible(true);
    }

    /**
     * Determines which line a given character offset falls on using binary-style
     * reverse scan of the line start offsets array.
     *
     * @param lineStartOffsets array of character offsets where each line begins
     * @param offset           the character offset to locate
     * @return the zero-based line index
     */
    protected int getLineForOffset(int[] lineStartOffsets, int offset) {
        for (int i = lineStartOffsets.length - 1; i >= 0; i--) {
            if (offset >= lineStartOffsets[i]) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Counts and displays the number of occurrences of the search text within
     * the current search region. Shows the result in a message dialog.
     */
    protected void count() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;

        String content = textArea.getText();
        boolean matchCase = matchCaseBox.isSelected();

        int[] bounds = new int[2];
        getSearchBounds(bounds);
        int regionStart = bounds[0];
        int regionEnd = bounds[1];

        String searchIn = content.substring(regionStart, regionEnd);
        String compareIn = matchCase ? searchIn : searchIn.toLowerCase();
        String compareText = matchCase ? searchText : searchText.toLowerCase();

        int count = 0;
        int idx = 0;
        while ((idx = compareIn.indexOf(compareText, idx)) >= 0) {
            count++;
            idx += compareText.length();
        }

        JOptionPane.showMessageDialog(this,
                count + " match" + (count != 1 ? "es" : "") + " found.",
                "Count", JOptionPane.INFORMATION_MESSAGE);
    }
}
