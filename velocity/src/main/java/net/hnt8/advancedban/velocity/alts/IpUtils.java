package net.hnt8.advancedban.velocity.alts;

/**
 * Small helper for telling IPv4/IPv6 addresses apart and masking them for
 * players without permission to see a full address.
 */
public final class IpUtils {

    private IpUtils() {
    }

    public static boolean isIpv6(String ip) {
        return ip != null && ip.contains(":");
    }

    /**
     * Whether the given command argument looks like an IP address rather than a player
     * name. Minecraft usernames can only contain letters, digits and underscores, so
     * anything with a "." or ":" in it can't be one.
     */
    public static boolean looksLikeIp(String value) {
        return value != null && (value.indexOf('.') != -1 || value.indexOf(':') != -1);
    }

    /**
     * Masks an address down to its first segment (first octet for IPv4, first
     * hextet for IPv6) so the rest is never revealed to players without the
     * avesban.alt.ip permission.
     */
    public static String mask(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "unknown";
        }

        if (isIpv6(ip)) {
            String first = ip.substring(0, ip.indexOf(':'));
            if (first.isEmpty()) {
                // Address started with "::" - nothing safe to show at all.
                return "*:*:*:*:*:*:*:*";
            }
            return first + ":*:*:*:*:*:*:*";
        }

        int dot = ip.indexOf('.');
        String first = dot == -1 ? ip : ip.substring(0, dot);
        return first + ".*.*.*";
    }

    public static String display(String ip, boolean fullAccess) {
        return fullAccess ? (ip == null ? "unknown" : ip) : mask(ip);
    }
}
