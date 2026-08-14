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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads LanguageTool language JARs from Maven Central to a local directory.
 * <p>
 * Downloaded JARs are cached in {@code ~/.purpleplatypus/languages/} so they
 * only need to be downloaded once per language. Dependencies (POS dictionaries,
 * morphology JARs, etc.) are discovered automatically by parsing the language
 * JAR's POM file from Maven Central.
 */
public class LanguageDownloader {

    private static final String LANGUAGETOOL_VERSION = "6.4";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    /** All available LanguageTool language artifacts (code → display name). */
    private static final LinkedHashMap<String, String> AVAILABLE_LANGUAGES = new LinkedHashMap<>();

    static {
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
        AVAILABLE_LANGUAGES.put("en", "English — American English");
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
        AVAILABLE_LANGUAGES.put("tl", "Filipino — Tagalog");
        AVAILABLE_LANGUAGES.put("uk", "Українська — Ukrainian");
        AVAILABLE_LANGUAGES.put("zh", "中文 — Chinese");
    }

    /** Artifacts to skip when resolving dependencies (already on classpath or test-only). */
    private static final Set<String> SKIP_ARTIFACTS = Set.of(
            "languagetool-core", "junit", "logback-classic", "openregex"
    );

    /** Regex patterns for parsing POM XML without a full XML parser. */
    private static final Pattern DEPENDENCY_BLOCK = Pattern.compile(
            "<dependency>\\s*(.*?)\\s*</dependency>", Pattern.DOTALL);
    private static final Pattern GROUP_ID = Pattern.compile("<groupId>([^<]+)</groupId>");
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>([^<]+)</artifactId>");
    private static final Pattern VERSION_TAG = Pattern.compile("<version>([^<]+)</version>");
    private static final Pattern SCOPE_TAG = Pattern.compile("<scope>([^<]+)</scope>");
    private static final Pattern CLASSIFIER_TAG = Pattern.compile("<classifier>([^<]+)</classifier>");
    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{([^}]+)}");

    private final Path languagesDir;
    private final HttpClient httpClient;

    public LanguageDownloader(Path configDir) {
        this.languagesDir = configDir.resolve("languages");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Returns all available languages as a map of language code → display name. */
    public static Map<String, String> getAvailableLanguages() {
        return Collections.unmodifiableMap(AVAILABLE_LANGUAGES);
    }

    /** Returns the display name for a language code. */
    public static String getDisplayName(String langCode) {
        return AVAILABLE_LANGUAGES.getOrDefault(langCode, langCode);
    }

    /** Returns the language code for a display name. */
    public static String getCodeForDisplayName(String displayName) {
        for (Map.Entry<String, String> entry : AVAILABLE_LANGUAGES.entrySet()) {
            if (entry.getValue().equals(displayName)) {
                return entry.getKey();
            }
        }
        return "en";
    }

    /** Returns true if the language JAR is already downloaded locally. */
    public boolean isDownloaded(String langCode) {
        if ("en".equals(langCode)) return true;
        return Files.exists(getJarPath(langCode));
    }

    /** Returns the path to the downloaded JAR for a language. */
    public Path getJarPath(String langCode) {
        String artifactId = "language-" + langCode;
        String filename = artifactId + "-" + LANGUAGETOOL_VERSION + ".jar";
        return languagesDir.resolve(filename);
    }

    /**
     * Downloads the language JAR and automatically resolves and downloads its
     * dependencies by parsing the POM file from Maven Central.
     *
     * @param langCode the language code (e.g., "fr", "de", "es")
     * @return the path to the downloaded language JAR
     * @throws IOException if the download fails
     */
    public Path download(String langCode) throws IOException {
        if ("en".equals(langCode)) {
            throw new IllegalArgumentException("English is bundled and does not need downloading");
        }

        Files.createDirectories(languagesDir);
        Path jarPath = getJarPath(langCode);

        // Download the main language JAR if needed
        if (!Files.exists(jarPath)) {
            String artifactId = "language-" + langCode;
            downloadArtifact("org.languagetool", artifactId, LANGUAGETOOL_VERSION, null, jarPath);
        }

        // Resolve and download dependencies from the POM
        resolveDependencies(langCode);

        return jarPath;
    }

    /**
     * Resolves dependencies by downloading and parsing the language's POM file.
     * Also fetches the parent POM to resolve version properties.
     */
    private void resolveDependencies(String langCode) {
        try {
            String artifactId = "language-" + langCode;
            String pomUrl = MAVEN_CENTRAL + "org/languagetool/" + artifactId + "/" + LANGUAGETOOL_VERSION
                    + "/" + artifactId + "-" + LANGUAGETOOL_VERSION + ".pom";

            String pomContent = fetchText(pomUrl);
            if (pomContent == null) return;

            // Also fetch parent POM for version properties
            String parentPomUrl = MAVEN_CENTRAL + "org/languagetool/languagetool-parent/"
                    + LANGUAGETOOL_VERSION + "/languagetool-parent-" + LANGUAGETOOL_VERSION + ".pom";
            String parentPom = fetchText(parentPomUrl);
            Map<String, String> properties = parseProperties(parentPom);

            // Parse dependencies from the language POM
            List<DependencyInfo> deps = parseDependencies(pomContent, properties);

            // Download each dependency
            for (DependencyInfo dep : deps) {
                Path depPath = languagesDir.resolve(dep.filename());
                if (!Files.exists(depPath)) {
                    try {
                        downloadUrl(dep.url(), depPath);
                    } catch (IOException e) {
                        System.err.println("LanguageDownloader: Could not download "
                                + dep.groupId() + ":" + dep.artifactId() + " - " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("LanguageDownloader: Error resolving dependencies for " + langCode + ": " + e.getMessage());
        }
    }

    /**
     * Parses dependency blocks from a POM file, filtering out test-scoped and
     * known-on-classpath artifacts.
     */
    private List<DependencyInfo> parseDependencies(String pomContent, Map<String, String> properties) {
        List<DependencyInfo> deps = new ArrayList<>();

        Matcher depMatcher = DEPENDENCY_BLOCK.matcher(pomContent);
        while (depMatcher.find()) {
            String block = depMatcher.group(1);

            // Skip test-scoped dependencies
            Matcher scopeMatcher = SCOPE_TAG.matcher(block);
            if (scopeMatcher.find() && "test".equals(scopeMatcher.group(1))) {
                continue;
            }

            Matcher gidMatcher = GROUP_ID.matcher(block);
            Matcher aidMatcher = ARTIFACT_ID.matcher(block);
            if (!gidMatcher.find() || !aidMatcher.find()) continue;

            String groupId = gidMatcher.group(1).trim();
            String artifactId = aidMatcher.group(1).trim();

            // Skip artifacts we already have on the classpath
            if (SKIP_ARTIFACTS.contains(artifactId)) continue;
            if ("org.languagetool".equals(groupId) && artifactId.startsWith("language")) continue;

            // Resolve version
            String version = null;
            Matcher verMatcher = VERSION_TAG.matcher(block);
            if (verMatcher.find()) {
                version = resolveProperty(verMatcher.group(1).trim(), properties);
            }

            // If no version in the POM, try to find it from Maven Central metadata
            if (version == null || version.startsWith("${")) {
                version = fetchLatestVersion(groupId, artifactId);
            }

            if (version == null) continue;

            // Check for classifier
            String classifier = null;
            Matcher clsMatcher = CLASSIFIER_TAG.matcher(block);
            if (clsMatcher.find()) {
                classifier = resolveProperty(clsMatcher.group(1).trim(), properties);
            }

            deps.add(new DependencyInfo(groupId, artifactId, version, classifier));
        }

        return deps;
    }

    /**
     * Parses properties from a POM (typically the parent POM) for version resolution.
     */
    private Map<String, String> parseProperties(String pomContent) {
        Map<String, String> props = new HashMap<>();
        if (pomContent == null) return props;

        // Extract <properties> section
        int start = pomContent.indexOf("<properties>");
        int end = pomContent.indexOf("</properties>");
        if (start < 0 || end < 0) return props;

        String propsSection = pomContent.substring(start, end);
        Pattern propPattern = Pattern.compile("<([^/>]+)>([^<]+)</\\1>");
        Matcher m = propPattern.matcher(propsSection);
        while (m.find()) {
            props.put(m.group(1).trim(), m.group(2).trim());
        }
        return props;
    }

    /**
     * Resolves a property reference like ${com.hankcs.hanlp.version} to its value.
     */
    private String resolveProperty(String value, Map<String, String> properties) {
        if (value == null) return null;
        Matcher m = PROPERTY_REF.matcher(value);
        if (m.matches()) {
            String propName = m.group(1);
            return properties.getOrDefault(propName, value);
        }
        return value;
    }

    /**
     * Fetches the latest version of an artifact from Maven Central metadata.
     */
    private String fetchLatestVersion(String groupId, String artifactId) {
        try {
            String groupPath = groupId.replace('.', '/');
            String metadataUrl = MAVEN_CENTRAL + groupPath + "/" + artifactId + "/maven-metadata.xml";
            String metadata = fetchText(metadataUrl);
            if (metadata == null) return null;

            // Try <latest> first, then last <version>
            Pattern latestPattern = Pattern.compile("<latest>([^<]+)</latest>");
            Matcher m = latestPattern.matcher(metadata);
            if (m.find()) return m.group(1);

            // Fall back to last <version> entry
            Pattern versionPattern = Pattern.compile("<version>([^<]+)</version>");
            Matcher vm = versionPattern.matcher(metadata);
            String last = null;
            while (vm.find()) last = vm.group(1);
            return last;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns all JAR paths for a language (main JAR + resolved dependencies).
     * Reads a cached dependency manifest to avoid re-parsing the POM.
     */
    public List<Path> getAllJarPaths(String langCode) {
        List<Path> paths = new ArrayList<>();
        paths.add(getJarPath(langCode));

        // Find all JARs in the languages directory that were downloaded for this language
        // Use the deps manifest file
        Path manifestPath = languagesDir.resolve(langCode + "-deps.txt");
        if (Files.exists(manifestPath)) {
            try {
                List<String> filenames = Files.readAllLines(manifestPath);
                for (String filename : filenames) {
                    Path depPath = languagesDir.resolve(filename.trim());
                    if (Files.exists(depPath) && !depPath.equals(paths.get(0))) {
                        paths.add(depPath);
                    }
                }
            } catch (IOException e) {
                // Fall through to glob search
            }
        }

        // Fallback: find any extra JARs that aren't language-*.jar files
        if (paths.size() == 1) {
            try (var stream = Files.list(languagesDir)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                        .filter(p -> !p.getFileName().toString().startsWith("language-"))
                        .forEach(paths::add);
            } catch (IOException e) {
                // ignore
            }
        }

        return paths;
    }

    /**
     * Downloads a Maven artifact JAR.
     */
    private void downloadArtifact(String groupId, String artifactId, String version,
                                  String classifier, Path targetPath) throws IOException {
        String groupPath = groupId.replace('.', '/');
        String filename = classifier != null
                ? artifactId + "-" + version + "-" + classifier + ".jar"
                : artifactId + "-" + version + ".jar";
        String url = MAVEN_CENTRAL + groupPath + "/" + artifactId + "/" + version + "/" + filename;
        downloadUrl(url, targetPath);
    }

    /**
     * Downloads a file from a URL to the target path.
     */
    private void downloadUrl(String url, Path targetPath) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
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

    /**
     * Fetches text content from a URL, returning null on failure.
     */
    private String fetchText(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /** Returns the LanguageTool version used for downloads. */
    public static String getLanguageToolVersion() {
        return LANGUAGETOOL_VERSION;
    }

    /** Internal dependency descriptor. */
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
            String fname = filename();
            return "https://repo1.maven.org/maven2/" + groupPath + "/" + artifactId + "/" + version + "/" + fname;
        }
    }
}
