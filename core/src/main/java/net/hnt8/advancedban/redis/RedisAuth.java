package net.hnt8.advancedban.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class RedisAuth {

    private RedisAuth() {
    }

    /**
     * @return error message, or null if the envelope is accepted
     */
    public static String validate(RedisEnvelope envelope, RedisSettings settings) {
        if (envelope == null) {
            return "empty message";
        }
        if (envelope.id == null || envelope.id.isEmpty()) {
            return "missing id";
        }
        if (envelope.type == null || envelope.type.isEmpty()) {
            return "missing type";
        }
        if (!settings.hasUsableToken() || envelope.token == null
                || !constantEquals(settings.authToken, envelope.token)) {
            return "invalid token";
        }
        long now = System.currentTimeMillis();
        if (envelope.ts <= 0L || Math.abs(now - envelope.ts) > settings.maxAgeMs) {
            return "stale timestamp";
        }
        return null;
    }

    private static boolean constantEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
