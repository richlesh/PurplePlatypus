/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * LinkDialog.java
 *
 * A modal dialog for inserting a Markdown link. Provides text fields for
 * the link text and URI, with the link text pre-filled from the editor selection.
 */
package com.glowingcat;

import javax.swing.*;
import java.awt.*;

/**
 * A modal dialog that collects link text and URI for inserting a Markdown link.
 */
public class LinkDialog extends JDialog {

    private final JTextField textField;
    private final JTextField uriField;
    private boolean confirmed = false;

    /**
     * Creates the Link dialog.
     *
     * @param owner        the parent frame
     * @param selectedText the currently selected text (used as default link text), may be null
     */
    public LinkDialog(JFrame owner, String selectedText) {
        this(owner, selectedText, "");
    }

    /**
     * Creates the Link dialog with pre-filled text and URI.
     *
     * @param owner        the parent frame
     * @param linkText     the link text to pre-fill, may be null
     * @param linkUri      the URI to pre-fill, may be null or empty
     */
    public LinkDialog(JFrame owner, String linkText, String linkUri) {
        super(owner, "Insert Link", true);

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        contentPanel.add(new JLabel("Text:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        textField = new JTextField(30);
        if (linkText != null && !linkText.isEmpty()) {
            textField.setText(linkText);
        }
        contentPanel.add(textField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        contentPanel.add(new JLabel("URL:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        uriField = new JTextField(30);
        uriField.setText(linkUri != null && !linkUri.isEmpty() ? linkUri : "https://");
        contentPanel.add(uriField, gbc);

        add(contentPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(saveButton);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    /** Returns whether the user clicked Save. */
    public boolean isConfirmed() {
        return confirmed;
    }

    /** Returns the link text entered by the user. */
    public String getLinkText() {
        return textField.getText();
    }

    /** Returns the URI entered by the user. */
    public String getLinkUri() {
        return uriField.getText();
    }
}
