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
            DatabaseManager.get().executeStatement(
                    SQLQuery.INSERT_PLAYER, uuid, name, ip, now, now);
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

    public List<TrackedPlayer> findByIp(String ip) {
        List<TrackedPlayer> players = new ArrayList<>();
        ResultSet rs = DatabaseManager.get().executeResultStatement(SQLQuery.SELECT_PLAYERS_BY_IP, ip);
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

    private static TrackedPlayer fromResultSet(ResultSet rs) throws SQLException {
        return new TrackedPlayer(
                rs.getString("uuid"),
                rs.getString("name"),
                rs.getString("lastIp"),
                rs.getLong("lastJoin"),
                rs.getLong("lastLeave"),
                rs.getLong("firstSeen"));
    }
}
