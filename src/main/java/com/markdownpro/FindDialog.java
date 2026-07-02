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
 */
public class FindDialog extends JDialog {
    protected final JFrame ownerFrame;
    protected final JTextArea textArea;
    protected JTextField searchField;
    protected JCheckBox findInSelectionBox;
    protected JCheckBox searchBackwardsBox;
    protected JCheckBox matchCaseBox;
    protected JCheckBox wrapAroundBox;

    // Remembered selection boundaries for "Find in selection"
    protected int selectionStart = -1;
    protected int selectionEnd = -1;

    public FindDialog(JFrame owner, JTextArea textArea) {
        this(owner, textArea, "Find");
    }

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

    protected JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.add(new JLabel("Find:"), BorderLayout.WEST);
        topPanel.add(searchField = new JTextField(24), BorderLayout.CENTER);
        return topPanel;
    }

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

    protected JButton createButton(String text) {
        JButton btn = new JButton(text);
        Dimension btnSize = new Dimension(130, 28);
        btn.setMaximumSize(btnSize);
        btn.setPreferredSize(btnSize);
        return btn;
    }

    protected void getSearchBounds(int[] bounds) {
        if (findInSelectionBox.isSelected() && selectionStart >= 0 && selectionEnd > selectionStart) {
            bounds[0] = selectionStart;
            bounds[1] = selectionEnd;
        } else {
            bounds[0] = 0;
            bounds[1] = textArea.getText().length();
        }
    }

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

    protected int getLineForOffset(int[] lineStartOffsets, int offset) {
        for (int i = lineStartOffsets.length - 1; i >= 0; i--) {
            if (offset >= lineStartOffsets[i]) {
                return i;
            }
        }
        return 0;
    }

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
