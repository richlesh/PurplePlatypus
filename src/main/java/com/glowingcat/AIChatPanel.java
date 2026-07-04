/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.net.URI;
import java.net.http.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/**
 * AI Chat panel that provides LLM-powered markdown writing assistance.
 * Users can ask for help with content, formatting, structure, and editing.
 */
public class AIChatPanel extends JPanel {

    private final JPanel chatPanel;
    private final JScrollPane chatScroll;
    private final JTextArea inputArea;
    private final JButton sendBtn;
    private final RSyntaxTextArea editorPane;
    private final Preferences preferences;
    private final List<Map<String, String>> messages = new ArrayList<>();
    private final String systemPrompt;
    private final ImageIcon humanIcon;
    private final ImageIcon aiIcon;
    private JLabel pulsingAiLabel;
    private Timer pulseTimer;
    private volatile Thread currentThread;
    private float pulseAlpha = 0f;
    private Runnable statusUpdater;
    private int promptCount = 0;

    public AIChatPanel(RSyntaxTextArea editorPane, Preferences preferences) {
        super(new BorderLayout());
        this.editorPane = editorPane;
        this.preferences = preferences;
        this.systemPrompt = buildSystemPrompt();

        // Load icons
        var humanUrl = AIChatPanel.class.getResource("/human.png");
        var aiUrl = AIChatPanel.class.getResource("/AI.png");
        humanIcon = humanUrl != null ? new ImageIcon(new ImageIcon(humanUrl).getImage().getScaledInstance(28, 28, Image.SCALE_SMOOTH)) : null;
        aiIcon = aiUrl != null ? new ImageIcon(new ImageIcon(aiUrl).getImage().getScaledInstance(28, 28, Image.SCALE_SMOOTH)) : null;

        setPreferredSize(new Dimension(380, 0));
        setBorder(BorderFactory.createTitledBorder("AI Assistant"));

        chatPanel = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                if (getParent() != null) {
                    int w = getParent().getWidth();
                    if (w > 0) {
                        Dimension d = super.getPreferredSize();
                        return new Dimension(w, d.height);
                    }
                }
                return super.getPreferredSize();
            }
        };
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(245, 245, 245));
        chatScroll = new JScrollPane(chatPanel);
        chatScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chatScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);

        inputArea = new JTextArea(3, 20);
        inputArea.setFont(new Font(preferences.getAiFontFamily(), Font.PLAIN, preferences.getAiFontSize()));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isShiftDown()) {
                    e.consume();
                    inputArea.insert("\n", inputArea.getCaretPosition());
                }
            }
        });
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        sendBtn = new JButton("Send");
        JButton clearBtn = new JButton("Clear");
        JPanel btnPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        btnPanel.add(sendBtn);
        btnPanel.add(clearBtn);

        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        inputPanel.add(inputScroll, BorderLayout.CENTER);
        inputPanel.add(btnPanel, BorderLayout.EAST);

        JLabel statusBar = new JLabel(" ");
        statusBar.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        statusUpdater = () -> {
            int sp = systemPrompt.length();
            int doc = editorPane.getText().length();
            statusBar.setText(String.format("System: %,d chars    Document: %,d chars", sp, doc));
        };
        statusUpdater.run();

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(inputPanel, BorderLayout.CENTER);
        southPanel.add(statusBar, BorderLayout.SOUTH);

        add(chatScroll, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        sendBtn.addActionListener(e -> sendMessage());
        clearBtn.addActionListener(e -> {
            messages.clear();
            chatPanel.removeAll();
            chatPanel.revalidate();
            chatPanel.repaint();
        });
    }

    /** Update fonts after preferences change. */
    public void updateFont() {
        Font font = new Font(preferences.getAiFontFamily(), Font.PLAIN, preferences.getAiFontSize());
        inputArea.setFont(font);
        for (Component c : chatPanel.getComponents()) {
            updateFontRecursive(c, font);
        }
        chatPanel.revalidate();
        chatPanel.repaint();
    }

    private void updateFontRecursive(Component c, Font font) {
        if (c instanceof JTextArea) c.setFont(font);
        if (c instanceof JTextPane) c.setFont(font);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) updateFontRecursive(child, font);
        }
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;
        inputArea.setText("");
        addUserBubble(text);

        // Show splash every 10 prompts if not licensed
        promptCount++;
        if (promptCount % 10 == 0 && !LicenseDialog.isLicensed(preferences)) {
            SplashScreen.show();
        }

        statusUpdater.run();

        String context = "Current markdown document:\n```markdown\n" + editorPane.getText() + "\n```";

        if (messages.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", context + "\n\nUser request: " + text));

        sendBtn.setEnabled(false);
        startPulse();
        currentThread = new Thread(() -> {
            try {
                String response = callLLM();
                SwingUtilities.invokeLater(() -> {
                    stopPulse();
                    processResponse(response);
                    sendBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    stopPulse();
                    if (!Thread.currentThread().isInterrupted())
                        addAiBubble("Error (" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
                    sendBtn.setEnabled(true);
                });
            }
        });
        currentThread.start();
    }

    private void addUserBubble(String text) {
        Color uColor = preferences.getUserPromptColorObj();
        JPanel bubble = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(uColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 12));

        JLabel icon = new JLabel(humanIcon);
        icon.setVerticalAlignment(SwingConstants.TOP);
        bubble.add(icon, BorderLayout.WEST);

        JTextArea msg = new JTextArea(text);
        msg.setFont(new Font(preferences.getAiFontFamily(), Font.PLAIN, preferences.getAiFontSize()));
        msg.setForeground(Color.WHITE);
        msg.setOpaque(false);
        msg.setEditable(false);
        msg.setLineWrap(true);
        msg.setWrapStyleWord(true);
        bubble.add(msg, BorderLayout.CENTER);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        row.add(bubble, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

        chatPanel.add(row);
        chatPanel.revalidate();
        scrollToBottom();
    }

    private void addAiBubble(String text) {
        Color aiColor = preferences.getAiResponseColorObj();
        JPanel bubble = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(aiColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 12));

        JLabel icon = new JLabel(aiIcon);
        icon.setVerticalAlignment(SwingConstants.TOP);
        bubble.add(icon, BorderLayout.WEST);

        JTextPane msg = new JTextPane();
        msg.setOpaque(false);
        msg.setEditable(false);
        msg.setFont(new Font(preferences.getAiFontFamily(), Font.PLAIN, preferences.getAiFontSize()));
        renderStyledMessage(msg, text);
        bubble.add(msg, BorderLayout.CENTER);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        row.add(bubble, BorderLayout.CENTER);

        chatPanel.add(row);
        chatPanel.revalidate();
        scrollToBottom();
    }

    private void addCodeApprovalBubble(String explanation, String newMarkdown) {
        if (!explanation.isEmpty()) {
            addAiBubble(explanation);
        }

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(BorderFactory.createEmptyBorder(2, 14, 6, 6));
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JLabel prompt = new JLabel("Apply changes to document?");
        prompt.setFont(new Font(preferences.getAiFontFamily(), Font.BOLD, preferences.getAiFontSize()));
        JButton allowBtn = new JButton("Allow");
        JButton rejectBtn = new JButton("Reject");
        allowBtn.addActionListener(e -> {
            editorPane.setText(newMarkdown);
            allowBtn.setEnabled(false);
            rejectBtn.setEnabled(false);
            prompt.setText("Changes applied.");
        });
        rejectBtn.addActionListener(e -> {
            allowBtn.setEnabled(false);
            rejectBtn.setEnabled(false);
            prompt.setText("Changes rejected.");
        });
        JPanel btnStack = new JPanel(new GridLayout(2, 1, 0, 2));
        btnStack.setOpaque(false);
        btnStack.add(allowBtn);
        btnStack.add(rejectBtn);
        btnRow.add(prompt);
        btnRow.add(btnStack);

        chatPanel.add(btnRow);
        chatPanel.revalidate();
        scrollToBottom();
    }

    private void startPulse() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        pulsingAiLabel = new JLabel(aiIcon) {
            @Override
            protected void paintComponent(Graphics g) {
                if (pulseAlpha > 0) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int cx = getWidth() / 2, cy = getHeight() / 2, r = Math.max(getWidth(), getHeight()) / 2 + 4;
                    float[] dist = {0.3f, 1.0f};
                    Color[] colors = {new Color(50, 130, 255, (int) (pulseAlpha * 160)), new Color(50, 130, 255, 0)};
                    g2.setPaint(new RadialGradientPaint(cx, cy, r, dist, colors));
                    g2.fillOval(cx - r, cy - r, r * 2, r * 2);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        pulsingAiLabel.setVerticalAlignment(SwingConstants.TOP);
        row.add(pulsingAiLabel, BorderLayout.WEST);

        JLabel thinking = new JLabel("Thinking...");
        thinking.setFont(new Font(preferences.getAiFontFamily(), Font.ITALIC, preferences.getAiFontSize()));
        thinking.setForeground(Color.GRAY);
        row.add(thinking, BorderLayout.CENTER);

        JButton cancelBtn = new JButton("\u2715");
        cancelBtn.setForeground(Color.RED);
        cancelBtn.setFont(cancelBtn.getFont().deriveFont(Font.BOLD, 14f));
        cancelBtn.setBorderPainted(false);
        cancelBtn.setContentAreaFilled(false);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.setToolTipText("Cancel");
        cancelBtn.addActionListener(e -> {
            if (currentThread != null) currentThread.interrupt();
            stopPulse();
            sendBtn.setEnabled(true);
        });
        row.add(cancelBtn, BorderLayout.EAST);

        chatPanel.add(row);
        chatPanel.revalidate();
        scrollToBottom();

        pulseTimer = new Timer(80, new ActionListener() {
            boolean increasing = true;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (increasing) {
                    pulseAlpha += 0.08f;
                    if (pulseAlpha >= 1f) { pulseAlpha = 1f; increasing = false; }
                } else {
                    pulseAlpha -= 0.08f;
                    if (pulseAlpha <= 0f) { pulseAlpha = 0f; increasing = true; }
                }
                if (pulsingAiLabel != null) pulsingAiLabel.repaint();
            }
        });
        pulseTimer.start();
    }

    private void stopPulse() {
        if (pulseTimer != null) { pulseTimer.stop(); pulseTimer = null; }
        pulseAlpha = 0f;
        if (pulsingAiLabel != null) pulsingAiLabel.repaint();
        int count = chatPanel.getComponentCount();
        if (count > 0) chatPanel.remove(count - 1);
        chatPanel.revalidate();
        chatPanel.repaint();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar v = chatScroll.getVerticalScrollBar();
            v.setValue(v.getMaximum());
        });
    }

    private void processResponse(String response) {
        // Normalize line endings to \n
        String normalized = response.replace("\r\n", "\n").replace("\r", "\n");

        int codeStart = normalized.indexOf("```markdown\n");
        if (codeStart < 0) codeStart = normalized.indexOf("```md\n");
        if (codeStart < 0) codeStart = normalized.indexOf("```markdown ");
        if (codeStart < 0) codeStart = normalized.indexOf("```md ");

        if (codeStart >= 0) {
            int blockStart = normalized.indexOf("\n", codeStart) + 1;
            // Find closing fence: look for ``` at the start of a line
            int blockEnd = -1;
            int searchFrom = blockStart;
            while (searchFrom < normalized.length()) {
                int candidate = normalized.indexOf("\n```", searchFrom);
                if (candidate < 0) break;
                // Verify it's a closing fence (only whitespace after ```)
                int afterFence = candidate + 4;
                if (afterFence >= normalized.length()
                        || normalized.charAt(afterFence) == '\n'
                        || normalized.substring(afterFence).stripLeading().isEmpty()
                        || normalized.substring(afterFence, Math.min(afterFence + 10, normalized.length())).trim().isEmpty()) {
                    blockEnd = candidate;
                    break;
                }
                searchFrom = candidate + 1;
            }
            if (blockEnd > blockStart) {
                String newMarkdown = normalized.substring(blockStart, blockEnd);
                String explanation = normalized.substring(0, codeStart).trim();
                int fenceEndPos = normalized.indexOf("\n", blockEnd + 1);
                if (fenceEndPos < 0) fenceEndPos = blockEnd + 4;
                if (fenceEndPos < normalized.length()) {
                    String after = normalized.substring(fenceEndPos).trim();
                    if (!after.isEmpty()) explanation += (explanation.isEmpty() ? "" : "\n") + after;
                }
                addCodeApprovalBubble(explanation, newMarkdown);
                return;
            }
        }
        addAiBubble(response);
    }

    private String callLLM() throws Exception {
        String vendor = preferences.getLlmVendor();
        String apiKey = preferences.getLlmApiKey();
        String model = preferences.getLlmModel();

        String baseUrl = switch (vendor) {
            case "Alibaba" -> "https://dashscope-us.aliyuncs.com/compatible-mode/v1";
            case "Anthropic" -> "https://api.anthropic.com/v1";
            case "DeepSeek" -> "https://api.deepseek.com/v1";
            case "Google" -> "https://generativelanguage.googleapis.com/v1beta/openai";
            case "Ollama" -> "http://localhost:11434/v1";
            case "OpenAI" -> "https://api.openai.com/v1";
            default -> "https://api.openai.com/v1";
        };

        if ("Anthropic".equals(vendor)) {
            return callAnthropic(apiKey, model);
        }

        StringBuilder body = new StringBuilder();
        body.append("{\"model\":\"").append(model).append("\",\"max_tokens\":128000,\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) body.append(",");
            body.append("{\"role\":\"").append(messages.get(i).get("role"))
                .append("\",\"content\":").append(jsonString(messages.get(i).get("content"))).append("}");
        }
        body.append("]}");

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Content-Type", "application/json")
            .timeout(java.time.Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

        if (apiKey != null && !apiKey.isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> resp = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .build()
            .send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

        String respBody = resp.body();
        String content = extractJsonValue(respBody, "content");
        if (content == null) throw new RuntimeException("Unexpected response: " + respBody.substring(0, Math.min(300, respBody.length())));
        messages.add(Map.of("role", "assistant", "content", content));
        return content;
    }

    private String callAnthropic(String apiKey, String model) throws Exception {
        StringBuilder body = new StringBuilder();
        body.append("{\"model\":\"").append(model).append("\",\"max_tokens\":128000,");
        String sys = messages.stream()
            .filter(m -> "system".equals(m.get("role")))
            .map(m -> m.get("content"))
            .findFirst().orElse("");
        body.append("\"system\":").append(jsonString(sys)).append(",\"messages\":[");
        boolean first = true;
        for (var m : messages) {
            if ("system".equals(m.get("role"))) continue;
            if (!first) body.append(",");
            body.append("{\"role\":\"").append(m.get("role"))
                .append("\",\"content\":").append(jsonString(m.get("content"))).append("}");
            first = false;
        }
        body.append("]}");

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        String respBody = resp.body();
        String content = extractJsonValue(respBody, "text");
        if (content == null) throw new RuntimeException("Unexpected response: " + respBody.substring(0, Math.min(300, respBody.length())));
        messages.add(Map.of("role", "assistant", "content", content));
        return content;
    }

    private String buildSystemPrompt() {
        return "You are an AI writing assistant embedded in PurplePlatypus, a desktop Markdown editor. "
            + "You help users write, edit, and improve markdown documents.\n\n"
            + "Your capabilities:\n"
            + "- Help draft new content (paragraphs, sections, lists, tables)\n"
            + "- Improve existing text (grammar, clarity, tone, structure)\n"
            + "- Add markdown formatting (headings, bold, italic, links, code blocks, etc.)\n"
            + "- Generate markdown tables from descriptions\n"
            + "- Suggest document structure and organization\n"
            + "- Help with technical writing, blog posts, documentation, READMEs\n"
            + "- Convert between formats (plain text to markdown, restructure content)\n\n"
            + "IMPORTANT RESPONSE FORMAT RULES:\n"
            + "- When the user asks you to modify, add to, rewrite, or generate content for the document, "
            + "ALWAYS respond with the COMPLETE updated document wrapped in a ```markdown code block. "
            + "Include ALL existing content plus your changes. The user will be given Accept/Reject buttons.\n"
            + "- For questions, explanations, or discussions that don't require document changes, "
            + "respond in plain text without a code block.\n\n"
            + "The current document content is provided with each user message.\n\n"
            + "Supported markdown features: headings, bold, italic, strikethrough, underline (<u>), "
            + "ordered/unordered/task lists, block quotes, code blocks, inline code, "
            + "links, images, tables (GFM), inline math ($...$), block math ($$...$$).";
    }

    private void renderStyledMessage(JTextPane pane, String text) {
        StyledDocument doc = pane.getStyledDocument();
        String fontName = preferences.getAiFontFamily();
        int fontSize = preferences.getAiFontSize();

        Style normal = doc.addStyle("normal", null);
        StyleConstants.setFontFamily(normal, fontName);
        StyleConstants.setFontSize(normal, fontSize);

        Style bold = doc.addStyle("bold", normal);
        StyleConstants.setBold(bold, true);

        Style italic = doc.addStyle("italic", normal);
        StyleConstants.setItalic(italic, true);

        Style code = doc.addStyle("code", normal);
        StyleConstants.setFontFamily(code, preferences.getPreviewCodeFontFamily());
        StyleConstants.setFontSize(code, preferences.getPreviewCodeFontSize());

        Style codeBlock = doc.addStyle("codeBlock", null);
        StyleConstants.setFontFamily(codeBlock, preferences.getPreviewCodeFontFamily());
        StyleConstants.setFontSize(codeBlock, preferences.getPreviewCodeFontSize());
        StyleConstants.setBackground(codeBlock, new Color(240, 240, 240));

        Style header = doc.addStyle("header", normal);
        StyleConstants.setBold(header, true);
        StyleConstants.setFontSize(header, fontSize + 4);

        boolean inCodeBlock = false;
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                insertText(doc, line + "\n", codeBlock);
                continue;
            }
            if (line.startsWith("### ")) { insertText(doc, line.substring(4) + "\n", header); continue; }
            if (line.startsWith("## ")) { insertText(doc, line.substring(3) + "\n", header); continue; }
            if (line.startsWith("# ")) { insertText(doc, line.substring(2) + "\n", header); continue; }

            String content = line;
            if (line.startsWith("- ") || line.startsWith("* ")) content = "\u2022 " + line.substring(2);

            renderInline(doc, content, normal, bold, italic, code);
            insertText(doc, "\n", normal);
        }
    }

    private void renderInline(StyledDocument doc, String text, Style normal, Style bold, Style italic, Style code) {
        int i = 0;
        while (i < text.length()) {
            // Inline code: `...`
            if (text.charAt(i) == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i) {
                    insertText(doc, text.substring(i + 1, end), code);
                    i = end + 1;
                    continue;
                }
            }
            // Bold: **...**
            if (i + 1 < text.length() && text.charAt(i) == '*' && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end > i) {
                    insertText(doc, text.substring(i + 2, end), bold);
                    i = end + 2;
                    continue;
                }
            }
            // Italic: *...*
            if (text.charAt(i) == '*') {
                int end = text.indexOf('*', i + 1);
                if (end > i && !(i + 1 < text.length() && text.charAt(i + 1) == '*')) {
                    insertText(doc, text.substring(i + 1, end), italic);
                    i = end + 1;
                    continue;
                }
            }
            // Plain text until next special char
            int next = text.length();
            for (int j = i + 1; j < text.length(); j++) {
                char c = text.charAt(j);
                if (c == '`' || c == '*') { next = j; break; }
            }
            insertText(doc, text.substring(i, next), normal);
            i = next;
        }
    }

    private static void insertText(StyledDocument doc, String text, Style style) {
        try { doc.insertString(doc.getLength(), text, style); } catch (BadLocationException ignored) {}
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.lastIndexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;
        int i = colonIdx + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return null;
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 5 < json.length()) {
                            sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                            i += 4;
                        }
                    }
                    default -> { sb.append('\\'); sb.append(next); }
                }
                i += 2;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
