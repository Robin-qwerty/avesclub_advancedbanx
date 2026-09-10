package net.hnt8.advancedban.manager;

import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.utils.SQLQuery;
import net.hnt8.advancedban.utils.TrackedPlayer;
import java.util.HashSet;
import java.util.Set;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks player join/leave data and IP associations in the database.
 */
public class PlayerManager {

    private static PlayerManager instance;

    public static synchronized PlayerManager get() {
        return instance == null ? instance = new PlayerManager() : instance;
    }

    public void recordJoin(String name, String uuid, String ip) {
        if (uuid == null || name == null || !DatabaseManager.get().isAvailable()) {
            return;
        }

        long now = TimeManager.getTime();
        int updated = DatabaseManager.get().executeUpdate(
                SQLQuery.UPDATE_PLAYER_JOIN, name, ip, now, uuid);

        // 0 = no existing row, -1 = query failed (do not try INSERT)
        if (updated == 0) {
            // firstIp is only ever set here, on first insert - it must never change afterwards.
            DatabaseManager.get().executeStatement(
                    SQLQuery.INSERT_PLAYER, uuid, name, ip, ip, now, now);
        }
    }

    public void recordLeave(String name, String uuid) {
        if (uuid == null || !DatabaseManager.get().isAvailable()) {
            return;
        }

        DatabaseManager.get().executeStatement(
                SQLQuery.UPDATE_PLAYER_LEAVE, TimeManager.getTime(), uuid);
    }

    /**
     * Writes lastLeave for remaining tracked/online players before the database is closed.
     */
    public void flushOnlineLeaves() {
        if (!DatabaseManager.get().isAvailable()) {
            return;
        }

        Set<String> names = new HashSet<>(Universal.get().getIps().keySet());
        for (Object player : Universal.get().getMethods().getOnlinePlayers()) {
            if (player != null) {
                names.add(Universal.get().getMethods().getName(player).toLowerCase());
            }
        }

        for (String name : names) {
            recordLeave(name, UUIDManager.get().getUUID(name));
        }
    }

    /**
     * Finds every player whose firstIp or lastIp matches the given address.
     */
    public List<TrackedPlayer> findByIp(String ip) {
        List<TrackedPlayer> players = new ArrayList<>();
        ResultSet rs = DatabaseManager.get().executeResultStatement(SQLQuery.SELECT_PLAYERS_BY_IP, ip, ip);
        if (rs == null) {
            return players;
        }

        try {
            while (rs.next()) {
                players.add(fromResultSet(rs));
            }
            rs.close();
        } catch (SQLException ex) {
            Universal.get().getLogger().severe("An error has occurred looking up players by IP.");
            Universal.get().debugSqlException(ex);
        }
        return players;
    }

    /**
     * Looks up the tracked record for a single player by uuid, or null if none exists.
     */
    public TrackedPlayer getByUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        ResultSet rs = DatabaseManager.get().executeResultStatement(SQLQuery.SELECT_PLAYER_BY_UUID, uuid);
        if (rs == null) {
            return null;
        }

        try {
            TrackedPlayer player = rs.next() ? fromResultSet(rs) : null;
            rs.close();
            return player;
        } catch (SQLException ex) {
            Universal.get().getLogger().severe("An error has occurred looking up a player by uuid.");
            Universal.get().debugSqlException(ex);
            return null;
        }
    }

    /**
     * Returns every IP address (from either firstIp or lastIp) that is associated
     * with more than one distinct player, ordered by the number of accounts sharing it.
     */
    public List<String> findSharedIps() {
        List<String> ips = new ArrayList<>();
        ResultSet rs = DatabaseManager.get().executeResultStatement(SQLQuery.SELECT_SHARED_IPS);
        if (rs == null) {
            return ips;
        }

        try {
            while (rs.next()) {
                ips.add(rs.getString("ip"));
            }
            rs.close();
        } catch (SQLException ex) {
            Universal.get().getLogger().severe("An error has occurred looking up shared IPs.");
            Universal.get().debugSqlException(ex);
        }
        return ips;
    }

    private static TrackedPlayer fromResultSet(ResultSet rs) throws SQLException {
        return new TrackedPlayer(
                rs.getString("uuid"),
                rs.getString("name"),
                rs.getString("firstIp"),
                rs.getString("lastIp"),
                rs.getLong("lastJoin"),
                rs.getLong("lastLeave"),
                rs.getLong("firstSeen"));
    }
}
