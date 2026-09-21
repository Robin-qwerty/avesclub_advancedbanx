package net.hnt8.advancedban.backendlink;

import net.hnt8.advancedban.backendlink.luckperms.VoiceMuteLuckPerms;
import net.hnt8.advancedban.redis.RedisBus;
import net.hnt8.advancedban.redis.RedisEnvelope;
import net.hnt8.advancedban.redis.RedisSettings;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * Redis connection for BackendLink. Loaded only after config.yml exists.
 */
final class BackendLinkRedis {

    private static RedisBus bus;
    private static RedisSettings settings;
    private static String linkName = "survival";

    private BackendLinkRedis() {
    }

    static void start(JavaPlugin plugin, RedisSettings redisSettings, String name) {
        shutdown();
        settings = redisSettings;
        linkName = name;
        if (settings == null || !settings.enabled) {
            plugin.getLogger().info("Redis is disabled. Backend commands need an online player to reach Velocity.");
            return;
        }
        bus = new RedisBus(settings, plugin.getLogger());
        VoiceMuteLuckPerms luckPerms = new VoiceMuteLuckPerms(plugin.getLogger());
        BackendRedisListener listener = new BackendRedisListener(
                settings, bus, luckPerms, linkName, plugin.getLogger());
        String linkChannel = settings.linkChannel(linkName);
        bus.start(listener::onMessage, linkChannel);
        plugin.getLogger().info("Redis connected. LinkName=" + linkName + " subscribed to " + linkChannel);
    }

    static boolean publishCommand(String command, String[] args, String operator, String serverName) {
        if (bus == null || !bus.isRunning() || settings == null) {
            return false;
        }
        RedisEnvelope envelope = RedisEnvelope.create(
                RedisEnvelope.TYPE_EXECUTE_COMMAND, settings.authToken, "link:" + linkName);
        envelope.operator = operator;
        envelope.command = command;
        envelope.args = Arrays.asList(args);
        envelope.payload.addProperty("server", serverName);
        bus.publish(settings.proxyIn, envelope.toJson());
        return true;
    }

    static void shutdown() {
        if (bus != null) {
            bus.shutdown();
            bus = null;
        }
    }
}
