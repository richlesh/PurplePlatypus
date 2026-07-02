package com.markdownpro;

import javax.swing.*;
import java.awt.*;

/**
 * A Replace dialog that extends FindDialog, adding a replacement text field
 * and Replace, Replace and Find, and Replace All buttons.
 */
public class ReplaceDialog extends FindDialog {

    private JTextField replaceField;

    public ReplaceDialog(JFrame owner, JTextArea textArea) {
        super(owner, textArea, "Replace");
    }

    @Override
    protected JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Find label and field
        gbc.gridx = 0;
        gbc.gridy = 0;
        topPanel.add(new JLabel("Find:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        // Initialize the parent's searchField via direct field assignment
        // (the field is declared in FindDialog as protected final, but initialized here)
        topPanel.add(searchField = new JTextField(24), gbc);

        // Replace label and field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        topPanel.add(new JLabel("Replace:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        replaceField = new JTextField(24);
        topPanel.add(replaceField, gbc);

        return topPanel;
    }

    @Override
    protected JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        JButton findBtn = createButton("Find");
        JButton replaceBtn = createButton("Replace");
        JButton replaceAndFindBtn = createButton("Replace and Find");
        JButton replaceAllBtn = createButton("Replace All");

        findBtn.addActionListener(e -> findNext());
        replaceBtn.addActionListener(e -> replace());
        replaceAndFindBtn.addActionListener(e -> replaceAndFind());
        replaceAllBtn.addActionListener(e -> replaceAll());

        buttonPanel.add(findBtn);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(replaceBtn);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(replaceAndFindBtn);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(replaceAllBtn);

        return buttonPanel;
    }

    /**
     * Replaces the currently selected text if it matches the search text.
     * Returns true if a replacement was made.
     */
    private boolean replace() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return false;

        String selectedText = textArea.getSelectedText();
        if (selectedText == null) return false;

        boolean matchCase = matchCaseBox.isSelected();
        boolean matches;
        if (matchCase) {
            matches = selectedText.equals(searchText);
        } else {
            matches = selectedText.equalsIgnoreCase(searchText);
        }

        if (matches) {
            textArea.replaceSelection(replaceField.getText());
            // Adjust remembered selection bounds if replacing within selection
            if (findInSelectionBox.isSelected() && selectionStart >= 0) {
                int lengthDiff = replaceField.getText().length() - searchText.length();
                selectionEnd += lengthDiff;
            }
            return true;
        }
        return false;
    }

    /**
     * Replaces the current match (if any) then finds the next occurrence.
     */
    private void replaceAndFind() {
        replace();
        findNext();
    }

    /**
     * Replaces all occurrences within the search bounds.
     */
    private void replaceAll() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) return;

        String replaceText = replaceField.getText();
        String content = textArea.getText();
        boolean matchCase = matchCaseBox.isSelected();

        int[] bounds = new int[2];
        getSearchBounds(bounds);
        int regionStart = bounds[0];
        int regionEnd = bounds[1];

        String searchIn = content.substring(regionStart, regionEnd);
        String compareIn = matchCase ? searchIn : searchIn.toLowerCase();
        String compareText = matchCase ? searchText : searchText.toLowerCase();

        // Build the replaced string
        StringBuilder result = new StringBuilder();
        int count = 0;
        int idx = 0;
        while (true) {
            int found = compareIn.indexOf(compareText, idx);
            if (found < 0) {
                result.append(searchIn.substring(idx));
                break;
            }
            result.append(searchIn, idx, found);
            result.append(replaceText);
            count++;
            idx = found + searchText.length();
        }

        if (count == 0) {
            JOptionPane.showMessageDialog(this, "Text not found.",
                    "Replace All", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Replace the region in the text area
        String newContent = content.substring(0, regionStart) + result + content.substring(regionEnd);
        textArea.setText(newContent);
        textArea.setCaretPosition(regionStart);

        // Update selection bounds if searching in selection
        if (findInSelectionBox.isSelected() && selectionStart >= 0) {
            selectionEnd = regionStart + result.length();
        }

        JOptionPane.showMessageDialog(this,
                count + " replacement" + (count != 1 ? "s" : "") + " made.",
                "Replace All", JOptionPane.INFORMATION_MESSAGE);
    }
}
