package net.hnt8.advancedban.utils;

/**
 * Removes legacy {@code &} / {@code §} color codes. MiniMessage rejects them and
 * otherwise aborts the punishment (the kick/ban never finishes).
 */
public final class LegacyFormat {

    private LegacyFormat() {
    }

    public static String strip(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String stripped = text.replaceAll("(?i)[&§]x(?:[&§][0-9a-f]){6}", "");
        stripped = stripped.replaceAll("(?i)[&§]#[0-9a-f]{6}", "");
        return stripped.replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }
}
