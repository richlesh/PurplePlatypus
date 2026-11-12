/*
 * (c) 2026 Glowing Cat Software
 *
 * Preview pane: right-clicking selected text asks Java (window.java.showSelectionMenu)
 * to show a "Copy Markdown / Copy HTML / Find in Source" menu.
 *
 * The payload carries three views of the selection:
 *   text          - plain selected text (for Find in Source)
 *   html          - the selected HTML, wrapped in shallow copies of its ancestors so a
 *                   partial selection keeps its context (link, list item, code block...)
 *   markdownHtml  - the same HTML prepared for HTML->Markdown conversion: rendered
 *                   constructs that a generic converter cannot round-trip (math, Mermaid,
 *                   emoji, images, alerts, footnotes) are mapped back to their source.
 *                   Where exact text is needed it is replaced by a placeholder token
 *                   (PPZTOK<n>PP) whose Markdown is supplied in `tokens`, so the converter
 *                   never escapes or reinterprets it.
 *
 * With no selection the event is left alone (e.g. the image/diagram lightbox handler).
 */
(function () {
    var STOP_AT = { BODY: true, HTML: true };

    function toArray(list) { return Array.prototype.slice.call(list); }

    // Record each MathJax expression's TeX source on its output element so it survives
    // cloning. Best effort: does nothing if MathJax is absent or its API differs.
    function tagMathSource() {
        try {
            var doc = window.MathJax && MathJax.startup && MathJax.startup.document;
            if (!doc || !doc.math) return;
            var items = doc.math.toArray ? doc.math.toArray() : toArray(doc.math);
            items.forEach(function (item) {
                var root = item.typesetRoot;
                if (root && root.setAttribute) {
                    root.setAttribute('data-tex', item.math);
                    root.setAttribute('data-display', item.display ? '1' : '0');
                }
            });
        } catch (e) { /* ignore */ }
    }

    function cloneSelection(sel) {
        var container = document.createElement('div');
        for (var i = 0; i < sel.rangeCount; i++) {
            var range = sel.getRangeAt(i);
            var wrapped = range.cloneContents();
            var node = range.commonAncestorContainer;
            if (node.nodeType !== 1) node = node.parentNode;
            for (var n = node; n && n.nodeType === 1 && !STOP_AT[n.tagName]; n = n.parentNode) {
                var shell = n.cloneNode(false);
                shell.appendChild(wrapped);
                wrapped = shell;
            }
            container.appendChild(wrapped);
        }
        return container;
    }

    function prepareForMarkdown(root, tokens) {
        function token(markdown) {
            tokens.push(markdown);
            return document.createTextNode('PPZTOK' + (tokens.length - 1) + 'PP');
        }
        function each(selector, fn) { toArray(root.querySelectorAll(selector)).forEach(fn); }
        function replace(el, node) { if (el.parentNode) el.parentNode.replaceChild(node, el); }

        // Math -> original TeX
        each('mjx-container', function (el) {
            var tex = el.getAttribute('data-tex');
            if (tex === null) return;
            var display = el.getAttribute('data-display') === '1';
            replace(el, token(display ? '$$' + tex + '$$' : '$' + tex + '$'));
        });

        // Mermaid diagrams -> fenced source block
        each('div.mermaid', function (el) {
            var src = el.getAttribute('data-src');
            if (src === null) return;
            var pre = document.createElement('pre');
            var code = document.createElement('code');
            code.className = 'language-mermaid';
            code.textContent = src;
            pre.appendChild(code);
            replace(el, pre);
        });

        // Emoji and keyboard-symbol images -> their character (or :shortcode: for
        // GitHub-only emoji, whose alt text is the shortcode)
        each('img.emoji, img.kbd-symbol', function (el) {
            replace(el, document.createTextNode(el.getAttribute('alt') || ''));
        });

        // Remaining images -> Markdown image syntax, using the author's original path
        each('img', function (el) {
            var src = el.getAttribute('data-md-src') || el.getAttribute('src') || '';
            var alt = (el.getAttribute('alt') || '').replace(/[\[\]\\]/g, '\\$&');
            var title = el.getAttribute('title');
            var md = '![' + alt + '](' + src.replace(/ /g, '%20')
                + (title ? ' "' + title.replace(/"/g, '\\"') + '"' : '') + ')';
            var attrs = [];
            ['width', 'height'].forEach(function (a) {
                if (el.hasAttribute(a)) attrs.push(a + '=' + el.getAttribute(a));
            });
            if (attrs.length) md += '{' + attrs.join(' ') + '}';
            replace(el, token(md));
        });

        // Figure captions duplicate the image alt text; the converter treats an unknown
        // <figure> as inline content, so make it a paragraph to keep it a block.
        each('figcaption', function (el) { el.parentNode.removeChild(el); });
        each('figure', function (el) {
            var p = document.createElement('p');
            while (el.firstChild) p.appendChild(el.firstChild);
            replace(el, p);
        });

        // GitHub alerts -> > [!TYPE] blockquotes
        each('div.markdown-alert', function (el) {
            var m = /markdown-alert-(note|tip|important|warning|caution)/.exec(el.className);
            var title = el.querySelector('.markdown-alert-title');
            if (title) title.parentNode.removeChild(title);
            var quote = document.createElement('blockquote');
            var marker = document.createElement('p');
            marker.appendChild(token('[!' + (m ? m[1].toUpperCase() : 'NOTE') + ']'));
            quote.appendChild(marker);
            while (el.firstChild) quote.appendChild(el.firstChild);
            replace(el, quote);
        });

        // Footnote references -> [^label]
        each('sup.footnote-ref', function (el) {
            var a = el.querySelector('a[href^="#fn-"]');
            if (a) replace(el, token('[^' + a.getAttribute('href').substring(4) + ']'));
        });

        // Footnote definitions -> [^label]: text
        each('a.footnote-backref', function (el) { el.parentNode.removeChild(el); });
        each('section.footnotes', function (section) {
            // A fragment, not a <div>: the converter would treat a div's paragraphs as
            // inline content and join the definition to the preceding line.
            var out = document.createDocumentFragment();
            toArray(section.querySelectorAll('li[id^="fn-"]')).forEach(function (li) {
                var marker = token('[^' + li.id.substring(3) + ']: ');
                var first = li.querySelector('p');
                if (first) first.insertBefore(marker, first.firstChild);
                else li.insertBefore(marker, li.firstChild);
                while (li.firstChild) out.appendChild(li.firstChild);
            });
            replace(section, out);
        });

        // Syntax-highlighting class would otherwise end up in the fence info string
        each('code', function (el) { el.classList.remove('hljs'); });
    }

    document.addEventListener('contextmenu', function (e) {
        var sel = window.getSelection();
        if (!sel || sel.isCollapsed || !window.java) return;
        var text = sel.toString();
        if (!text || !text.trim()) return;
        e.preventDefault();

        tagMathSource();
        var html = cloneSelection(sel).innerHTML;
        var tokens = [];
        var mdRoot = cloneSelection(sel);
        prepareForMarkdown(mdRoot, tokens);

        window.java.showSelectionMenu(JSON.stringify({
            text: text,
            html: html,
            markdownHtml: mdRoot.innerHTML,
            tokens: tokens,
            x: e.clientX,
            y: e.clientY
        }));
    });
})();
