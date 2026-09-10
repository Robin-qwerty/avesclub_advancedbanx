package net.hnt8.advancedban.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.velocity.alts.AltsCommand;
import net.hnt8.advancedban.velocity.listener.BackendCommandListener;
import net.hnt8.advancedban.velocity.listener.ChatListenerVelocity;
import net.hnt8.advancedban.velocity.listener.ConnectionListenerVelocity;
import net.hnt8.advancedban.velocity.listener.ServerConnectListener;

import java.nio.file.Path;
import java.util.logging.Logger;

@Plugin(
        id = "avesban",
        name = "Avesban",
        version = "${project.version}",
        description = "Advanced punishment system for Velocity",
        authors = {"Leoko", "2vY", "Robinaves"}
)
public class VelocityMain {

    private static VelocityMain instance;

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public VelocityMain(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        instance = this;
    }

    public static VelocityMain get() {
        return instance;
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Universal.get().setup(new VelocityMethods());

        server.getEventManager().register(this, new ConnectionListenerVelocity());
        server.getEventManager().register(this, new ChatListenerVelocity());
        server.getEventManager().register(this, new BackendCommandListener(server));
        server.getEventManager().register(this, new ServerConnectListener());

        // Registered directly here (not via core's Command enum) so the alt-account
        // menu is fully proxy-side - no backend/Paper update is needed for it.
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("alts").aliases("altaccounts").build(),
                new AltsCommand());

        logger.info("Avesban has been enabled!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        Universal.get().shutdown();
        logger.info("Avesban has been disabled!");
    }
}

