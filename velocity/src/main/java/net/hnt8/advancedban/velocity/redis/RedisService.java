package net.hnt8.advancedban.velocity.redis;

import net.hnt8.advancedban.redis.RedisBus;
import net.hnt8.advancedban.redis.RedisEnvelope;
import net.hnt8.advancedban.redis.RedisSettings;
import net.hnt8.advancedban.redis.RpcPendingStore;
import net.hnt8.advancedban.velocity.VelocityMain;

/**
 * Owns the Velocity Redis connection, web-command listener, and voice-mute coordinator.
 */
public final class RedisService {

    private final RedisSettings settings;
    private final RedisBus bus;
    private final RpcPendingStore pending = new RpcPendingStore();
    private final ProxyCommandListener commands;
    private final VoiceMuteCoordinator voiceMutes;

    private RedisService(RedisSettings settings, RedisBus bus) {
        this.settings = settings;
        this.bus = bus;
        this.commands = new ProxyCommandListener(settings, bus);
        this.voiceMutes = new VoiceMuteCoordinator(settings, bus, pending);
    }

    public static RedisService start(RedisSettings settings) {
        if (settings == null || !settings.enabled) {
            VelocityMain.get().getLogger().info("Redis is disabled. Web panel commands and voice-mute LuckPerms RPC are off.");
            return null;
        }
        if (!settings.hasUsableToken() || "change-me".equals(settings.authToken)) {
            VelocityMain.get().getLogger().warning("Redis.AuthToken is empty or still 'change-me'. Set a real shared secret before enabling Redis on a public network.");
        }

        RedisBus bus = new RedisBus(settings, VelocityMain.get().getLogger());
        RedisService service = new RedisService(settings, bus);
        bus.start((channel, message) -> service.dispatch(channel, message),
                settings.proxyIn, settings.linkAcks);
        VelocityMain.get().getLogger().info("Redis connected to " + settings.host + ":" + settings.port);
        return service;
    }

    private void dispatch(String channel, String message) {
        final RedisEnvelope envelope;
        try {
            envelope = RedisEnvelope.parse(message);
        } catch (Exception ex) {
            VelocityMain.get().getLogger().warning("Ignored invalid Redis JSON on " + channel + ": " + ex.getMessage());
            return;
        }

        // Complete pending RPCs on this subscriber thread so command threads
        // waiting for an ACK cannot deadlock on the Velocity scheduler.
        if (settings.linkAcks.equals(channel)) {
            voiceMutes.onLinkAck(channel, envelope);
            return;
        }
        VelocityMain.get().getServer().getScheduler()
                .buildTask(VelocityMain.get(), () -> commands.onMessage(channel, envelope))
                .schedule();
    }

    public VoiceMuteCoordinator voiceMutes() {
        return voiceMutes;
    }

    public void shutdown() {
        bus.shutdown();
    }
}
