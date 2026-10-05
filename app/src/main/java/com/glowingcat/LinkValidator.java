/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts and classifies the links in a Markdown document and resolves the
 * ones that can be checked without network access (internal heading anchors and
 * local files). Web URL reachability is checked separately (off the EDT) by the
 * caller via {@link #isWebLinkReachable(String, int)}.
 * <p>
 * This class is deliberately free of Swing/UI dependencies so the extraction and
 * classification logic can be unit-tested.
 */
public final class LinkValidator {

    /** The kind of target a Markdown link points at. */
    public enum LinkType {
        /** Same-document heading anchor, e.g. {@code #section}. */
        ANCHOR,
        /** A local file path (relative or absolute), optionally with a {@code #fragment}. */
        LOCAL_FILE,
        /** A {@code http://} or {@code https://} web URL. */
        WEB,
        /** Anything else we don't validate (e.g. {@code mailto:}, {@code data:}). */
        OTHER
    }

    /** A single link found in the document. */
    public static final class MarkdownLink {
        /** Visible link text (may be empty). */
        public final String text;
        /** Raw href target as written in the source (percent-encoding preserved). */
        public final String target;
        /** Path portion of the target (target without any {@code #fragment}). */
        public final String path;
        /** Fragment portion (after {@code #}), or null if none. */
        public final String fragment;
        /** Whether the link was an image link ({@code ![...]}). */
        public final boolean image;
        /** Classified link type. */
        public final LinkType type;
        /** Character offset of the target within the document. */
        public final int offset;
        /** 1-based line number of the target. */
        public final int line;

        MarkdownLink(String text, String target, String path, String fragment,
                     boolean image, LinkType type, int offset, int line) {
            this.text = text;
            this.target = target;
            this.path = path;
            this.fragment = fragment;
            this.image = image;
            this.type = type;
            this.offset = offset;
            this.line = line;
        }
    }

    // Inline links and images: optional leading '!', [text](target). The target
    // group stops at the first ')' or whitespace, matching the lenient style used
    // elsewhere in the app.
    private static final Pattern INLINE_LINK = Pattern.compile(
            "(!?)\\[([^\\]]*)\\]\\(\\s*([^)\\s]+)(?:\\s+\"[^\"]*\")?\\s*\\)");

    // Autolinks: <https://example.com>
    private static final Pattern AUTOLINK = Pattern.compile(
            "<((?:https?)://[^>\\s]+)>");

    private LinkValidator() {}

    /**
     * Extracts all inline links, images, and autolinks from the given Markdown.
     *
     * @param markdown the document source
     * @return the links in document order
     */
    public static List<MarkdownLink> extractLinks(String markdown) {
        List<MarkdownLink> links = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return links;

        Matcher m = INLINE_LINK.matcher(markdown);
        while (m.find()) {
            boolean image = !m.group(1).isEmpty();
            String text = m.group(2);
            String rawTarget = m.group(3).trim();
            int targetOffset = m.start(3);
            links.add(buildLink(text, rawTarget, image, targetOffset, markdown));
        }

        Matcher a = AUTOLINK.matcher(markdown);
        while (a.find()) {
            String rawTarget = a.group(1).trim();
            int targetOffset = a.start(1);
            links.add(buildLink(rawTarget, rawTarget, false, targetOffset, markdown));
        }
        return links;
    }

    private static MarkdownLink buildLink(String text, String rawTarget, boolean image,
                                          int targetOffset, String markdown) {
        String path = rawTarget;
        String fragment = null;
        int hash = rawTarget.indexOf('#');
        if (hash >= 0) {
            path = rawTarget.substring(0, hash);
            fragment = rawTarget.substring(hash + 1);
        }
        LinkType type = classify(rawTarget, path);
        int line = lineOf(markdown, targetOffset);
        return new MarkdownLink(text, rawTarget, path, fragment, image, type, targetOffset, line);
    }

    /** Classifies a raw href into a {@link LinkType}. */
    static LinkType classify(String rawTarget, String path) {
        String lower = rawTarget.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return LinkType.WEB;
        }
        // Pure fragment (no path) => same-document anchor.
        if (path.isEmpty() && rawTarget.startsWith("#")) {
            return LinkType.ANCHOR;
        }
        // Other URI schemes we don't validate: mailto:, tel:, data:, ftp:, etc.
        // Detect a scheme as "letters followed by ':' before any '/'".
        int colon = rawTarget.indexOf(':');
        int slash = rawTarget.indexOf('/');
        if (colon > 0 && (slash < 0 || colon < slash)) {
            String scheme = rawTarget.substring(0, colon);
            if (scheme.matches("[A-Za-z][A-Za-z0-9+.-]*")) {
                return LinkType.OTHER;
            }
        }
        // Protocol-relative URLs (//host/...) are web links we won't resolve locally.
        if (rawTarget.startsWith("//")) {
            return LinkType.OTHER;
        }
        return LinkType.LOCAL_FILE;
    }

    /** Returns the 1-based line number containing the given character offset. */
    static int lineOf(String text, int offset) {
        int line = 1;
        int limit = Math.min(offset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    /**
     * Generates a heading anchor slug the same way the preview's
     * HeadingAnchorExtension does: lowercase, strip punctuation (keep letters,
     * digits, spaces, hyphens), trim, and replace runs of whitespace with hyphens.
     */
    public static String slug(String headingText) {
        return headingText.toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }

    /** Collects the set of heading-anchor slugs defined in the given Markdown. */
    public static Set<String> collectHeadingSlugs(String markdown) {
        Set<String> slugs = new HashSet<>();
        if (markdown == null) return slugs;
        Pattern heading = Pattern.compile("(?m)^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
        Matcher m = heading.matcher(markdown);
        while (m.find()) {
            slugs.add(slug(m.group(2).trim()));
        }
        return slugs;
    }

    /**
     * Resolves a local-file link path against a base directory, decoding
     * percent-encoding. Returns the resolved file (not guaranteed to exist).
     *
     * @param baseDir the directory of the document (may be null)
     * @param path    the link's path portion (no fragment)
     * @return the resolved File, or null if the path is empty
     */
    public static File resolveLocalFile(File baseDir, String path) {
        if (path == null || path.isEmpty()) return null;
        String decoded = path;
        try {
            decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            // use raw path
        }
        File f = new File(decoded);
        if (!f.isAbsolute() && baseDir != null) {
            f = new File(baseDir, decoded);
        }
        return f;
    }

    /**
     * Checks whether a same-document anchor resolves to a heading in the given slug set.
     */
    public static boolean anchorResolves(String fragment, Set<String> headingSlugs) {
        if (fragment == null || fragment.isEmpty()) return false;
        return headingSlugs.contains(fragment.toLowerCase());
    }

    /**
     * Reads a Markdown file and returns its heading slug set, for validating a
     * {@code other.md#section} cross-file fragment. Returns an empty set if the
     * file cannot be read.
     */
    public static Set<String> headingSlugsOf(File mdFile) {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(mdFile.toPath()),
                    StandardCharsets.UTF_8);
            return collectHeadingSlugs(content);
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    /** Returns true if the given path looks like a Markdown file by extension. */
    public static boolean isMarkdownFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    /**
     * Performs a lightweight reachability check for a web URL. Must be called off
     * the EDT. Tries an HTTP HEAD request (falling back to GET) and treats any
     * response with status &lt; 400 as reachable. Network/timeout errors return false.
     *
     * @param url            the web URL to check
     * @param timeoutMillis  per-request timeout
     * @return true if the server responded with a non-error status
     */
    public static boolean isWebLinkReachable(String url, int timeoutMillis) {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(timeoutMillis))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        try {
            java.net.http.HttpRequest head = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofMillis(timeoutMillis))
                    .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "PurplePlatypus-LinkValidator")
                    .build();
            int status = client.send(head,
                    java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 405 || status == 403 || status == 501) {
                // Some servers reject HEAD; retry with a ranged GET.
                return getReachable(client, url, timeoutMillis);
            }
            return status < 400;
        } catch (Exception e) {
            // HEAD failed outright — try GET before giving up.
            try {
                return getReachable(client, url, timeoutMillis);
            } catch (Exception e2) {
                return false;
            }
        }
    }

    private static boolean getReachable(java.net.http.HttpClient client, String url, int timeoutMillis)
            throws Exception {
        java.net.http.HttpRequest get = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofMillis(timeoutMillis))
                .header("User-Agent", "PurplePlatypus-LinkValidator")
                .header("Range", "bytes=0-0")
                .GET()
                .build();
        int status = client.send(get,
                java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
        return status < 400;
    }
}
