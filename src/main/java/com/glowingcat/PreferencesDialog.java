/*
 * (c) 2026 The Boeing Company
 */

/**
 * PreferencesDialog.java
 *
 * A modal dialog for editing PurplePlatypus user preferences. Provides combo boxes
 * for selecting font family and font size for both the markdown editor pane
 * and the HTML preview pane.
 */
package com.glowingcat;

import javax.swing.*;
import java.awt.*;

public class PreferencesDialog extends JDialog {

    private final JComboBox<String> editorFontCombo;
    private final JComboBox<Integer> editorSizeCombo;
    private final JComboBox<String> previewFontCombo;
    private final JComboBox<Integer> previewSizeCombo;
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

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel editorHeader = new JLabel("Markdown Source");
        editorHeader.setFont(editorHeader.getFont().deriveFont(Font.BOLD));
        contentPanel.add(editorHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1; gbc.gridx = 0;
        contentPanel.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(editorFontCombo, gbc);

        gbc.gridy = 2; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(new JLabel("Size:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(editorSizeCombo, gbc);

        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 6, 10, 6);
        contentPanel.add(new JSeparator(), gbc);

        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2;
        JLabel previewHeader = new JLabel("Preview");
        previewHeader.setFont(previewHeader.getFont().deriveFont(Font.BOLD));
        contentPanel.add(previewHeader, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 5; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(new JLabel("Font:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(previewFontCombo, gbc);

        gbc.gridy = 6; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(new JLabel("Size:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(previewSizeCombo, gbc);

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
    }
}
