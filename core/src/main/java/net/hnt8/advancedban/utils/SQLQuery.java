package net.hnt8.advancedban.utils;

import net.hnt8.advancedban.manager.DatabaseManager;

/**
 * Created by Leo on 29.07.2017.
 */
public enum SQLQuery {
    CREATE_TABLE_PUNISHMENT(
            "CREATE TABLE IF NOT EXISTS `Punishments` ("+
            "`id` int NOT NULL AUTO_INCREMENT," +
            "`name` VARCHAR(16) NULL DEFAULT NULL," +
            "`uuid` VARCHAR(35) NULL DEFAULT NULL," +
            "`reason` VARCHAR(255) NULL DEFAULT NULL," +
            "`operator` VARCHAR(16) NULL DEFAULT NULL," +
            "`punishmentType` VARCHAR(16) NULL DEFAULT NULL," +
            "`start` BIGINT DEFAULT NULL," +
            "`end` BIGINT DEFAULT NULL," +
            "`calculation` VARCHAR(50) NULL DEFAULT NULL," +
            "`server` VARCHAR(64) NULL DEFAULT NULL," +
            "`targetServer` VARCHAR(64) NULL DEFAULT NULL," +
            "PRIMARY KEY (`id`))",

            "CREATE TABLE IF NOT EXISTS Punishments (" +
            "id INTEGER IDENTITY PRIMARY KEY," +
            "name VARCHAR(16)," +
            "uuid VARCHAR(35)," +
            "reason VARCHAR(255)," +
            "operator VARCHAR(16)," +
            "punishmentType VARCHAR(16)," +
            "start BIGINT," +
            "end BIGINT," +
            "calculation VARCHAR(50)," +
            "server VARCHAR(64)," +
            "targetServer VARCHAR(64))"
    ),
    CREATE_TABLE_PUNISHMENT_HISTORY(
            "CREATE TABLE IF NOT EXISTS `PunishmentHistory` (" +
            "`id` int NOT NULL AUTO_INCREMENT," +
            "`name` VARCHAR(16) NULL DEFAULT NULL," +
            "`uuid` VARCHAR(35) NULL DEFAULT NULL," +
            "`reason` VARCHAR(255) NULL DEFAULT NULL," +
            "`operator` VARCHAR(16) NULL DEFAULT NULL," +
            "`punishmentType` VARCHAR(16) NULL DEFAULT NULL," +
            "`start` BIGINT DEFAULT NULL," +
            "`end` BIGINT DEFAULT NULL," +
            "`calculation` VARCHAR(50) NULL DEFAULT NULL," +
            "`server` VARCHAR(64) NULL DEFAULT NULL," +
            "`targetServer` VARCHAR(64) NULL DEFAULT NULL," +
            "PRIMARY KEY (`id`))",

            "CREATE TABLE IF NOT EXISTS PunishmentHistory (" +
            "id INTEGER IDENTITY PRIMARY KEY," +
            "name VARCHAR(16)," +
            "uuid VARCHAR(35)," +
            "reason VARCHAR(255)," +
            "operator VARCHAR(16)," +
            "punishmentType VARCHAR(16)," +
            "start BIGINT," +
            "end BIGINT," +
            "calculation VARCHAR(50)," +
            "server VARCHAR(64)," +
            "targetServer VARCHAR(64))"
    ),
    INSERT_PUNISHMENT(
            "INSERT INTO `Punishments` " +
            "(`name`, `uuid`, `reason`, `operator`, `punishmentType`, `start`, `end`, `calculation`, `server`, `targetServer`) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",

            "INSERT INTO Punishments " +
            "(name, uuid, reason, operator, punishmentType, start, end, calculation, server, targetServer) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    ),
    INSERT_PUNISHMENT_HISTORY(
            "INSERT INTO `PunishmentHistory` " +
            "(`name`, `uuid`, `reason`, `operator`, `punishmentType`, `start`, `end`, `calculation`, `server`, `targetServer`) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",

            "INSERT INTO PunishmentHistory " +
            "(name, uuid, reason, operator, punishmentType, start, end, calculation, server, targetServer) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    ),
    SELECT_EXACT_PUNISHMENT(
            "SELECT * FROM `Punishments` WHERE `uuid` = ? AND `start` = ? AND `punishmentType` = ?",
            "SELECT * FROM Punishments WHERE uuid = ? AND start = ? AND punishmentType = ?"
    ),
    DELETE_PUNISHMENT(
            "DELETE FROM `Punishments` WHERE `id` = ?",
            "DELETE FROM Punishments WHERE id = ?"
    ),
    DELETE_OLD_PUNISHMENTS(
            "DELETE FROM `Punishments` WHERE `end` <= ? AND `end` != -1",
            "DELETE FROM Punishments WHERE end <= ? AND end != -1"
    ),
    SELECT_USER_PUNISHMENTS(
            "SELECT * FROM `Punishments` WHERE `uuid` = ?",
            "SELECT * FROM Punishments WHERE uuid = ?"
    ),
    SELECT_USER_PUNISHMENTS_HISTORY(
            "SELECT * FROM `PunishmentHistory` WHERE `uuid` = ?",
            "SELECT * FROM PunishmentHistory WHERE uuid = ?"
    ),
    SELECT_USER_PUNISHMENTS_WITH_IP(
            "SELECT * FROM `Punishments` WHERE `uuid` = ? OR `uuid` = ?",
            "SELECT * FROM Punishments WHERE uuid = ? OR uuid = ?"
    ),
    SELECT_USER_PUNISHMENTS_HISTORY_WITH_IP(
            "SELECT * FROM `PunishmentHistory` WHERE `uuid` = ? OR `uuid` = ?",
            "SELECT * FROM PunishmentHistory WHERE uuid = ? OR uuid = ?"
    ),
    SELECT_USER_PUNISHMENTS_HISTORY_BY_CALCULATION(
            "SELECT * FROM `PunishmentHistory` WHERE `uuid` = ? AND `calculation` = ?",
            "SELECT * FROM PunishmentHistory WHERE uuid = ? AND calculation = ?"
    ),
    UPDATE_PUNISHMENT_REASON(
            "UPDATE `Punishments` SET `reason` = ? WHERE `id` = ?",
            "UPDATE Punishments SET reason = ? WHERE id = ?"
    ),
    SELECT_PUNISHMENT_BY_ID(
            "SELECT * FROM `Punishments` WHERE `id` = ?",
            "SELECT * FROM Punishments WHERE id = ?"
    ),
    SELECT_ALL_PUNISHMENTS(
            "SELECT * FROM `Punishments`",
            "SELECT * FROM Punishments"
    ),
    SELECT_ALL_PUNISHMENTS_HISTORY(
            "SELECT * FROM `PunishmentHistory`",
            "SELECT * FROM PunishmentHistory"
    ),
    SELECT_ALL_PUNISHMENTS_LIMIT(
            "SELECT * FROM `Punishments` ORDER BY `start` DESC LIMIT ?",
            "SELECT * FROM Punishments ORDER BY start DESC LIMIT ?"
    ),
    SELECT_ALL_PUNISHMENTS_HISTORY_LIMIT(
            "SELECT * FROM `PunishmentHistory` ORDER BY `start` DESC LIMIT ?",
            "SELECT * FROM PunishmentHistory ORDER BY start DESC LIMIT ?"
    ),
    CREATE_TABLE_PLAYERS(
            "CREATE TABLE IF NOT EXISTS `Players` (" +
            "`uuid` VARCHAR(35) NOT NULL," +
            "`name` VARCHAR(16) NULL DEFAULT NULL," +
            "`lastIp` VARCHAR(45) NULL DEFAULT NULL," +
            "`lastJoin` BIGINT NULL DEFAULT NULL," +
            "`lastLeave` BIGINT NULL DEFAULT NULL," +
            "`firstSeen` BIGINT NULL DEFAULT NULL," +
            "PRIMARY KEY (`uuid`))",

            "CREATE TABLE IF NOT EXISTS Players (" +
            "uuid VARCHAR(35) NOT NULL PRIMARY KEY," +
            "name VARCHAR(16)," +
            "lastIp VARCHAR(45)," +
            "lastJoin BIGINT," +
            "lastLeave BIGINT," +
            "firstSeen BIGINT)"
    ),
    INSERT_PLAYER(
            "INSERT INTO `Players` (`uuid`, `name`, `lastIp`, `lastJoin`, `firstSeen`) VALUES (?, ?, ?, ?, ?)",
            "INSERT INTO Players (uuid, name, lastIp, lastJoin, firstSeen) VALUES (?, ?, ?, ?, ?)"
    ),
    UPDATE_PLAYER_JOIN(
            "UPDATE `Players` SET `name` = ?, `lastIp` = ?, `lastJoin` = ? WHERE `uuid` = ?",
            "UPDATE Players SET name = ?, lastIp = ?, lastJoin = ? WHERE uuid = ?"
    ),
    UPDATE_PLAYER_LEAVE(
            "UPDATE `Players` SET `lastLeave` = ? WHERE `uuid` = ?",
            "UPDATE Players SET lastLeave = ? WHERE uuid = ?"
    ),
    SELECT_PLAYERS_BY_IP(
            "SELECT * FROM `Players` WHERE `lastIp` = ? ORDER BY `lastJoin` DESC",
            "SELECT * FROM Players WHERE lastIp = ? ORDER BY lastJoin DESC"
    );

    private String mysql;
    private String hsqldb;

    SQLQuery(String mysql, String hsqldb) {
        this.mysql = mysql;
        this.hsqldb = hsqldb;
    }

    @Override
    public String toString() {
        return DatabaseManager.get().isUseMySQL() ? mysql : hsqldb;
    }
}
