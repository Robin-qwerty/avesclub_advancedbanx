package net.hnt8.advancedban.velocity.alts;

/**
 * Shared MiniMessage rendering bits for the alt-account feature, used by both the
 * {@code /alts} command and the join-time alt alert.
 */
public final class AltDisplay {

    private AltDisplay() {
    }

    public static String scoreSpan(int score, String label) {
        String tag = scoreTagName(score);
        return "<" + tag + ">[" + label + "]</" + tag + ">";
    }

    public static String scoreTagName(int score) {
        if (score >= 75) return "red";
        if (score >= 50) return "gold";
        if (score >= 25) return "yellow";
        return "green";
    }

    public static String banTag(boolean banned) {
        return banned ? " <red>[BANNED]</red>" : "";
    }
}
