/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.spellcheck;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.nio.file.Path;
import java.util.List;

/**
 * Coordinates live spell checking for an RSyntaxTextArea editor.
 * <p>
 * When enabled, listens for document changes, debounces them, then performs
 * spell checking on a background thread and applies highlights on the EDT.
 */
public class SpellCheckController {

    private static final int DEBOUNCE_MS = 500;

    private final RSyntaxTextArea textArea;
    private final Path configDir;
    private SpellCheckService service;
    private final SpellCheckHighlighter highlighter;
    private final SpellCheckContextMenu contextMenu;
    private final Timer debounceTimer;
    private final DocumentListener docListener;
    private boolean enabled = false;
    private boolean contextMenuConfigured = false;

    /**
     * Creates a spell-check controller for the given text area.
     *
     * @param textArea  the editor component
     * @param configDir directory for user dictionary storage (e.g., ~/.purpleplatypus/)
     */
    public SpellCheckController(RSyntaxTextArea textArea, Path configDir) {
        this(textArea, configDir, "en");
    }

    /**
     * Creates a spell-check controller for the given text area with a specific language.
     *
     * @param textArea  the editor component
     * @param configDir directory for user dictionary storage (e.g., ~/.purpleplatypus/)
     * @param langCode  the language code (e.g., "en", "fr", "de")
     */
    public SpellCheckController(RSyntaxTextArea textArea, Path configDir, String langCode) {
        this.textArea = textArea;
        this.configDir = configDir;
        this.service = new SpellCheckService(configDir, langCode);
        this.highlighter = new SpellCheckHighlighter(textArea);
        this.contextMenu = new SpellCheckContextMenu();

        // Debounce timer — fires once after user stops typing
        debounceTimer = new Timer(DEBOUNCE_MS, e -> runSpellCheck());
        debounceTimer.setRepeats(false);

        docListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { scheduleCheck(); }
            @Override
            public void removeUpdate(DocumentEvent e) { scheduleCheck(); }
            @Override
            public void changedUpdate(DocumentEvent e) { /* attribute change — ignore */ }
        };
    }

    /**
     * Enables or disables live spell checking.
     *
     * @param enabled true to start checking, false to stop and clear highlights
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;

        if (enabled) {
            textArea.getDocument().addDocumentListener(docListener);
            configurePopupMenu();
            // Trigger an initial check
            scheduleCheck();
        } else {
            textArea.getDocument().removeDocumentListener(docListener);
            debounceTimer.stop();
            highlighter.clearHighlights();
        }
    }

    /** Returns whether spell checking is currently enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Changes the spell-check language. Clears current highlights and
     * reinitializes the service with the new language.
     *
     * @param langCode the new language code (e.g., "en", "fr", "de")
     */
    public void setLanguage(String langCode) {
        highlighter.clearHighlights();
        service.setLanguage(langCode);
        if (enabled) {
            // Re-check after a short delay to let the service reinitialize
            Timer delayTimer = new Timer(500, e -> scheduleCheck());
            delayTimer.setRepeats(false);
            delayTimer.start();
        }
    }

    /** Returns the current language code. */
    public String getLanguage() {
        return service.getCurrentLanguage();
    }

    /**
     * Triggers an immediate re-check (e.g., after adding a word to the dictionary).
     */
    public void recheck() {
        if (enabled) {
            runSpellCheck();
        }
    }

    /** Stops all timers and removes listeners. Call when the editor window is disposed. */
    public void dispose() {
        setEnabled(false);
        debounceTimer.stop();
    }

    private void scheduleCheck() {
        debounceTimer.restart();
    }

    private void runSpellCheck() {
        if (!service.isReady()) {
            // LanguageTool still loading — try again shortly
            Timer retryTimer = new Timer(200, e -> runSpellCheck());
            retryTimer.setRepeats(false);
            retryTimer.start();
            return;
        }

        String text = textArea.getText();

        // Run the check on a background thread
        SwingWorker<SpellCheckResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SpellCheckResult doInBackground() {
                List<int[]> skipRegions = MarkdownSpellFilter.computeSkipRegions(text);
                List<SpellCheckService.SpellError> errors = service.check(text);
                return new SpellCheckResult(errors, skipRegions);
            }

            @Override
            protected void done() {
                try {
                    SpellCheckResult result = get();
                    // Only apply if the text hasn't changed since we started
                    if (enabled && text.equals(textArea.getText())) {
                        highlighter.applyHighlights(result.errors(), result.skipRegions());
                    }
                } catch (Exception e) {
                    // Cancelled or error — ignore
                }
            }
        };
        worker.execute();
    }

    private void configurePopupMenu() {
        if (contextMenuConfigured) return;
        JPopupMenu popup = textArea.getPopupMenu();
        if (popup != null) {
            contextMenu.configureContextMenu(popup, textArea, highlighter, service, this::recheck);
            contextMenuConfigured = true;
        }
    }

    /** Internal record to pass results from background thread to EDT. */
    private record SpellCheckResult(
            List<SpellCheckService.SpellError> errors,
            List<int[]> skipRegions) {}
}
