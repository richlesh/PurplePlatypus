/*
 * (c) 2026 The Boeing Company
 */

/**
 * Preferences.java
 *
 * Manages user preferences for the MarkdownPro application. Preferences are
 * persisted as a JSON file ({@code .markdownpro.json}) in the user's home directory.
 * Includes font family and font size settings for both the editor and preview panes.
 */
package com.markdownpro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Holds and persists user preferences for MarkdownPro.
 * <p>
 * Preferences are loaded from and saved to {@code ~/.markdownpro.json}.
 * If the file does not exist or cannot be read, sensible defaults are used.
 */
public class Preferences {

    private static final String PREFS_FILENAME = ".markdownpro.json";

    /** Font family for the markdown editor pane. */
    private String editorFontFamily = "Monospaced";

    /** Font size for the markdown editor pane. */
    private int editorFontSize = 14;

    /** Font family for the HTML preview pane. */
    private String previewFontFamily = "SansSerif";

    /** Font size for the HTML preview pane. */
    private int previewFontSize = 14;

    /**
     * Returns the editor font family.
     *
     * @return the font family name
     */
    public String getEditorFontFamily() {
        return editorFontFamily;
    }

    /**
     * Sets the editor font family.
     *
     * @param editorFontFamily the font family name
     */
    public void setEditorFontFamily(String editorFontFamily) {
        this.editorFontFamily = editorFontFamily;
    }

    /**
     * Returns the editor font size.
     *
     * @return the font size in points
     */
    public int getEditorFontSize() {
        return editorFontSize;
    }

    /**
     * Sets the editor font size.
     *
     * @param editorFontSize the font size in points
     */
    public void setEditorFontSize(int editorFontSize) {
        this.editorFontSize = editorFontSize;
    }

    /**
     * Returns the preview pane font family.
     *
     * @return the font family name
     */
    public String getPreviewFontFamily() {
        return previewFontFamily;
    }

    /**
     * Sets the preview pane font family.
     *
     * @param previewFontFamily the font family name
     */
    public void setPreviewFontFamily(String previewFontFamily) {
        this.previewFontFamily = previewFontFamily;
    }

    /**
     * Returns the preview pane font size.
     *
     * @return the font size in points
     */
    public int getPreviewFontSize() {
        return previewFontSize;
    }

    /**
     * Sets the preview pane font size.
     *
     * @param previewFontSize the font size in points
     */
    public void setPreviewFontSize(int previewFontSize) {
        this.previewFontSize = previewFontSize;
    }

    /**
     * Returns the path to the preferences file in the user's home directory.
     *
     * @return the path to {@code ~/.markdownpro.json}
     */
    private static Path getPrefsPath() {
        return Paths.get(System.getProperty("user.home"), PREFS_FILENAME);
    }

    /**
     * Loads preferences from {@code ~/.markdownpro.json}. If the file does not
     * exist or cannot be parsed, returns a new instance with default values.
     *
     * @return the loaded (or default) preferences
     */
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

    /**
     * Saves the current preferences to {@code ~/.markdownpro.json} in
     * pretty-printed JSON format.
     */
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
