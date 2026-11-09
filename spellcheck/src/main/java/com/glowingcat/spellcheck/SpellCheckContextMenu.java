/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.spellcheck;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamically adds spelling suggestions and an "Add to Dictionary" option
 * to the editor's right-click context menu when the cursor is on a misspelled word.
 */
public class SpellCheckContextMenu {

    private static final int MAX_SUGGESTIONS = 5;

    private final List<JComponent> dynamicItems = new ArrayList<>();

    /**
     * Configures the given popup menu to show spell-check suggestions.
     *
     * @param menu            the editor's existing context menu
     * @param textArea        the RSyntaxTextArea editor
     * @param highlighter     the spell-check highlighter (to find errors at offset)
     * @param service         the spell-check service (for adding to dictionary)
     * @param recheckCallback callback to trigger a re-check after dictionary change
     */
    public void configureContextMenu(JPopupMenu menu, RSyntaxTextArea textArea,
                                     SpellCheckHighlighter highlighter,
                                     SpellCheckService service,
                                     Runnable recheckCallback) {
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                removeDynamicItems(menu);

                // Determine the offset under the mouse (or caret if mouse is unavailable)
                int offset = getOffsetUnderMouse(textArea);
                if (offset < 0) {
                    offset = textArea.getCaretPosition();
                }

                SpellCheckService.SpellError error = highlighter.getErrorAtOffset(offset);
                if (error == null) return;

                String misspelledWord;
                try {
                    misspelledWord = textArea.getDocument().getText(
                            error.startOffset(), error.endOffset() - error.startOffset());
                } catch (BadLocationException ex) {
                    return;
                }

                int insertIndex = 0;

                boolean isGrammar = error.isGrammar();

                // For grammar/style issues, show LanguageTool's explanation as a
                // non-clickable header so the user understands the suggestion.
                if (isGrammar) {
                    String message = error.message();
                    if (message != null && !message.isBlank()) {
                        JMenuItem header = new JMenuItem(truncate(message, 80));
                        header.setEnabled(false);
                        header.setToolTipText(message);
                        menu.insert(header, insertIndex++);
                        dynamicItems.add(header);
                    }
                }

                // Add suggestions
                List<String> suggestions = error.suggestions();
                int count = Math.min(suggestions.size(), MAX_SUGGESTIONS);
                if (count > 0) {
                    for (int i = 0; i < count; i++) {
                        String suggestion = suggestions.get(i);
                        JMenuItem item = new JMenuItem(suggestion);
                        item.setFont(item.getFont().deriveFont(Font.BOLD));
                        final int start = error.startOffset();
                        final int end = error.endOffset();
                        item.addActionListener(evt -> {
                            try {
                                textArea.getDocument().remove(start, end - start);
                                textArea.getDocument().insertString(start, suggestion, null);
                            } catch (BadLocationException ex) {
                                // Ignore
                            }
                        });
                        menu.insert(item, insertIndex++);
                        dynamicItems.add(item);
                    }
                } else {
                    JMenuItem noSuggestions = new JMenuItem("(no suggestions)");
                    noSuggestions.setEnabled(false);
                    menu.insert(noSuggestions, insertIndex++);
                    dynamicItems.add(noSuggestions);
                }

                // "Add to Dictionary" only makes sense for spelling mistakes —
                // adding a grammar phrase like "the the" to the dictionary is
                // meaningless, so suppress it for grammar/style issues.
                if (!isGrammar) {
                    JMenuItem addToDict = new JMenuItem("Add \"" + misspelledWord + "\" to Dictionary");
                    addToDict.addActionListener(evt -> {
                        service.addToDictionary(misspelledWord);
                        recheckCallback.run();
                    });
                    menu.insert(addToDict, insertIndex++);
                    dynamicItems.add(addToDict);
                }

                // Separator
                JPopupMenu.Separator separator = new JPopupMenu.Separator();
                menu.insert(separator, insertIndex);
                dynamicItems.add(separator);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                removeDynamicItems(menu);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                removeDynamicItems(menu);
            }
        });
    }

    private void removeDynamicItems(JPopupMenu menu) {
        for (JComponent item : dynamicItems) {
            menu.remove(item);
        }
        dynamicItems.clear();
    }

    /** Truncates a message for display in a menu header, appending an ellipsis if shortened. */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= maxLen ? s : s.substring(0, Math.max(0, maxLen - 1)) + "\u2026";
    }

    /**
     * Attempts to determine the document offset under the mouse pointer.
     */
    private int getOffsetUnderMouse(RSyntaxTextArea textArea) {
        try {
            Point mousePos = MouseInfo.getPointerInfo().getLocation();
            SwingUtilities.convertPointFromScreen(mousePos, textArea);
            return textArea.viewToModel(mousePos);
        } catch (Exception e) {
            return -1;
        }
    }
}
