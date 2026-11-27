/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.aichat;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/** Clipboard contents carrying both HTML and plain-text flavors. */
public final class RichTextSelection implements Transferable {
    private static final DataFlavor HTML_FLAVOR;
    static {
        try {
            HTML_FLAVOR = new DataFlavor("text/html;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** JavaScript returning {@code plainText + '\u0001' + html} for the current selection, or null. */
    public static final String SELECTION_SCRIPT =
            "(function(){var s=window.getSelection();"
            + "if(!s||s.rangeCount==0||s.isCollapsed)return null;"
            + "var d=document.createElement('div');"
            + "for(var i=0;i<s.rangeCount;i++)d.appendChild(s.getRangeAt(i).cloneContents());"
            + "return s.toString()+'\u0001'+d.innerHTML;})()";

    private final String text;
    private final String html;

    public RichTextSelection(String text, String html) {
        this.text = text;
        this.html = html;
    }

    /**
     * Builds a selection from the result of {@link #SELECTION_SCRIPT}, or returns null
     * if the result is empty.
     */
    public static RichTextSelection fromScriptResult(Object result) {
        if (!(result instanceof String s)) return null;
        int sep = s.indexOf('\u0001');
        if (sep <= 0) return null;
        return new RichTextSelection(s.substring(0, sep),
                "<html><body>" + s.substring(sep + 1) + "</body></html>");
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {HTML_FLAVOR, DataFlavor.stringFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return HTML_FLAVOR.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (HTML_FLAVOR.equals(flavor)) return html;
        if (DataFlavor.stringFlavor.equals(flavor)) return text;
        throw new UnsupportedFlavorException(flavor);
    }
}
