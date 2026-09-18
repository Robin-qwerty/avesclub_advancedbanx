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

    private static final String IPV4_PATTERN =
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$";

    /**
     * Whether the given command argument looks like an IP address rather than a player name.
     * Floodgate Bedrock names start with {@code .} (e.g. {@code .CloseChunk7120}) and must
     * not be treated as IPs just because they contain a dot.
     */
    public static boolean looksLikeIp(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.indexOf(':') != -1) {
            // Java usernames cannot contain ':', so treat as IPv6 (or host:port style).
            return true;
        }
        return value.matches(IPV4_PATTERN);
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
