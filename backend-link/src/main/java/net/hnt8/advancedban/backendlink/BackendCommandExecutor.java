package net.hnt8.advancedban.backendlink;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

public class BackendCommandExecutor implements CommandExecutor {

    private static final String CURRENT_PERM_PREFIX = "avesban.";
    private static final String LEGACY_PERM_PREFIX = "ab.";

    // Mirrors the permission each command used to declare in plugin.yml. Checked here
    // in Java (instead of via plugin.yml's own "permission:" field) so both the new
    // "avesban." nodes and permissions granted before the rename ("ab.") keep working -
    // Bukkit's manifest-based permission check can only ever match one literal string.
    private static final Map<String, String> PERMISSIONS = new HashMap<>();
    static {
        PERMISSIONS.put("ban", "ban.perma");
        PERMISSIONS.put("tempban", "ban.temp");
        PERMISSIONS.put("punish", "ban.temp");
        PERMISSIONS.put("banip", "ipban.perma");
        PERMISSIONS.put("tempipban", "ipban.temp");
        PERMISSIONS.put("mute", "mute.perma");
        PERMISSIONS.put("tempmute", "mute.temp");
        PERMISSIONS.put("warn", "warn.perma");
        PERMISSIONS.put("tempwarn", "warn.temp");
        PERMISSIONS.put("kick", "kick.use");
        PERMISSIONS.put("unban", "ban.undo");
        PERMISSIONS.put("unmute", "mute.undo");
    }

    private final String commandName;

    public BackendCommandExecutor(String commandName) {
        this.commandName = commandName;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String permissionSuffix = PERMISSIONS.get(commandName);
        if (permissionSuffix != null && !hasPermission(sender, permissionSuffix)) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        // Forward the command to the proxy
        BackendLinkMain.getInstance().sendCommandToProxy(commandName, args);

        // Don't send any response - the proxy will handle that
        // This prevents duplicate messages
        return true;
    }

    private boolean hasPermission(CommandSender sender, String permissionSuffix) {
        return sender.hasPermission(CURRENT_PERM_PREFIX + permissionSuffix)
                || sender.hasPermission(LEGACY_PERM_PREFIX + permissionSuffix);
    }
}
