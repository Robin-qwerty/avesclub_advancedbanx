package net.hnt8.advancedban.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * SnakeYAML rejects unquoted scalars that contain {@code :}, especially a trailing colon
 * ({@code LinkPrefix: avesban:link:}). Quotes those values in place so existing config files boot.
 */
public final class YamlColonQuoter {

    private YamlColonQuoter() {
    }

    /**
     * Quotes unquoted {@code :} scalars in a YAML document string (in memory).
     */
    public static String quoteUnquotedColonValues(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        boolean endsWithNewline = text.endsWith("\n");
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            // split("\n", -1) keeps a trailing empty line when the text ends with \n
            if (i == lines.length - 1 && lines[i].isEmpty() && endsWithNewline) {
                break;
            }
            out.append(quoteLine(lines[i]));
        }
        if (endsWithNewline) {
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * @return true if the file was rewritten
     */
    public static boolean quoteUnquotedColonValues(File file, Logger logger) {
        if (file == null || !file.exists()) {
            return false;
        }
        try {
            List<String> original = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> rewritten = new ArrayList<String>(original.size());
            boolean changed = false;
            for (String line : original) {
                String fixed = quoteLine(line);
                if (!fixed.equals(line)) {
                    changed = true;
                }
                rewritten.add(fixed);
            }
            if (!changed) {
                return false;
            }
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < rewritten.size(); i++) {
                if (i > 0) {
                    out.append('\n');
                }
                out.append(rewritten.get(i));
            }
            if (!rewritten.isEmpty()) {
                out.append('\n');
            }
            Files.write(file.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
            if (logger != null) {
                logger.info("Quoted YAML ':' values in " + file.getName()
                        + " (e.g. LinkPrefix: 'avesban:link:') so the file can be loaded.");
            }
            return true;
        } catch (IOException ex) {
            if (logger != null) {
                logger.warning("Could not quote YAML colon values in " + file.getName() + ": " + ex.getMessage());
            }
            return false;
        }
    }

    static String quoteLine(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return line;
        }
        int commentAt = findUnquotedHash(line);
        String body = commentAt >= 0 ? line.substring(0, commentAt) : line;
        String comment = commentAt >= 0 ? line.substring(commentAt) : "";
        int colon = indexOfKeyColon(body);
        if (colon < 0) {
            return line;
        }
        String value = body.substring(colon + 1).trim();
        if (value.isEmpty()) {
            return line;
        }
        char first = value.charAt(0);
        if (first == '\'' || first == '"' || first == '{' || first == '[' || first == '|' || first == '>') {
            return line;
        }
        if (value.indexOf(':') < 0) {
            return line;
        }
        String prefix = rtrim(body.substring(0, colon + 1));
        String quoted = prefix + " '" + value.replace("'", "''") + "'";
        if (comment.isEmpty()) {
            return quoted;
        }
        return quoted + " " + comment.trim();
    }

    private static int indexOfKeyColon(String body) {
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '#') {
                return -1;
            }
            if (c == ':') {
                return i;
            }
        }
        return -1;
    }

    private static int findUnquotedHash(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble) {
                return i;
            }
        }
        return -1;
    }

    private static String rtrim(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }
}
