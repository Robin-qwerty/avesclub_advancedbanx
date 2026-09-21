package net.hnt8.advancedban.backendlink;

import net.hnt8.advancedban.backendlink.luckperms.VoiceMuteLuckPerms;
import net.hnt8.advancedban.redis.RedisAuth;
import net.hnt8.advancedban.redis.RedisBus;
import net.hnt8.advancedban.redis.RedisEnvelope;
import net.hnt8.advancedban.redis.RedisSettings;

import java.util.logging.Logger;

/**
 * Handles VOICE_PERM RPCs addressed to this named BackendLink.
 */
final class BackendRedisListener {

    private final RedisSettings settings;
    private final RedisBus bus;
    private final VoiceMuteLuckPerms luckPerms;
    private final String linkName;
    private final Logger logger;

    BackendRedisListener(RedisSettings settings, RedisBus bus, VoiceMuteLuckPerms luckPerms,
                         String linkName, Logger logger) {
        this.settings = settings;
        this.bus = bus;
        this.luckPerms = luckPerms;
        this.linkName = linkName;
        this.logger = logger;
    }

    void onMessage(String channel, String message) {
        RedisEnvelope envelope;
        try {
            envelope = RedisEnvelope.parse(message);
        } catch (Exception ex) {
            logger.warning("Ignored invalid Redis JSON: " + ex.getMessage());
            return;
        }
        if (!RedisEnvelope.TYPE_VOICE_PERM.equals(envelope.type)) {
            return;
        }
        String error = RedisAuth.validate(envelope, settings);
        if (error != null) {
            logger.warning("Rejected voice-mute RPC: " + error);
            return;
        }

        String action = envelope.payloadString("action");
        String uuid = envelope.payloadString("uuid");
        String permission = envelope.payloadString("permission");
        if (permission == null || permission.isEmpty()) {
            permission = "voicechat.speak";
        }
        boolean set = RedisEnvelope.ACTION_SET.equalsIgnoreCase(action);
        boolean unset = RedisEnvelope.ACTION_UNSET.equalsIgnoreCase(action);
        if (!set && !unset) {
            ack(envelope, false, "unknown action");
            return;
        }
        if (!settings.voiceMuteEnabled) {
            ack(envelope, false, "voice mute disabled on this BackendLink");
            return;
        }

        boolean value = envelope.payloadBoolean("value", false);
        String applyError = luckPerms.apply(uuid, permission, set, value);
        if (applyError == null) {
            ack(envelope, true, "ok");
        } else {
            ack(envelope, false, applyError);
        }
    }

    private void ack(RedisEnvelope request, boolean ok, String message) {
        RedisEnvelope ack = RedisEnvelope.create(ok ? RedisEnvelope.TYPE_ACK : RedisEnvelope.TYPE_ERROR,
                settings.authToken, "link:" + linkName);
        ack.id = request.id;
        ack.ok = ok;
        ack.message = message;
        try {
            bus.publish(settings.linkAcks, ack.toJson());
        } catch (Exception ex) {
            logger.warning("Failed to publish voice-mute ACK: " + ex.getMessage());
        }
    }
}
