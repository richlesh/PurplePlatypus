/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.spellcheck;

import org.languagetool.JLanguageTool;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
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
    private volatile JLanguageTool langTool;
    private final CompletableFuture<Void> initFuture;

    /**
     * Creates the spell-check service.
     *
     * @param configDir directory for storing the user dictionary (e.g., ~/.purpleplatypus/)
     */
    public SpellCheckService(Path configDir) {
        this.configDir = configDir;
        this.userDictPath = configDir.resolve(USER_DICT_FILENAME);
        loadUserDictionary();
        initFuture = CompletableFuture.runAsync(this::initLanguageTool);
    }

    /** Returns true once LanguageTool has finished initializing. */
    public boolean isReady() {
        return langTool != null;
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

    private void initLanguageTool() {
        try {
            JLanguageTool lt = new JLanguageTool(new AmericanEnglish());
            for (String ruleId : DISABLED_RULES) {
                lt.disableRule(ruleId);
            }
            this.langTool = lt;
        } catch (Exception e) {
            System.err.println("SpellCheckService: Failed to initialize LanguageTool: " + e.getMessage());
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
