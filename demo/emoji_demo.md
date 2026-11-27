## Emoji Sampler

A test set of emoji across different Unicode ranges.

## Emojii Replacer

EmojiReplacer.replaceEmoji(html) scans an HTML string and replaces non-BMP characters (code points above U+FFFF, which includes most emoji) with <img> tags pointing at Twemoji SVG images on the jsDelivr CDN. This works around JavaFX WebView's unreliable rendering of non-BMP characters.

### BMP (Basic Multilingual Plane)

These emoji live in the U+0000–U+FFFF range.

| Emoji | Name |
|-------|------|
| ☀️ | Sun |
| ☂️ | Umbrella |
| ★ | Black Star |
| ☎️ | Telephone |
| ✂️ | Scissors |
| ✈️ | Airplane |
| ✉️ | Envelope |
| ✏️ | Pencil |
| ❤️ | Red Heart |
| ⚽ | Soccer Ball |

### Supplemental (Non-BMP)

These emoji live above U+FFFF and require surrogate pairs.

| Emoji | Name |
|-------|------|
| 😀 | Grinning Face |
| 😂 | Face with Tears of Joy |
| 🥳 | Partying Face |
| 🚀 | Rocket |
| 🍕 | Pizza |
| 🎉 | Party Popper |
| 🐙 | Octopus |
| 🦆 | Duck |
| 🌈 | Rainbow |
| 🔥 | Fire |

### Country Flags (Regional Indicators)

Flags are formed from pairs of regional indicator symbols.

| Flag | Country |
|------|---------|
| 🇺🇸 | United States |
| 🇬🇧 | United Kingdom |
| 🇨🇦 | Canada |
| 🇯🇵 | Japan |
| 🇩🇪 | Germany |
| 🇫🇷 | France |
| 🇧🇷 | Brazil |
| 🇦🇺 | Australia |
| 🇮🇳 | India |
| 🇿🇦 | South Africa |

## Symbol Replacer

Here is the complete list of all 21 characters now replaced by the `SymbolReplacer` technique:

| # | Char | Code Point | Unicode Name | Keyboard Meaning |
|---|------|-----------|--------------|------------------|
| 1 | ⌘ | U+2318 | PLACE OF INTEREST SIGN | Command |
| 2 | ⇧ | U+21E7 | UPWARDS WHITE ARROW | Shift |
| 3 | ⌥ | U+2325 | OPTION KEY | Option / Alt |
| 4 | ⌃ | U+2303 | UP ARROWHEAD | Control |
| 5 | ↩ | U+21A9 | LEFTWARDS ARROW WITH HOOK | Return |
| 6 | ⏎ | U+23CE | RETURN SYMBOL | Return |
| 7 | ⌫ | U+232B | ERASE TO THE LEFT | Backspace / Delete |
| 8 | ⌦ | U+2326 | ERASE TO THE RIGHT | Forward Delete |
| 9 | ⎋ | U+238B | BROKEN CIRCLE WITH NORTHWEST ARROW | Escape |
| 10 | ⇥ | U+21E5 | RIGHTWARDS ARROW TO BAR | Tab |
| 11 | ⇤ | U+21E4 | LEFTWARDS ARROW TO BAR | Backtab |
| 12 | ← | U+2190 | LEFTWARDS ARROW | Left arrow |
| 13 | ↑ | U+2191 | UPWARDS ARROW | Up arrow |
| 14 | → | U+2192 | RIGHTWARDS ARROW | Right arrow |
| 15 | ↓ | U+2193 | DOWNWARDS ARROW | Down arrow |
| 16 | ⇞ | U+21DE | UPWARDS ARROW WITH DOUBLE STROKE | Page Up |
| 17 | ⇟ | U+21DF | DOWNWARDS ARROW WITH DOUBLE STROKE | Page Down |
| 18 | ⊞ | U+229E | SQUARED PLUS | Windows / Super key |
| 19 | ⇪ | U+21EA | UPWARDS WHITE ARROW FROM BAR | Caps Lock |
| 20 | ⌤ | U+2324 | UP ARROWHEAD BETWEEN TWO HORIZONTAL BARS | Enter |
| 21 | ⏏ | U+23CF | EJECT SYMBOL | Eject |

Grouped by category:
- **Modifiers (4):** ⌘ Command, ⇧ Shift, ⌥ Option/Alt, ⌃ Control
- **Editing/action (9):** ↩ Return, ⏎ Return, ⌫ Backspace, ⌦ Forward Delete, ⎋ Escape, ⇥ Tab, ⇤ Backtab, ⌤ Enter, ⏏ Eject
- **Navigation (6):** ← ↑ → ↓ arrows, ⇞ Page Up, ⇟ Page Down
- **Platform (2):** ⊞ Windows/Super, ⇪ Caps Lock

Each is a BMP character absent from most text fonts; the technique renders it as an inline `<img>` data-URI SVG built from a system-font glyph outline, so it survives the JavaFX WebView PDF/print pipeline. This is separate from `EmojiReplacer`, which handles non-BMP emoji via Twemoji images.

