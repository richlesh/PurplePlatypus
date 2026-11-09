/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import com.glowingcat.spellcheck.MarkdownSpellFilter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live validation of GitHub-style emoji shortcodes ({@code :smile:}) in the editor.
 * <p>
 * When enabled it debounces document changes, scans for {@code :shortcode:} tokens outside
 * code regions, and draws a squiggly <b>orange</b> underline under any token that is not a
 * known emoji (per {@link EmojiShortcodeValidator}). Right-clicking an underlined token adds a
 * list of similar valid shortcodes to the editor's context menu; choosing one replaces the
 * token in place.
 * <p>
 * The design mirrors the spell-check controller/highlighter/menu trio but is self-contained so
 * the two features can coexist on the same editor and shared popup menu.
 */
public final class EmojiShortcodeChecker {

    private static final int DEBOUNCE_MS = 400;
    private static final int MAX_SUGGESTIONS = 8;
    private static final Color ORANGE = new Color(0xE8, 0x7A, 0x00);

    /** Matches :shortcode: — same shape MarkdownExtras uses to resolve shortcodes. */
    private static final Pattern SHORTCODE =
            Pattern.compile(":([a-z0-9_+\\-]+):", Pattern.CASE_INSENSITIVE);

    /** One invalid shortcode occurrence in the document. */
    public record InvalidToken(int start, int end, String code) {}

    private final RSyntaxTextArea textArea;
    private final Highlighter.HighlightPainter painter = new SquigglyPainter(ORANGE);
    private final List<Object> highlightTags = new ArrayList<>();
    private List<InvalidToken> current = Collections.emptyList();

    private final Timer debounce;
    private final DocumentListener docListener;
    private boolean enabled = false;
    private boolean menuConfigured = false;

    public EmojiShortcodeChecker(RSyntaxTextArea textArea) {
        this.textArea = textArea;
        this.debounce = new Timer(DEBOUNCE_MS, e -> rescan());
        this.debounce.setRepeats(false);
        this.docListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { schedule(); }
            @Override public void removeUpdate(DocumentEvent e) { schedule(); }
            @Override public void changedUpdate(DocumentEvent e) { /* attributes only */ }
        };
    }

    /** Enables or disables live checking. Disabling clears all highlights. */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            textArea.getDocument().addDocumentListener(docListener);
            configureContextMenu();
            schedule();
        } else {
            textArea.getDocument().removeDocumentListener(docListener);
            debounce.stop();
            clearHighlights();
        }
    }

    /** @return whether live checking is active. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Stops timers and removes listeners. Call on window dispose. */
    public void dispose() {
        setEnabled(false);
        debounce.stop();
    }

    private void schedule() {
        debounce.restart();
    }

    private void rescan() {
        if (!enabled) return;
        String text = textArea.getText();
        List<int[]> skip = MarkdownSpellFilter.computeSkipRegions(text);
        List<InvalidToken> invalid = findInvalid(text, skip);
        applyHighlights(invalid);
    }

    /**
     * Scans {@code text} for {@code :shortcode:} tokens that are not known emoji, skipping any
     * token that falls inside a skip region (code blocks, inline code, URLs, etc.). Pure and
     * static so it can be unit-tested without a UI.
     *
     * @param text        the full document text
     * @param skipRegions sorted [start,end) regions to ignore (may be null/empty)
     * @return invalid shortcode tokens in document order
     */
    public static List<InvalidToken> findInvalid(String text, List<int[]> skipRegions) {
        if (text == null || text.isEmpty()) return List.of();
        List<InvalidToken> out = new ArrayList<>();
        Matcher m = SHORTCODE.matcher(text);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (inSkipRegion(start, end, skipRegions)) continue;
            String code = m.group(1);
            if (!EmojiShortcodeValidator.isValid(code)) {
                out.add(new InvalidToken(start, end, code));
            }
        }
        return out;
    }

    private static boolean inSkipRegion(int start, int end, List<int[]> skipRegions) {
        if (skipRegions == null) return false;
        for (int[] r : skipRegions) {
            if (start < r[1] && end > r[0]) return true; // overlap
        }
        return false;
    }

    private void applyHighlights(List<InvalidToken> invalid) {
        clearHighlights();
        Highlighter highlighter = textArea.getHighlighter();
        for (InvalidToken t : invalid) {
            try {
                highlightTags.add(highlighter.addHighlight(t.start(), t.end(), painter));
            } catch (BadLocationException ex) {
                // Offsets shifted since scan — skip this one.
            }
        }
        current = invalid;
    }

    private void clearHighlights() {
        Highlighter highlighter = textArea.getHighlighter();
        for (Object tag : highlightTags) {
            highlighter.removeHighlight(tag);
        }
        highlightTags.clear();
        current = Collections.emptyList();
    }

    /** @return the invalid token spanning {@code offset}, or null if none. */
    private InvalidToken tokenAt(int offset) {
        for (InvalidToken t : current) {
            if (offset >= t.start() && offset <= t.end()) return t;
        }
        return null;
    }

    private void configureContextMenu() {
        if (menuConfigured) return;
        JPopupMenu menu = textArea.getPopupMenu();
        if (menu == null) return;
        menu.addPopupMenuListener(new PopupMenuListener() {
            private final List<JMenuItem> dynamic = new ArrayList<>();

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                remove();
                if (!enabled) return;
                int offset = offsetUnderMouse();
                if (offset < 0) offset = textArea.getCaretPosition();
                InvalidToken token = tokenAt(offset);
                if (token == null) return;

                List<String> suggestions = EmojiShortcodeValidator.suggest(token.code(), MAX_SUGGESTIONS);
                int idx = 0;
                JMenuItem header = new JMenuItem(
                        Messages.get("emoji.invalidShortcode", ":" + token.code() + ":"));
                header.setEnabled(false);
                menu.insert(header, idx++);
                dynamic.add(header);

                if (suggestions.isEmpty()) {
                    JMenuItem none = new JMenuItem(Messages.get("emoji.noSuggestions"));
                    none.setEnabled(false);
                    menu.insert(none, idx++);
                    dynamic.add(none);
                } else {
                    for (String s : suggestions) {
                        JMenuItem item = new JMenuItem(":" + s + ":");
                        item.setFont(item.getFont().deriveFont(Font.BOLD));
                        final int start = token.start();
                        final int end = token.end();
                        final String replacement = ":" + s + ":";
                        item.addActionListener(ev -> replaceToken(start, end, replacement));
                        menu.insert(item, idx++);
                        dynamic.add(item);
                    }
                }
                JPopupMenu.Separator sep = new JPopupMenu.Separator();
                menu.insert(sep, idx);
                // Track the separator too so it is removed with the rest.
                dynamic.add(new TrackedSeparator(sep));
            }

            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { remove(); }
            @Override public void popupMenuCanceled(PopupMenuEvent e) { remove(); }

            private void remove() {
                for (JMenuItem c : dynamic) {
                    if (c instanceof TrackedSeparator ts) {
                        menu.remove(ts.sep);
                    } else {
                        menu.remove(c);
                    }
                }
                dynamic.clear();
            }
        });
        menuConfigured = true;
    }

    /** Wrapper so the dynamic-item list can hold the separator uniformly. */
    private static final class TrackedSeparator extends JMenuItem {
        final JPopupMenu.Separator sep;
        TrackedSeparator(JPopupMenu.Separator sep) { this.sep = sep; }
    }

    private void replaceToken(int start, int end, String replacement) {
        try {
            textArea.getDocument().remove(start, end - start);
            textArea.getDocument().insertString(start, replacement, null);
            // Re-scan promptly so the fixed token loses its underline.
            schedule();
        } catch (BadLocationException ex) {
            // Document changed under us — ignore; next scan will reconcile.
        }
    }

    private int offsetUnderMouse() {
        try {
            Point p = MouseInfo.getPointerInfo().getLocation();
            SwingUtilities.convertPointFromScreen(p, textArea);
            return textArea.viewToModel2D(p);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Paints a squiggly (wave) underline in a fixed color beneath the token. */
    private static final class SquigglyPainter implements Highlighter.HighlightPainter {
        private static final int WAVE_HEIGHT = 2;
        private static final int WAVE_WIDTH = 4;
        private final Color color;

        SquigglyPainter(Color color) { this.color = color; }

        @Override
        public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
            try {
                Rectangle2DUtil r0 = Rectangle2DUtil.of(c.modelToView2D(offs0));
                Rectangle2DUtil r1 = Rectangle2DUtil.of(c.modelToView2D(offs1));
                if (r0 == null || r1 == null) return;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.0f));

                if (r0.y == r1.y) {
                    drawWave(g2, r0.x, r1.x + r1.w, r0.y + r0.h - 1);
                } else {
                    int textWidth = c.getWidth();
                    drawWave(g2, r0.x, textWidth, r0.y + r0.h - 1);
                    drawWave(g2, 0, r1.x + r1.w, r1.y + r1.h - 1);
                    int lineHeight = r0.h;
                    for (int y = r0.y + lineHeight; y < r1.y; y += lineHeight) {
                        drawWave(g2, 0, textWidth, y + lineHeight - 1);
                    }
                }
                g2.dispose();
            } catch (BadLocationException ex) {
                // Can't compute view — skip painting.
            }
        }

        private void drawWave(Graphics2D g2, int x1, int x2, int y) {
            int x = x1;
            while (x < x2) {
                int nextX = Math.min(x + WAVE_WIDTH, x2);
                int midX = Math.min(x + WAVE_WIDTH / 2, x2);
                g2.drawLine(x, y, midX, y - WAVE_HEIGHT);
                g2.drawLine(midX, y - WAVE_HEIGHT, nextX, y);
                x = nextX;
            }
        }
    }

    /** Tiny adapter converting a Rectangle2D view rect into int fields used by the painter. */
    private record Rectangle2DUtil(int x, int y, int w, int h) {
        static Rectangle2DUtil of(java.awt.geom.Rectangle2D r) {
            if (r == null) return null;
            return new Rectangle2DUtil((int) r.getX(), (int) r.getY(),
                    (int) r.getWidth(), (int) r.getHeight());
        }
    }
}
