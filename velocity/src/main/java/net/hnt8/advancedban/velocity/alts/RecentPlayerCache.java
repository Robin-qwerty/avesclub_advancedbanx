package net.hnt8.advancedban.velocity.alts;

import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.manager.PlayerManager;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Background-refreshed cache of recently active player names, used to fill out
 * {@code /alts} tab-completion beyond just who's currently online.
 * <p>
 * Deliberately NOT "every player ever in the database" - on a server with years of
 * history that list only grows and stops being useful for autocomplete (nobody needs
 * to tab-complete someone who joined once in 2019). It's also never queried directly
 * from {@code suggest()}, which Velocity can call on every keystroke - only this
 * periodic background task touches the database; lookups always read the in-memory
 * cache instantly.
 */
public final class RecentPlayerCache {

    private static final int LIMIT = 200;
    private static final long REFRESH_PERIOD_TICKS = 20L * 60; // 60 seconds

    private static final AtomicReference<List<String>> cache = new AtomicReference<>(Collections.emptyList());
    private static volatile boolean started;

    private RecentPlayerCache() {
    }

    /**
     * Starts the periodic background refresh. Safe to call more than once - only the
     * first call schedules the task.
     */
    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Universal.get().getMethods().scheduleAsyncRep(RecentPlayerCache::refresh, 0L, REFRESH_PERIOD_TICKS);
    }

    public static List<String> get() {
        return cache.get();
    }

    private static void refresh() {
        cache.set(PlayerManager.get().findRecentPlayerNames(LIMIT));
    }
}
