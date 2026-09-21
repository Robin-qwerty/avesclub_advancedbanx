package net.hnt8.advancedban.velocity.redis;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.hnt8.advancedban.velocity.VelocityMain;

/**
 * Console-like sender used when Laravel (or BackendLink) asks Velocity to run a punishment command.
 * The operator name is stored on the punishment row instead of CONSOLE.
 */
public final class WebCommandSource implements CommandSource {

    private final String operatorName;

    public WebCommandSource(String operator) {
        String name = operator == null ? "" : operator.trim();
        if (name.isEmpty()) {
            name = "CONSOLE";
        }
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        this.operatorName = name;
    }

    public String getOperatorName() {
        return operatorName;
    }

    @Override
    public Tristate getPermissionValue(String permission) {
        return Tristate.TRUE;
    }

    @Override
    public void sendMessage(Component component) {
        VelocityMain.get().getLogger().info("[web:" + operatorName + "] "
                + PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Override
    public void sendMessage(Identity identity, Component component) {
        sendMessage(component);
    }
}
