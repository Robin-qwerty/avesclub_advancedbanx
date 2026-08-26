package net.hnt8.advancedban.velocity;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bedrock (Geyser/Floodgate) cannot reliably show MiniMessage or § colors on the disconnect screen.
 */
public final class BedrockCompat {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static volatile boolean floodgateChecked;
    private static Method floodgateInstance;
    private static Method floodgateIsPlayer;

    private BedrockCompat() {
    }

    public static boolean isBedrock(Player player) {
        if (player == null) {
            return false;
        }
        if (isFloodgatePlayer(player.getUniqueId())) {
            return true;
        }
        String username = player.getUsername();
        if (username != null && username.startsWith(".")) {
            return true;
        }
        try {
            return player.getRemoteAddress() != null && player.getRemoteAddress().getPort() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Component disconnectReason(Player player, String miniMessage) {
        if (isBedrock(player)) {
            return Component.text(toPlainText(miniMessage));
        }
        return MINI_MESSAGE.deserialize(miniMessage.replace('§', '&'));
    }

    public static String toPlainText(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return "";
        }
        try {
            Component parsed = MINI_MESSAGE.deserialize(miniMessage.replace('§', '&'));
            return PLAIN.serialize(parsed);
        } catch (Exception ignored) {
            return miniMessage
                    .replaceAll("<[^>]*>", "")
                    .replaceAll("§.", "")
                    .replaceAll("&[0-9a-fk-orA-FK-OR]", "");
        }
    }

    private static boolean isFloodgatePlayer(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        resolveFloodgate();
        if (floodgateInstance == null || floodgateIsPlayer == null) {
            return false;
        }
        try {
            Object api = floodgateInstance.invoke(null);
            if (api == null) {
                return false;
            }
            Object result = floodgateIsPlayer.invoke(api, uuid);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void resolveFloodgate() {
        if (floodgateChecked) {
            return;
        }
        synchronized (BedrockCompat.class) {
            if (floodgateChecked) {
                return;
            }
            floodgateChecked = true;
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateInstance = apiClass.getMethod("getInstance");
                floodgateIsPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            } catch (ReflectiveOperationException ignored) {
                floodgateInstance = null;
                floodgateIsPlayer = null;
            }
        }
    }
}
