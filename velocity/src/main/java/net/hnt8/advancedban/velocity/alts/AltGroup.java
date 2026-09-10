package net.hnt8.advancedban.velocity.alts;

import net.hnt8.advancedban.utils.TrackedPlayer;

import java.util.List;

/**
 * A single IP address and every tracked account that has used it, together
 * with a heuristic alt-likelihood score for that address.
 */
public class AltGroup {

    private final String ip;
    private final List<TrackedPlayer> members;
    private final int score;
    private final String band;
    private final List<String> reasons;

    public AltGroup(String ip, List<TrackedPlayer> members, int score, String band, List<String> reasons) {
        this.ip = ip;
        this.members = members;
        this.score = score;
        this.band = band;
        this.reasons = reasons;
    }

    public String getIp() {
        return ip;
    }

    public List<TrackedPlayer> getMembers() {
        return members;
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
