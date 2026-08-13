/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.spellcheck;

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * Core spell-checking service backed by LanguageTool.
 * <p>
 * Initializes LanguageTool asynchronously on first construction and manages
 * a per-user dictionary stored in the provided config directory.
 * Supports switching languages at runtime by downloading language JARs.
 */
public class SpellCheckService {

    /** A spelling or grammar error found in the text. */
    public record SpellError(int startOffset, int endOffset, String message, List<String> suggestions) {}

    private static final String USER_DICT_FILENAME = "user-dictionary.txt";

    /** Rules to disable — too noisy for markdown editing. */
    private static final List<String> DISABLED_RULES = List.of(
            "UPPERCASE_SENTENCE_START",
            "COMMA_PARENTHESIS_WHITESPACE",
            "EN_UNPAIRED_BRACKETS",
            "WHITESPACE_RULE",
            "EN_QUOTES",
            "DASH_RULE",
            "THREE_NN",
            "ENGLISH_WORD_REPEAT_BEGINNING_RULE"
    );

    private final Path configDir;
    private final Path userDictPath;
    private final Set<String> userDictionary = new CopyOnWriteArraySet<>();
    private final LanguageDownloader downloader;
    private volatile JLanguageTool langTool;
    private volatile String currentLanguage;
    private volatile boolean initializing = false;

    /**
     * Creates the spell-check service with the default language (English).
     *
     * @param configDir directory for storing the user dictionary (e.g., ~/.purpleplatypus/)
     */
    public SpellCheckService(Path configDir) {
        this(configDir, "en");
    }

    /**
     * Creates the spell-check service with a specified language.
     *
     * @param configDir directory for storing the user dictionary (e.g., ~/.purpleplatypus/)
     * @param langCode  the language code (e.g., "en", "fr", "de")
     */
    public SpellCheckService(Path configDir, String langCode) {
        this.configDir = configDir;
        this.userDictPath = configDir.resolve(USER_DICT_FILENAME);
        this.downloader = new LanguageDownloader(configDir);
        this.currentLanguage = langCode != null ? langCode : "en";
        loadUserDictionary();
        initializeAsync();
    }

    /** Returns true once LanguageTool has finished initializing. */
    public boolean isReady() {
        return langTool != null && !initializing;
    }

    /** Returns the current language code. */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Changes the spell-check language. Downloads the language JAR if needed.
     * Reinitializes LanguageTool asynchronously.
     *
     * @param langCode the new language code
     */
    public void setLanguage(String langCode) {
        if (langCode == null || langCode.equals(currentLanguage)) return;
        this.currentLanguage = langCode;
        this.langTool = null;
        initializeAsync();
    }

    /**
     * Checks the given text for spelling and grammar errors.
     *
     * @param text the text to check
     * @return list of errors found, empty if the service isn't ready yet
     */
    public List<SpellError> check(String text) {
        JLanguageTool lt = langTool;
        if (lt == null || text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<RuleMatch> matches = lt.check(text);
            List<SpellError> errors = new ArrayList<>();
            for (RuleMatch match : matches) {
                String word = text.substring(match.getFromPos(), match.getToPos());
                // Skip words in the user dictionary (case-insensitive)
                if (userDictionary.contains(word.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                errors.add(new SpellError(
                        match.getFromPos(),
                        match.getToPos(),
                        match.getMessage(),
                        match.getSuggestedReplacements()
                ));
            }
            return errors;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Adds a word to the user dictionary and persists it.
     *
     * @param word the word to add
     */
    public void addToDictionary(String word) {
        if (word == null || word.isBlank()) return;
        userDictionary.add(word.toLowerCase(Locale.ROOT));
        saveUserDictionary();
    }

    /** Returns an unmodifiable view of the user dictionary words. */
    public Set<String> getUserDictionary() {
        return Collections.unmodifiableSet(userDictionary);
    }

    private void initializeAsync() {
        initializing = true;
        CompletableFuture.runAsync(this::initLanguageTool);
    }

    private void initLanguageTool() {
        try {
            // LanguageTool's grammar.xml exceeds the default JDK XML entity size limit
            System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
            System.setProperty("jdk.xml.entityExpansionLimit", "0");

            Language language = resolveLanguage(currentLanguage);
            if (language == null) {
                System.err.println("SpellCheckService: Could not resolve language: " + currentLanguage);
                initializing = false;
                return;
            }

            JLanguageTool lt = new JLanguageTool(language);
            for (String ruleId : DISABLED_RULES) {
                lt.disableRule(ruleId);
            }
            this.langTool = lt;
        } catch (Exception e) {
            System.err.println("SpellCheckService: Failed to initialize LanguageTool: " + e.getMessage());
            e.printStackTrace();
        } finally {
            initializing = false;
        }
    }

    /**
     * Resolves the Language object for the given code.
     * For English, uses the bundled AmericanEnglish. For others, downloads the JAR
     * and loads the Language class dynamically.
     */
    private Language resolveLanguage(String langCode) {
        if ("en".equals(langCode)) {
            return new AmericanEnglish();
        }

        // Download the language JAR if not already present
        try {
            if (!downloader.isDownloaded(langCode)) {
                downloader.download(langCode);
            }
        } catch (IOException e) {
            System.err.println("SpellCheckService: Failed to download language pack for " + langCode + ": " + e.getMessage());
            return null;
        }

        // Load the language from the downloaded JAR
        return loadLanguageFromJar(langCode);
    }

    /**
     * Loads a Language instance from a downloaded JAR using URLClassLoader.
     * Reads the language-module.properties to find the language class names,
     * then instantiates the first one (or the best match for the language code).
     */
    private Language loadLanguageFromJar(String langCode) {
        Path jarPath = downloader.getJarPath(langCode);
        if (!Files.exists(jarPath)) {
            return null;
        }

        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{jarUrl},
                    this.getClass().getClassLoader()
            );

            // Read language-module.properties from the JAR
            InputStream propsStream = classLoader.getResourceAsStream(
                    "META-INF/org/languagetool/language-module.properties");
            if (propsStream == null) {
                System.err.println("SpellCheckService: No language-module.properties in " + jarPath);
                return null;
            }

            Properties props = new Properties();
            props.load(propsStream);
            propsStream.close();

            String classesStr = props.getProperty("languageClasses");
            if (classesStr == null || classesStr.isBlank()) {
                return null;
            }

            // Parse comma-separated class names and instantiate the best match
            String[] classNames = classesStr.split(",");
            Language bestMatch = null;

            for (String className : classNames) {
                className = className.trim();
                if (className.isEmpty()) continue;
                try {
                    Class<?> langClass = classLoader.loadClass(className);
                    Language lang = (Language) langClass.getDeclaredConstructor().newInstance();
                    // Prefer a more specific variant (first class is usually the base)
                    if (bestMatch == null) {
                        bestMatch = lang;
                    }
                } catch (Exception e) {
                    // Try next class
                }
            }

            return bestMatch;
        } catch (Exception e) {
            System.err.println("SpellCheckService: Failed to load language from JAR: " + e.getMessage());
            return null;
        }
    }

    private void loadUserDictionary() {
        try {
            if (Files.exists(userDictPath)) {
                List<String> lines = Files.readAllLines(userDictPath, StandardCharsets.UTF_8);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        userDictionary.add(trimmed.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("SpellCheckService: Could not load user dictionary: " + e.getMessage());
        }
    }

    private void saveUserDictionary() {
        try {
            Files.createDirectories(configDir);
            List<String> sorted = userDictionary.stream().sorted().collect(Collectors.toList());
            Files.write(userDictPath, sorted, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("SpellCheckService: Could not save user dictionary: " + e.getMessage());
        }
    }
}
