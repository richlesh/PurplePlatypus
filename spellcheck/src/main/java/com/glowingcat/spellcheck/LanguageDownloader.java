/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.spellcheck;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;

/**
 * Downloads LanguageTool language JARs from Maven Central to a local directory.
 * <p>
 * Downloaded JARs are cached in {@code ~/.purpleplatypus/languages/} so they
 * only need to be downloaded once per language.
 */
public class LanguageDownloader {

    private static final String LANGUAGETOOL_VERSION = "6.4";
    private static final String MAVEN_CENTRAL_BASE =
            "https://repo1.maven.org/maven2/org/languagetool/";

    /** All available LanguageTool language artifacts (code → display name). */
    private static final LinkedHashMap<String, String> AVAILABLE_LANGUAGES = new LinkedHashMap<>();

    static {
        AVAILABLE_LANGUAGES.put("en", "English (American)");
        AVAILABLE_LANGUAGES.put("ar", "Arabic");
        AVAILABLE_LANGUAGES.put("ast", "Asturian");
        AVAILABLE_LANGUAGES.put("be", "Belarusian");
        AVAILABLE_LANGUAGES.put("br", "Breton");
        AVAILABLE_LANGUAGES.put("ca", "Catalan");
        AVAILABLE_LANGUAGES.put("crh", "Crimean Tatar");
        AVAILABLE_LANGUAGES.put("da", "Danish");
        AVAILABLE_LANGUAGES.put("de", "German");
        AVAILABLE_LANGUAGES.put("de-DE-x-simple-language", "Simple German");
        AVAILABLE_LANGUAGES.put("el", "Greek");
        AVAILABLE_LANGUAGES.put("eo", "Esperanto");
        AVAILABLE_LANGUAGES.put("es", "Spanish");
        AVAILABLE_LANGUAGES.put("fa", "Persian");
        AVAILABLE_LANGUAGES.put("fr", "French");
        AVAILABLE_LANGUAGES.put("ga", "Irish");
        AVAILABLE_LANGUAGES.put("gl", "Galician");
        AVAILABLE_LANGUAGES.put("it", "Italian");
        AVAILABLE_LANGUAGES.put("ja", "Japanese");
        AVAILABLE_LANGUAGES.put("km", "Khmer");
        AVAILABLE_LANGUAGES.put("nl", "Dutch");
        AVAILABLE_LANGUAGES.put("pl", "Polish");
        AVAILABLE_LANGUAGES.put("pt", "Portuguese");
        AVAILABLE_LANGUAGES.put("ro", "Romanian");
        AVAILABLE_LANGUAGES.put("ru", "Russian");
        AVAILABLE_LANGUAGES.put("sk", "Slovak");
        AVAILABLE_LANGUAGES.put("sl", "Slovenian");
        AVAILABLE_LANGUAGES.put("sv", "Swedish");
        AVAILABLE_LANGUAGES.put("ta", "Tamil");
        AVAILABLE_LANGUAGES.put("tl", "Tagalog");
        AVAILABLE_LANGUAGES.put("uk", "Ukrainian");
        AVAILABLE_LANGUAGES.put("zh", "Chinese");
    }

    private final Path languagesDir;

    /**
     * Creates a downloader that stores JARs in the given config directory.
     *
     * @param configDir the base config directory (e.g., ~/.purpleplatypus/)
     */
    public LanguageDownloader(Path configDir) {
        this.languagesDir = configDir.resolve("languages");
    }

    /**
     * Returns all available languages as a map of language code → display name.
     */
    public static Map<String, String> getAvailableLanguages() {
        return Collections.unmodifiableMap(AVAILABLE_LANGUAGES);
    }

    /**
     * Returns the display name for a language code.
     */
    public static String getDisplayName(String langCode) {
        return AVAILABLE_LANGUAGES.getOrDefault(langCode, langCode);
    }

    /**
     * Returns the language code for a display name.
     */
    public static String getCodeForDisplayName(String displayName) {
        for (Map.Entry<String, String> entry : AVAILABLE_LANGUAGES.entrySet()) {
            if (entry.getValue().equals(displayName)) {
                return entry.getKey();
            }
        }
        return "en";
    }

    /**
     * Returns true if the language JAR is already downloaded locally.
     */
    public boolean isDownloaded(String langCode) {
        if ("en".equals(langCode)) return true; // English is bundled
        return Files.exists(getJarPath(langCode));
    }

    /**
     * Returns the path to the downloaded JAR for a language.
     */
    public Path getJarPath(String langCode) {
        String artifactId = "language-" + langCode;
        String filename = artifactId + "-" + LANGUAGETOOL_VERSION + ".jar";
        return languagesDir.resolve(filename);
    }

    /**
     * Downloads the language JAR from Maven Central if not already present.
     *
     * @param langCode the language code (e.g., "fr", "de", "es")
     * @return the path to the downloaded JAR
     * @throws IOException if the download fails
     */
    public Path download(String langCode) throws IOException {
        if ("en".equals(langCode)) {
            throw new IllegalArgumentException("English is bundled and does not need downloading");
        }

        Path jarPath = getJarPath(langCode);
        if (Files.exists(jarPath)) {
            return jarPath;
        }

        Files.createDirectories(languagesDir);

        String artifactId = "language-" + langCode;
        String url = MAVEN_CENTRAL_BASE + artifactId + "/" + LANGUAGETOOL_VERSION
                + "/" + artifactId + "-" + LANGUAGETOOL_VERSION + ".jar";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to download language pack: HTTP " + response.statusCode()
                        + " for " + url);
            }

            // Download to a temp file first, then move atomically
            Path tempFile = languagesDir.resolve(jarPath.getFileName() + ".tmp");
            try (InputStream is = response.body()) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempFile, jarPath, StandardCopyOption.REPLACE_EXISTING);

            return jarPath;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    /**
     * Returns the LanguageTool version used for downloads.
     */
    public static String getLanguageToolVersion() {
        return LANGUAGETOOL_VERSION;
    }
}
