package net.hnt8.advancedban.utils;

import net.hnt8.advancedban.manager.PunishmentManager;

/**
 * Staff-facing name/IP colors shared by {@code /alts} and {@code /check}.
 * Red = active account ban. Gold (orange) = active issue such as a banned IP or a
 * banned account on the same address. Yellow = past ban or IP-ban only.
 */
public final class AccountTone {

    private AccountTone() {
    }

    public static String nameColor(String uuid, String firstIp, String lastIp) {
        if (uuid != null && PunishmentManager.get().isBanned(uuid)) {
            return "red";
        }
        if (hasActiveIssue(uuid, firstIp, lastIp)) {
            return "gold";
        }
        if (hasBanHistory(uuid, firstIp, lastIp)) {
            return "yellow";
        }
        return "white";
    }

    public static String colorName(String name, String uuid, String firstIp, String lastIp) {
        String color = nameColor(uuid, firstIp, lastIp);
        return "<" + color + ">" + name + "</" + color + ">";
    }

    public static String ipColor(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "gray";
        }
        if (PunishmentManager.get().getBan(ip) != null) {
            return "red";
        }
        if (!PunishmentManager.get().getPunishments(ip, PunishmentType.BAN, false).isEmpty()) {
            return "yellow";
        }
        return "white";
    }

    public static String colorIp(String shown, String rawIp) {
        String color = ipColor(rawIp);
        return "<" + color + ">" + shown + "</" + color + ">";
    }

    private static boolean hasActiveIssue(String uuid, String firstIp, String lastIp) {
        if (isBannedIp(firstIp) || isBannedIp(lastIp)) {
            return true;
        }
        if (uuid == null) {
            return false;
        }
        if (firstIp != null && !PunishmentManager.get().getBannedAccountsOnIp(firstIp, uuid).isEmpty()) {
            return true;
        }
        return lastIp != null && (firstIp == null || !lastIp.equalsIgnoreCase(firstIp))
                && !PunishmentManager.get().getBannedAccountsOnIp(lastIp, uuid).isEmpty();
    }

    private static boolean isBannedIp(String ip) {
        return ip != null && !ip.isEmpty() && PunishmentManager.get().getBan(ip) != null;
    }

    private static boolean hasBanHistory(String uuid, String firstIp, String lastIp) {
        if (uuid != null && !PunishmentManager.get().getPunishments(uuid, PunishmentType.BAN, false).isEmpty()) {
            return true;
        }
        if (firstIp != null && !PunishmentManager.get().getPunishments(firstIp, PunishmentType.BAN, false).isEmpty()) {
            return true;
        }
        return lastIp != null && (firstIp == null || !lastIp.equalsIgnoreCase(firstIp))
                && !PunishmentManager.get().getPunishments(lastIp, PunishmentType.BAN, false).isEmpty();
    }
}
