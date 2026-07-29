/*
 * (c) 2026 Glowing Cat Software
 */

/**
 * Preferences.java
 *
 * Manages user preferences for the PurplePlatypus application. Preferences are
 * persisted as a JSON file ({@code .purpleplatypus-settings.json}) in the user's home directory.
 * Includes font family and font size settings for both the editor and preview panes.
 */
package com.glowingcat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Holds and persists user preferences for PurplePlatypus.
 * <p>
 * Preferences are loaded from and saved to {@code ~/.purpleplatypus-settings.json}.
 * If the file does not exist or cannot be read, sensible defaults are used.
 */
public class Preferences {

    private static final String PREFS_FILENAME = ".purpleplatypus-settings.json";

    /** Font family for the markdown editor pane. */
    private String editorFontFamily = "Monospaced";

    /** Font size for the markdown editor pane. */
    private int editorFontSize = 14;

    /** Font family for the HTML preview pane. */
    private String previewFontFamily = "SansSerif";

    /** Font size for the HTML preview pane. */
    private int previewFontSize = 14;

    /** Font family for code in the HTML preview pane. */
    private String previewCodeFontFamily = "Monospaced";

    /** Font size for code in the HTML preview pane. */
    private int previewCodeFontSize = 13;

    // --- Editor settings ---

    /** Editor selection/highlight color (hex string). */
    private String selectionColor = "#B482FF";

    /** Whether to use real tabs (true) or spaces (false). */
    private boolean useTabs = false;

    /** Number of spaces per tab stop. */
    private int tabSize = 4;

    // --- LLM / AI Chat settings ---

    /** LLM vendor name (OpenAI, Anthropic, Google, DeepSeek, Alibaba, Ollama). */
    private String llmVendor = "OpenAI";

    /** LLM model identifier. */
    private String llmModel = "gpt-4o";

    /** LLM API key (null means not configured). */
    private String llmApiKey = null;

    /** Custom LLM endpoint URL for Generic OpenAI API vendor. */
    private String llmEndpoint = null;

    /** Font family for the AI chat panel. */
    private String aiFontFamily = detectAIFont();

    /** Font size for the AI chat panel. */
    private int aiFontSize = 14;

    /** Background color for user prompt chat bubbles (hex string for Gson). */
    private String userPromptColor = "#9B59B6";

    /** Text color for user prompt chat bubbles (hex string for Gson). */
    private String userTextColor = "#FFFFFF";

    /** Background color for AI response chat bubbles (hex string for Gson). */
    private String aiResponseColor = "#6C3483";

    /** Text color for AI response chat bubbles (hex string for Gson). */
    private String aiTextColor = "#FFFFFF";

    /** Toolbar toggle button highlight color (hex string for Gson). */
    private String buttonHighlightColor = "#B482FF";

    // --- Window state (not shown in preferences dialog) ---

    /** Window width. */
    private int windowWidth = 1200;

    /** Window height. */
    private int windowHeight = 700;

    /** Editor/preview split pane divider location. */
    private int editorPreviewDivider = 600;

    /** Main split pane divider (content vs AI panel). */
    private int mainDivider = 800;

    /** Whether the preview pane is visible. */
    private boolean previewVisible = true;

    /** Whether the AI chat pane is visible. */
    private boolean aiVisible = true;

    // --- License ---

    /** License email address. */
    private String licenseEmail = null;

    /** License key (16 hex chars). */
    private String licenseKey = null;

    private static String detectAIFont() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] candidates;
        if (os.contains("linux")) candidates = new String[]{"DejaVu Sans", "Arial", "Helvetica", "SansSerif"};
        else if (os.contains("win")) candidates = new String[]{"Calibri", "Arial", "Helvetica", "SansSerif"};
        else candidates = new String[]{"Arial", "Helvetica", "SansSerif"};
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, 14);
            if (!f.getFamily().equals("Dialog")) return name;
        }
        return "SansSerif";
    }

    // --- LLM Getters/Setters ---

    public String getLlmVendor() { return llmVendor; }
    public void setLlmVendor(String llmVendor) { this.llmVendor = llmVendor; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public String getLlmApiKey() { return llmApiKey; }
    public void setLlmApiKey(String llmApiKey) { this.llmApiKey = llmApiKey; }
    public String getLlmEndpoint() { return llmEndpoint; }
    public void setLlmEndpoint(String llmEndpoint) { this.llmEndpoint = llmEndpoint; }
    public String getAiFontFamily() { return aiFontFamily; }
    public void setAiFontFamily(String aiFontFamily) { this.aiFontFamily = aiFontFamily; }
    public int getAiFontSize() { return aiFontSize; }
    public void setAiFontSize(int aiFontSize) { this.aiFontSize = aiFontSize; }

    public Color getUserPromptColorObj() { return Color.decode(userPromptColor); }
    public void setUserPromptColor(Color color) { this.userPromptColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public String getUserPromptColor() { return userPromptColor; }
    public void setUserPromptColor(String hex) { this.userPromptColor = hex; }

    public Color getUserTextColorObj() { return Color.decode(userTextColor); }
    public void setUserTextColor(Color color) { this.userTextColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public String getUserTextColor() { return userTextColor; }
    public void setUserTextColor(String hex) { this.userTextColor = hex; }

    public Color getAiResponseColorObj() { return Color.decode(aiResponseColor); }
    public void setAiResponseColor(Color color) { this.aiResponseColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public String getAiResponseColor() { return aiResponseColor; }
    public void setAiResponseColor(String hex) { this.aiResponseColor = hex; }

    public Color getAiTextColorObj() { return Color.decode(aiTextColor); }
    public void setAiTextColor(Color color) { this.aiTextColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public String getAiTextColor() { return aiTextColor; }
    public void setAiTextColor(String hex) { this.aiTextColor = hex; }

    public Color getButtonHighlightColorObj() { return Color.decode(buttonHighlightColor); }
    public void setButtonHighlightColor(Color color) { this.buttonHighlightColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public String getButtonHighlightColor() { return buttonHighlightColor; }
    public void setButtonHighlightColor(String hex) { this.buttonHighlightColor = hex; }

    public String getEditorFontFamily() { return editorFontFamily; }
    public void setEditorFontFamily(String editorFontFamily) { this.editorFontFamily = editorFontFamily; }
    public int getEditorFontSize() { return editorFontSize; }
    public void setEditorFontSize(int editorFontSize) { this.editorFontSize = editorFontSize; }
    public String getPreviewFontFamily() { return previewFontFamily; }
    public void setPreviewFontFamily(String previewFontFamily) { this.previewFontFamily = previewFontFamily; }
    public int getPreviewFontSize() { return previewFontSize; }
    public void setPreviewFontSize(int previewFontSize) { this.previewFontSize = previewFontSize; }
    public String getPreviewCodeFontFamily() { return previewCodeFontFamily; }
    public void setPreviewCodeFontFamily(String previewCodeFontFamily) { this.previewCodeFontFamily = previewCodeFontFamily; }
    public int getPreviewCodeFontSize() { return previewCodeFontSize; }
    public void setPreviewCodeFontSize(int previewCodeFontSize) { this.previewCodeFontSize = previewCodeFontSize; }

    // --- Editor settings getters/setters ---

    public String getSelectionColor() { return selectionColor; }
    public void setSelectionColor(String hex) { this.selectionColor = hex; }
    public Color getSelectionColorObj() { return Color.decode(selectionColor); }
    public void setSelectionColor(Color color) { this.selectionColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public boolean isUseTabs() { return useTabs; }
    public void setUseTabs(boolean useTabs) { this.useTabs = useTabs; }
    public int getTabSize() { return tabSize; }
    public void setTabSize(int tabSize) { this.tabSize = Math.max(1, Math.min(8, tabSize)); }

    // --- Window state getters/setters ---

    public int getWindowWidth() { return windowWidth; }
    public void setWindowWidth(int windowWidth) { this.windowWidth = windowWidth; }
    public int getWindowHeight() { return windowHeight; }
    public void setWindowHeight(int windowHeight) { this.windowHeight = windowHeight; }
    public int getEditorPreviewDivider() { return editorPreviewDivider; }
    public void setEditorPreviewDivider(int editorPreviewDivider) { this.editorPreviewDivider = editorPreviewDivider; }
    public int getMainDivider() { return mainDivider; }
    public void setMainDivider(int mainDivider) { this.mainDivider = mainDivider; }
    public boolean isPreviewVisible() { return previewVisible; }
    public void setPreviewVisible(boolean previewVisible) { this.previewVisible = previewVisible; }
    public boolean isAiVisible() { return aiVisible; }
    public void setAiVisible(boolean aiVisible) { this.aiVisible = aiVisible; }

    // --- License getters/setters ---

    public String getLicenseEmail() { return licenseEmail; }
    public void setLicenseEmail(String licenseEmail) { this.licenseEmail = licenseEmail; }
    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }

    private static Path getPrefsPath() {
        return Paths.get(System.getProperty("user.home"), PREFS_FILENAME);
    }

    public static Preferences load() {
        Path path = getPrefsPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                Preferences prefs = gson.fromJson(reader, Preferences.class);
                if (prefs != null) {
                    return prefs;
                }
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                // Fall through to return defaults
            }
        }
        return new Preferences();
    }

    public void save() {
        Path path = getPrefsPath();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            // Silently fail - preferences are non-critical
        }
    }
}
