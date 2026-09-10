package net.hnt8.advancedban.velocity.alts;

import net.hnt8.advancedban.manager.PunishmentManager;
import net.hnt8.advancedban.utils.Punishment;
import net.hnt8.advancedban.utils.TrackedPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Estimates how likely a group of accounts sharing one IP address are alts of
 * each other, as opposed to separate people (e.g. the same household).
 * <p>
 * This is a heuristic, not a fact - it exists to help staff prioritize which
 * shared addresses are worth a closer look, not to auto-convict anyone.
 */
public final class AltScorer {

    private AltScorer() {
    }

    public static ScoreResult score(String ip, List<TrackedPlayer> members, Set<String> onlineUuids) {
        List<String> reasons = new ArrayList<>();
        int score;

        boolean ipv6 = IpUtils.isIpv6(ip);
        if (ipv6) {
            score = 55;
            reasons.add("IPv6 address - devices are rarely behind a shared IPv6 the way they are with IPv4 NAT");
        } else {
            score = 20;
            reasons.add("IPv4 address - an entire household commonly shares one public IPv4 address");
        }

        int extraMembers = Math.max(0, members.size() - 2);
        if (extraMembers > 0) {
            score += Math.min(extraMembers * 8, 24);
            reasons.add(members.size() + " accounts have used this address");
        }

        if (anyNamesLookSimilar(members)) {
            score += 15;
            reasons.add("some of the usernames look related to each other");
        }

        long closestFirstSeenGap = closestFirstSeenGap(members);
        if (closestFirstSeenGap != Long.MAX_VALUE) {
            if (closestFirstSeenGap < 60L * 60 * 1000) {
                score += 20;
                reasons.add("two of these accounts first joined the server within an hour of each other");
            } else if (closestFirstSeenGap < 24L * 60 * 60 * 1000) {
                score += 10;
                reasons.add("two of these accounts first joined the server on the same day");
            } else if (closestFirstSeenGap < 7L * 24 * 60 * 60 * 1000) {
                score += 3;
            }
        }

        boolean anyBanned = false;
        boolean anyClean = false;
        for (TrackedPlayer member : members) {
            if (PunishmentManager.get().isBanned(member.getUuid())) {
                anyBanned = true;
            } else {
                anyClean = true;
            }
        }
        if (anyBanned && anyClean) {
            score += 30;
            reasons.add("one account on this address is banned while another isn't - possible ban evasion");
        }

        if (anyMemberEvadingIpBan(members)) {
            score += 25;
            reasons.add("one of these accounts is now using a different IP than an IP that's banned - possible IP-ban evasion");
        }

        int onlineCount = 0;
        for (TrackedPlayer member : members) {
            if (onlineUuids.contains(member.getUuid())) {
                onlineCount++;
            }
        }
        if (onlineCount >= 2) {
            score -= 25;
            reasons.add("multiple of these accounts are online at the same time right now, which is more consistent with separate people");
        }

        score = Math.max(0, Math.min(100, score));
        return new ScoreResult(score, band(score), reasons);
    }

    // True if any member's tracked firstIp is banned while their current lastIp differs and isn't
    // (the classic "got IP banned, switched network/VPN, kept playing" pattern).
    private static boolean anyMemberEvadingIpBan(List<TrackedPlayer> members) {
        for (TrackedPlayer member : members) {
            String firstIp = member.getFirstIp();
            String lastIp = member.getLastIp();
            if (firstIp == null || lastIp == null || firstIp.equalsIgnoreCase(lastIp)) {
                continue;
            }
            Punishment firstIpBan = PunishmentManager.get().getBan(firstIp);
            if (firstIpBan == null) {
                continue;
            }
            Punishment lastIpBan = PunishmentManager.get().getBan(lastIp);
            if (lastIpBan == null) {
                return true;
            }
        }
        return false;
    }

    private static String band(int score) {
        if (score >= 75) return "Very likely alt";
        if (score >= 50) return "Likely alt";
        if (score >= 25) return "Possibly related (household?)";
        return "Likely unrelated";
    }

    private static long closestFirstSeenGap(List<TrackedPlayer> members) {
        long closest = Long.MAX_VALUE;
        for (int i = 0; i < members.size(); i++) {
            long a = members.get(i).getFirstSeen();
            if (a <= 0) continue;
            for (int j = i + 1; j < members.size(); j++) {
                long b = members.get(j).getFirstSeen();
                if (b <= 0) continue;
                closest = Math.min(closest, Math.abs(a - b));
            }
        }
        return closest;
    }

    private static boolean anyNamesLookSimilar(List<TrackedPlayer> members) {
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                if (namesLookSimilar(members.get(i).getName(), members.get(j).getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean namesLookSimilar(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        if (na.equals(nb)) {
            return true;
        }
        if (na.length() >= 3 && nb.length() >= 3 && (na.contains(nb) || nb.contains(na))) {
            return true;
        }
        return Math.min(na.length(), nb.length()) >= 4 && levenshtein(na, nb) <= 2;
    }

    private static String normalize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    public static final class ScoreResult {
        private final int score;
        private final String band;
        private final List<String> reasons;

        ScoreResult(int score, String band, List<String> reasons) {
            this.score = score;
            this.band = band;
            this.reasons = reasons;
        }

        public int getScore() {
            return score;
        }

        public String getBand() {
            return band;
        }

        public List<String> getReasons() {
            return reasons;
        }
    }
}
