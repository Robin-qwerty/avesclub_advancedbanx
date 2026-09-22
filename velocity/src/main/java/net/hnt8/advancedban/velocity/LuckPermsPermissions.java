package net.hnt8.advancedban.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

/**
 * LuckPerms lookups that ignore the current Velocity {@code server=} context.
 * Staff often have {@code avesban.ban.temp} on a backend (survival) but not as a
 * proxy-wide node; Velocity then hides /tempban (red, no tab-complete) even though
 * they can still run it through BackendLink.
 */
final class LuckPermsPermissions {

    private static volatile Boolean present;

    private LuckPermsPermissions() {
    }

    static boolean hasIgnoringServerContext(CommandSource source, String permission) {
        if (permission == null || permission.isEmpty() || !(source instanceof Player)) {
            return false;
        }
        if (!available()) {
            return false;
        }
        try {
            User user = LuckPermsProvider.get().getUserManager().getUser(((Player) source).getUniqueId());
            if (user == null) {
                return false;
            }
            return user.getCachedData()
                    .getPermissionData(QueryOptions.nonContextual())
                    .checkPermission(permission)
                    .asBoolean();
        } catch (IllegalStateException ignored) {
            // LuckPerms API class is on the classpath but the plugin is not enabled.
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean available() {
        Boolean cached = present;
        if (cached != null) {
            return cached;
        }
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            present = Boolean.TRUE;
            return true;
        } catch (ClassNotFoundException ex) {
            present = Boolean.FALSE;
            return false;
        }
    }
}
