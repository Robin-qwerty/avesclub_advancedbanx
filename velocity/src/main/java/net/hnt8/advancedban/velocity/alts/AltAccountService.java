package net.hnt8.advancedban.velocity.alts;

import net.hnt8.advancedban.MethodInterface;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.manager.PlayerManager;
import net.hnt8.advancedban.manager.PunishmentManager;
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
     * no tracked record. Returns a group even when only one account used that
     * IP (same as {@link #findGroupForIp}), so staff see the same result whether
     * they look up by name or by address.
     */
    public static AltGroup findGroupForPlayer(String name) {
        TrackedPlayer record = resolveTrackedPlayer(name);
        if (record == null) {
            return null;
        }

        String ip = record.getLastIp() != null ? record.getLastIp() : record.getFirstIp();
        if (ip == null) {
            return null;
        }

        List<TrackedPlayer> members = PlayerManager.get().findByIp(ip);
        if (members.isEmpty()) {
            return null;
        }

        Set<String> onlineUuids = onlineUuids();
        AltScorer.ScoreResult result = AltScorer.score(ip, members, onlineUuids);
        return new AltGroup(ip, members, result.getScore(), result.getBand(), result.getReasons());
    }

    /**
     * Resolves a tracked player by UUID cache first, then by case-insensitive DB name.
     * Also tries with/without the Floodgate {@code .} prefix for Bedrock names.
     */
    private static TrackedPlayer resolveTrackedPlayer(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        for (String candidate : nameLookupVariants(name)) {
            String uuid = UUIDManager.get().getInMemoryUUID(candidate);
            if (uuid == null && !candidate.startsWith(".")) {
                // Avoid hitting Mojang for Floodgate names (they always start with '.').
                uuid = UUIDManager.get().getUUID(candidate);
            }
            if (uuid != null) {
                TrackedPlayer byUuid = PlayerManager.get().getByUuid(uuid);
                if (byUuid != null) {
                    return byUuid;
                }
            }

            TrackedPlayer byName = PlayerManager.get().getByName(candidate);
            if (byName != null) {
                return byName;
            }
        }
        return null;
    }

    private static List<String> nameLookupVariants(String name) {
        List<String> variants = new ArrayList<>();
        variants.add(name);
        if (name.startsWith(".") && name.length() > 1) {
            variants.add(name.substring(1));
        } else if (!name.startsWith(".")) {
            variants.add("." + name);
        }
        return variants;
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

    /**
     * Finds every account that's currently banned and shares an IP with the given player,
     * checking both their tracked firstIp and lastIp independently (a ban tied to one of the
     * two wouldn't be found by {@link #findGroupForPlayer}, which only follows one address).
     * Used for the join-time alt alert - returns an empty list if nothing is linked.
     */
    public static List<TrackedPlayer> findBannedLinkedAccounts(String uuid, TrackedPlayer record) {
        List<TrackedPlayer> result = new ArrayList<>();
        if (record == null) {
            return result;
        }

        String lastIp = record.getLastIp();
        String firstIp = record.getFirstIp();
        if (lastIp != null) {
            result.addAll(PunishmentManager.get().getBannedAccountsOnIp(lastIp, uuid));
        }
        if (firstIp != null && !firstIp.equalsIgnoreCase(lastIp)) {
            for (TrackedPlayer candidate : PunishmentManager.get().getBannedAccountsOnIp(firstIp, uuid)) {
                if (result.stream().noneMatch(existing -> existing.getUuid().equalsIgnoreCase(candidate.getUuid()))) {
                    result.add(candidate);
                }
            }
        }
        return result;
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
