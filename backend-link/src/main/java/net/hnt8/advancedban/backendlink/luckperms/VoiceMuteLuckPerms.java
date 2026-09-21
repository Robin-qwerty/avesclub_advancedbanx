package net.hnt8.advancedban.backendlink.luckperms;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Applies voice-chat mute via the LuckPerms plugin API. Does not open LuckPerms SQL.
 */
public final class VoiceMuteLuckPerms {

    private final Logger logger;

    public VoiceMuteLuckPerms(Logger logger) {
        this.logger = logger;
    }

    /**
     * @return null on success, otherwise an error message
     */
    public String apply(String uuidRaw, String permission, boolean set, boolean value) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return "LuckPerms is not installed";
        }
        final LuckPerms api;
        try {
            api = LuckPermsProvider.get();
        } catch (IllegalStateException ex) {
            return "LuckPerms API is not loaded";
        }
        UUID uuid;
        try {
            uuid = parseUuid(uuidRaw);
        } catch (IllegalArgumentException ex) {
            return "invalid uuid";
        }
        if (permission == null || permission.isEmpty()) {
            return "missing permission";
        }

        try {
            User user = api.getUserManager().loadUser(uuid).join();
            if (user == null) {
                return "could not load LuckPerms user";
            }
            List<Node> snapshot = new ArrayList<Node>(user.getNodes());
            for (Node node : snapshot) {
                if (permission.equalsIgnoreCase(node.getKey())) {
                    user.data().remove(node);
                }
            }
            if (set) {
                user.data().add(PermissionNode.builder(permission).value(value).build());
            }
            api.getUserManager().saveUser(user).join();
            api.getMessagingService().ifPresent(service -> service.pushUserUpdate(user));
            logger.info((set ? "Set" : "Removed") + " LuckPerms permission " + permission
                    + "=" + (set ? value : "unset") + " for " + uuid);
            return null;
        } catch (Exception ex) {
            logger.warning("LuckPerms voice-mute failed for " + uuidRaw + ": " + ex.getMessage());
            return "LuckPerms update failed";
        }
    }

    static UUID parseUuid(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("uuid");
        }
        String value = raw.trim();
        if (value.indexOf('-') >= 0) {
            return UUID.fromString(value);
        }
        if (value.length() != 32) {
            throw new IllegalArgumentException("uuid");
        }
        return UUID.fromString(value.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
    }
}
