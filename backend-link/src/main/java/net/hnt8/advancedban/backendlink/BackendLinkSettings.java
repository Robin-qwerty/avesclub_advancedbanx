package net.hnt8.advancedban.backendlink;

import net.hnt8.advancedban.redis.RedisSettings;
import org.bukkit.configuration.file.FileConfiguration;

final class BackendLinkSettings {

    private BackendLinkSettings() {
    }

    static RedisSettings redis(FileConfiguration config) {
        RedisSettings settings = new RedisSettings();
        settings.enabled = config.getBoolean("Redis.Enabled", false);
        settings.host = config.getString("Redis.Host", "127.0.0.1");
        settings.port = config.getInt("Redis.Port", 6379);
        String password = config.getString("Redis.Password", "");
        settings.password = password == null || password.isEmpty() ? null : password;
        settings.database = config.getInt("Redis.Database", 0);
        settings.authToken = config.getString("Redis.AuthToken", "change-me");
        settings.maxAgeMs = config.getLong("Redis.MaxAgeMs", 60000L);
        settings.proxyIn = config.getString("Redis.Channels.ProxyIn", "avesban:proxy:in");
        settings.linkAcks = config.getString("Redis.Channels.LinkAcks", "avesban:link:acks");
        settings.linkPrefix = config.getString("Redis.Channels.LinkPrefix", "avesban:link:");
        settings.voiceMuteEnabled = config.getBoolean("VoiceMute.Enabled", true);
        return settings;
    }

    static String linkName(FileConfiguration config) {
        String name = config.getString("LinkName", "survival");
        return name == null || name.trim().isEmpty() ? "survival" : name.trim();
    }
}
