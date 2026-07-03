/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;

/**
 * A panel containing an RSyntaxTextArea configured for Markdown editing
 * with syntax highlighting and line numbers.
 */
public class EditorPanel extends JPanel {

    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;

    public EditorPanel(Preferences preferences) {
        super(new BorderLayout());

        textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        textArea.setFont(new Font(preferences.getEditorFontFamily(), Font.PLAIN, preferences.getEditorFontSize()));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setDragEnabled(false);
        textArea.setCodeFoldingEnabled(false);
        textArea.setAntiAliasingEnabled(true);

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Markdown Source"));

        add(scrollPane, BorderLayout.CENTER);
    }

    /** Returns the text area component. */
    public RSyntaxTextArea getTextArea() {
        return textArea;
    }

    /** Returns the scroll pane wrapping the text area. */
    public RTextScrollPane getScrollPane() {
        return scrollPane;
    }

    /** Applies font preferences to the editor. */
    public void applyPreferences(Preferences preferences) {
        textArea.setFont(new Font(preferences.getEditorFontFamily(), Font.PLAIN, preferences.getEditorFontSize()));
    }
}
