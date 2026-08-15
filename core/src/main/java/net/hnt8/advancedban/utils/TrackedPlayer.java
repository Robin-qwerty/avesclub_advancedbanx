package net.hnt8.advancedban.utils;

/**
 * Represents a tracked player record stored in the Players table.
 */
public class TrackedPlayer {

    private final String uuid;
    private final String name;
    private final String lastIp;
    private final long lastJoin;
    private final long lastLeave;
    private final long firstSeen;

    public TrackedPlayer(String uuid, String name, String lastIp, long lastJoin, long lastLeave, long firstSeen) {
        this.uuid = uuid;
        this.name = name;
        this.lastIp = lastIp;
        this.lastJoin = lastJoin;
        this.lastLeave = lastLeave;
        this.firstSeen = firstSeen;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getLastIp() {
        return lastIp;
    }

    public long getLastJoin() {
        return lastJoin;
    }

    public long getLastLeave() {
        return lastLeave;
    }

    public long getFirstSeen() {
        return firstSeen;
    }
}
