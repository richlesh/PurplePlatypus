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
        // Sorted alphabetically by display name (native language name — English name)
        AVAILABLE_LANGUAGES.put("ar", "العربية — Arabic");
        AVAILABLE_LANGUAGES.put("ast", "Asturianu — Asturian");
        AVAILABLE_LANGUAGES.put("be", "Беларуская — Belarusian");
        AVAILABLE_LANGUAGES.put("br", "Brezhoneg — Breton");
        AVAILABLE_LANGUAGES.put("ca", "Català — Catalan");
        AVAILABLE_LANGUAGES.put("crh", "Qırımtatar — Crimean Tatar");
        AVAILABLE_LANGUAGES.put("da", "Dansk — Danish");
        AVAILABLE_LANGUAGES.put("de", "Deutsch — German");
        AVAILABLE_LANGUAGES.put("de-DE-x-simple-language", "Einfaches Deutsch — Simple German");
        AVAILABLE_LANGUAGES.put("el", "Ελληνικά — Greek");
        AVAILABLE_LANGUAGES.put("en", "English (American)");
        AVAILABLE_LANGUAGES.put("eo", "Esperanto");
        AVAILABLE_LANGUAGES.put("es", "Español — Spanish");
        AVAILABLE_LANGUAGES.put("fr", "Français — French");
        AVAILABLE_LANGUAGES.put("ga", "Gaeilge — Irish");
        AVAILABLE_LANGUAGES.put("gl", "Galego — Galician");
        AVAILABLE_LANGUAGES.put("it", "Italiano — Italian");
        AVAILABLE_LANGUAGES.put("ja", "日本語 — Japanese");
        AVAILABLE_LANGUAGES.put("km", "ភាសាខ្មែរ — Khmer");
        AVAILABLE_LANGUAGES.put("nl", "Nederlands — Dutch");
        AVAILABLE_LANGUAGES.put("fa", "فارسی — Persian");
        AVAILABLE_LANGUAGES.put("pl", "Polski — Polish");
        AVAILABLE_LANGUAGES.put("pt", "Português — Portuguese");
        AVAILABLE_LANGUAGES.put("ro", "Română — Romanian");
        AVAILABLE_LANGUAGES.put("ru", "Русский — Russian");
        AVAILABLE_LANGUAGES.put("sk", "Slovenčina — Slovak");
        AVAILABLE_LANGUAGES.put("sl", "Slovenščina — Slovenian");
        AVAILABLE_LANGUAGES.put("sv", "Svenska — Swedish");
        AVAILABLE_LANGUAGES.put("ta", "தமிழ் — Tamil");
        AVAILABLE_LANGUAGES.put("tl", "Tagalog");
        AVAILABLE_LANGUAGES.put("uk", "Українська — Ukrainian");
        AVAILABLE_LANGUAGES.put("zh", "中文 — Chinese");
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
     * Downloads the language JAR and its POS dictionary dependency (if any)
     * from Maven Central if not already present.
     *
     * @param langCode the language code (e.g., "fr", "de", "es")
     * @return the path to the downloaded language JAR
     * @throws IOException if the download fails
     */
    public Path download(String langCode) throws IOException {
        if ("en".equals(langCode)) {
            throw new IllegalArgumentException("English is bundled and does not need downloading");
        }

        Path jarPath = getJarPath(langCode);
        Files.createDirectories(languagesDir);

        // Download the main language JAR if needed
        if (!Files.exists(jarPath)) {
            String artifactId = "language-" + langCode;
            downloadArtifact(artifactId, LANGUAGETOOL_VERSION, jarPath);
        }

        // Also download the POS dictionary dependency if it exists and isn't downloaded yet
        String posDictArtifactId = getPosDictArtifact(langCode);
        if (posDictArtifactId != null) {
            String posDictVersion = getPosDictVersion(posDictArtifactId);
            Path posDictPath = languagesDir.resolve(posDictArtifactId + "-" + posDictVersion + ".jar");
            if (!Files.exists(posDictPath)) {
                try {
                    downloadArtifact(posDictArtifactId, posDictVersion, posDictPath);
                } catch (IOException e) {
                    System.err.println("LanguageDownloader: Could not download POS dict " + posDictArtifactId + ": " + e.getMessage());
                }
            }
        }

        return jarPath;
    }

    /**
     * Downloads a single Maven artifact JAR.
     */
    private void downloadArtifact(String artifactId, String version, Path targetPath) throws IOException {
        String url = MAVEN_CENTRAL_BASE + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".jar";

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
                throw new IOException("Failed to download: HTTP " + response.statusCode()
                        + " for " + url);
            }

            // Download to a temp file first, then move atomically
            Path tempFile = targetPath.getParent().resolve(targetPath.getFileName() + ".tmp");
            try (InputStream is = response.body()) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    /**
     * Returns the POS dictionary artifact ID for a language, or null if none is needed.
     */
    private static String getPosDictArtifact(String langCode) {
        return switch (langCode) {
            case "de", "de-DE-x-simple-language" -> "german-pos-dict";
            case "es" -> "spanish-pos-dict";
            case "fr" -> "french-pos-dict";
            case "nl" -> "dutch-pos-dict";
            case "pt" -> "portuguese-pos-dict";
            case "ca" -> "catalan-pos-dict";
            case "gl" -> "galician-pos-dict";
            case "uk" -> "ukrainian-pos-dict";
            case "ro" -> "romanian-pos-dict";
            default -> null;
        };
    }

    /**
     * Returns the version for a POS dictionary artifact.
     * These have their own versioning separate from LanguageTool.
     */
    private static String getPosDictVersion(String artifactId) {
        return switch (artifactId) {
            case "german-pos-dict" -> "0.4";
            case "spanish-pos-dict" -> "0.3";
            case "french-pos-dict" -> "0.7";
            case "dutch-pos-dict" -> "0.3";
            case "portuguese-pos-dict" -> "0.4";
            case "catalan-pos-dict" -> "0.5";
            case "galician-pos-dict" -> "0.3";
            case "ukrainian-pos-dict" -> "6.4";
            case "romanian-pos-dict" -> "0.2";
            default -> "0.1";
        };
    }

    /**
     * Returns all JAR paths for a language (main JAR + pos-dict if applicable).
     */
    public List<Path> getAllJarPaths(String langCode) {
        List<Path> paths = new ArrayList<>();
        paths.add(getJarPath(langCode));

        String posDictArtifactId = getPosDictArtifact(langCode);
        if (posDictArtifactId != null) {
            String version = getPosDictVersion(posDictArtifactId);
            Path posDictPath = languagesDir.resolve(posDictArtifactId + "-" + version + ".jar");
            if (Files.exists(posDictPath)) {
                paths.add(posDictPath);
            }
        }
        return paths;
    }

    /**
     * Returns the LanguageTool version used for downloads.
     */
    public static String getLanguageToolVersion() {
        return LANGUAGETOOL_VERSION;
    }
}
