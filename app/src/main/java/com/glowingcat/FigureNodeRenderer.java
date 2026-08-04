/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.commonmark.node.*;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.*;

import java.util.*;

/**
 * Custom HTML node renderer that renders a paragraph containing only a single image
 * (with non-empty alt text) as a {@code <figure>} element with a {@code <figcaption>},
 * similar to Pandoc's implicit figure behavior.
 * <p>
 * A paragraph qualifies as a figure if:
 * <ul>
 *   <li>Its only child is an Image node (possibly wrapped in a single link)</li>
 *   <li>The image has non-empty alt text</li>
 * </ul>
 */
public class FigureNodeRenderer implements NodeRenderer {

    private final HtmlWriter html;
    private final HtmlNodeRendererContext context;

    public FigureNodeRenderer(HtmlNodeRendererContext context) {
        this.context = context;
        this.html = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(Paragraph.class);
    }

    @Override
    public void render(Node node) {
        Paragraph paragraph = (Paragraph) node;

        // Check if this paragraph contains only a single image (or image inside a link)
        Image image = getSoleImage(paragraph);
        if (image != null && !getAltText(image).isEmpty()) {
            String altText = getAltText(image);

            html.line();
            html.tag("figure");
            // Render the image (and link wrapper if present) normally
            Node child = paragraph.getFirstChild();
            while (child != null) {
                Node next = child.getNext();
                context.render(child);
                child = next;
            }
            html.tag("figcaption");
            html.text(altText);
            html.tag("/figcaption");
            html.tag("/figure");
            html.line();
        } else {
            // Default paragraph rendering
            html.line();
            html.tag("p");
            Node child = paragraph.getFirstChild();
            while (child != null) {
                Node next = child.getNext();
                context.render(child);
                child = next;
            }
            html.tag("/p");
            html.line();
        }
    }

    /**
     * Returns the Image node if the paragraph contains only a single image,
     * optionally wrapped in a link. Returns null otherwise.
     */
    private Image getSoleImage(Paragraph paragraph) {
        Node firstChild = paragraph.getFirstChild();
        if (firstChild == null) return null;

        // Single image directly in paragraph
        if (firstChild instanceof Image && firstChild.getNext() == null) {
            return (Image) firstChild;
        }

        // Image wrapped in a link: <a><img></a>
        if (firstChild instanceof Link && firstChild.getNext() == null) {
            Node linkChild = firstChild.getFirstChild();
            if (linkChild instanceof Image && linkChild.getNext() == null) {
                return (Image) linkChild;
            }
        }

        return null;
    }

    /**
     * Extracts the alt text from an Image node by collecting text from its children.
     */
    private String getAltText(Image image) {
        StringBuilder sb = new StringBuilder();
        Node child = image.getFirstChild();
        while (child != null) {
            if (child instanceof Text) {
                sb.append(((Text) child).getLiteral());
            }
            child = child.getNext();
        }
        return sb.toString().trim();
    }

    /**
     * Factory for creating FigureNodeRenderer instances.
     */
    public static class Factory implements HtmlNodeRendererFactory {
        @Override
        public NodeRenderer create(HtmlNodeRendererContext context) {
            return new FigureNodeRenderer(context);
        }
    }
}
