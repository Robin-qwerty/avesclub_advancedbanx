package net.hnt8.advancedban.velocity.redis;

import com.google.gson.JsonObject;
import net.hnt8.advancedban.manager.PunishmentManager;
import net.hnt8.advancedban.redis.RedisAuth;
import net.hnt8.advancedban.redis.RedisBus;
import net.hnt8.advancedban.redis.RedisEnvelope;
import net.hnt8.advancedban.redis.RedisSettings;
import net.hnt8.advancedban.redis.RpcPendingStore;
import net.hnt8.advancedban.utils.Punishment;
import net.hnt8.advancedban.utils.PunishmentType;
import net.hnt8.advancedban.velocity.VelocityMain;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Sends LuckPerms voice-mute RPCs to a named BackendLink (primary, then secondary).
 */
public final class VoiceMuteCoordinator {

    private final RedisSettings settings;
    private final RedisBus bus;
    private final RpcPendingStore pending;

    public VoiceMuteCoordinator(RedisSettings settings, RedisBus bus, RpcPendingStore pending) {
        this.settings = settings;
        this.bus = bus;
        this.pending = pending;
    }

    public void onLinkAck(String channel, RedisEnvelope envelope) {
        if (!settings.linkAcks.equals(channel)) {
            return;
        }
        if (!RedisEnvelope.TYPE_ACK.equals(envelope.type) && !RedisEnvelope.TYPE_ERROR.equals(envelope.type)) {
            return;
        }
        if (RedisAuth.validate(envelope, settings) != null) {
            return;
        }
        boolean ok = envelope.ok != null ? envelope.ok : RedisEnvelope.TYPE_ACK.equals(envelope.type);
        pending.complete(envelope.id, ok);
    }

    public void onPunishmentCreated(Punishment punishment) {
        if (!shouldHandle(punishment)) {
            return;
        }
        requestVoicePerm(punishment, RedisEnvelope.ACTION_SET);
    }

    public void onPunishmentRevoked(Punishment punishment) {
        if (!shouldHandle(punishment)) {
            return;
        }
        if (PunishmentManager.get().getMute(punishment.getUuid()) != null) {
            return;
        }
        requestVoicePerm(punishment, RedisEnvelope.ACTION_UNSET);
    }

    private boolean shouldHandle(Punishment punishment) {
        if (!settings.voiceMuteEnabled || punishment == null) {
            return false;
        }
        return punishment.getType().getBasic() == PunishmentType.MUTE;
    }

    private void requestVoicePerm(Punishment punishment, String action) {
        if (!bus.isRunning()) {
            return;
        }
        String uuid = punishment.getUuid();
        if (uuid == null || looksLikeIp(uuid)) {
            return;
        }

        RedisEnvelope envelope = buildRequest(punishment, action);
        CompletableFuture<Boolean> future = pending.register(envelope.id);

        String primary = settings.primaryLink;
        if (primary == null || primary.isEmpty()) {
            pending.cancel(envelope.id);
            VelocityMain.get().getLogger().warning("Voice mute skipped: BackendLinks.Primary is empty.");
            return;
        }

        publishToLink(primary, envelope);
        if (pending.await(future, settings.failoverTimeoutMs)) {
            return;
        }

        String secondary = settings.secondaryLink;
        if (secondary == null || secondary.isEmpty() || secondary.equalsIgnoreCase(primary)) {
            pending.cancel(envelope.id);
            VelocityMain.get().getLogger().warning("Voice mute " + action + " for " + punishment.getName()
                    + " was not acknowledged by BackendLink '" + primary + "'.");
            return;
        }

        publishToLink(secondary, envelope);
        if (!pending.await(future, settings.failoverTimeoutMs)) {
            pending.cancel(envelope.id);
            VelocityMain.get().getLogger().warning("Voice mute " + action + " for " + punishment.getName()
                    + " was not acknowledged by primary '" + primary + "' or secondary '" + secondary + "'.");
        }
    }

    private RedisEnvelope buildRequest(Punishment punishment, String action) {
        RedisEnvelope envelope = RedisEnvelope.create(RedisEnvelope.TYPE_VOICE_PERM, settings.authToken, "velocity");
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", punishment.getUuid());
        payload.addProperty("name", punishment.getName());
        payload.addProperty("action", action);
        payload.addProperty("permission", settings.voicePermission);
        payload.addProperty("value", settings.voiceValue);
        envelope.payload = payload;
        return envelope;
    }

    private void publishToLink(String linkName, RedisEnvelope envelope) {
        Logger logger = VelocityMain.get().getLogger();
        try {
            bus.publish(settings.linkChannel(linkName), envelope.toJson());
            logger.info("Sent voice-mute " + envelope.payloadString("action")
                    + " for " + envelope.payloadString("name") + " to BackendLink '" + linkName + "' (id=" + envelope.id + ")");
        } catch (Exception ex) {
            logger.warning("Failed to publish voice-mute to '" + linkName + "': " + ex.getMessage());
        }
    }

    private static boolean looksLikeIp(String value) {
        return (value.indexOf('.') >= 0 && value.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$"))
                || value.indexOf(':') >= 0;
    }
}
