package net.hnt8.advancedban.utils;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Adds keys that exist in the jar default YAML but are missing from the on-disk file.
 * Never overwrites values the admin already set. New top-level sections are appended
 * (with their comments from the default file) so existing comments stay intact.
 */
public final class ConfigMigrator {

    private ConfigMigrator() {
    }

    public static List<String> mergeMissingKeys(File userFile, InputStream defaultYaml, Logger logger) {
        if (userFile == null || defaultYaml == null) {
            return Collections.emptyList();
        }
        try {
            String defaultText = YamlColonQuoter.quoteUnquotedColonValues(readFully(defaultYaml));
            if (!userFile.exists()) {
                File parent = userFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Files.write(userFile.toPath(), defaultText.getBytes(StandardCharsets.UTF_8));
                if (logger != null) {
                    logger.info("Created " + userFile.getName() + " from plugin defaults.");
                }
                return Collections.singletonList("(created " + userFile.getName() + ")");
            }

            YamlColonQuoter.quoteUnquotedColonValues(userFile, logger);
            String userText = new String(Files.readAllBytes(userFile.toPath()), StandardCharsets.UTF_8);
            Yaml yaml = new Yaml();
            Map<String, Object> defaults = asMap(yaml.load(defaultText));
            Map<String, Object> user = asMap(yaml.load(userText));

            List<Missing> missing = new ArrayList<Missing>();
            collectMissing(defaults, user, "", missing);
            if (missing.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> added = new ArrayList<String>();
            StringBuilder out = new StringBuilder(userText);
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }

            List<Missing> nested = new ArrayList<Missing>();
            for (Missing item : missing) {
                if (item.parentPath.isEmpty()) {
                    String section = extractTopLevelSection(defaultText, item.key);
                    out.append('\n');
                    out.append(section);
                    if (!section.endsWith("\n")) {
                        out.append('\n');
                    }
                    added.add(item.path);
                } else {
                    nested.add(item);
                }
            }

            List<String> lines = toMutableLines(out.toString());
            for (Missing item : nested) {
                if (insertNested(lines, item)) {
                    added.add(item.path);
                }
            }

            Files.write(userFile.toPath(), joinLines(lines).getBytes(StandardCharsets.UTF_8));
            if (logger != null) {
                logger.info("Added missing " + userFile.getName() + " keys: " + added);
            }
            return added;
        } catch (Exception ex) {
            if (logger != null) {
                logger.warning("Failed to merge missing config keys in " + userFile.getName() + ": " + ex.getMessage());
            }
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object loaded) {
        if (loaded instanceof Map) {
            return (Map<String, Object>) loaded;
        }
        return new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    private static void collectMissing(Map<String, Object> defaults, Map<String, Object> user, String prefix, List<Missing> out) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (!containsKey(user, key)) {
                out.add(new Missing(path, prefix, key, entry.getValue()));
                continue;
            }
            Object defVal = entry.getValue();
            Object userVal = getIgnoreCase(user, key);
            if (defVal instanceof Map && userVal instanceof Map) {
                collectMissing((Map<String, Object>) defVal, (Map<String, Object>) userVal, path, out);
            }
        }
    }

    private static boolean containsKey(Map<String, Object> map, String key) {
        return getIgnoreCase(map, key) != null || map.containsKey(key);
    }

    private static Object getIgnoreCase(Map<String, Object> map, String key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    static String extractTopLevelSection(String yaml, String key) {
        String[] raw = yaml.split("\n", -1);
        int keyLine = -1;
        for (int i = 0; i < raw.length; i++) {
            if (isTopLevelKey(raw[i], key)) {
                keyLine = i;
                break;
            }
        }
        if (keyLine < 0) {
            return dumpFragment(key, Collections.emptyMap());
        }
        int start = keyLine;
        while (start > 0) {
            String prev = raw[start - 1];
            String trimmed = prev.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                start--;
            } else {
                break;
            }
        }
        int end = keyLine + 1;
        while (end < raw.length) {
            String line = raw[end];
            if (isTopLevelNonComment(line)) {
                break;
            }
            end++;
        }
        while (end > start && raw[end - 1].trim().isEmpty()) {
            end--;
        }
        StringBuilder section = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                section.append('\n');
            }
            section.append(raw[i]);
        }
        return section.toString();
    }

    private static boolean isTopLevelKey(String line, String key) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#") || indent(line) != 0) {
            return false;
        }
        return trimmed.startsWith(key + ":") || trimmed.startsWith(key + " :");
    }

    private static boolean isTopLevelNonComment(String line) {
        if (line.isEmpty()) {
            return false;
        }
        if (indent(line) != 0) {
            return false;
        }
        String trimmed = line.trim();
        return !trimmed.isEmpty() && !trimmed.startsWith("#");
    }

    private static boolean insertNested(List<String> lines, Missing item) {
        int parentIndex = findKeyIndex(lines, item.parentPath);
        if (parentIndex < 0) {
            return false;
        }
        int parentIndent = indent(lines.get(parentIndex));
        int childIndent = detectChildIndent(lines, parentIndex, parentIndent);
        int insertAt = findBlockEnd(lines, parentIndex, parentIndent);
        String dumped = dumpFragment(item.key, item.value);
        String[] dumpedLines = dumped.split("\n", -1);
        List<String> toInsert = new ArrayList<String>();
        String pad = repeat(' ', childIndent);
        for (String dumpedLine : dumpedLines) {
            if (dumpedLine.trim().isEmpty()) {
                continue;
            }
            toInsert.add(pad + dumpedLine);
        }
        if (toInsert.isEmpty()) {
            return false;
        }
        lines.addAll(insertAt, toInsert);
        return true;
    }

    static int findKeyIndex(List<String> lines, String dottedPath) {
        String[] parts = dottedPath.split("\\.");
        int from = 0;
        int expectedIndent = 0;
        int found = -1;
        for (String part : parts) {
            found = -1;
            for (int i = from; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int ind = indent(line);
                if (ind < expectedIndent) {
                    return -1;
                }
                if (ind == expectedIndent && keyName(line).equals(part)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                return -1;
            }
            from = found + 1;
            expectedIndent = indent(lines.get(found)) + 2;
        }
        return found;
    }

    private static int findBlockEnd(List<String> lines, int keyIndex, int keyIndent) {
        int i = keyIndex + 1;
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                i++;
                continue;
            }
            if (indent(line) <= keyIndent) {
                return i;
            }
            i++;
        }
        return lines.size();
    }

    private static int detectChildIndent(List<String> lines, int parentIndex, int parentIndent) {
        for (int i = parentIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int ind = indent(line);
            if (ind <= parentIndent) {
                break;
            }
            return ind;
        }
        return parentIndent + 2;
    }

    private static String keyName(String line) {
        String trimmed = line.trim();
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return "";
        }
        return trimmed.substring(0, colon).trim();
    }

    static int indent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static String dumpFragment(String key, Object value) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setExplicitStart(false);
        options.setExplicitEnd(false);
        // Channel prefixes like avesban:link: must be quoted or Bukkit YAML fails to load.
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.SINGLE_QUOTED);
        Map<String, Object> fragment = new LinkedHashMap<String, Object>();
        fragment.put(key, value);
        String dumped = new Yaml(options).dump(fragment);
        if (dumped.startsWith("---")) {
            dumped = dumped.substring(dumped.indexOf('\n') + 1);
        }
        return dumped.trim();
    }

    private static List<String> toMutableLines(String text) {
        String[] raw = text.split("\n", -1);
        List<String> lines = new ArrayList<String>(raw.length);
        Collections.addAll(lines, raw);
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static String joinLines(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines.get(i));
        }
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        return out.toString();
    }

    private static String readFully(InputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static String repeat(char c, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            out.append(c);
        }
        return out.toString();
    }

    private static final class Missing {
        private final String path;
        private final String parentPath;
        private final String key;
        private final Object value;

        private Missing(String path, String parentPath, String key, Object value) {
            this.path = path;
            this.parentPath = parentPath;
            this.key = key;
            this.value = value;
        }
    }
}
