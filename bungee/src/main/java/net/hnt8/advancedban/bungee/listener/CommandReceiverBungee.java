package net.hnt8.advancedban.bungee.listener;

import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.bungee.BungeeMain;
import net.hnt8.advancedban.manager.CommandManager;
import net.hnt8.advancedban.utils.tabcompletion.TabCompleter;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.Collections;

public class CommandReceiverBungee extends Command implements TabExecutor {

    private final String permission;
    private final TabCompleter tabCompleter;

    /**
     * @param name         name of the command
     * @param permission   permission required to use the command. May be null
     * @param tabCompleter argument suggestions; may be null
     */
    public CommandReceiverBungee(String name, String permission, TabCompleter tabCompleter) {
        super(name, permission);
        this.permission = permission;
        this.tabCompleter = tabCompleter;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return permission == null || Universal.get().hasPerms(sender, permission);
    }

    @Override
    public void execute(final CommandSender sender, final String[] args) {
        if (args.length > 0) {
            args[0] = (BungeeMain.get().getProxy().getPlayer(args[0]) != null
                    ? BungeeMain.get().getProxy().getPlayer(args[0]).getName()
                    : args[0]);
        }
        CommandManager.get().onCommand(sender, this.getName(), args);
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (!hasPermission(sender) || tabCompleter == null) {
            return Collections.emptyList();
        }
        return tabCompleter.onTabComplete(sender, args);
    }
}
