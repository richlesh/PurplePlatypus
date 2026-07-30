/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.aichat;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI Chat panel that provides LLM-powered markdown writing assistance.
 * Users can ask for help with content, formatting, structure, and editing.
 *
 * Use {@link #builder()} to construct instances.
 */
public class AIChatPanel extends JPanel {

    private final JPanel chatPanel;
    private final JScrollPane chatScroll;
    private final JTextArea inputArea;
    private final JButton sendBtn;
    private final DocumentEditor editor;
    private final AIChatPreferences aiPreferences;
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
    private LLMClient llmClient;
    private Runnable promptNagCallback;

    /**
     * Create a builder for constructing an AIChatPanel.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AIChatPanel.
     */
    public static class Builder {
        private DocumentEditor editor;
        private AIChatPreferences preferences;
        private LLMClient llmClient;
        private Runnable onPromptNag;

        private Builder() {}

        /** Set the document editor (required). */
        public Builder editor(DocumentEditor editor) {
            this.editor = editor;
            return this;
        }

        /** Set the AI chat preferences (required). */
        public Builder preferences(AIChatPreferences preferences) {
            this.preferences = preferences;
            return this;
        }

        /** Set the LLM client (optional; will use LLMClientFactory if not set). */
        public Builder llmClient(LLMClient llmClient) {
            this.llmClient = llmClient;
            return this;
        }

        /** Set a callback invoked every N prompts for nag/licensing (optional). */
        public Builder onPromptNag(Runnable onPromptNag) {
            this.onPromptNag = onPromptNag;
            return this;
        }

        /** Build the AIChatPanel. */
        public AIChatPanel build() {
            if (editor == null) throw new IllegalStateException("editor is required");
            if (preferences == null) throw new IllegalStateException("preferences is required");
            return new AIChatPanel(this);
        }
    }

    private AIChatPanel(Builder builder) {
        super(new BorderLayout());
        this.editor = builder.editor;
        this.aiPreferences = builder.preferences;
        this.llmClient = builder.llmClient;
        this.promptNagCallback = builder.onPromptNag;
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
        inputArea.setFont(new Font(aiPreferences.getAiFontFamily(), Font.PLAIN, aiPreferences.getAiFontSize()));
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
            int doc = editor.getText().length();
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
            if (llmClient instanceof GenericClient gc) {
                GenericVendorConfig cfg = gc.getConfig();
                if (cfg != null) cfg.resetGuid();
            }
        });
    }

    /** Set or replace the LLM client at runtime (e.g., after preferences change). */
    public void setLlmClient(LLMClient client) {
        this.llmClient = client;
    }

    /** Set or replace the prompt nag callback at runtime. */
    public void setPromptNagCallback(Runnable callback) {
        this.promptNagCallback = callback;
    }

    /** Update fonts after preferences change. */
    public void updateFont() {
        Font font = new Font(aiPreferences.getAiFontFamily(), Font.PLAIN, aiPreferences.getAiFontSize());
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

        // Invoke nag callback every 10 prompts
        promptCount++;
        if (promptCount % 10 == 0 && promptNagCallback != null) {
            promptNagCallback.run();
        }

        statusUpdater.run();

        String context = "Current markdown document:\n```markdown\n" + editor.getText() + "\n```";

        if (messages.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", context + "\n\nUser request: " + text));

        // Ensure we have an LLM client
        if (llmClient == null) {
            llmClient = LLMClientFactory.create(aiPreferences);
        }

        sendBtn.setEnabled(false);
        startPulse();
        final LLMClient client = llmClient;
        currentThread = new Thread(() -> {
            try {
                String response = client.chat(messages, systemPrompt);
                messages.add(Map.of("role", "assistant", "content", response));
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
        Color uColor = aiPreferences.getUserPromptColorObj();
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
        msg.setFont(new Font(aiPreferences.getAiFontFamily(), Font.PLAIN, aiPreferences.getAiFontSize()));
        msg.setForeground(aiPreferences.getUserTextColorObj());
        msg.setOpaque(false);
        msg.setEditable(false);
        msg.setLineWrap(true);
        msg.setWrapStyleWord(true);
        bubble.add(msg, BorderLayout.CENTER);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        row.add(bubble, BorderLayout.CENTER);

        chatPanel.add(row);
        chatPanel.revalidate();
        scrollToBottom();
    }

    private void addAiBubble(String text) {
        Color aiColor = aiPreferences.getAiResponseColorObj();
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
        msg.setFont(new Font(aiPreferences.getAiFontFamily(), Font.PLAIN, aiPreferences.getAiFontSize()));
        msg.setForeground(aiPreferences.getAiTextColorObj());
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
        } else {
            // Show a summary so the user always sees something
            int lines = newMarkdown.split("\n").length;
            addAiBubble("Here's the updated document (" + lines + " lines). Review and accept or reject the changes.");
        }

        JPanel btnRow = new JPanel();
        btnRow.setLayout(new BoxLayout(btnRow, BoxLayout.X_AXIS));
        btnRow.setOpaque(false);
        btnRow.setBorder(BorderFactory.createEmptyBorder(2, 14, 6, 6));
        JLabel prompt = new JLabel("Apply changes to document?");
        prompt.setFont(new Font(aiPreferences.getAiFontFamily(), Font.BOLD, aiPreferences.getAiFontSize()));
        JButton allowBtn = new JButton("Accept");
        JButton rejectBtn = new JButton("Reject");
        allowBtn.addActionListener(e -> {
            editor.setText(newMarkdown);
            prompt.setText("Changes accepted.");
            btnRow.remove(allowBtn);
            btnRow.remove(rejectBtn);
            btnRow.revalidate();
            btnRow.repaint();
            chatPanel.revalidate();
        });
        rejectBtn.addActionListener(e -> {
            prompt.setText("Changes rejected.");
            btnRow.remove(allowBtn);
            btnRow.remove(rejectBtn);
            btnRow.revalidate();
            btnRow.repaint();
            chatPanel.revalidate();
        });
        btnRow.add(prompt);
        btnRow.add(Box.createHorizontalStrut(8));
        btnRow.add(allowBtn);
        btnRow.add(Box.createHorizontalStrut(4));
        btnRow.add(rejectBtn);

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
        thinking.setFont(new Font(aiPreferences.getAiFontFamily(), Font.ITALIC, aiPreferences.getAiFontSize()));
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
            // Find the LAST closing fence (```): the outer markdown block's closing fence
            // is always the last one, since inner code blocks are nested within it
            int blockEnd = -1;
            int searchFrom = blockStart;
            while (searchFrom < normalized.length()) {
                int candidate = normalized.indexOf("\n```", searchFrom);
                if (candidate < 0) break;
                int afterFence = candidate + 4;
                // Check that after ``` there's only whitespace or end of string
                if (afterFence >= normalized.length()) {
                    blockEnd = candidate;
                } else {
                    char nextChar = normalized.charAt(afterFence);
                    if (nextChar == '\n' || normalized.substring(afterFence).trim().isEmpty()) {
                        blockEnd = candidate;
                    }
                }
                searchFrom = candidate + 4;
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

    private String buildSystemPrompt() {
        try (var is = AIChatPanel.class.getResourceAsStream("/system_prompt.md")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            // Fall through to hardcoded fallback
        }
        return "You are an AI writing assistant. Help users write and improve markdown documents.";
    }

    private void renderStyledMessage(JTextPane pane, String text) {
        StyledDocument doc = pane.getStyledDocument();
        String fontName = aiPreferences.getAiFontFamily();
        int fontSize = aiPreferences.getAiFontSize();
        String codeFontName = aiPreferences.getAiCodeFontFamily();
        int codeFontSize = aiPreferences.getAiCodeFontSize();

        Style normal = doc.addStyle("normal", null);
        StyleConstants.setFontFamily(normal, fontName);
        StyleConstants.setFontSize(normal, fontSize);
        StyleConstants.setForeground(normal, pane.getForeground());

        Style bold = doc.addStyle("bold", normal);
        StyleConstants.setBold(bold, true);

        Style italic = doc.addStyle("italic", normal);
        StyleConstants.setItalic(italic, true);

        Style code = doc.addStyle("code", normal);
        StyleConstants.setFontFamily(code, codeFontName);
        StyleConstants.setFontSize(code, codeFontSize);

        Style codeBlock = doc.addStyle("codeBlock", null);
        StyleConstants.setFontFamily(codeBlock, codeFontName);
        StyleConstants.setFontSize(codeBlock, codeFontSize);
        StyleConstants.setForeground(codeBlock, pane.getForeground());

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
}
