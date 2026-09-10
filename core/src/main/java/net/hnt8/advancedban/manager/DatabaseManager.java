package net.hnt8.advancedban.manager;

import com.zaxxer.hikari.HikariDataSource;
import net.hnt8.advancedban.MethodInterface;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.utils.DynamicDataSource;
import net.hnt8.advancedban.utils.SQLQuery;
import net.hnt8.advancedban.utils.Punishment;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetProvider;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The Database Manager is used to interact directly with the database is use.<br>
 * Will automatically direct the requests to either MySQL or HSQLDB.
 * <br><br>
 * Looking to request {@link Punishment Punishments} from the Database?
 * Use {@link PunishmentManager#getPunishments(SQLQuery, Object...)} or
 * {@link PunishmentManager#getPunishmentFromResultSet(ResultSet)} for already parsed data.
 */
public class DatabaseManager {

    private HikariDataSource dataSource;
    private boolean useMySQL;
    private volatile boolean closed = false;
    private final Object reconnectLock = new Object();
    private volatile long lastReconnectAt;
    private volatile boolean lastReconnectOk;

    private RowSetFactory factory;
    
    private static DatabaseManager instance = null;

    /**
     * Get the instance of the command manager
     *
     * @return the database manager instance
     */
    public static synchronized DatabaseManager get() {
        return instance == null ? instance = new DatabaseManager() : instance;
    }

    /**
     * Initially connects to the database and sets up the required tables of they don't already exist.
     *
     * @param useMySQLServer whether to preferably use MySQL (uses HSQLDB as fallback)
     */
    public void setup(boolean useMySQLServer) {
        MethodInterface mi = Universal.get().getMethods();
        File storageFile = new File(mi.getDataFolder(), ".storage");
        
        // Read previous storage type
        boolean previousUseMySQL = false;
        if (storageFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(storageFile.toPath())).trim();
                previousUseMySQL = "mysql".equalsIgnoreCase(content);
            } catch (IOException ex) {
                Universal.get().getLogger().warning("Failed to read storage type file: " + ex.getMessage());
            }
        }
        
        useMySQL = useMySQLServer;

        try {
            dataSource = new DynamicDataSource(useMySQL).generateDataSource();
        } catch (ClassNotFoundException ex) {
            Universal.get().getLogger().severe("ERROR: Failed to configure data source!");
            Universal.get().getLogger().severe("MySQL driver not found. Please ensure MySQL connector is available.");
            Universal.get().getLogger().fine(ex.getMessage());
            Universal.get().debugException(ex);
            return;
        } catch (Exception ex) {
            Universal.get().getLogger().severe("ERROR: Failed to configure data source!");
            Universal.get().getLogger().severe("Exception: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            Universal.get().debugException(ex);
            return;
        }

        // Test the connection before trying to use it
        try (Connection testConnection = dataSource.getConnection()) {
            if (!testConnection.isValid(3)) {
                Universal.get().getLogger().severe("ERROR: Database connection is not valid!");
                if (useMySQL) {
                    Universal.get().getLogger().severe("Please check your MySQL configuration in config.yml or MySQL.yml");
                    Universal.get().getLogger().severe("Verify: IP, Port, Database Name, Username, Password, and that MySQL server is running");
                }
                return;
            }
        } catch (SQLException ex) {
            Universal.get().getLogger().severe("ERROR: Failed to connect to database!");
            if (useMySQL) {
                Universal.get().getLogger().severe("MySQL Connection Error: " + ex.getMessage());
                Universal.get().getLogger().severe("Please check your MySQL configuration in config.yml or MySQL.yml");
                Universal.get().getLogger().severe("Verify: IP, Port, Database Name, Username, Password, and that MySQL server is running");
            } else {
                Universal.get().getLogger().severe("HSQLDB Connection Error: " + ex.getMessage());
            }
            Universal.get().debugSqlException(ex);
            return;
        }

        executeStatement(SQLQuery.CREATE_TABLE_PUNISHMENT);
        executeStatement(SQLQuery.CREATE_TABLE_PUNISHMENT_HISTORY);
        executeStatement(SQLQuery.CREATE_TABLE_PLAYERS);
        // Runs on both MySQL and HSQLDB so databases created before the firstIp
        // column existed get upgraded too, not just MySQL ones.
        addFirstIpColumnIfMissing();
        if (useMySQL) {
            // Fix LONG columns to BIGINT if they exist (for existing databases)
            fixLongColumnsToBigInt();
            // Add server column to existing tables if it doesn't exist
            addServerColumnIfMissing();
            // Add targetServer column to existing tables if it doesn't exist
            addTargetServerColumnIfMissing();
            // Create indexes after ensuring column types are correct
            createMySqlIndexes();
        }
        
        // Check if we're switching from HSQLDB to MySQL and migrate if needed
        if (useMySQL && !previousUseMySQL) {
            Universal.get().getLogger().info("Detected storage change from HSQLDB to MySQL. Checking if migration is needed...");
            migrateFromHSQLDBToMySQL(mi);
        }
        
        // Save current storage type
        try {
            if (!storageFile.exists()) {
                storageFile.createNewFile();
            }
            try (FileWriter writer = new FileWriter(storageFile)) {
                writer.write(useMySQL ? "mysql" : "hsqldb");
            }
        } catch (IOException ex) {
            Universal.get().getLogger().warning("Failed to save storage type file: " + ex.getMessage());
        }
    }

    /**
     * Migrates data from HSQLDB to MySQL when switching storage types.
     * Only runs if MySQL is empty and HSQLDB has data.
     *
     * @param mi the method interface
     */
    private void migrateFromHSQLDBToMySQL(MethodInterface mi) {
        try {
            // First, check if MySQL already has data
            boolean mysqlHasData = false;
            try (ResultSet rs = executeResultStatement(SQLQuery.SELECT_ALL_PUNISHMENTS)) {
                if (rs != null && rs.next()) {
                    mysqlHasData = true;
                }
            } catch (SQLException ex) {
                // Table might not exist yet, that's okay
            }
            
            if (mysqlHasData) {
                Universal.get().getLogger().info("MySQL database already contains data. Skipping migration.");
                return;
            }
            
            // Check if HSQLDB file exists
            File hsqldbFile = new File(mi.getDataFolder(), "data/storage.script");
            if (!hsqldbFile.exists()) {
                Universal.get().getLogger().info("No HSQLDB data found. Nothing to migrate.");
                return;
            }
            
            Universal.get().getLogger().info("Starting migration from HSQLDB to MySQL...");
            
            // Create temporary HSQLDB connection
            HikariDataSource hsqldbSource;
            try {
                DynamicDataSource hsqldbDataSource = new DynamicDataSource(false);
                hsqldbSource = hsqldbDataSource.generateDataSource();
            } catch (Exception ex) {
                Universal.get().getLogger().severe("Failed to connect to HSQLDB for migration: " + ex.getMessage());
                Universal.get().debugException(ex);
                return;
            }
            
            int migratedPunishments = 0;
            int migratedHistory = 0;
            
            try (Connection hsqldbConn = hsqldbSource.getConnection();
                 Connection mysqlConn = dataSource.getConnection()) {
                
                // Migrate active punishments
                try (PreparedStatement selectStmt = hsqldbConn.prepareStatement("SELECT * FROM Punishments");
                     ResultSet rs = selectStmt.executeQuery();
                     PreparedStatement insertStmt = mysqlConn.prepareStatement(
                         "INSERT INTO `Punishments` (`name`, `uuid`, `reason`, `operator`, `punishmentType`, `start`, `end`, `calculation`, `server`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    
                    while (rs.next()) {
                        insertStmt.setString(1, rs.getString("name"));
                        insertStmt.setString(2, rs.getString("uuid"));
                        insertStmt.setString(3, rs.getString("reason"));
                        insertStmt.setString(4, rs.getString("operator"));
                        insertStmt.setString(5, rs.getString("punishmentType"));
                        insertStmt.setLong(6, rs.getLong("start"));
                        insertStmt.setLong(7, rs.getLong("end"));
                        insertStmt.setString(8, rs.getString("calculation"));
                        // Server column may not exist in HSQLDB, so use null
                        try {
                            insertStmt.setString(9, rs.getString("server"));
                        } catch (SQLException ex) {
                            insertStmt.setString(9, null);
                        }
                        insertStmt.executeUpdate();
                        migratedPunishments++;
                    }
                }
                
                // Migrate history
                try (PreparedStatement selectStmt = hsqldbConn.prepareStatement("SELECT * FROM PunishmentHistory");
                     ResultSet rs = selectStmt.executeQuery();
                     PreparedStatement insertStmt = mysqlConn.prepareStatement(
                         "INSERT INTO `PunishmentHistory` (`name`, `uuid`, `reason`, `operator`, `punishmentType`, `start`, `end`, `calculation`, `server`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    
                    while (rs.next()) {
                        insertStmt.setString(1, rs.getString("name"));
                        insertStmt.setString(2, rs.getString("uuid"));
                        insertStmt.setString(3, rs.getString("reason"));
                        insertStmt.setString(4, rs.getString("operator"));
                        insertStmt.setString(5, rs.getString("punishmentType"));
                        insertStmt.setLong(6, rs.getLong("start"));
                        insertStmt.setLong(7, rs.getLong("end"));
                        insertStmt.setString(8, rs.getString("calculation"));
                        // Server column may not exist in HSQLDB, so use null
                        try {
                            insertStmt.setString(9, rs.getString("server"));
                        } catch (SQLException ex) {
                            insertStmt.setString(9, null);
                        }
                        insertStmt.executeUpdate();
                        migratedHistory++;
                    }
                }
                
            } catch (SQLException ex) {
                Universal.get().getLogger().severe("Error during migration: " + ex.getMessage());
                Universal.get().debugSqlException(ex);
                return;
            } finally {
                hsqldbSource.close();
            }
            
            Universal.get().getLogger().info("Migration completed successfully!");
            Universal.get().getLogger().info("Migrated " + migratedPunishments + " active punishments and " + migratedHistory + " history entries.");
            
        } catch (Exception ex) {
            Universal.get().getLogger().severe("Failed to migrate data from HSQLDB to MySQL: " + ex.getMessage());
            Universal.get().debugException(ex);
        }
    }

    /**
     * Creates performance indexes for MySQL tables if they do not already exist.
     * This keeps startup idempotent and avoids duplicate-index errors on reboot.
     */
    private void createMySqlIndexes() {
        try (Connection connection = dataSource.getConnection()) {
            Universal.get().getLogger().info("Creating MySQL indexes...");
            
            // Active punishments table
            ensureMySqlIndex(connection, "Punishments", "idx_punishments_uuid_type_start", "uuid", "punishmentType", "start");
            ensureMySqlIndex(connection, "Punishments", "idx_punishments_end", "end");
            ensureMySqlIndex(connection, "Punishments", "idx_punishments_start", "start");

            // History table
            ensureMySqlIndex(connection, "PunishmentHistory", "idx_history_uuid", "uuid");
            ensureMySqlIndex(connection, "PunishmentHistory", "idx_history_uuid_calculation", "uuid", "calculation");
            ensureMySqlIndex(connection, "PunishmentHistory", "idx_history_start", "start");

            ensureMySqlIndex(connection, "Players", "idx_players_name", "name");
            ensureMySqlIndex(connection, "Players", "idx_players_lastIp", "lastIp");
            ensureMySqlIndex(connection, "Players", "idx_players_firstIp", "firstIp");
            ensureMySqlIndex(connection, "Players", "idx_players_lastJoin", "lastJoin");
            
            Universal.get().getLogger().info("MySQL indexes creation completed.");
        } catch (SQLException ex) {
            Universal.get().getLogger().warning("Failed to create MySQL indexes: " + ex.getMessage());
            Universal.get().debugSqlException(ex);
        }
    }

    /**
     * Fixes LONG columns to BIGINT for existing MySQL databases.
     * MySQL interprets LONG as MEDIUMTEXT, which cannot be indexed.
     */
    private void fixLongColumnsToBigInt() {
        try (Connection connection = dataSource.getConnection()) {
            // Check and fix start/end columns in Punishments table
            fixColumnTypeIfNeeded(connection, "Punishments", "start");
            fixColumnTypeIfNeeded(connection, "Punishments", "end");
            
            // Check and fix start/end columns in PunishmentHistory table
            fixColumnTypeIfNeeded(connection, "PunishmentHistory", "start");
            fixColumnTypeIfNeeded(connection, "PunishmentHistory", "end");
        } catch (SQLException ex) {
            Universal.get().getLogger().warning("Failed to fix LONG columns to BIGINT: " + ex.getMessage());
            Universal.get().debugSqlException(ex);
        }
    }

    /**
     * Checks if a column is of type LONG (MEDIUMTEXT) and converts it to BIGINT if needed.
     */
    private void fixColumnTypeIfNeeded(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, null, table, column)) {
            if (rs.next()) {
                String typeName = rs.getString("TYPE_NAME");
                // Check if it's MEDIUMTEXT (MySQL's interpretation of LONG)
                if (typeName != null && (typeName.equalsIgnoreCase("MEDIUMTEXT") || typeName.equalsIgnoreCase("LONGTEXT") || typeName.equalsIgnoreCase("TEXT"))) {
                    Universal.get().getLogger().info("Converting " + table + "." + column + " from " + typeName + " to BIGINT...");
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` BIGINT DEFAULT NULL");
                        Universal.get().getLogger().info("Successfully converted " + table + "." + column + " to BIGINT.");
                    } catch (SQLException ex) {
                        // If column doesn't exist or other error, log and continue
                        Universal.get().getLogger().warning("Failed to convert " + table + "." + column + ": " + ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Adds the server column to existing MySQL tables if it doesn't exist.
     * This allows existing databases to be upgraded without data loss.
     */
    private void addServerColumnIfMissing() {
        try (Connection connection = dataSource.getConnection()) {
            // Check if server column exists in Punishments table
            if (!columnExists(connection, "Punishments", "server")) {
                Universal.get().getLogger().info("Adding 'server' column to Punishments table...");
                try (PreparedStatement stmt = connection.prepareStatement(
                        "ALTER TABLE `Punishments` ADD COLUMN `server` VARCHAR(64) NULL DEFAULT NULL")) {
                    stmt.execute();
                    Universal.get().getLogger().info("Successfully added 'server' column to Punishments table.");
                }
            }
            
            // Check if server column exists in PunishmentHistory table
            if (!columnExists(connection, "PunishmentHistory", "server")) {
                Universal.get().getLogger().info("Adding 'server' column to PunishmentHistory table...");
                try (PreparedStatement stmt = connection.prepareStatement(
                        "ALTER TABLE `PunishmentHistory` ADD COLUMN `server` VARCHAR(64) NULL DEFAULT NULL")) {
                    stmt.execute();
                    Universal.get().getLogger().info("Successfully added 'server' column to PunishmentHistory table.");
                }
            }
        } catch (SQLException ex) {
            Universal.get().getLogger().warning("Failed to add server column: " + ex.getMessage());
            Universal.get().debugSqlException(ex);
        }
    }

    /**
     * Adds the targetServer column to existing MySQL tables if it doesn't exist.
     * This allows existing databases to be upgraded without data loss.
     */
    private void addTargetServerColumnIfMissing() {
        try (Connection connection = dataSource.getConnection()) {
            // Check if targetServer column exists in Punishments table
            if (!columnExists(connection, "Punishments", "targetServer")) {
                Universal.get().getLogger().info("Adding 'targetServer' column to Punishments table...");
                try (PreparedStatement stmt = connection.prepareStatement(
                        "ALTER TABLE `Punishments` ADD COLUMN `targetServer` VARCHAR(64) NULL DEFAULT NULL")) {
                    stmt.execute();
                    Universal.get().getLogger().info("Successfully added 'targetServer' column to Punishments table.");
                }
            }
            
            // Check if targetServer column exists in PunishmentHistory table
            if (!columnExists(connection, "PunishmentHistory", "targetServer")) {
                Universal.get().getLogger().info("Adding 'targetServer' column to PunishmentHistory table...");
                try (PreparedStatement stmt = connection.prepareStatement(
                        "ALTER TABLE `PunishmentHistory` ADD COLUMN `targetServer` VARCHAR(64) NULL DEFAULT NULL")) {
                    stmt.execute();
                    Universal.get().getLogger().info("Successfully added 'targetServer' column to PunishmentHistory table.");
                }
            }
        } catch (SQLException ex) {
            Universal.get().getLogger().warning("Failed to add targetServer column: " + ex.getMessage());
            Universal.get().debugSqlException(ex);
        }
    }

    /**
     * Adds the firstIp column to the Players table if it doesn't exist, and backfills it
     * from lastIp for players that already had a record before this column existed.
     * Unlike the other "if missing" helpers this runs for both MySQL and HSQLDB, since
     * HSQLDB databases created before this column existed also need the migration.
     */
    private void addFirstIpColumnIfMissing() {
        try (Connection connection = dataSource.getConnection()) {
            if (columnExists(connection, "Players", "firstIp")) {
                return;
            }

            Universal.get().getLogger().info("Adding 'firstIp' column to Players table...");
            String alterSql = useMySQL
                    ? "ALTER TABLE `Players` ADD COLUMN `firstIp` VARCHAR(45) NULL DEFAULT NULL"
                    : "ALTER TABLE Players ADD COLUMN firstIp VARCHAR(45) DEFAULT NULL";
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(alterSql);
            }

            String backfillSql = useMySQL
                    ? "UPDATE `Players` SET `firstIp` = `lastIp` WHERE `firstIp` IS NULL AND `lastIp` IS NOT NULL"
                    : "UPDATE Players SET firstIp = lastIp WHERE firstIp IS NULL AND lastIp IS NOT NULL";
            try (Statement stmt = connection.createStatement()) {
                int updated = stmt.executeUpdate(backfillSql);
                Universal.get().getLogger().info("Backfilled 'firstIp' for " + updated + " existing player(s) using their lastIp.");
            }
        } catch (SQLException ex) {
            Universal.get().getLogger().warning("Failed to add firstIp column: " + ex.getMessage());
            Universal.get().debugSqlException(ex);
        }
    }

    /**
     * Checks if a column exists in a MySQL table.
     */
    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private void ensureMySqlIndex(Connection connection, String table, String indexName, String... columns) throws SQLException {
        if (mySqlIndexExists(connection, table, indexName)) {
            Universal.get().getLogger().fine("Index " + indexName + " already exists on table " + table + ", skipping.");
            return;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE INDEX `").append(indexName).append("` ON `").append(table).append("` (");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('`').append(columns[i]).append('`');
        }
        sql.append(')');

        String sqlString = sql.toString();
        Universal.get().getLogger().info("Creating index " + indexName + " on table " + table + "...");
        
        // Use Statement for DDL operations instead of PreparedStatement
        try (Statement statement = connection.createStatement()) {
            statement.execute(sqlString);
            
            // Verify the index was actually created
            if (mySqlIndexExists(connection, table, indexName)) {
                Universal.get().getLogger().info("Successfully created index " + indexName + " on table " + table + ".");
            } else {
                Universal.get().getLogger().warning("Index " + indexName + " was not created on table " + table + " (verification failed).");
            }
        } catch (SQLException ex) {
            // Check if it's a duplicate index error (index might have been created by another connection)
            if (ex.getErrorCode() == 1061 || ex.getMessage().contains("Duplicate key name")) {
                Universal.get().getLogger().fine("Index " + indexName + " already exists (detected during creation), skipping.");
            } else if (ex.getMessage() != null && ex.getMessage().contains("BLOB/TEXT") && ex.getMessage().contains("key length")) {
                // Column is still TEXT/BLOB type, skip this index
                Universal.get().getLogger().warning("Cannot create index " + indexName + " on table " + table + ": column type is not indexable. The column may need to be converted to BIGINT.");
                Universal.get().getLogger().info("Attempting to fix column types and retry...");
                // Don't throw, just skip this index
            } else {
                Universal.get().getLogger().warning("Failed to create index " + indexName + " on table " + table + ": " + ex.getMessage());
                // Don't throw - continue with other indexes
            }
        }
    }

    private boolean mySqlIndexExists(Connection connection, String table, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        
        // Try exact table name first
        if (indexExists(metaData, catalog, table, indexName)) {
            return true;
        }
        
        // MySQL table name case can vary by OS/filesystem; check common variants.
        if (indexExists(metaData, catalog, table.toLowerCase(), indexName)) {
            return true;
        }
        if (indexExists(metaData, catalog, table.toUpperCase(), indexName)) {
            return true;
        }
        
        // Also try with null catalog (some MySQL setups don't return catalog properly)
        if (indexExists(metaData, null, table, indexName)) {
            return true;
        }
        
        return false;
    }

    private boolean indexExists(DatabaseMetaData metaData, String catalog, String table, String indexName) throws SQLException {
        try (ResultSet rs = metaData.getIndexInfo(catalog, null, table, false, false)) {
            while (rs.next()) {
                String existing = rs.getString("INDEX_NAME");
                // PRIMARY key is returned as null by getIndexInfo, skip it
                if (existing != null && existing.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        } catch (SQLException ex) {
            // If table doesn't exist yet, this will throw - that's okay, index doesn't exist
            if (ex.getMessage() != null && ex.getMessage().contains("doesn't exist")) {
                return false;
            }
            throw ex;
        }
        return false;
    }

    /**
     * Shuts down the HSQLDB if used.
     */
    public void shutdown() {
        closed = true;
        if (dataSource == null) {
            return;
        }

        if (!useMySQL) {
            try(Connection connection = dataSource.getConnection(); final PreparedStatement statement = connection.prepareStatement("SHUTDOWN")){
                statement.execute();
            }catch (SQLException | NullPointerException exc){
                Universal.get().getLogger().warning("An unexpected error has occurred turning off the database");
                Universal.get().debugException(exc);
            }
        }

        dataSource.close();
    }

    /**
     * Whether the connection pool is still usable.
     *
     * @return true if statements can still be executed
     */
    public boolean isAvailable() {
        return !closed && dataSource != null && !dataSource.isClosed();
    }

    /**
     * Ping the database and, if that fails, rebuild the connection pool.
     * Concurrent callers share one attempt (5s cooldown).
     *
     * @return true if the database is usable afterwards
     */
    public boolean tryReconnect() {
        if (closed) {
            return false;
        }
        synchronized (reconnectLock) {
            long now = System.currentTimeMillis();
            if (now - lastReconnectAt < 5000L) {
                return lastReconnectOk && isAvailable();
            }
            lastReconnectAt = now;
            lastReconnectOk = reconnectNow();
            return lastReconnectOk;
        }
    }

    private boolean reconnectNow() {
        Universal.get().getLogger().warning("Database connection lost. Trying to reconnect...");
        if (ping()) {
            Universal.get().getLogger().info("Database connection is healthy again.");
            return true;
        }

        HikariDataSource old = dataSource;
        try {
            HikariDataSource replacement = new DynamicDataSource(useMySQL).generateDataSource();
            try (Connection connection = replacement.getConnection()) {
                if (!connection.isValid(3)) {
                    replacement.close();
                    Universal.get().getLogger().severe("Database reconnect failed: new connection is not valid.");
                    return false;
                }
            }
            dataSource = replacement;
            if (old != null && !old.isClosed()) {
                try {
                    old.close();
                } catch (Exception ignored) {
                }
            }
            Universal.get().getLogger().info("Database reconnect succeeded.");
            return true;
        } catch (Exception ex) {
            Universal.get().getLogger().severe("Database reconnect failed: " + ex.getMessage());
            Universal.get().debugException(ex);
            return false;
        }
    }

    private boolean ping() {
        if (!isAvailable()) {
            return false;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet rs = statement.executeQuery()) {
            return rs.next();
        } catch (SQLException ex) {
            return false;
        }
    }
    
    private synchronized CachedRowSet createCachedRowSet() throws SQLException {
    	if (factory == null) {
    		factory = RowSetProvider.newFactory();
    	}
    	return factory.createCachedRowSet();
    }

    /**
     * Execute a sql statement without any results.
     *
     * @param sql        the sql statement
     * @param parameters the parameters
     */
    public void executeStatement(SQLQuery sql, Object... parameters) {
        executeStatement(sql, false, parameters);
    }

    /**
     * Execute an update statement and return the number of affected rows.
     *
     * @param sql        the sql statement
     * @param parameters the parameters
     * @return affected row count, or 0 on failure
     */
    public int executeUpdate(SQLQuery sql, Object... parameters) {
        return executeUpdate(sql.toString(), parameters);
    }

    private int executeUpdate(String sql, Object... parameters) {
        if (!isAvailable()) {
            return closed ? 0 : -1;
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            return statement.executeUpdate();
        } catch (SQLException ex) {
            if (isClosedPoolError(ex)) {
                return 0;
            }
            Universal.get().getLogger().severe(
                    "An unexpected error has occurred executing an update in the database\n"
                            + "SQL Error: " + ex.getMessage() + "\n"
                            + "SQL State: " + ex.getSQLState() + "\n"
                            + "Error Code: " + ex.getErrorCode()
            );
            Universal.get().getLogger().fine("Query: \n" + sql);
            Universal.get().debugSqlException(ex);
        } catch (NullPointerException ex) {
            if (closed) {
                return 0;
            }
            Universal.get().getLogger().severe(
                    "An unexpected error has occurred connecting to the database\n"
                            + "The database connection pool may not be initialized properly."
            );
            Universal.get().debugException(ex);
        }
        return -1;
    }

    /**
     * Execute a sql statement.
     *
     * @param sql        the sql statement
     * @param parameters the parameters
     * @return the result set
     */
    public ResultSet executeResultStatement(SQLQuery sql, Object... parameters) {
        return executeStatement(sql, true, parameters);
    }

    private ResultSet executeStatement(SQLQuery sql, boolean result, Object... parameters) {
        return executeStatement(sql.toString(), result, parameters);
    }

    private ResultSet executeStatement(String sql, boolean result, Object... parameters) {
    	if (!isAvailable()) {
    		if (!closed) {
    			Universal.get().getLogger().severe("Database is unavailable while executing a statement.");
    		}
    		return null;
    	}
    	
    	try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

    		for (int i = 0; i < parameters.length; i++) {
    			statement.setObject(i + 1, parameters[i]);
    		}

    		if (result) {
    			CachedRowSet results = createCachedRowSet();
    			results.populate(statement.executeQuery());
    			return results;
    		}
   			statement.execute();
    	} catch (SQLException ex) {
    		if (isClosedPoolError(ex)) {
    			return null;
    		}
    		Universal.get().getLogger().severe(
   					"An unexpected error has occurred executing a statement in the database\n"
   							+ "SQL Error: " + ex.getMessage() + "\n"
   							+ "SQL State: " + ex.getSQLState() + "\n"
   							+ "Error Code: " + ex.getErrorCode()
    				);
    		if (useMySQL) {
    			Universal.get().getLogger().severe("Please verify your MySQL connection settings in config.yml or MySQL.yml");
    		}
    		Universal.get().getLogger().fine("Query: \n" + sql);
    		Universal.get().debugSqlException(ex);
       	} catch (NullPointerException ex) {
            if (closed) {
                return null;
            }
            Universal.get().getLogger().severe(
                    "An unexpected error has occurred connecting to the database\n"
                            + "The database connection pool may not be initialized properly.\n"
                            + "Check if your MySQL data is correct and if your MySQL-Server is online"
            );
            if (useMySQL) {
            	Universal.get().getLogger().severe("Please verify your MySQL connection settings in config.yml or MySQL.yml");
            }
            Universal.get().debugException(ex);
        }
        return null;
    }

    private boolean isClosedPoolError(SQLException ex) {
        return closed || dataSource == null || dataSource.isClosed();
    }

    /**
     * Check whether there is a valid connection to the database.
     *
     * @return whether there is a valid connection
     */
    public boolean isConnectionValid() {
        return isAvailable() && dataSource.isRunning();
    }

    /**
     * Check whether MySQL is actually used.
     *
     * @return whether MySQL is used
     */
    public boolean isUseMySQL() {
        return useMySQL;
    }
}