package net.hnt8.advancedban.redis;

import net.hnt8.advancedban.MethodInterface;

/**
 * Redis / voice-mute settings loaded from Velocity (or Bukkit) config.yml.
 */
public final class RedisSettings {

    public boolean enabled;
    public String host = "127.0.0.1";
    public int port = 6379;
    public String password;
    public int database;
    public String authToken = "change-me";
    public long maxAgeMs = 60000L;
    public String proxyIn = "avesban:proxy:in";
    public String proxyOut = "avesban:proxy:out";
    public String linkAcks = "avesban:link:acks";
    public String linkPrefix = "avesban:link:";
    public String ackKeyPrefix = "avesban:ack:";
    public String primaryLink = "survival";
    public String secondaryLink = "lobby";
    public long failoverTimeoutMs = 1500L;
    public boolean voiceMuteEnabled = true;
    public String voicePermission = "voicechat.speak";
    public boolean voiceValue;

    public static RedisSettings fromMethods(MethodInterface mi) {
        Object config = mi.getConfig();
        RedisSettings settings = new RedisSettings();
        settings.enabled = mi.getBoolean(config, "Redis.Enabled", false);
        settings.host = mi.getString(config, "Redis.Host", "127.0.0.1");
        settings.port = mi.getInteger(config, "Redis.Port", 6379);
        String password = mi.getString(config, "Redis.Password", "");
        settings.password = password == null || password.isEmpty() ? null : password;
        settings.database = mi.getInteger(config, "Redis.Database", 0);
        settings.authToken = mi.getString(config, "Redis.AuthToken", "change-me");
        settings.maxAgeMs = mi.getLong(config, "Redis.MaxAgeMs", 60000L);
        settings.proxyIn = mi.getString(config, "Redis.Channels.ProxyIn", "avesban:proxy:in");
        settings.proxyOut = mi.getString(config, "Redis.Channels.ProxyOut", "avesban:proxy:out");
        settings.linkAcks = mi.getString(config, "Redis.Channels.LinkAcks", "avesban:link:acks");
        settings.linkPrefix = mi.getString(config, "Redis.Channels.LinkPrefix", "avesban:link:");
        settings.ackKeyPrefix = mi.getString(config, "Redis.Channels.AckKeyPrefix", "avesban:ack:");
        settings.primaryLink = mi.getString(config, "BackendLinks.Primary", "survival");
        settings.secondaryLink = mi.getString(config, "BackendLinks.Secondary", "lobby");
        settings.failoverTimeoutMs = mi.getLong(config, "BackendLinks.FailoverTimeoutMs", 1500L);
        settings.voiceMuteEnabled = mi.getBoolean(config, "VoiceMute.Enabled", true);
        settings.voicePermission = mi.getString(config, "VoiceMute.Permission", "voicechat.speak");
        settings.voiceValue = mi.getBoolean(config, "VoiceMute.Value", false);
        return settings;
    }

    public String linkChannel(String linkName) {
        return linkPrefix + linkName;
    }

    public boolean hasUsableToken() {
        return authToken != null && !authToken.isEmpty();
    }
}
