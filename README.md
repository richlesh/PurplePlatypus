![](app/src/main/resources/app_icon_256.png)

# PurplePlatypus 1.8.0

A lightweight desktop Markdown editor built with Java Swing, featuring a live preview pane that renders your Markdown as you type and an AI writing assistant powered by LLM APIs.

## Features

- **Split-pane editor** — Write Markdown on the left, see the rendered HTML preview on the right, with synchronized scrolling so the preview stays aligned with your position in the editor
- **Live preview** — The preview updates in real time as you type, with no manual refresh needed
- **AI writing assistant** — Built-in chat panel powered by LLM APIs to help draft, edit, and improve your markdown content
- **Live spell checker** — Real-time spell and grammar checking powered by LanguageTool with support for 31 languages; right-click suggestions and user dictionary; language packs download on demand from Maven Central
- **Localization** — Full UI localization in English, Spanish, French, German, Italian, Japanese, and Simplified Chinese; select language in Preferences; RTL content support (Arabic, Hebrew) in preview and AI chat
- **Multi-vendor LLM support** — Connect to OpenAI, Anthropic, Google, Amazon Bedrock, IBM watsonx, Microsoft Azure, DeepSeek, Alibaba, Cerebras, Groq, Meta, Mistral, Moonshot AI, Perplexity, xAI, or local Ollama models; also supports a Generic OpenAI API endpoint for any OpenAI-compatible service
- **Generic LLM vendor** — YAML-configurable API endpoint for any LLM service (corporate APIs, custom proxies, etc.) with OAuth/IAM token exchange, configurable request/response format, and single-shot or multi-turn conversation modes
- **Multi-window** — Open multiple editor windows with File > New
- **Window management** — Window menu with Minimize, Zoom, Previous/Next window navigation, Cascade All, and Tile All
- **Cross-platform** — Runs on macOS (ARM64), Windows (x64, ARM64), and Linux (x64, ARM64)
- **Native look and feel** — Uses the platform's native UI (Aqua on macOS, Windows 11 on Windows, GTK on Linux)
- **macOS integration** — Menu bar in the system menu bar, About and Preferences in the application menu, native file dialogs, Command key shortcuts
- **Toolbar** — Status bar showing the full file path with document statistics, toggle buttons for word wrap, invisible characters, spell check, synchronized scrolling, preview, reload, AI panel and light/dark UI modes.
- **Dark mode** — Toggle between light and dark themes; dark mode applies to the editor, preview, AI chat, toolbar, and dialogs
- **Line numbers** — A line number gutter on the left side of the editor that stays in sync as you scroll and type
- **File operations** — Create new files, open existing `.md`/`.markdown`/`.txt`/`.textbundle`/`.textpack` files, and save your work
- **Recent files** — File > Recents menu shows recently opened documents; selecting one brings its window to front if already open
- **TextBundle support** — Open and export `.textbundle` packages with images in the `assets/` subfolder; `.textbundle` directories appear as selectable files in the Open dialog on all platforms
- **TextPack support** — Open and export `.textpack` files (compressed TextBundle archives); Save is disabled for files opened from a TextPack (use Save As or Export instead)
- **Dirty checking** — Prompts to save unsaved changes when closing a window or quitting the application
- **Undo/Redo** — Full multi-level undo and redo support
- **Clipboard** — Cut, Copy, and Paste via the Edit menu
- **Markdown formatting** — Bold, Italic, Underline, Strikethrough, Superscript, Subscript, Center, and Insert (++underline++) via the Markdown menu (enabled when text is selected)
- **Headings** — Insert Heading 1 through Heading 6 via the Markdown menu (replaces any existing heading prefix)
- **Horizontal Rule** — Insert a `---` separator via the Markdown menu
- **Footnotes** — Insert footnote references and definitions via the Markdown menu
- **Image drag-and-drop** — Drag GIF, JPEG, or PNG files onto the editor to insert markdown image links with relative paths; the caret tracks the pointer for precise placement
- **Lightbox viewer** - View images in a lightbox with zoom and pan features via right-click on image
- **Links and Images** — Insert or edit markdown links and images via dialogs
- **Tables** — Insert or edit GFM pipe-style markdown tables via a visual dialog with dynamic row/column add/remove, clipboard operations, and per-column alignment.  Pandoc grid tables also supported.
- **Lists** — Convert lines to ordered, unordered, or task lists
- **Block formatting** — Block Quote, Inline Code, Block Code, Inline Math, Block Math, and Mermaid Graph
- **Print** — Page Setup and Print (⌘/Ctrl+P) using the native system print dialog
- **Export** — Export to HTML, PDF, Word Document (DOCX), TextBundle, TextPack, RTF, or Plain Text formats
- **Import** — Import from HTML, Plain Text, RTF, or Word Document files, converting content to Markdown
- **Find** — Search with options for Match Case, Wrap Around, Search Backwards, Find in Selection, and Regular Expression (remembers the original selection for repeated searches)
- **Find All** — Opens a results window showing matching lines with highlighted text; click a match to jump to it in the editor
- **Count** — Quickly count the number of matches in the document or selection
- **Replace** — Find and replace with Replace, Replace and Find, and Replace All operations
- **Escape sequences in Find/Replace** — "Interpret Escapes" option processes `\t`, `\n`, `\r`, `\\`, and `\uXXXX` in find and replace fields
- **Search/Replace recents** — Save frequently used search and replace expressions with +/- buttons; recall them from a dropdown menu
- **Find in Preview** — Search for selected editor text in the rendered preview (⇧⌘F); right-click selected preview text to find it in the source
- **Internal link navigation** — Click internal anchor links (e.g. `[Section](#section)`) in the preview to jump to the corresponding heading in both the editor and preview
- **Go to Line** — Jump to a specific line number in the source (⇧⌘J)
- **Line ending conversion** — Detect and convert between Unix (`\n`) and Windows (`\r\n`) line endings via the Edit menu; line ending format is preserved on save
- **Cleanup Pandoc Tables** — Convert Pandoc grid-style tables to standard GFM pipe-style tables
- **Format Table** — Auto-format the GFM table at the cursor, padding columns to uniform width respecting header alignment (left, center, right)
- **Zap Gremlins** — Configurable substitution of Unicode characters (smart quotes, em-dashes, non-breaking spaces, etc.) with ASCII equivalents; user-editable rules saved to preferences
- **HTML Encode** — Convert non-ASCII characters to HTML entities (named where available per HTML5, otherwise numeric code points)
- **Table of Contents** — Create or update a localized Table of Contents section with internal links to headings; user-selectable depth (H2–H6) with nested lists for heading hierarchy
- **Selection-aware editing** — Cleanup Pandoc Tables, Zap Gremlins, and HTML Encode operate on the selection if text is selected, or the full document otherwise
- **Show invisible characters** — Toggle button in the toolbar to reveal spaces (dots), tabs (arrows), and line endings (paragraph marks)
- **Document statistics** — Live display of line count, word count, and character count in the toolbar, updated as you type
- **External change detection** — Detects when a file is modified on disk by another program and prompts to reload or keep current changes
- **Large file performance** — Preview updates and document statistics are debounced for large files; syntax highlighting is disabled above 1 MB; AI context is truncated above 20K characters

- **Markdown support** — CommonMark with extensions: GFM tables, strikethrough, task lists, autolink, footnotes, heading anchors, image attributes, insert (underline), and YAML front matter
- **Styled preview** — Clean, readable HTML output with custom CSS styling and MathJax support
- **Syntax highlighting** — Code blocks in the preview and AI chat are syntax-highlighted via highlight.js with automatic language detection and light/dark theme support
- **Live Spellcheck** - Live spell and grammar check with customizable dictionaries and language support
- **Mermaid diagrams** — Mermaid code blocks are rendered as diagrams in both the preview and AI chat panes and right-click to enlarge in lightbox format.
- **Figure captions** — Images with alt text are rendered as `<figure>` elements with a visible `<figcaption>` in the preview
- **Preview fallback** — On platforms where JavaFX WebView is unavailable (e.g. Windows ARM64), the preview gracefully falls back to a Swing-based HTML renderer with reduced functionality
- **Preferences** — Configurable font family and size for editor, preview, and AI chat panes; LLM vendor/model/API key settings; chat bubble colors; toolbar button highlight color
- **Window state persistence** — Window size, divider positions, and panel visibility are remembered between sessions
- **License key** — Optional license key to support continued development; enter via File > License Key on macOS or the application menu on other platforms
- **Emoji support** — Non-BMP emoji characters are rendered using Twemoji SVG images in both the preview and AI chat panes

## AI Assistant

The built-in AI chat panel helps you write and improve markdown content:

- Draft new content (paragraphs, sections, lists, tables)
- Improve existing text (grammar, clarity, tone, structure)
- Add markdown formatting and suggest document organization
- Generate tables from descriptions
- Help with technical writing, blog posts, documentation, and READMEs

When the AI suggests a document change, you're given Allow/Reject buttons to accept or decline the changes.

The AI chat panel renders responses with full markdown support (tables, code blocks, math, links) via an embedded WebView with MathJax. A copy button on each AI response copies the raw markdown to the clipboard. For large documents, the AI returns efficient unified diffs instead of full replacements, reducing token usage and response time.

Configure your LLM provider in Preferences (vendor, model, and API key). For custom or corporate APIs, select the "Generic" vendor and use "Configure..." to define a YAML configuration with custom endpoints, headers, authentication, and response parsing.

## Generic LLM Vendor

The Generic vendor supports any LLM API through a YAML configuration file (`~/.glowingcat-generic.yml`):

- **Custom endpoints** — Define any REST API URL with variable substitution
- **Flexible authentication** — Bearer tokens, Basic auth, API keys, or OAuth/IAM token exchange
- **Token exchange** — Optional `Auth` section automatically fetches and caches access tokens (supports IBM IAM, OAuth2, etc.)
- **Conversation modes** — Single-shot (with server-side GUID tracking) or multi-turn (full history sent)
- **Response parsing** — JSONPath-like expressions to extract content from any response format
- **Model discovery** — Configurable models endpoint with custom field extraction
- **Variables** — `${AUTH_TOKEN}`, `${MODEL}`, `${PROMPT}`, `${MESSAGES}`, `${MESSAGES_NO_SYSTEM}`, `${SYSTEM_PROMPT}`, `${GUID}`

Sample configurations for Amazon Bedrock, Anthropic, IBM watsonx.ai, Microsoft Azure, and OpenAI are included in the `aichat/src/main/resources/config/` folder.

## Export Formats

- **HTML** — Fully styled HTML with CSS, relative image paths preserved
- **PDF** — Print-to-file via the system's PDF output, with print-friendly links (URLs shown in parentheses)
- **Word Document (DOCX)** — Export to `.docx` with math support (LaTeX converted to OMML) and Mermaid diagrams rendered as PNG images
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
mvn install -q && mvn exec:java -pl app
```

Or run `com.glowingcat.Main` directly from your IDE.

## Tech Stack

- **Java Swing** — GUI framework
- **commonmark-java** — Markdown parsing and HTML rendering (with GFM tables, strikethrough, task lists, autolink, footnotes, heading anchors, image attributes, ins, and YAML front matter extensions)
- **RSyntaxTextArea** — Syntax-aware text editor component
- **JavaFX WebView** — HTML preview rendering with MathJax (with JEditorPane fallback)
- **highlight.js** — Syntax highlighting for code blocks in preview and AI chat
- **Mermaid** — Diagram rendering for flowcharts, sequence diagrams, and more
- **Apache POI** — Word Document (DOCX) export and import
- **SnuggleTeX** — LaTeX to OMML conversion for math in DOCX export
- **Gson** — JSON serialization for user preferences
- **SnakeYAML** — YAML parsing for Generic vendor configuration
- **LanguageTool** — Spell and grammar checking engine with multi-language support
- **java.net.http** — HTTP client for LLM API calls

## Class Diagram

A high-level overview of PurplePlatypus's main classes and their relationships:

```mermaid
classDiagram
    class Main {
        +main(String[] args) void
        +openFileInWindow(File file) void
    }

    class EditorWindow {
        -EditorPanel editorPanel
        -PreviewPanel previewPanel
        -AIChatPanel aiChatPanel
        -Preferences preferences
        -File currentFile
        -boolean dirty
        +loadFileContent(File file, String content) void
        +confirmClose() boolean
        +showPreferencesDialog() void
        +showAiSettingsDialog() void
        +showLicenseDialog() void
        +showAboutDialog() void
    }

    class EditorPanel {
        -RSyntaxTextArea textArea
        -RTextScrollPane scrollPane
        +getTextArea() RSyntaxTextArea
        +getScrollPane() RTextScrollPane
        +applyPreferences(Preferences prefs) void
    }

    class PreviewPanel {
        -WebView webView
        -JEditorPane fallbackPane
        +updatePreview(String md, File file, Preferences prefs) void
        +getStyledHtml(String html, File file, Preferences prefs, boolean export) String
        +scrollToRatio(double ratio) void
        +scrollToAnchor(String anchor) void
        +findInPreview(String text) boolean
        +forceFullReload() void
    }

    class Preferences {
        -List~String~ recentFiles
        -List~String~ searchRecents
        -List~String[]~ gremlins
        +load() Preferences
        +save() void
        +addRecentFile(String path) void
        +getEditorFontFamily() String
        +getSpellCheckLanguage() String
    }

    class Theme {
        +LIGHT Theme
        +DARK Theme
        +editorBackground Color
        +toolbarBackground Color
    }

    class FindDialog {
        -JTextArea textArea
        -boolean matchCase
        -boolean regex
        +findNext() void
        +findAll() void
        +focusSearchField() void
    }

    class ReplaceDialog {
        +replace() void
        +replaceAll() void
    }

    class DocxExporter {
        +export(Node docNode, File outputFile) void
        +insertMath(XWPFParagraph para, String latex, boolean display) boolean
    }

    class AIChatPanel {
        -LLMClient llmClient
        -DocumentRetriever retriever
        +builder() Builder
        +setLlmClient(LLMClient client) void
        +setDarkMode(boolean dark) void
        +updateFont() void
    }

    class LLMClient {
        <<interface>>
        +chat(List~Map~ messages, String systemPrompt) String
    }

    class OpenAIClient {
        -String baseUrl
        -String apiKey
        -String model
        +chat(List~Map~ messages, String systemPrompt) String
    }

    class AnthropicClient {
        -String apiKey
        -String model
        +chat(List~Map~ messages, String systemPrompt) String
    }

    class GenericClient {
        -GenericVendorConfig config
        +chat(List~Map~ messages, String systemPrompt) String
        +getConfig() GenericVendorConfig
    }

    class LLMClientFactory {
        +create(AIChatPreferences prefs) LLMClient
    }

    class VendorRegistry {
        +getVendors() List~VendorInfo~
        +getVendor(String name) VendorInfo
        +getVendorNames() String[]
    }

    class GenericVendorConfig {
        -String guid
        +load() void
        +resetGuid() void
        +callPrompt(String token, String model, String prompt, ...) String
        +isValid() boolean
        +applyTrustStore() void
    }

    class AIChatPreferences {
        +getLlmVendor() String
        +getLlmModel() String
        +getLlmApiKey() String
        +getLlmEndpoint() String
        +load() AIChatPreferences
        +save() void
    }

    class DiffApplier {
        +apply(String original, String diff) String
    }

    class DocumentRetriever {
        +initialize(AIChatPreferences prefs) void
        +retrieve(String query) List~String~
        +isInitialized() boolean
    }

    class SpellCheckController {
        -SpellCheckService service
        -RSyntaxTextArea textArea
        +setEnabled(boolean enabled) void
        +isEnabled() boolean
        +setLanguage(String langCode) void
    }

    class SpellCheckService {
        +check(String text) List~SpellError~
        +setLanguage(String langCode) void
        +addToDictionary(String word) void
        +isReady() boolean
    }

    class FigureNodeRenderer {
        <<NodeRenderer>>
    }

    Main --> EditorWindow : creates
    EditorWindow *-- EditorPanel
    EditorWindow *-- PreviewPanel
    EditorWindow *-- AIChatPanel
    EditorWindow --> Preferences
    EditorWindow --> Theme
    EditorWindow --> FindDialog
    EditorWindow --> DocxExporter
    EditorWindow --> SpellCheckController
    EditorWindow --> FigureNodeRenderer
    ReplaceDialog --|> FindDialog
    AIChatPanel --> LLMClient
    AIChatPanel --> DiffApplier
    AIChatPanel --> DocumentRetriever
    AIChatPanel --> AIChatPreferences
    LLMClientFactory --> LLMClient : creates
    LLMClientFactory --> VendorRegistry
    OpenAIClient ..|> LLMClient
    AnthropicClient ..|> LLMClient
    GenericClient ..|> LLMClient
    GenericClient --> GenericVendorConfig
    SpellCheckController --> SpellCheckService
```

---

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE) for details.

© 2026 Richard Lesh
