package net.hnt8.advancedban.utils;

/**
 * Distinguishes IP arguments from player names. Floodgate names start with {@code .}
 * and must not be treated as IPv4 just because they contain a dot. IPv6 contains {@code :},
 * which Java usernames cannot.
 */
public final class IpAddresses {

    private static final String IPV4_PATTERN =
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$";

    private IpAddresses() {
    }

    public static boolean isIpv6(String value) {
        return value != null && value.indexOf(':') >= 0;
    }

    public static boolean looksLikeIp(String value) {
        if (value == null || value.isEmpty() || value.indexOf(' ') >= 0) {
            return false;
        }
        if (isIpv6(value)) {
            return true;
        }
        return value.matches(IPV4_PATTERN);
    }

    /** Lowercase IPv6 so a typed address matches the form Java usually stores. */
    public static String canonical(String value) {
        if (value == null) {
            return null;
        }
        return isIpv6(value) ? value.toLowerCase() : value;
    }
}
