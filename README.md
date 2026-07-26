![app_icon_256](src/main/resources/app_icon_256.png)

# PurplePlatypus 1.2.0

A lightweight desktop Markdown editor built with Java Swing, featuring a live preview pane that renders your Markdown as you type and an AI writing assistant powered by LLM APIs.

## Features

- **Split-pane editor** — Write Markdown on the left, see the rendered HTML preview on the right, with synchronized scrolling so the preview stays aligned with your position in the editor
- **Live preview** — The preview updates in real time as you type, with no manual refresh needed
- **AI writing assistant** — Built-in chat panel powered by LLM APIs to help draft, edit, and improve your markdown content
- **Multi-vendor LLM support** — Connect to OpenAI, Anthropic, Google, DeepSeek, Alibaba, Cerebras, Groq, Meta, Mistral, Moonshot AI, Perplexity, xAI, or local Ollama models
- **Multi-window** — Open multiple editor windows with File > New
- **Window management** — Window menu with Minimize, Zoom, Previous/Next window navigation, Cascade All, and Tile All
- **Cross-platform** — Runs on macOS (ARM64), Windows (x64, ARM64), and Linux (x64, ARM64)
- **Native look and feel** — Uses the platform's native UI (Aqua on macOS, Windows 11 on Windows, GTK on Linux)
- **macOS integration** — Menu bar in the system menu bar, About and Preferences in the application menu, native file dialogs, Command key shortcuts
- **Toolbar** — Status bar showing the full file path with document statistics, toggle buttons to show/hide invisible characters, synchronized scrolling, preview, and AI panels
- **Line numbers** — A line number gutter on the left side of the editor that stays in sync as you scroll and type
- **File operations** — Create new files, open existing `.md`/`.markdown`/`.txt`/`.textbundle`/`.textpack` files, and save your work
- **TextBundle support** — Open and export `.textbundle` packages with images in the `assets/` subfolder; `.textbundle` directories appear as selectable files in the Open dialog on all platforms
- **TextPack support** — Open and export `.textpack` files (compressed TextBundle archives); Save is disabled for files opened from a TextPack (use Save As or Export instead)
- **Dirty checking** — Prompts to save unsaved changes when closing a window or quitting the application
- **Undo/Redo** — Full multi-level undo and redo support
- **Clipboard** — Cut, Copy, and Paste via the Edit menu
- **Markdown formatting** — Bold, Italic, Underline, Strikethrough, Superscript, Subscript, and Insert (++underline++) via the Markdown menu (enabled when text is selected)
- **Headings** — Insert Heading 1 through Heading 6 via the Markdown menu (replaces any existing heading prefix)
- **Horizontal Rule** — Insert a `---` separator via the Markdown menu
- **Footnotes** — Insert footnote references and definitions via the Markdown menu
- **Image drag-and-drop** — Drag GIF, JPEG, or PNG files onto the editor to insert markdown image links with relative paths; the caret tracks the pointer for precise placement
- **Links and Images** — Insert or edit markdown links and images via dialogs
- **Tables** — Insert or edit GFM-style markdown tables via a visual dialog
- **Lists** — Convert lines to ordered, unordered, or task lists
- **Block formatting** — Block Quote, Inline Code, Block Code, Inline Math, and Block Math
- **Print** — Page Setup and Print (⌘/Ctrl+P) using the native system print dialog
- **Export** — Export to HTML, PDF, TextBundle, TextPack, RTF, or Plain Text formats
- **Find** — Search with options for Match Case, Wrap Around, Search Backwards, Find in Selection, and Regular Expression (remembers the original selection for repeated searches)
- **Find All** — Opens a results window showing matching lines with highlighted text; click a match to jump to it in the editor
- **Count** — Quickly count the number of matches in the document or selection
- **Replace** — Find and replace with Replace, Replace and Find, and Replace All operations
- **Escape sequences in Find/Replace** — "Interpret Escapes" option processes `\t`, `\n`, `\r`, `\\`, and `\uXXXX` in find and replace fields
- **Line ending conversion** — Detect and convert between Unix (`\n`) and Windows (`\r\n`) line endings via the Edit menu; line ending format is preserved on save
- **Show invisible characters** — Toggle button in the toolbar to reveal spaces (dots), tabs (arrows), and line endings (paragraph marks)
- **Document statistics** — Live display of line count, word count, and character count in the toolbar, updated as you type
- **External change detection** — Detects when a file is modified on disk by another program and prompts to reload or keep current changes

- **Markdown support** — CommonMark with extensions: GFM tables, strikethrough, task lists, autolink, footnotes, heading anchors, image attributes, ins (underline), and YAML front matter
- **Styled preview** — Clean, readable HTML output with custom CSS styling and MathJax support
- **Preview fallback** — On platforms where JavaFX WebView is unavailable (e.g. Windows ARM64), the preview gracefully falls back to a Swing-based HTML renderer with reduced functionality
- **Preferences** — Configurable font family and size for editor, preview, and AI chat panes; LLM vendor/model/API key settings; chat bubble colors
- **Window state persistence** — Window size, divider positions, and panel visibility are remembered between sessions

## AI Assistant

The built-in AI chat panel helps you write and improve markdown content:

- Draft new content (paragraphs, sections, lists, tables)
- Improve existing text (grammar, clarity, tone, structure)
- Add markdown formatting and suggest document organization
- Generate tables from descriptions
- Help with technical writing, blog posts, documentation, and READMEs

When the AI suggests a complete document replacement, you're given Allow/Reject buttons to accept or decline the changes.

Configure your LLM provider in Preferences (vendor, model, and API key).

## Export Formats

- **HTML** — Fully styled HTML with CSS, relative image paths preserved
- **PDF** — Print-to-file via the system's PDF output
- **TextBundle** — Standard `.textbundle` package with `text.md`, `info.json`, and images copied into `assets/` (preserving subfolder hierarchy)
- **TextPack** — Compressed `.textpack` file (zipped TextBundle) for easy sharing
- **RTF** — Rich Text Format with headings, bold, italic, strikethrough, code, lists, and block quotes
- **Plain Text** — Export markdown content as plain text with formatting stripped

## Platform Support

| Platform | Preview | Status |
|----------|---------|--------|
| macOS ARM64 | JavaFX WebView | Full support |
| Windows x64 | JavaFX WebView | Full support |
| Windows ARM64 | Swing JEditorPane | Reduced (no MathJax/advanced CSS) |
| Linux x64 | JavaFX WebView | Full support |
| Linux ARM64 | JavaFX WebView | Full support |

## Requirements

- Java 21 or later
- Maven 3.6+

## Building

```bash
mvn compile
```

## Running

```bash
mvn exec:java -Dexec.mainClass="com.glowingcat.Main"
```

Or run `com.glowingcat.Main` directly from your IDE.

## Tech Stack

- **Java Swing** — GUI framework
- **commonmark-java** — Markdown parsing and HTML rendering (with GFM tables, strikethrough, task lists, autolink, footnotes, heading anchors, image attributes, ins, and YAML front matter extensions)
- **RSyntaxTextArea** — Syntax-aware text editor component
- **JavaFX WebView** — HTML preview rendering with MathJax (with JEditorPane fallback)
- **Gson** — JSON serialization for user preferences
- **java.net.http** — HTTP client for LLM API calls

---

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE) for details.

© 2026 Richard Lesh
