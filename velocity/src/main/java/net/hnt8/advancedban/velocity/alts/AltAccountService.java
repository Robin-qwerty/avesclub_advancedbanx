package net.hnt8.advancedban.velocity.alts;

import net.hnt8.advancedban.MethodInterface;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.manager.PlayerManager;
import net.hnt8.advancedban.manager.UUIDManager;
import net.hnt8.advancedban.utils.TrackedPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds alt-account groups (IP addresses shared by more than one tracked
 * player) on top of {@link PlayerManager}, scored via {@link AltScorer}.
 */
public final class AltAccountService {

    private AltAccountService() {
    }

    /**
     * Every shared IP address, sorted by alt-likelihood score (highest first).
     */
    public static List<AltGroup> listGroups() {
        Set<String> onlineUuids = onlineUuids();
        List<AltGroup> groups = new ArrayList<>();

        for (String ip : PlayerManager.get().findSharedIps()) {
            List<TrackedPlayer> members = PlayerManager.get().findByIp(ip);
            // The membership could have changed between the two queries (e.g. a player
            // switched IP in between) - only keep it if it's still actually shared.
            if (members.size() < 2) {
                continue;
            }
            AltScorer.ScoreResult result = AltScorer.score(ip, members, onlineUuids);
            groups.add(new AltGroup(ip, members, result.getScore(), result.getBand(), result.getReasons()));
        }

        groups.sort(Comparator.comparingInt(AltGroup::getScore).reversed());
        return groups;
    }

    /**
     * Finds the alt group for a specific player by name, based on their most
     * recent known IP (falling back to their first IP), or null if they have
     * no record or don't share an address with anyone else.
     */
    public static AltGroup findGroupForPlayer(String name) {
        String uuid = UUIDManager.get().getUUID(name.toLowerCase());
        if (uuid == null) {
            return null;
        }

        TrackedPlayer record = PlayerManager.get().getByUuid(uuid);
        if (record == null) {
            return null;
        }

        String ip = record.getLastIp() != null ? record.getLastIp() : record.getFirstIp();
        if (ip == null) {
            return null;
        }

        List<TrackedPlayer> members = PlayerManager.get().findByIp(ip);
        if (members.size() < 2) {
            return null;
        }

        Set<String> onlineUuids = onlineUuids();
        AltScorer.ScoreResult result = AltScorer.score(ip, members, onlineUuids);
        return new AltGroup(ip, members, result.getScore(), result.getBand(), result.getReasons());
    }

    /**
     * Looks up every tracked account that has used a specific IP address directly, or
     * null if no tracked account has ever used it. Unlike {@link #listGroups()} this
     * also returns a group of just one account, since staff looking up a specific
     * address (e.g. from a report or server log) want to know who's behind it even if
     * nobody else shares it.
     */
    public static AltGroup findGroupForIp(String ip) {
        List<TrackedPlayer> members = PlayerManager.get().findByIp(ip);
        if (members.isEmpty()) {
            return null;
        }

        Set<String> onlineUuids = onlineUuids();
        AltScorer.ScoreResult result = AltScorer.score(ip, members, onlineUuids);
        return new AltGroup(ip, members, result.getScore(), result.getBand(), result.getReasons());
    }

    private static Set<String> onlineUuids() {
        MethodInterface mi = Universal.get().getMethods();
        Set<String> uuids = new HashSet<>();
        for (Object player : mi.getOnlinePlayers()) {
            uuids.add(mi.getInternUUID(player));
        }
        return uuids;
    }
}
