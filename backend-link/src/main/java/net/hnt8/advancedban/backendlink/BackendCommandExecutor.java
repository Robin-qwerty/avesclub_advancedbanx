package net.hnt8.advancedban.backendlink;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BackendCommandExecutor implements CommandExecutor, TabCompleter {

    private static final String CURRENT_PERM_PREFIX = "avesban.";
    private static final String LEGACY_PERM_PREFIX = "ab.";

    private static final Set<String> TEMP_COMMANDS = new HashSet<String>(Arrays.asList(
            "tempban", "punish", "tempipban", "tempmute", "tempwarn"));

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
        BackendLinkMain.getInstance().sendCommandToProxy(commandName, args, sender.getName());

        // Don't send any response - the proxy will handle that
        // This prevents duplicate messages
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String permissionSuffix = PERMISSIONS.get(commandName);
        if (permissionSuffix != null && !hasPermission(sender, permissionSuffix)) {
            return Collections.emptyList();
        }
        return suggestions(args, TEMP_COMMANDS.contains(commandName.toLowerCase(Locale.ROOT)));
    }

    static List<String> suggestions(String[] args, boolean temporary) {
        List<String> suggestions = new ArrayList<String>();
        String[] effective = args;
        boolean hiddenTag = false;
        if (effective.length > 1 && "-s".equalsIgnoreCase(effective[0])) {
            effective = Arrays.copyOfRange(effective, 1, effective.length);
            hiddenTag = true;
        }

        if (effective.length <= 1) {
            if (!hiddenTag) {
                suggestions.add("-s");
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                suggestions.add(player.getName());
            }
        } else if (temporary && effective.length == 2) {
            String current = effective[1];
            String amount = current.isEmpty() ? "X" : current.replaceAll("[^0-9].*", "");
            if (amount.isEmpty()) {
                amount = "X";
            }
            if (amount.matches("\\d+|X")) {
                for (String unit : new String[]{"s", "m", "h", "d", "w", "mo"}) {
                    suggestions.add(amount + unit);
                }
            }
            suggestions.add("#");
        } else if ((temporary && effective.length == 3) || effective.length == 2) {
            suggestions.add("@");
        }

        if (effective.length > 0) {
            String prefix = effective[effective.length - 1].toLowerCase(Locale.ROOT);
            suggestions.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        }
        return suggestions;
    }

    private boolean hasPermission(CommandSender sender, String permissionSuffix) {
        return sender.hasPermission(CURRENT_PERM_PREFIX + permissionSuffix)
                || sender.hasPermission(LEGACY_PERM_PREFIX + permissionSuffix);
    }
}
