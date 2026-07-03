/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * PreferencesDialog.java
 *
 * A modal dialog for editing PurplePlatypus user preferences. Provides combo boxes
 * for selecting font family and font size for the editor, preview text, and preview code.
 */
package com.glowingcat;

import javax.swing.*;
import java.awt.*;

public class PreferencesDialog extends JDialog {

    private final JComboBox<String> editorFontCombo;
    private final JComboBox<Integer> editorSizeCombo;
    private final JComboBox<String> previewFontCombo;
    private final JComboBox<Integer> previewSizeCombo;
    private final JComboBox<String> previewCodeFontCombo;
    private final JComboBox<Integer> previewCodeSizeCombo;
    private boolean confirmed = false;

    private static final Integer[] FONT_SIZES = {8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 28, 32, 36};

    public PreferencesDialog(JFrame owner, Preferences prefs) {
        super(owner, "Preferences", true);

        String[] fontFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();

        editorFontCombo = new JComboBox<>(fontFamilies);
        editorFontCombo.setSelectedItem(prefs.getEditorFontFamily());
        editorSizeCombo = new JComboBox<>(FONT_SIZES);
        editorSizeCombo.setSelectedItem(prefs.getEditorFontSize());

        previewFontCombo = new JComboBox<>(fontFamilies);
        previewFontCombo.setSelectedItem(prefs.getPreviewFontFamily());
        previewSizeCombo = new JComboBox<>(FONT_SIZES);
        previewSizeCombo.setSelectedItem(prefs.getPreviewFontSize());

        previewCodeFontCombo = new JComboBox<>(fontFamilies);
        previewCodeFontCombo.setSelectedItem(prefs.getPreviewCodeFontFamily());
        previewCodeSizeCombo = new JComboBox<>(FONT_SIZES);
        previewCodeSizeCombo.setSelectedItem(prefs.getPreviewCodeFontSize());

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Editor section
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JLabel editorHeader = new JLabel("Markdown Source");
        editorHeader.setFont(editorHeader.getFont().deriveFont(Font.BOLD));
        contentPanel.add(editorHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(editorFontCombo, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(new JLabel("Size:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(editorSizeCombo, gbc);

        // Separator
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 6, 10, 6);
        contentPanel.add(new JSeparator(), gbc);

        // Preview Text section
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        JLabel previewHeader = new JLabel("Preview Text");
        previewHeader.setFont(previewHeader.getFont().deriveFont(Font.BOLD));
        contentPanel.add(previewHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(previewFontCombo, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(new JLabel("Size:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(previewSizeCombo, gbc);

        // Separator
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 6, 10, 6);
        contentPanel.add(new JSeparator(), gbc);

        // Preview Code section
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        JLabel previewCodeHeader = new JLabel("Preview Code");
        previewCodeHeader.setFont(previewCodeHeader.getFont().deriveFont(Font.BOLD));
        contentPanel.add(previewCodeHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(previewCodeFontCombo, gbc);

        gbc.gridy = ++row; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(new JLabel("Size:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(previewCodeSizeCombo, gbc);

        add(contentPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> { confirmed = true; dispose(); });
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okButton);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    public boolean isConfirmed() { return confirmed; }

    public void applyTo(Preferences prefs) {
        prefs.setEditorFontFamily((String) editorFontCombo.getSelectedItem());
        prefs.setEditorFontSize((Integer) editorSizeCombo.getSelectedItem());
        prefs.setPreviewFontFamily((String) previewFontCombo.getSelectedItem());
        prefs.setPreviewFontSize((Integer) previewSizeCombo.getSelectedItem());
        prefs.setPreviewCodeFontFamily((String) previewCodeFontCombo.getSelectedItem());
        prefs.setPreviewCodeFontSize((Integer) previewCodeSizeCombo.getSelectedItem());
    }
}
