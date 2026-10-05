package com.glowingcat;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class LinkValidatorTest {

    @Test
    public void classifiesLinkTypes() {
        String md = String.join("\n",
                "[web](https://example.com)",
                "[anchor](#section-one)",
                "[file](other.md)",
                "[fileRef](docs/other.md#heading)",
                "[mail](mailto:a@b.com)",
                "![img](images/pic.png)",
                "<https://auto.example.com>");

        List<LinkValidator.MarkdownLink> links = LinkValidator.extractLinks(md);
        // 6 inline (incl. image) + 1 autolink
        assertEquals(7, links.size());

        assertEquals(LinkValidator.LinkType.WEB, links.get(0).type);
        assertEquals(LinkValidator.LinkType.ANCHOR, links.get(1).type);
        assertEquals("section-one", links.get(1).fragment);
        assertEquals(LinkValidator.LinkType.LOCAL_FILE, links.get(2).type);
        assertEquals(LinkValidator.LinkType.LOCAL_FILE, links.get(3).type);
        assertEquals("docs/other.md", links.get(3).path);
        assertEquals("heading", links.get(3).fragment);
        assertEquals(LinkValidator.LinkType.OTHER, links.get(4).type);  // mailto:
        assertEquals(LinkValidator.LinkType.LOCAL_FILE, links.get(5).type);
        assertTrue(links.get(5).image);
        assertEquals(LinkValidator.LinkType.WEB, links.get(6).type);    // autolink
    }

    @Test
    public void reportsLineNumbers() {
        String md = "line one\nline two [x](a.md)\n\nline four [y](#z)";
        List<LinkValidator.MarkdownLink> links = LinkValidator.extractLinks(md);
        assertEquals(2, links.size());
        assertEquals(2, links.get(0).line);
        assertEquals(4, links.get(1).line);
    }

    @Test
    public void slugMatchesHeadingAnchorRules() {
        assertEquals("lanthanides-and-actinides",
                LinkValidator.slug("Lanthanides and Actinides"));
        assertEquals("hello-world", LinkValidator.slug("Hello, World!"));
        assertEquals("a-b-c", LinkValidator.slug("A   B   C"));
    }

    @Test
    public void collectsHeadingSlugsAndResolvesAnchors() {
        String md = String.join("\n",
                "# Title",
                "## Section One",
                "### Deep-Dive",
                "text");
        Set<String> slugs = LinkValidator.collectHeadingSlugs(md);
        assertTrue(slugs.contains("title"));
        assertTrue(slugs.contains("section-one"));
        assertTrue(slugs.contains("deep-dive"));

        assertTrue(LinkValidator.anchorResolves("section-one", slugs));
        assertTrue(LinkValidator.anchorResolves("Section-One", slugs)); // case-insensitive
        assertFalse(LinkValidator.anchorResolves("missing", slugs));
    }

    @Test
    public void resolvesLocalFileRelativeToBase() throws Exception {
        File tmp = Files.createTempDirectory("linkval").toFile();
        File sub = new File(tmp, "docs");
        assertTrue(sub.mkdirs());
        File target = new File(sub, "other file.md"); // space to test decoding
        Files.writeString(target.toPath(), "# Heading A\n");

        File resolved = LinkValidator.resolveLocalFile(tmp, "docs/other%20file.md");
        assertNotNull(resolved);
        assertTrue("Should resolve and exist: " + resolved, resolved.exists());

        Set<String> slugs = LinkValidator.headingSlugsOf(resolved);
        assertTrue(slugs.contains("heading-a"));
    }

    @Test
    public void missingLocalFileDoesNotExist() throws Exception {
        File tmp = Files.createTempDirectory("linkval2").toFile();
        File resolved = LinkValidator.resolveLocalFile(tmp, "nope.md");
        assertNotNull(resolved);
        assertFalse(resolved.exists());
    }

    @Test
    public void isMarkdownFileByExtension() {
        assertTrue(LinkValidator.isMarkdownFile("a.md"));
        assertTrue(LinkValidator.isMarkdownFile("A.MARKDOWN"));
        assertFalse(LinkValidator.isMarkdownFile("a.txt"));
        assertFalse(LinkValidator.isMarkdownFile("a.png"));
    }

    @Test
    public void htmlExtensionDetectionAndRewrite() {
        assertTrue(LinkValidator.isHtmlFile("page.html"));
        assertTrue(LinkValidator.isHtmlFile("PAGE.HTM"));
        assertFalse(LinkValidator.isHtmlFile("page.md"));
        assertEquals("page.md", LinkValidator.htmlToMarkdownPath("page.html"));
        assertEquals("docs/page.md", LinkValidator.htmlToMarkdownPath("docs/page.htm"));
        assertEquals("page.md", LinkValidator.htmlToMarkdownPath("page.md")); // unchanged
    }

    @Test
    public void htmlFallbackResolvesToSiblingMarkdown() throws Exception {
        File tmp = Files.createTempDirectory("linkvalhtml").toFile();
        File md = new File(tmp, "page.md");
        Files.writeString(md.toPath(), "# Heading\n");
        // No page.html exists, but page.md does -> fallback returns the .md file.
        File resolved = LinkValidator.resolveLocalFileWithHtmlFallback(tmp, "page.html");
        assertNotNull(resolved);
        assertTrue(resolved.exists());
        assertEquals("page.md", resolved.getName());
    }

    @Test
    public void htmlFallbackPrefersExistingHtml() throws Exception {
        File tmp = Files.createTempDirectory("linkvalhtml2").toFile();
        File html = new File(tmp, "page.html");
        Files.writeString(html.toPath(), "<html></html>");
        // page.html exists -> returned as-is (no fallback), even if a .md also exists.
        File resolved = LinkValidator.resolveLocalFileWithHtmlFallback(tmp, "page.html");
        assertNotNull(resolved);
        assertTrue(resolved.exists());
        assertEquals("page.html", resolved.getName());
    }

    @Test
    public void htmlFallbackReturnsNonexistentOriginalWhenNeitherExists() throws Exception {
        File tmp = Files.createTempDirectory("linkvalhtml3").toFile();
        File resolved = LinkValidator.resolveLocalFileWithHtmlFallback(tmp, "missing.html");
        assertNotNull(resolved);
        assertFalse(resolved.exists());
        assertEquals("missing.html", resolved.getName());
    }
}
