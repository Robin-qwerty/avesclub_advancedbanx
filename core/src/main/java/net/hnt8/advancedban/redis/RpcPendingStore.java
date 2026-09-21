package net.hnt8.advancedban.redis;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Correlates Redis request ids with ACK replies so concurrent mutes cannot confirm each other.
 */
public final class RpcPendingStore {

    private final Map<String, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<String, CompletableFuture<Boolean>>();

    public CompletableFuture<Boolean> register(String id) {
        CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
        pending.put(id, future);
        return future;
    }

    public void complete(String id, boolean ok) {
        if (id == null) {
            return;
        }
        CompletableFuture<Boolean> future = pending.remove(id);
        if (future != null) {
            future.complete(ok);
        }
    }

    public boolean await(CompletableFuture<Boolean> future, long timeoutMs) {
        try {
            Boolean result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return result != null && result;
        } catch (TimeoutException timeout) {
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public void cancel(String id) {
        complete(id, false);
    }
}
