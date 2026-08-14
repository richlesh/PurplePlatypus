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
            downloadArtifact("org.languagetool", artifactId, LANGUAGETOOL_VERSION, jarPath);
        }

        // Also download additional dependencies (POS dicts, morphology, etc.)
        List<DependencyInfo> deps = getLanguageDependencies(langCode);
        for (DependencyInfo dep : deps) {
            Path depPath = languagesDir.resolve(dep.filename());
            if (!Files.exists(depPath)) {
                try {
                    downloadUrl(dep.url(), depPath);
                } catch (IOException e) {
                    System.err.println("LanguageDownloader: Could not download " + dep.artifactId() + ": " + e.getMessage());
                }
            }
        }

        return jarPath;
    }

    /**
     * Downloads a Maven artifact JAR by groupId/artifactId/version.
     */
    private void downloadArtifact(String groupId, String artifactId, String version, Path targetPath) throws IOException {
        String groupPath = groupId.replace('.', '/');
        String url = "https://repo1.maven.org/maven2/" + groupPath + "/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".jar";
        downloadUrl(url, targetPath);
    }

    /**
     * Downloads a file from a URL to the target path.
     */
    private void downloadUrl(String url, Path targetPath) throws IOException {
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

    /** POS dictionary descriptor: groupId, artifactId, version, optional classifier. */
    private record DependencyInfo(String groupId, String artifactId, String version, String classifier) {
        DependencyInfo(String groupId, String artifactId, String version) {
            this(groupId, artifactId, version, null);
        }

        String filename() {
            if (classifier != null) {
                return artifactId + "-" + version + "-" + classifier + ".jar";
            }
            return artifactId + "-" + version + ".jar";
        }

        String url() {
            String groupPath = groupId.replace('.', '/');
            if (classifier != null) {
                return "https://repo1.maven.org/maven2/" + groupPath + "/" + artifactId + "/" + version
                        + "/" + artifactId + "-" + version + "-" + classifier + ".jar";
            }
            return "https://repo1.maven.org/maven2/" + groupPath + "/" + artifactId + "/" + version
                    + "/" + artifactId + "-" + version + ".jar";
        }
    }

    /**
     * Returns the additional dependency JARs needed for a language (dictionaries, morphology, etc.).
     */
    private static List<DependencyInfo> getLanguageDependencies(String langCode) {
        return switch (langCode) {
            case "ast" -> List.of(new DependencyInfo("org.languagetool", "asturian-pos-dict", "0.1"));
            case "ca" -> List.of(new DependencyInfo("org.softcatala", "catalan-pos-dict", "3.3"));
            case "crh" -> List.of(new DependencyInfo("org.qirimca.nlp", "morfologik-crh-lt", "1.0.1"));
            case "de", "de-DE-x-simple-language" -> List.of(new DependencyInfo("de.danielnaber", "german-pos-dict", "1.2.4"));
            case "el" -> List.of(new DependencyInfo("org.ioperm", "morphology-el", "1.0.0"));
            case "es" -> List.of(new DependencyInfo("org.softcatala", "spanish-pos-dict", "2.5"));
            case "fr" -> List.of(new DependencyInfo("org.languagetool", "french-pos-dict", "0.7"));
            case "ja" -> List.of(new DependencyInfo("com.github.lucene-gosen", "lucene-gosen", "6.2.1", "ipadic"));
            case "nl" -> List.of(new DependencyInfo("org.languagetool", "dutch-pos-dict", "0.1"));
            case "pt" -> List.of(new DependencyInfo("org.languagetool", "portuguese-pos-dict", "1.2.0"));
            case "uk" -> List.of(new DependencyInfo("ua.net.nlp", "morfologik-ukrainian-lt", "6.4.0"));
            case "zh" -> List.of(new DependencyInfo("com.hankcs", "hanlp", "portable-1.8.2"));
            default -> List.of();
        };
    }

    /**
     * Returns all JAR paths for a language (main JAR + dependencies).
     */
    public List<Path> getAllJarPaths(String langCode) {
        List<Path> paths = new ArrayList<>();
        paths.add(getJarPath(langCode));

        List<DependencyInfo> deps = getLanguageDependencies(langCode);
        for (DependencyInfo dep : deps) {
            Path depPath = languagesDir.resolve(dep.filename());
            if (Files.exists(depPath)) {
                paths.add(depPath);
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
