# PurplePlatypus

A lightweight desktop Markdown editor built with Java Swing, featuring a live preview pane that renders your Markdown as you type.

## Features

- **Split-pane editor** — Write Markdown on the left, see the rendered HTML preview on the right
- **Live preview** — The preview updates in real time as you type, with no manual refresh needed
- **Line numbers** — A line number gutter on the left side of the editor that stays in sync as you scroll and type
- **File operations** — Create new files, open existing `.md`/`.markdown`/`.txt` files, and save your work
- **Undo/Redo** — Full multi-level undo and redo support
- **Clipboard** — Cut, Copy, and Paste via the Edit menu
- **Find** — Search with options for Match Case, Wrap Around, Search Backwards, and Find in Selection (remembers the original selection for repeated searches)
- **Find All** — Opens a results window showing matching lines with highlighted text; click a match to jump to it in the editor
- **Count** — Quickly count the number of matches in the document or selection
- **Replace** — Find and replace with Replace, Replace and Find, and Replace All operations
- **Keyboard shortcuts** — Ctrl+N (New), Ctrl+O (Open), Ctrl+S (Save), Ctrl+Shift+S (Save As), Ctrl+Z (Undo), Ctrl+Y (Redo), Ctrl+X (Cut), Ctrl+C (Copy), Ctrl+V (Paste), Ctrl+F (Find), Ctrl+H (Replace)
- **Markdown support** — Headings, bold, italic, lists, code blocks, blockquotes, links, and more via the CommonMark specification
- **Styled preview** — Clean, readable HTML output with custom CSS styling

## Requirements

- Java 17 or later
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
- **commonmark-java** — Markdown parsing and HTML rendering
