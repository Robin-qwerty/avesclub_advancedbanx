package net.hnt8.advancedban.backendlink;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Folia forbids legacy {@link org.bukkit.scheduler.BukkitScheduler} for many operations.
 * Uses Paper/Folia threaded schedulers via reflection when available; falls back to Bukkit scheduler on Spigot.
 */
final class BackendLinkScheduler {

    private BackendLinkScheduler() {
    }

    static void scheduleRepeating(JavaPlugin plugin, Runnable runnable, long initialDelayTicks, long periodTicks) {
        Object globalScheduler = resolveGlobalRegionScheduler();
        if (globalScheduler != null && scheduleRepeatingOnGlobal(plugin, runnable, globalScheduler, initialDelayTicks, periodTicks)) {
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, runnable, initialDelayTicks, periodTicks);
    }

    /** Run on the player's owning region (Folia) or main thread (Spigot). Safe to call from async when using Folia entity scheduler. */
    static void runOnPlayerRegion(JavaPlugin plugin, Player player, Runnable runnable, long delayTicks) {
        if (player == null) {
            return;
        }
        try {
            Method getScheduler = player.getClass().getMethod("getScheduler");
            Object entityScheduler = getScheduler.invoke(player);
            Consumer<Object> task = st -> runnable.run();
            if (delayTicks <= 0) {
                Method run = entityScheduler.getClass().getMethod("run",
                        org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class);
                run.invoke(entityScheduler, plugin, task, null);
            } else {
                Method runDelayed = entityScheduler.getClass().getMethod("runDelayed",
                        org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class, long.class);
                runDelayed.invoke(entityScheduler, plugin, task, null, delayTicks);
            }
            return;
        } catch (ReflectiveOperationException ignored) {
            // Not Paper/Folia or API mismatch
        }
        if (delayTicks <= 0) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    private static Object resolveGlobalRegionScheduler() {
        try {
            Method m = Bukkit.class.getMethod("getGlobalRegionScheduler");
            return m.invoke(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean scheduleRepeatingOnGlobal(JavaPlugin plugin, Runnable runnable, Object globalScheduler,
                                                     long initialDelayTicks, long periodTicks) {
        try {
            Method runAtFixedRate = globalScheduler.getClass().getMethod("runAtFixedRate",
                    org.bukkit.plugin.Plugin.class, Consumer.class, long.class, long.class);
            Consumer<Object> task = st -> runnable.run();
            runAtFixedRate.invoke(globalScheduler, plugin, task, initialDelayTicks, periodTicks);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
