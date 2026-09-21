package net.hnt8.advancedban.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jedis pool + blocking subscriber thread. Publish and subscribe use separate connections.
 */
public final class RedisBus {

    public interface MessageHandler {
        void onMessage(String channel, String message);
    }

    private final RedisSettings settings;
    private final Logger logger;
    private final Object publishLock = new Object();

    private volatile JedisPool pool;
    private volatile Jedis subscriber;
    private volatile JedisPubSub pubSub;
    private volatile Thread subscriberThread;
    private volatile boolean running;

    public RedisBus(RedisSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    public synchronized void start(final MessageHandler handler, String... channels) {
        if (running) {
            return;
        }
        if (channels == null || channels.length == 0) {
            throw new IllegalArgumentException("At least one Redis channel is required");
        }
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(4);
        poolConfig.setMinIdle(0);
        poolConfig.setTestOnBorrow(true);

        pool = new JedisPool(poolConfig, settings.host, settings.port, 2000, settings.password, settings.database);
        running = true;

        final String[] subscribeChannels = channels;
        subscriberThread = new Thread(() -> {
            while (running) {
                JedisPubSub localPubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            handler.onMessage(channel, message);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Failed to handle Redis message on " + channel, ex);
                        }
                    }
                };
                pubSub = localPubSub;
                try {
                    subscriber = new Jedis(settings.host, settings.port, 2000);
                    if (settings.password != null) {
                        subscriber.auth(settings.password);
                    }
                    if (settings.database > 0) {
                        subscriber.select(settings.database);
                    }
                    logger.info("Redis subscribed to " + String.join(", ", subscribeChannels));
                    subscriber.subscribe(localPubSub, subscribeChannels);
                } catch (JedisException ex) {
                    if (running) {
                        logger.warning("Redis subscriber disconnected: " + ex.getMessage());
                        sleepQuietly(2000L);
                    }
                } catch (Exception ex) {
                    if (running) {
                        logger.log(Level.WARNING, "Redis subscriber failed", ex);
                        sleepQuietly(2000L);
                    }
                } finally {
                    closeQuietly(subscriber);
                    subscriber = null;
                }
            }
        }, "Avesban-Redis-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void publish(String channel, String message) {
        if (!running || pool == null) {
            throw new IllegalStateException("Redis is not connected");
        }
        synchronized (publishLock) {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish(channel, message);
            }
        }
    }

    public void setEx(String key, String value, long ttlSeconds) {
        if (!running || pool == null) {
            return;
        }
        synchronized (publishLock) {
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(key, ttlSeconds, value);
            }
        }
    }

    public synchronized void shutdown() {
        running = false;
        JedisPubSub active = pubSub;
        if (active != null && active.isSubscribed()) {
            try {
                active.unsubscribe();
            } catch (Exception ignored) {
            }
        }
        closeQuietly(subscriber);
        subscriber = null;
        if (subscriberThread != null) {
            subscriberThread.interrupt();
            subscriberThread = null;
        }
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception ignored) {
            }
            pool = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    private static void closeQuietly(Jedis jedis) {
        if (jedis == null) {
            return;
        }
        try {
            jedis.close();
        } catch (Exception ignored) {
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
