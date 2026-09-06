/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.aichat;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Replaces certain BMP "symbol" characters — notably the macOS keyboard modifier
 * glyphs such as ⌘ (U+2318), ⇧ (U+21E7), ⌥ (U+2325) and ⌃ (U+2303) — with inline
 * {@code <svg>} vector images built from the actual system-font glyph outlines.
 * <p>
 * <b>Why this exists.</b> These glyphs are not present in most text fonts. On screen
 * JavaFX WebView silently falls back to a system font (e.g. "Apple Symbols") that
 * contains them, so they display correctly. However, WebView's <i>print</i> pipeline
 * (used for File &gt; Print and Export &gt; PDF) does not perform the same font
 * substitution, so the glyphs vanish from printed / exported output. Naming the
 * fallback font in CSS ({@code font-family: ..., 'Apple Symbols', ...}) does not fix
 * this because the print path does not honour the fallback the same way the screen
 * renderer does.
 * <p>
 * The robust fix — mirroring what {@link EmojiReplacer} does for non-BMP emoji — is to
 * embed the glyph directly as vector artwork so WebKit has nothing to look up: we take
 * the glyph {@link Shape outline} from an AWT {@link Font} that can display the code
 * point (Java2D <i>does</i> resolve these on screen and in print) and serialise it as an
 * inline SVG path. The SVG uses {@code fill: currentColor} so it inherits the surrounding
 * text colour, and is sized in {@code em} units so it scales with the font size.
 * <p>
 * Java2D user space is y-down, the same convention as SVG, so no vertical flip is needed.
 */
public final class SymbolReplacer {

    /** Code points that are replaced with inline SVG. Keyboard modifier / editing symbols. */
    private static final int[] SYMBOL_CODE_POINTS = {
            0x2318, // ⌘ PLACE OF INTEREST SIGN (Command)
            0x21E7, // ⇧ UPWARDS WHITE ARROW (Shift)
            0x2325, // ⌥ OPTION KEY (Option / Alt)
            0x2303, // ⌃ UP ARROWHEAD (Control)
            0x21A9, // ↩ LEFTWARDS ARROW WITH HOOK (Return)
            0x23CE, // ⏎ RETURN SYMBOL
            0x232B, // ⌫ ERASE TO THE LEFT (Backspace/Delete)
            0x2326, // ⌦ ERASE TO THE RIGHT (Forward Delete)
            0x238B, // ⎋ BROKEN CIRCLE WITH NORTHWEST ARROW (Escape)
            0x21E5, // ⇥ RIGHTWARDS ARROW TO BAR (Tab)
            0x21E4, // ⇤ LEFTWARDS ARROW TO BAR (Backtab)
            0x2190, // ← LEFTWARDS ARROW
            0x2191, // ↑ UPWARDS ARROW
            0x2192, // → RIGHTWARDS ARROW
            0x2193, // ↓ DOWNWARDS ARROW
            0x21DE, // ⇞ UPWARDS ARROW WITH DOUBLE STROKE (Page Up)
            0x21DF, // ⇟ DOWNWARDS ARROW WITH DOUBLE STROKE (Page Down)
    };

    /** Fonts to try, in order, when resolving a glyph outline. */
    private static final String[] CANDIDATE_FONTS = {
            "Apple Symbols",       // macOS
            "Segoe UI Symbol",     // Windows
            "Noto Sans Symbols2",  // Linux (Noto)
            "Noto Sans Symbols",
            "Arial Unicode MS",
            "DejaVu Sans",
            Font.SANS_SERIF,       // last-ditch logical font
    };

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    /** Lazily-built cache of code point -> inline SVG markup (or empty string if unavailable). */
    private static final Map<Integer, String> CACHE = new LinkedHashMap<>();

    /** Set of code points we handle, for fast membership tests. */
    private static final java.util.Set<Integer> HANDLED = new java.util.HashSet<>();
    static {
        for (int cp : SYMBOL_CODE_POINTS) HANDLED.add(cp);
    }

    private SymbolReplacer() {}

    /**
     * Replaces every handled symbol code point in {@code html} with an inline SVG image.
     * Characters that are not handled, or for which no glyph could be resolved, are left
     * unchanged.
     *
     * @param html HTML fragment (may be {@code null})
     * @return the HTML with symbol characters replaced by inline SVG, or the input unchanged
     */
    public static String replaceSymbols(String html) {
        if (html == null || html.isEmpty()) return html;

        // Fast path: nothing to do if none of our symbols are present.
        boolean any = false;
        for (int i = 0; i < html.length(); i++) {
            if (HANDLED.contains((int) html.charAt(i))) { any = true; break; }
        }
        if (!any) return html;

        StringBuilder out = new StringBuilder(html.length() + 64);
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            int cp = c;
            if (HANDLED.contains(cp)) {
                String svg = svgFor(cp);
                if (svg != null && !svg.isEmpty()) {
                    out.append(svg);
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Returns cached inline SVG for a code point, building it on first use. */
    private static synchronized String svgFor(int codePoint) {
        String cached = CACHE.get(codePoint);
        if (cached != null) return cached;
        String svg = buildSvg(codePoint);
        CACHE.put(codePoint, svg == null ? "" : svg);
        return svg;
    }

    /**
     * Builds an inline SVG {@code <svg>} element for the given code point using the first
     * candidate font that can display it. Returns {@code null} if no font can render it
     * (e.g. in a headless environment with no symbol fonts installed).
     */
    private static String buildSvg(int codePoint) {
        Font font = resolveFont(codePoint);
        if (font == null) return null;

        String text = new String(Character.toChars(codePoint));
        GlyphVector gv = font.createGlyphVector(FRC, text);
        Shape outline = gv.getOutline();
        Rectangle2D b = outline.getBounds2D();
        if (b.getWidth() <= 0 || b.getHeight() <= 0) return null;

        // Normalise so the artwork sits at the origin of the viewBox.
        AffineTransform tx = AffineTransform.getTranslateInstance(-b.getX(), -b.getY());
        Shape norm = tx.createTransformedShape(outline);
        String path = toSvgPath(norm);
        if (path.isEmpty()) return null;

        // Height scaled to ~1em; width preserves aspect ratio. vertical-align keeps it on
        // the text baseline. currentColor lets it follow surrounding text colour (dark mode).
        double w = b.getWidth();
        double h = b.getHeight();
        String alt = htmlEscape(text);
        return String.format(Locale.US,
                "<svg class=\"kbd-symbol\" role=\"img\" aria-label=\"%s\" "
                        + "xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.2f %.2f\" "
                        + "style=\"height:1em;width:%.3fem;vertical-align:-0.12em;display:inline-block\">"
                        + "<path d=\"%s\" fill=\"currentColor\"/></svg>",
                alt, w, h, (w / h), path);
    }

    /** Finds a font that can display the code point, preferring the candidate list. */
    private static Font resolveFont(int codePoint) {
        for (String name : CANDIDATE_FONTS) {
            Font f = new Font(name, Font.PLAIN, 100);
            // A logical font ("SansSerif") always reports its family as the logical name;
            // canDisplay still reflects real glyph coverage via the underlying physical font.
            if (f.canDisplay(codePoint)) {
                return f;
            }
        }
        // Last resort: scan installed fonts (may be empty in some headless setups).
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            for (Font f : ge.getAllFonts()) {
                if (f.canDisplay(codePoint)) {
                    return f.deriveFont(100f);
                }
            }
        } catch (Throwable ignore) {
            // Headless / no fonts — fall through.
        }
        return null;
    }

    /** Serialises a {@link Shape} to an SVG path {@code d} attribute string. */
    private static String toSvgPath(Shape shape) {
        StringBuilder sb = new StringBuilder();
        PathIterator it = shape.getPathIterator(null);
        double[] c = new double[6];
        while (!it.isDone()) {
            int type = it.currentSegment(c);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    sb.append(String.format(Locale.US, "M%.2f %.2f ", c[0], c[1]));
                    break;
                case PathIterator.SEG_LINETO:
                    sb.append(String.format(Locale.US, "L%.2f %.2f ", c[0], c[1]));
                    break;
                case PathIterator.SEG_QUADTO:
                    sb.append(String.format(Locale.US, "Q%.2f %.2f %.2f %.2f ", c[0], c[1], c[2], c[3]));
                    break;
                case PathIterator.SEG_CUBICTO:
                    sb.append(String.format(Locale.US, "C%.2f %.2f %.2f %.2f %.2f %.2f ",
                            c[0], c[1], c[2], c[3], c[4], c[5]));
                    break;
                case PathIterator.SEG_CLOSE:
                    sb.append("Z ");
                    break;
                default:
                    break;
            }
            it.next();
        }
        return sb.toString().trim();
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
