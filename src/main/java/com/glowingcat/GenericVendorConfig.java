/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/**
 * Manages a Generic vendor YAML configuration for custom LLM API endpoints.
 * The YAML is stored at ~/.purpleplatypus-generic.yml and defines how to
 * call a chat/prompt API and a models-listing API with configurable request
 * format, headers, and response parsing via JSONPath-like expressions.
 *
 * Supports two conversation modes:
 *   - single-shot: sends only the latest user prompt with a conversation GUID
 *   - multi-turn: sends the full message history array
 */
public class GenericVendorConfig {

    private static final String CONFIG_FILENAME = ".purpleplatypus-generic.yml";
    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), CONFIG_FILENAME);

    // Parsed config sections
    private Map<String, Object> promptConfig;
    private Map<String, Object> modelsConfig;

    // Conversation GUID — generated once per session/clear
    private String conversationGuid = UUID.randomUUID().toString();

    /** Default YAML template for new configurations. */
    public static final String DEFAULT_YAML = """
            # Generic LLM Vendor Configuration
            # Variables: ${AUTH_TOKEN}, ${MODEL}, ${PROMPT}, ${MESSAGES}, ${GUID}
            #
            # ConversationMode:
            #   single-shot - sends only the current prompt with a conversation GUID
            #                 for server-side history tracking. Uses ${PROMPT} and ${GUID}.
            #   multi-turn  - sends the full conversation history. Uses ${MESSAGES}
            #                 which expands to a JSON array of {role, content} objects.

            Prompt:
              URI: https://example.com/api/conversation
              Method: POST
              Headers:
                Content-Type: application/json
                Authorization: "Basic ${AUTH_TOKEN}"
              ConversationMode: single-shot
              Body: |
                {
                  "model": "${MODEL}",
                  "messages": [
                    {"role": "system", "content": "always respond using markdown in UTF-8"},
                    {"role": "user", "content": "${PROMPT}"}
                  ],
                  "temperature": 0,
                  "conversation_guid": "${GUID}",
                  "conversation_mode": ["non-rag"],
                  "stream": false
                }
              Response:
                ContentPath: "choices[0].message.content"

            # To use multi-turn mode instead, change ConversationMode and Body:
            #
            #  ConversationMode: multi-turn
            #  Body: |
            #    {
            #      "model": "${MODEL}",
            #      "messages": ${MESSAGES},
            #      "temperature": 0,
            #      "stream": false
            #    }

            Models:
              URI: https://example.com/api/models
              Method: GET
              Headers:
                Authorization: "Basic ${AUTH_TOKEN}"
              Response:
                ListPath: ""
                IdField: "model_id"
                DescriptionField: "short_description"
            """;

    public GenericVendorConfig() {
        load();
    }

    /** Reset conversation GUID (called on Clear). */
    public void resetGuid() {
        conversationGuid = UUID.randomUUID().toString();
    }

    /** Get the current conversation GUID. */
    public String getGuid() {
        return conversationGuid;
    }

    /** Load config from disk; if missing or invalid, use defaults. */
    @SuppressWarnings("unchecked")
    public void load() {
        String yamlContent = loadYamlString();
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(yamlContent);
            if (root != null) {
                promptConfig = (Map<String, Object>) root.get("Prompt");
                modelsConfig = (Map<String, Object>) root.get("Models");
            }
        } catch (Exception e) {
            // If parsing fails, leave configs null — calls will fail gracefully
            promptConfig = null;
            modelsConfig = null;
        }
    }

    /** Load the raw YAML string from disk; returns default if file doesn't exist. */
    public static String loadYamlString() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                return Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return DEFAULT_YAML;
            }
        }
        return DEFAULT_YAML;
    }

    /** Save the given YAML string to disk. */
    public static void saveYamlString(String yaml) {
        try {
            Files.writeString(CONFIG_PATH, yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Silently fail — non-critical
        }
    }

    /** Whether the configuration is valid (has at least a Prompt section). */
    public boolean isValid() {
        return promptConfig != null;
    }

    /** Get the conversation mode: "single-shot" or "multi-turn". */
    public String getConversationMode() {
        if (promptConfig == null) return "single-shot";
        Object mode = promptConfig.get("ConversationMode");
        if (mode instanceof String s) {
            return s.trim().toLowerCase();
        }
        return "single-shot";
    }

    /**
     * Call the prompt/chat endpoint.
     *
     * @param authToken  the API key / auth token from preferences
     * @param model      the selected model
     * @param prompt     the current user prompt text
     * @param messages   full conversation history (list of role/content maps)
     * @return the extracted response content
     */
    @SuppressWarnings("unchecked")
    public String callPrompt(String authToken, String model, String prompt,
                             List<Map<String, String>> messages) throws Exception {
        if (promptConfig == null) {
            throw new RuntimeException("Generic vendor not configured. Use Configure... in Preferences.");
        }

        String uri = substituteVars(getString(promptConfig, "URI"), authToken, model, prompt, messages);
        String method = getString(promptConfig, "Method");
        if (method == null) method = "POST";

        Map<String, String> headers = getHeaders(promptConfig, authToken, model, prompt, messages);
        String body = substituteVars(getString(promptConfig, "Body"), authToken, model, prompt, messages);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(120));

        for (Map.Entry<String, String> h : headers.entrySet()) {
            reqBuilder.header(h.getKey(), h.getValue());
        }

        if ("POST".equalsIgnoreCase(method)) {
            reqBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "", StandardCharsets.UTF_8));
        } else {
            reqBuilder.GET();
        }

        HttpResponse<String> resp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build()
                .send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " +
                    resp.body().substring(0, Math.min(300, resp.body().length())));
        }

        // Extract content using ContentPath
        Map<String, Object> responseConfig = (Map<String, Object>) promptConfig.get("Response");
        if (responseConfig == null) {
            throw new RuntimeException("No Response section in Generic vendor config");
        }
        String contentPath = (String) responseConfig.get("ContentPath");
        if (contentPath == null || contentPath.isBlank()) {
            // If no path specified, return the whole body
            return resp.body();
        }

        return evaluateJsonPath(resp.body(), contentPath);
    }

    /**
     * Fetch the list of available models from the configured endpoint.
     *
     * @param authToken the API key / auth token
     * @return list of model ID strings
     */
    @SuppressWarnings("unchecked")
    public List<String> fetchModels(String authToken) {
        List<String> result = new ArrayList<>();
        if (modelsConfig == null) return result;

        try {
            String uri = substituteVars(getString(modelsConfig, "URI"), authToken, "", "", null);
            String method = getString(modelsConfig, "Method");
            if (method == null) method = "GET";

            Map<String, String> headers = getHeaders(modelsConfig, authToken, "", "", null);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(15));

            for (Map.Entry<String, String> h : headers.entrySet()) {
                reqBuilder.header(h.getKey(), h.getValue());
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = substituteVars(getString(modelsConfig, "Body"), authToken, "", "", null);
                reqBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            } else {
                reqBuilder.GET();
            }

            HttpResponse<String> resp = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) return result;

            Map<String, Object> responseConfig = (Map<String, Object>) modelsConfig.get("Response");
            if (responseConfig == null) return result;

            String listPath = (String) responseConfig.get("ListPath");
            String idField = (String) responseConfig.get("IdField");
            if (idField == null) idField = "id";

            String body = resp.body().trim();

            // Navigate to the array using ListPath
            String arrayJson;
            if (listPath == null || listPath.isBlank()) {
                arrayJson = body;
            } else {
                arrayJson = evaluateJsonPath(body, listPath);
            }

            // Extract model IDs from the JSON array
            if (arrayJson != null && arrayJson.trim().startsWith("[")) {
                // Simple regex extraction of the id field from each object
                Pattern p = Pattern.compile("\"" + Pattern.quote(idField) + "\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(arrayJson);
                while (m.find()) {
                    result.add(m.group(1));
                }
            }
        } catch (Exception e) {
            // Return empty list on any failure
        }

        return result;
    }

    // --- Private helpers ---

    @SuppressWarnings("unchecked")
    private Map<String, String> getHeaders(Map<String, Object> config, String authToken,
                                           String model, String prompt,
                                           List<Map<String, String>> messages) {
        Map<String, String> result = new LinkedHashMap<>();
        Object headersObj = config.get("Headers");
        if (headersObj instanceof Map) {
            Map<String, Object> headersMap = (Map<String, Object>) headersObj;
            for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                String val = entry.getValue() != null ? entry.getValue().toString() : "";
                result.put(entry.getKey(), substituteVars(val, authToken, model, prompt, messages));
            }
        }
        return result;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * Substitute ${AUTH_TOKEN}, ${MODEL}, ${PROMPT}, ${GUID}, ${MESSAGES} in a template string.
     * ${MESSAGES} expands to a JSON array of message objects for multi-turn mode.
     */
    private String substituteVars(String template, String authToken, String model,
                                  String prompt, List<Map<String, String>> messages) {
        if (template == null) return null;
        String result = template;
        result = result.replace("${AUTH_TOKEN}", authToken != null ? authToken : "");
        result = result.replace("${MODEL}", model != null ? model : "");
        result = result.replace("${PROMPT}", jsonEscape(prompt != null ? prompt : ""));
        result = result.replace("${GUID}", conversationGuid);

        if (messages != null && result.contains("${MESSAGES}")) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < messages.size(); i++) {
                if (i > 0) sb.append(",");
                Map<String, String> msg = messages.get(i);
                sb.append("{\"role\":\"").append(jsonEscape(msg.get("role")))
                  .append("\",\"content\":\"").append(jsonEscape(msg.get("content"))).append("\"}");
            }
            sb.append("]");
            result = result.replace("${MESSAGES}", sb.toString());
        }

        return result;
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Evaluate a simple JSONPath expression against a JSON string.
     * Supports: field.field, field[index].field, etc.
     * Examples: "choices[0].message.content", "data[0].id", "result"
     */
    static String evaluateJsonPath(String json, String path) {
        String current = json.trim();

        // Split path into segments: "choices[0].message.content" -> ["choices", "[0]", "message", "content"]
        List<String> segments = new ArrayList<>();
        Matcher m = Pattern.compile("([^.\\[]+)|\\[(\\d+)]").matcher(path);
        while (m.find()) {
            if (m.group(1) != null) segments.add(m.group(1));
            else if (m.group(2) != null) segments.add("[" + m.group(2) + "]");
        }

        for (String seg : segments) {
            if (current == null) return null;
            current = current.trim();

            if (seg.startsWith("[") && seg.endsWith("]")) {
                // Array index access
                int index = Integer.parseInt(seg.substring(1, seg.length() - 1));
                current = getArrayElement(current, index);
            } else {
                // Object field access
                current = getObjectField(current, seg);
            }
        }

        if (current == null) return null;
        current = current.trim();

        // If the result is a JSON string, unwrap the quotes and unescape
        if (current.startsWith("\"")) {
            return unescapeJsonString(current);
        }
        return current;
    }

    /** Get the value of a field in a JSON object string. */
    private static String getObjectField(String json, String field) {
        if (!json.startsWith("{")) return null;

        String pattern = "\"" + field + "\"";
        int idx = -1;
        int searchFrom = 0;

        // Find the field key at the correct nesting level
        while (searchFrom < json.length()) {
            int candidate = json.indexOf(pattern, searchFrom);
            if (candidate < 0) return null;

            // Check that this is at the top level of this object
            int depth = 0;
            boolean inString = false;
            boolean valid = true;
            for (int i = 1; i < candidate; i++) { // start after opening {
                char c = json.charAt(i);
                if (inString) {
                    if (c == '\\') { i++; continue; }
                    if (c == '"') inString = false;
                } else {
                    if (c == '"') inString = true;
                    else if (c == '{' || c == '[') depth++;
                    else if (c == '}' || c == ']') depth--;
                }
            }
            if (depth == 0) {
                idx = candidate;
                break;
            }
            searchFrom = candidate + 1;
        }

        if (idx < 0) return null;

        // Find the colon after the key
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;

        // Find the start of the value
        int valStart = colonIdx + 1;
        while (valStart < json.length() && Character.isWhitespace(json.charAt(valStart))) valStart++;
        if (valStart >= json.length()) return null;

        return extractJsonValue(json, valStart);
    }

    /** Get an element from a JSON array string by index. */
    private static String getArrayElement(String json, int index) {
        if (!json.startsWith("[")) return null;

        int pos = 1; // after opening [
        int currentIndex = 0;

        while (pos < json.length()) {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
            if (pos >= json.length() || json.charAt(pos) == ']') return null;

            int valueStart = pos;
            int valueEnd = findValueEnd(json, pos);
            if (valueEnd < 0) return null;

            if (currentIndex == index) {
                return json.substring(valueStart, valueEnd).trim();
            }

            pos = valueEnd;
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
            if (pos < json.length() && json.charAt(pos) == ',') pos++;
            currentIndex++;
        }

        return null;
    }

    /** Extract a JSON value starting at the given position. Returns the value substring. */
    private static String extractJsonValue(String json, int start) {
        int end = findValueEnd(json, start);
        if (end < 0) return null;
        return json.substring(start, end).trim();
    }

    /** Find the end position of a JSON value starting at pos. */
    private static int findValueEnd(String json, int pos) {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        if (pos >= json.length()) return -1;

        char c = json.charAt(pos);

        if (c == '"') {
            // String
            int i = pos + 1;
            while (i < json.length()) {
                if (json.charAt(i) == '\\') { i += 2; continue; }
                if (json.charAt(i) == '"') return i + 1;
                i++;
            }
            return -1;
        } else if (c == '{' || c == '[') {
            // Object or Array — find matching close
            char open = c;
            char close = (c == '{') ? '}' : ']';
            int depth = 1;
            int i = pos + 1;
            boolean inStr = false;
            while (i < json.length() && depth > 0) {
                char ch = json.charAt(i);
                if (inStr) {
                    if (ch == '\\') { i++; }
                    else if (ch == '"') inStr = false;
                } else {
                    if (ch == '"') inStr = true;
                    else if (ch == open) depth++;
                    else if (ch == close) depth--;
                }
                i++;
            }
            return i;
        } else {
            // Number, boolean, null
            int i = pos;
            while (i < json.length()) {
                char ch = json.charAt(i);
                if (ch == ',' || ch == '}' || ch == ']' || Character.isWhitespace(ch)) break;
                i++;
            }
            return i;
        }
    }

    /** Unescape a JSON string value (remove surrounding quotes, process escape sequences). */
    private static String unescapeJsonString(String jsonStr) {
        if (jsonStr.length() < 2) return jsonStr;
        String inner = jsonStr.substring(1, jsonStr.length() - 1);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < inner.length()) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < inner.length()) {
                char next = inner.charAt(i + 1);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 5 < inner.length()) {
                            sb.append((char) Integer.parseInt(inner.substring(i + 2, i + 6), 16));
                            i += 4;
                        }
                    }
                    default -> { sb.append('\\'); sb.append(next); }
                }
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
