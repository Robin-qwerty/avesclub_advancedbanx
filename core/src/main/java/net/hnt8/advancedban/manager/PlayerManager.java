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

    /**
     * Records a player's join (name, lastIp, lastJoin - and firstIp/firstSeen on their very
     * first ever join). Retries through a transient database blip the same way
     * {@link PunishmentManager#load} does, instead of silently dropping the update - a banned
     * player reconnecting from a new IP during exactly that kind of blip would otherwise still
     * get denied (bans are cached/retried already) while their new IP quietly never gets saved.
     */
    public void recordJoin(String name, String uuid, String ip) {
        if (uuid == null || name == null) {
            return;
        }

        int updated = recordJoinOnce(name, uuid, ip);
        if (updated == -1 && DatabaseManager.get().tryReconnect()) {
            Universal.get().getLogger().warning("Retrying player-join record for " + name + " after reconnect...");
            updated = recordJoinOnce(name, uuid, ip);
        }
        for (int attempt = 1; attempt < 3 && updated == -1; attempt++) {
            if (!DatabaseManager.get().isAvailable()) {
                break;
            }
            try {
                Thread.sleep(150L * attempt);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            Universal.get().getLogger().warning("Retrying player-join record for " + name + " (attempt " + (attempt + 1) + "/3)");
            updated = recordJoinOnce(name, uuid, ip);
        }
    }

    private int recordJoinOnce(String name, String uuid, String ip) {
        if (!DatabaseManager.get().isAvailable()) {
            return -1;
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
        return updated;
    }

    public void recordLeave(String name, String uuid) {
        if (uuid == null) {
            return;
        }

        boolean ok = recordLeaveOnce(uuid);
        if (!ok && DatabaseManager.get().tryReconnect()) {
            ok = recordLeaveOnce(uuid);
        }
        for (int attempt = 1; attempt < 3 && !ok; attempt++) {
            if (!DatabaseManager.get().isAvailable()) {
                break;
            }
            try {
                Thread.sleep(150L * attempt);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            ok = recordLeaveOnce(uuid);
        }
    }

    private boolean recordLeaveOnce(String uuid) {
        if (!DatabaseManager.get().isAvailable()) {
            return false;
        }
        return DatabaseManager.get().executeUpdate(SQLQuery.UPDATE_PLAYER_LEAVE, TimeManager.getTime(), uuid) != -1;
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
