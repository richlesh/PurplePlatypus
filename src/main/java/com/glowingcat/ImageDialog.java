/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * ImageDialog.java
 *
 * A modal dialog for inserting a Markdown image link. Shows a native file chooser
 * for selecting image files (GIF, JPG, JPEG, PNG) and computes the relative path
 * from the current document's location.
 */
package com.glowingcat;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;

/**
 * A modal dialog that lets the user select an image file and enter alt text
 * for inserting a Markdown image link.
 */
public class ImageDialog extends JDialog {

    private final JTextField altTextField;
    private final JTextField pathField;
    private final JFrame ownerFrame;
    private final File documentFile;
    private boolean confirmed = false;
    private String imagePath = "";

    /**
     * Creates the Image dialog.
     *
     * @param owner        the parent frame
     * @param selectedText the currently selected text (used as default alt text), may be null
     * @param documentFile the current document file (used to compute relative paths), may be null
     */
    public ImageDialog(JFrame owner, String selectedText, File documentFile) {
        this(owner, selectedText, "", documentFile);
    }

    /**
     * Creates the Image dialog with a pre-filled image path.
     *
     * @param owner        the parent frame
     * @param altText      the alt text to pre-fill, may be null
     * @param existingPath the existing image path to pre-fill, may be null or empty
     * @param documentFile the current document file (used to compute relative paths), may be null
     */
    public ImageDialog(JFrame owner, String altText, String existingPath, File documentFile) {
        super(owner, "Insert Image", true);
        this.ownerFrame = owner;
        this.documentFile = documentFile;

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Alt text field
        gbc.gridx = 0;
        gbc.gridy = 0;
        contentPanel.add(new JLabel("Alt Text:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        altTextField = new JTextField(30);
        if (altText != null && !altText.isEmpty()) {
            altTextField.setText(altText);
        }
        contentPanel.add(altTextField, gbc);

        // Image path field + browse button
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        contentPanel.add(new JLabel("Image:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        pathField = new JTextField(25);
        pathField.setEditable(false);
        if (existingPath != null && !existingPath.isEmpty()) {
            imagePath = existingPath;
            pathField.setText(existingPath);
        }
        contentPanel.add(pathField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseForImage());
        contentPanel.add(browseButton, gbc);

        add(contentPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> {
            if (imagePath.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select an image file.",
                        "No Image Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
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

    /**
     * Opens a native file dialog for selecting image files.
     */
    private void browseForImage() {
        FileDialog fileDialog = new FileDialog(ownerFrame, "Select Image", FileDialog.LOAD);
        fileDialog.setFilenameFilter((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".gif") || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg") || lower.endsWith(".png");
        });
        fileDialog.setVisible(true);
        if (fileDialog.getFile() != null) {
            File selectedFile = new File(fileDialog.getDirectory(), fileDialog.getFile());
            computeRelativePath(selectedFile);
        }
    }

    /**
     * Computes the relative path from the document location to the selected image.
     * Falls back to the absolute path if no document has been saved yet.
     */
    private void computeRelativePath(File imageFile) {
        if (documentFile != null && documentFile.getParentFile() != null) {
            Path docDir = documentFile.getParentFile().toPath();
            Path imgPath = imageFile.toPath();
            try {
                imagePath = docDir.relativize(imgPath).toString();
            } catch (IllegalArgumentException e) {
                // Different roots (e.g., different drives on Windows)
                imagePath = imageFile.getAbsolutePath();
            }
        } else {
            imagePath = imageFile.getAbsolutePath();
        }
        pathField.setText(imagePath);
    }

    /** Returns whether the user clicked Save. */
    public boolean isConfirmed() {
        return confirmed;
    }

    /** Returns the alt text entered by the user. */
    public String getAltText() {
        return altTextField.getText();
    }

    /** Returns the image path (relative or absolute). */
    public String getImagePath() {
        return imagePath;
    }
}
