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

import com.glowingcat.aichat.AIChatPreferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.awt.Color;
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

    /** Toolbar toggle button highlight color (hex string for Gson). */
    private String buttonHighlightColor = "#B482FF";

    // --- Legacy AI fields (kept for reading from old settings files for migration) ---
    private String llmVendor = null;
    private String llmModel = null;
    private String llmApiKey = null;
    private String llmEndpoint = null;
    private String aiFontFamily = null;
    private Integer aiFontSize = null;


    /** Background color for user prompt chat bubbles (hex string). */
    private String userPromptColor = "#B47FFF";     // HSV(265,50,100) Purple


    /** Text color for user prompt chat bubbles (hex string). */
    private String userTextColor = "#333333";       // HSV(0,20,0)

    /** Background color for AI response chat bubbles (hex string). */
    private String aiResponseColor = "#8E3EFF";     // HSV(265,50,100)

    /** Text color for AI response chat bubbles (hex string). */
    private String aiTextColor = "#BBBBBB";         // HSV(0,75,0)

    // --- Window state (not shown in preferences dialog) ---

    /** Recently opened file paths (most recent first). */
    private java.util.List<String> recentFiles = new java.util.ArrayList<>();

    /** Saved search expressions (most recent first). */
    private java.util.List<String> searchRecents = new java.util.ArrayList<>();

    /** Saved replace expressions (most recent first). */
    private java.util.List<String> replaceRecents = new java.util.ArrayList<>();

    /** Gremlin character substitution rules: each entry is [enabled, search, replacement]. */
    private java.util.List<String[]> gremlins = null;

    /** Maximum number of recent files to remember. */
    private static final transient int MAX_RECENT_FILES = 20;

    /** Maximum number of search/replace recents to remember. */
    private static final transient int MAX_SEARCH_RECENTS = 50;

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

    /** Whether dark mode is enabled. */
    private boolean darkMode = false;

    /** Spell check language code (e.g., "en", "fr", "de"). */
    private String spellCheckLanguage = "en";

    /** UI language/locale code (e.g., "en", "es", "fr", "de", "it", "ja", "zh"). Empty = system default. */
    private String uiLanguage = "";

    // --- License ---

    /** License email address. */
    private String licenseEmail = null;

    /** License key (16 hex chars). */
    private String licenseKey = null;

    // --- LLM Legacy Getters (for migration) ---

    String getLegacyLlmVendor() { return llmVendor; }
    String getLegacyLlmModel() { return llmModel; }
    String getLegacyLlmApiKey() { return llmApiKey; }
    String getLegacyLlmEndpoint() { return llmEndpoint; }
    String getLegacyAiFontFamily() { return aiFontFamily; }
    Integer getLegacyAiFontSize() { return aiFontSize; }

    // --- AI Chat Colors ---

    public String getUserPromptColor() { return userPromptColor; }
    public void setUserPromptColor(String hex) { this.userPromptColor = hex; }
    public void setUserPromptColor(Color color) { this.userPromptColor = toHex(color); }
    public Color getUserPromptColorObj() { return Color.decode(userPromptColor); }

    public String getUserTextColor() { return userTextColor; }
    public void setUserTextColor(String hex) { this.userTextColor = hex; }
    public void setUserTextColor(Color color) { this.userTextColor = toHex(color); }
    public Color getUserTextColorObj() { return Color.decode(userTextColor); }

    public String getAiResponseColor() { return aiResponseColor; }
    public void setAiResponseColor(String hex) { this.aiResponseColor = hex; }
    public void setAiResponseColor(Color color) { this.aiResponseColor = toHex(color); }
    public Color getAiResponseColorObj() { return Color.decode(aiResponseColor); }

    public String getAiTextColor() { return aiTextColor; }
    public void setAiTextColor(String hex) { this.aiTextColor = hex; }
    public void setAiTextColor(Color color) { this.aiTextColor = toHex(color); }
    public Color getAiTextColorObj() { return Color.decode(aiTextColor); }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** Returns true if this settings file contains legacy AI fields that need migration. */
    boolean hasLegacyAiSettings() {
        return llmVendor != null || llmApiKey != null || aiFontFamily != null;
    }

    /** Clear legacy AI fields so they are not written back to the settings file. */
    void clearLegacyAiSettings() {
        llmVendor = null;
        llmModel = null;
        llmApiKey = null;
        llmEndpoint = null;
        aiFontFamily = null;
        aiFontSize = null;
    }

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
    public boolean isDarkMode() { return darkMode; }
    public void setDarkMode(boolean darkMode) { this.darkMode = darkMode; }
    public String getSpellCheckLanguage() { return spellCheckLanguage != null ? spellCheckLanguage : "en"; }
    public void setSpellCheckLanguage(String spellCheckLanguage) { this.spellCheckLanguage = spellCheckLanguage; }
    public String getUiLanguage() { return uiLanguage != null ? uiLanguage : ""; }
    public void setUiLanguage(String uiLanguage) { this.uiLanguage = uiLanguage; }

    // --- License getters/setters ---

    public String getLicenseEmail() { return licenseEmail; }
    public void setLicenseEmail(String licenseEmail) { this.licenseEmail = licenseEmail; }
    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }

    // --- Recent Files ---

    /** Get the list of recently opened file paths (most recent first). */
    public java.util.List<String> getRecentFiles() {
        if (recentFiles == null) recentFiles = new java.util.ArrayList<>();
        return java.util.Collections.unmodifiableList(recentFiles);
    }

    /** Add a file to the recent files list (moves to front if already present). */
    public void addRecentFile(String path) {
        if (recentFiles == null) recentFiles = new java.util.ArrayList<>();
        recentFiles.remove(path);
        recentFiles.add(0, path);
        while (recentFiles.size() > MAX_RECENT_FILES) {
            recentFiles.remove(recentFiles.size() - 1);
        }
    }

    /** Clear all recent files. */
    public void clearRecentFiles() {
        if (recentFiles == null) recentFiles = new java.util.ArrayList<>();
        recentFiles.clear();
    }

    // --- Search/Replace Recents ---

    /** Get the list of saved search expressions (most recent first). */
    public java.util.List<String> getSearchRecents() {
        if (searchRecents == null) searchRecents = new java.util.ArrayList<>();
        return java.util.Collections.unmodifiableList(searchRecents);
    }

    /** Add a search expression to the recents (moves to front if already present). */
    public void addSearchRecent(String expr) {
        if (searchRecents == null) searchRecents = new java.util.ArrayList<>();
        searchRecents.remove(expr);
        searchRecents.add(0, expr);
        while (searchRecents.size() > MAX_SEARCH_RECENTS) {
            searchRecents.remove(searchRecents.size() - 1);
        }
    }

    /** Remove a search expression from the recents. */
    public void removeSearchRecent(String expr) {
        if (searchRecents == null) searchRecents = new java.util.ArrayList<>();
        searchRecents.remove(expr);
    }

    /** Get the list of saved replace expressions (most recent first). */
    public java.util.List<String> getReplaceRecents() {
        if (replaceRecents == null) replaceRecents = new java.util.ArrayList<>();
        return java.util.Collections.unmodifiableList(replaceRecents);
    }

    /** Add a replace expression to the recents (moves to front if already present). */
    public void addReplaceRecent(String expr) {
        if (replaceRecents == null) replaceRecents = new java.util.ArrayList<>();
        replaceRecents.remove(expr);
        replaceRecents.add(0, expr);
        while (replaceRecents.size() > MAX_SEARCH_RECENTS) {
            replaceRecents.remove(replaceRecents.size() - 1);
        }
    }

    /** Remove a replace expression from the recents. */
    public void removeReplaceRecent(String expr) {
        if (replaceRecents == null) replaceRecents = new java.util.ArrayList<>();
        replaceRecents.remove(expr);
    }

    // --- Gremlin Substitutions ---

    /** Get the list of gremlin substitution rules. Returns default set if none configured. */
    public java.util.List<String[]> getGremlins() {
        if (gremlins == null) {
            gremlins = getDefaultGremlins();
        }
        return gremlins;
    }

    /** Set the gremlin substitution rules. */
    public void setGremlins(java.util.List<String[]> gremlins) {
        this.gremlins = gremlins;
    }

    /** Default gremlin substitutions for common Unicode characters. */
    private static java.util.List<String[]> getDefaultGremlins() {
        java.util.List<String[]> defaults = new java.util.ArrayList<>();
        defaults.add(new String[]{"true", "\u2013", "--"});       // En-Dash → --
        defaults.add(new String[]{"true", "\u2014", "---"});      // Em-Dash → ---
        defaults.add(new String[]{"true", "\u00A0", " "});        // Non-breaking space → space
        defaults.add(new String[]{"true", "\u2018", "'"});        // Left single quote → '
        defaults.add(new String[]{"true", "\u2019", "'"});        // Right single quote → '
        defaults.add(new String[]{"true", "\u201C", "\""});       // Left double quote → "
        defaults.add(new String[]{"true", "\u201D", "\""});       // Right double quote → "
        defaults.add(new String[]{"true", "\u2026", "..."});      // Ellipsis → ...
        defaults.add(new String[]{"true", "\u00B7", "*"});        // Middle dot → *
        defaults.add(new String[]{"true", "\u2022", "*"});        // Bullet → *
        defaults.add(new String[]{"true", "\u00AB", "\""});       // Left guillemet → "
        defaults.add(new String[]{"true", "\u00BB", "\""});       // Right guillemet → "
        return defaults;
    }

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
                    // Migrate legacy AI settings to AIChatPreferences if needed
                    prefs.migrateAiSettings();
                    return prefs;
                }
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                // Fall through to return defaults
            }
        }
        return new Preferences();
    }

    /**
     * Migrate legacy AI settings from this file to ~/.glowingcat-ai-settings.json.
     * Only migrates if the AI settings file does not already exist and this file
     * contains legacy AI fields.
     */
    private void migrateAiSettings() {
        if (!hasLegacyAiSettings()) return;

        Path aiPath = Paths.get(System.getProperty("user.home"), ".glowingcat-ai-settings.json");
        if (Files.exists(aiPath)) {
            // AI settings file already exists, just clear legacy fields
            clearLegacyAiSettings();
            save();
            return;
        }

        // Migrate to AIChatPreferences
        AIChatPreferences aiPrefs = new AIChatPreferences();
        if (llmVendor != null) aiPrefs.setLlmVendor(llmVendor);
        if (llmModel != null) aiPrefs.setLlmModel(llmModel);
        if (llmApiKey != null) aiPrefs.setLlmApiKey(llmApiKey);
        if (llmEndpoint != null) aiPrefs.setLlmEndpoint(llmEndpoint);
        if (aiFontFamily != null) aiPrefs.setAiFontFamily(aiFontFamily);
        if (aiFontSize != null) aiPrefs.setAiFontSize(aiFontSize);
        if (userPromptColor != null) aiPrefs.setUserPromptColor(userPromptColor);
        if (userTextColor != null) aiPrefs.setUserTextColor(userTextColor);
        if (aiResponseColor != null) aiPrefs.setAiResponseColor(aiResponseColor);
        if (aiTextColor != null) aiPrefs.setAiTextColor(aiTextColor);
        aiPrefs.save();

        // Clear legacy fields and re-save
        clearLegacyAiSettings();
        save();
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
