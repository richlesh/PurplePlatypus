/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.spellcheck;

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.Languages;
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
        } catch (Exception e) {
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

            // Extract English dictionary resources to filesystem on first run.
            // Morfologik requires filesystem Paths for dictionary access, which fails
            // when resources are inside a fat JAR. In dev mode, extraction is skipped.
            if ("en".equals(currentLanguage)) {
                boolean extracted = extractEnglishResources();
                if (extracted) {
                    configureEnglishDataBroker();
                }
            }

            Language language = resolveLanguage(currentLanguage);
            if (language == null) {
                System.err.println("SpellCheckService: Could not resolve language: " + currentLanguage);
                initializing = false;
                return;
            }

            // For non-English languages, register with LanguageTool's Languages class
            // so that internal calls to Languages.getLanguageForShortCode() succeed.
            if (!"en".equals(currentLanguage)) {
                registerLanguage(language);
            }

            JLanguageTool lt = new JLanguageTool(language);
            for (String ruleId : DISABLED_RULES) {
                lt.disableRule(ruleId);
            }
            this.langTool = lt;
        } catch (Throwable e) {
            System.err.println("SpellCheckService: Failed to initialize LanguageTool (" + currentLanguage + "): "
                    + e.getClass().getName() + ": " + e.getMessage());
        } finally {
            initializing = false;
        }
    }

    /**
     * Extracts English language resources from the classpath to the config directory.
     * This is needed because Morfologik's Dictionary.read(Path) requires filesystem access,
     * which fails when resources are inside a fat JAR.
     *
     * @return true if extraction was performed (running from JAR), false if not needed (dev mode)
     */
    private boolean extractEnglishResources() {
        Path enDir = configDir.resolve("languages").resolve("en");
        Path marker = enDir.resolve(".extracted_english");
        if (Files.exists(marker)) {
            // Check if it was a real extraction or a dev-mode skip
            try {
                String content = Files.readString(marker);
                return "extracted".equals(content.trim());
            } catch (IOException e) {
                return false;
            }
        }

        try {
            Files.createDirectories(enDir);
            ClassLoader cl = getClass().getClassLoader();

            // Find the source of English resources — could be a JAR or directory
            URL resUrl = cl.getResource("org/languagetool/resource/en/english.dict");
            if (resUrl == null) return false; // Resources not available

            if ("file".equals(resUrl.getProtocol())) {
                // Running from exploded classpath (development) — extraction not needed
                Files.writeString(marker, "not-needed");
                return false;
            }

            // Running from a JAR — extract all English resources
            // Find the JAR file containing the resources
            String jarUrlStr = resUrl.toString(); // jar:file:/path/to/jar!/org/...
            String jarPath = jarUrlStr.substring(9, jarUrlStr.indexOf("!")); // strip "jar:file:"

            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath)) {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    // Extract LanguageTool English resource files
                    if (name.startsWith("org/languagetool/resource/en/") ||
                        name.startsWith("org/languagetool/rules/en/")) {
                        if (!entry.isDirectory()) {
                            Path target = enDir.resolve(name);
                            Files.createDirectories(target.getParent());
                            try (InputStream is = jar.getInputStream(entry)) {
                                Files.copy(is, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }
            }
            Files.writeString(marker, "extracted");
            return true;
        } catch (IOException e) {
            System.err.println("SpellCheckService: Could not extract English resources: " + e.getMessage());
            return false;
        }
    }

    /**
     * Configures LanguageTool's DataBroker to look for resources in the extracted
     * English directory first, falling back to the classpath. This ensures Morfologik
     * can find dictionary files as filesystem Paths regardless of packaging format.
     */
    private void configureEnglishDataBroker() {
        Path enDir = configDir.resolve("languages").resolve("en");
        if (!Files.exists(enDir)) return;

        try {
            URL enDirUrl = enDir.toUri().toURL();
            URLClassLoader enClassLoader = new URLClassLoader(
                    new URL[]{enDirUrl},
                    getClass().getClassLoader()
            );

            JLanguageTool.setDataBroker(new org.languagetool.broker.DefaultResourceDataBroker() {
                @Override
                public InputStream getAsStream(String path) {
                    String p = path.startsWith("/") ? path.substring(1) : path;
                    InputStream is = enClassLoader.getResourceAsStream(p);
                    return is != null ? is : super.getAsStream(path);
                }

                @Override
                public URL getAsURL(String path) {
                    String p = path.startsWith("/") ? path.substring(1) : path;
                    URL url = enClassLoader.getResource(p);
                    return url != null ? url : super.getAsURL(path);
                }

                @Override
                public URL getFromResourceDirAsUrl(String path) {
                    String fullPath = getResourceDir() + path;
                    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
                    URL url = enClassLoader.getResource(p);
                    return url != null ? url : super.getFromResourceDirAsUrl(path);
                }

                @Override
                public InputStream getFromResourceDirAsStream(String path) {
                    String fullPath = getResourceDir() + path;
                    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
                    InputStream is = enClassLoader.getResourceAsStream(p);
                    return is != null ? is : super.getFromResourceDirAsStream(path);
                }

                @Override
                public boolean resourceExists(String path) {
                    String fullPath = getResourceDir() + path;
                    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
                    return enClassLoader.getResource(p) != null || super.resourceExists(path);
                }

                @Override
                public InputStream getFromRulesDirAsStream(String path) {
                    String fullPath = getRulesDir() + path;
                    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
                    InputStream is = enClassLoader.getResourceAsStream(p);
                    return is != null ? is : super.getFromRulesDirAsStream(path);
                }

                @Override
                public URL getFromRulesDirAsUrl(String path) {
                    String fullPath = getRulesDir() + path;
                    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
                    URL url = enClassLoader.getResource(p);
                    return url != null ? url : super.getFromRulesDirAsUrl(path);
                }
            });
        } catch (Exception e) {
            // Fall through — will use default classpath-based broker
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

        // Download the language JAR and dependencies if not already present
        try {
            downloader.download(langCode);
        } catch (IOException e) {
            System.err.println("SpellCheckService: Failed to download language pack for " + langCode + ": " + e.getMessage());
            // Continue anyway — the main JAR might already exist
            if (!downloader.isDownloaded(langCode)) {
                return null;
            }
        }

        // Load the language from the downloaded JAR
        return loadLanguageFromJar(langCode);
    }

    /**
     * Loads a Language instance from a downloaded JAR. Extracts all JARs
     * (language + POS dictionary) to a directory so that LanguageTool's
     * Morfologik dictionary loading (which requires filesystem Paths)
     * can access the .dict files.
     */
    private Language loadLanguageFromJar(String langCode) {
        Path jarPath = downloader.getJarPath(langCode);
        if (!Files.exists(jarPath)) {
            return null;
        }

        try {
            // Get all JAR paths (language JAR + pos-dict JAR if applicable)
            List<Path> allJars = downloader.getAllJarPaths(langCode);

            // Extract all JARs to a shared directory
            Path extractDir = jarPath.getParent().resolve(langCode);
            for (Path jar : allJars) {
                if (Files.exists(jar)) {
                    extractJarIfNeeded(jar, extractDir);
                }
            }

            // Create a classloader that uses the extracted directory (first, for file: Path access)
            // plus the original JARs (for any resources that don't need Path access)
            List<URL> urls = new ArrayList<>();
            urls.add(extractDir.toUri().toURL());
            for (Path jar : allJars) {
                if (Files.exists(jar)) {
                    urls.add(jar.toUri().toURL());
                }
            }
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader langClassLoader = new URLClassLoader(
                    urls.toArray(new URL[0]),
                    this.getClass().getClassLoader()
            );

            // Configure LanguageTool to use this classloader for class and resource loading
            JLanguageTool.setClassBrokerBroker(className -> langClassLoader.loadClass(className));
            JLanguageTool.setDataBroker(new org.languagetool.broker.DefaultResourceDataBroker() {
                @Override
                public InputStream getAsStream(String path) {
                    String p = path.startsWith("/") ? path.substring(1) : path;
                    InputStream is = langClassLoader.getResourceAsStream(p);
                    return is != null ? is : super.getAsStream(path);
                }

                @Override
                public URL getAsURL(String path) {
                    String p = path.startsWith("/") ? path.substring(1) : path;
                    URL url = langClassLoader.getResource(p);
                    return url != null ? url : super.getAsURL(path);
                }

                @Override
                public java.util.List<URL> getAsURLs(String path) {
                    try {
                        String p = path.startsWith("/") ? path.substring(1) : path;
                        java.util.List<URL> urls = java.util.Collections.list(langClassLoader.getResources(p));
                        if (!urls.isEmpty()) return urls;
                    } catch (IOException e) { /* fall through */ }
                    return super.getAsURLs(path);
                }

                @Override
                public URL getFromResourceDirAsUrl(String path) {
                    String fullPath = getResourceDir() + path;
                    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
                    URL url = langClassLoader.getResource(p);
                    return url != null ? url : super.getFromResourceDirAsUrl(path);
                }

                @Override
                public InputStream getFromResourceDirAsStream(String path) {
                    String fullPath = getResourceDir() + path;
                    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
                    InputStream is = langClassLoader.getResourceAsStream(p);
                    return is != null ? is : super.getFromResourceDirAsStream(path);
                }

                @Override
                public boolean resourceExists(String path) {
                    String fullPath = getResourceDir() + path;
                    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
                    return langClassLoader.getResource(p) != null || super.resourceExists(path);
                }

                @Override
                public InputStream getFromRulesDirAsStream(String path) {
                    String fullPath = getRulesDir() + path;
                    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
                    InputStream is = langClassLoader.getResourceAsStream(p);
                    return is != null ? is : super.getFromRulesDirAsStream(path);
                }

                @Override
                public URL getFromRulesDirAsUrl(String path) {
                    String fullPath = getRulesDir() + path;
                    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
                    URL url = langClassLoader.getResource(p);
                    return url != null ? url : super.getFromRulesDirAsUrl(path);
                }

                @Override
                public java.util.ResourceBundle getResourceBundle(String baseName, java.util.Locale locale) {
                    try {
                        return java.util.ResourceBundle.getBundle(baseName, locale, langClassLoader);
                    } catch (java.util.MissingResourceException e) {
                        return super.getResourceBundle(baseName, locale);
                    }
                }
            });

            // Read language-module.properties DIRECTLY from the JAR file
            String propsPath = "META-INF/org/languagetool/language-module.properties";
            URL propsUrl = new URL("jar:" + jarUrl + "!/" + propsPath);
            Properties props = new Properties();
            try (InputStream propsStream = propsUrl.openStream()) {
                props.load(propsStream);
            }

            String classesStr = props.getProperty("languageClasses");
            if (classesStr == null || classesStr.isBlank()) {
                System.err.println("SpellCheckService: No languageClasses in " + jarPath);
                return null;
            }

            // Parse comma-separated class names and instantiate them
            String[] classNames = classesStr.split(",");
            Language bestMatch = null;
            List<Language> allVariants = new ArrayList<>();

            for (String className : classNames) {
                className = className.trim();
                if (className.isEmpty()) continue;
                try {
                    Class<?> langClass = langClassLoader.loadClass(className);
                    Language lang = (Language) langClass.getDeclaredConstructor().newInstance();
                    allVariants.add(lang);
                    if (bestMatch == null) {
                        bestMatch = lang;
                    }
                } catch (Exception e) {
                    System.err.println("SpellCheckService: Could not load " + className + ": " + e.getMessage());
                }
            }

            // Register all variants with LanguageTool's Languages registry
            for (Language variant : allVariants) {
                registerLanguage(variant);
            }

            return bestMatch;
        } catch (Exception e) {
            System.err.println("SpellCheckService: Failed to load language from JAR: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Extracts a JAR file to a directory if not already extracted.
     * Uses a per-JAR marker file to track which JARs have been extracted.
     */
    private void extractJarIfNeeded(Path jarPath, Path extractDir) throws IOException {
        // Use jar filename as marker to support multiple JARs in same dir
        Path marker = extractDir.resolve(".extracted_" + jarPath.getFileName());
        if (Files.exists(marker)) {
            return;
        }

        Files.createDirectories(extractDir);

        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath.toFile())) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                Path targetPath = extractDir.resolve(entry.getName());

                // Security: prevent path traversal
                if (!targetPath.normalize().startsWith(extractDir.normalize())) {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    try (InputStream is = jar.getInputStream(entry)) {
                        Files.copy(is, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }

        // Write marker file
        Files.writeString(marker, "extracted");
    }

    /**
     * Registers a dynamically loaded Language with LanguageTool's internal Languages registry.
     * This is necessary so that internal calls to Languages.getLanguageForShortCode() succeed.
     * Uses reflection to access the internal lists since there's no public API for this.
     */
    @SuppressWarnings("unchecked")
    private void registerLanguage(Language language) {
        try {
            // Check if already registered
            if (Languages.isLanguageSupported(language.getShortCodeWithCountryAndVariant())) {
                return;
            }
        } catch (Exception e) {
            // Not registered — proceed to register
        }

        try {
            // Access Languages.dynLanguages (List<Language>)
            java.lang.reflect.Field dynField = Languages.class.getDeclaredField("dynLanguages");
            dynField.setAccessible(true);
            List<Language> dynLanguages = (List<Language>) dynField.get(null);
            dynLanguages.add(language);

            // Access Languages.staticAndDynamicLanguages (List<Language>)
            java.lang.reflect.Field sadField = Languages.class.getDeclaredField("staticAndDynamicLanguages");
            sadField.setAccessible(true);
            List<Language> sadLanguages = (List<Language>) sadField.get(null);
            sadLanguages.add(language);
        } catch (Exception e) {
            System.err.println("SpellCheckService: Could not register language via reflection: " + e.getMessage());
            // Try the public API as fallback (may fail with ClassNotFoundException)
            try {
                Languages.getOrAddLanguageByClassName(language.getClass().getName());
            } catch (Exception ex) {
                System.err.println("SpellCheckService: Fallback registration also failed: " + ex.getMessage());
            }
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
