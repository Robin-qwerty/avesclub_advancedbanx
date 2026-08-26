package net.hnt8.advancedban.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.hnt8.advancedban.MethodInterface;
import net.hnt8.advancedban.Universal;

public class DynamicDataSource {
    private HikariConfig config = new HikariConfig();

    public DynamicDataSource(boolean preferMySQL) throws ClassNotFoundException {
        MethodInterface mi = Universal.get().getMethods();
        if (preferMySQL) {
            String ip = mi.getString(mi.getMySQLFile(), "MySQL.IP", "Unknown");
            String dbName = mi.getString(mi.getMySQLFile(), "MySQL.DB-Name", "Unknown");
            String usrName = mi.getString(mi.getMySQLFile(), "MySQL.Username", "Unknown");
            String password = mi.getString(mi.getMySQLFile(), "MySQL.Password", "Unknown");
            String properties = mi.getString(mi.getMySQLFile(), "MySQL.Properties", "verifyServerCertificate=false&useSSL=false&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true");
            int port = mi.getInteger(mi.getMySQLFile(), "MySQL.Port", 3306);
            
            // Ensure allowPublicKeyRetrieval is set for MySQL 8.0+ compatibility
            if (!properties.toLowerCase().contains("allowpublickeyretrieval")) {
                properties += (properties.isEmpty() ? "" : "&") + "allowPublicKeyRetrieval=true";
            }

            // Try modern driver first, fallback to legacy driver
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                Class.forName("com.mysql.jdbc.Driver");
            }
            config.setJdbcUrl("jdbc:mysql://" + ip + ":" + port + "/" + dbName + "?"+properties);
            config.setUsername(usrName);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(10000);
            config.setValidationTimeout(3000);
            config.setConnectionTestQuery("SELECT 1");
            // Replace idle connections before MySQL wait_timeout drops them.
            config.setMaxLifetime(1800000);
            config.setKeepaliveTime(300000);
            config.setIdleTimeout(600000);
            config.setLeakDetectionThreshold(60000);
        } else {
            // No need to worry about relocation because the maven-shade-plugin also changes strings
            String driverClassName = "org.hsqldb.jdbc.JDBCDriver";
            Class.forName(driverClassName);
            config.setDriverClassName(driverClassName);
            config.setJdbcUrl("jdbc:hsqldb:file:" + mi.getDataFolder().getPath() + "/data/storage;hsqldb.lock_file=false");
            config.setUsername("SA");
            config.setPassword("");
        }
    }

    public HikariDataSource generateDataSource(){
        return new HikariDataSource(config);
    }
}
