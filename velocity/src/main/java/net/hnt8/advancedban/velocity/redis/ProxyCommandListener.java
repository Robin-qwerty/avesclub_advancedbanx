package net.hnt8.advancedban.velocity.redis;

import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.redis.RedisAuth;
import net.hnt8.advancedban.redis.RedisBus;
import net.hnt8.advancedban.redis.RedisEnvelope;
import net.hnt8.advancedban.redis.RedisSettings;
import net.hnt8.advancedban.utils.Command;
import net.hnt8.advancedban.velocity.VelocityMain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Handles {@code EXECUTE_COMMAND} from Laravel and BackendLink on {@code avesban:proxy:in}.
 */
public final class ProxyCommandListener {

    private static final Set<String> ALLOWED = new HashSet<String>(Arrays.asList(
            "ban", "tempban", "punish", "banip", "ban-ip", "ipban", "tempipban",
            "mute", "tempmute", "warn", "tempwarn", "kick", "unban", "unmute"
    ));

    private final RedisSettings settings;
    private final RedisBus bus;

    public ProxyCommandListener(RedisSettings settings, RedisBus bus) {
        this.settings = settings;
        this.bus = bus;
    }

    public void onMessage(String channel, RedisEnvelope envelope) {
        if (!settings.proxyIn.equals(channel)) {
            return;
        }
        if (!RedisEnvelope.TYPE_EXECUTE_COMMAND.equals(envelope.type)) {
            return;
        }

        String error = RedisAuth.validate(envelope, settings);
        if (error != null) {
            reply(envelope, false, error);
            return;
        }

        String commandName = envelope.command == null ? "" : envelope.command.trim().toLowerCase();
        if (!ALLOWED.contains(commandName)) {
            reply(envelope, false, "command not allowed");
            return;
        }
        Command command = Command.getByName(commandName);
        if (command == null) {
            reply(envelope, false, "unknown command");
            return;
        }

        String[] args = envelope.args.toArray(new String[0]);
        if (!command.validateArguments(args)) {
            reply(envelope, false, "invalid arguments");
            return;
        }

        String serverName = envelope.payloadString("server");
        if (serverName != null && !serverName.isEmpty()) {
            Universal.setCurrentServerName(serverName);
        }
        try {
            WebCommandSource source = new WebCommandSource(envelope.operator);
            command.execute(source, args);
            reply(envelope, true, "ok");
        } catch (Exception ex) {
            VelocityMain.get().getLogger().log(Level.WARNING, "Redis command " + commandName + " failed", ex);
            reply(envelope, false, "command failed");
        } finally {
            Universal.clearCurrentServerName();
        }
    }

    private void reply(RedisEnvelope request, boolean ok, String message) {
        RedisEnvelope ack = RedisEnvelope.create(ok ? RedisEnvelope.TYPE_ACK : RedisEnvelope.TYPE_ERROR,
                settings.authToken, "velocity");
        ack.id = request.id;
        ack.ok = ok;
        ack.message = message;
        String json = ack.toJson();
        try {
            bus.publish(settings.proxyOut, json);
            if (settings.ackKeyPrefix != null && !settings.ackKeyPrefix.isEmpty() && request.id != null) {
                bus.setEx(settings.ackKeyPrefix + request.id, json, 60);
            }
        } catch (Exception ex) {
            VelocityMain.get().getLogger().warning("Failed to publish Redis ACK: " + ex.getMessage());
        }
    }
}
