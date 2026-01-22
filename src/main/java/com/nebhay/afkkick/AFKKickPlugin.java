package com.nebhay.afkkick;


import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A plugin that kicks players who have been inactive (not moved or interacted).
 */
public class AFKKickPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AFKKickConfig config;
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AFKKickPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
      //  LOGGER.atInfo().log("Setting up AFKKickPlugin version " + this.getManifest().getVersion());
        
        loadConfig();
        
        EventRegistry registry = this.getEventRegistry();
        
        // Register events using the EventRegistry
        registry.registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        registry.registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        registry.registerGlobal(PlayerChatEvent.class, this::onPlayerChat);
        registry.registerGlobal(PlayerMouseButtonEvent.class, this::onPlayerClick);

        // Check for AFK players using configured interval
        scheduler.scheduleAtFixedRate(this::checkAFKPlayers, config.getCheckIntervalMs(), config.getCheckIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void loadConfig() {
        Path modsFolder = Paths.get("mods");
        Path configFolder = modsFolder.resolve("AFKKICK");
        Path configFile = configFolder.resolve("config.json");

        try {
            if (!Files.exists(configFolder)) {
                Files.createDirectories(configFolder);
            }

            if (Files.exists(configFile)) {
                try (Reader reader = Files.newBufferedReader(configFile)) {
                    config = GSON.fromJson(reader, AFKKickConfig.class);
                }
            }

            if (config == null) {
                config = new AFKKickConfig();
                try (Writer writer = Files.newBufferedWriter(configFile)) {
                    GSON.toJson(config, writer);
                }
            }
        } catch (IOException e) {
           // LOGGER.atError().log("Failed to load or create config: " + e.getMessage());
            config = new AFKKickConfig(); // Fallback to defaults
        }
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("Shutting down AFKKickPlugin");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        UUID uuid = playerRef.getUuid();
        playerDataMap.put(uuid, new PlayerData(playerRef.getTransform().getPosition().clone()));
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        playerDataMap.remove(event.getPlayerRef().getUuid());
    }

    private void onPlayerChat(PlayerChatEvent event) {
        // LOGGER.atInfo().log("DEBUG: Chat detected from " + event.getSender().getUuid());
        resetAFKTimer(event.getSender().getUuid());
    }

    private void onPlayerClick(PlayerMouseButtonEvent event) {
        // LOGGER.atInfo().log("DEBUG: Click detected from " + event.getPlayerRefComponent().getUuid());
        resetAFKTimer(event.getPlayerRefComponent().getUuid());
    }

    private void resetAFKTimer(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            data.lastActivityTime = System.currentTimeMillis();
            data.warned = false;
            data.preKickWarned = false;
        }
    }

    private void checkAFKPlayers() {
        long currentTime = System.currentTimeMillis();
        
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (!playerRef.isValid()) {
                continue;
            }

            UUID uuid = playerRef.getUuid();

            // Check for AFK bypass permission
            if (PermissionsModule.get().hasPermission(uuid, "afkkick.bypass")) {
                continue;
            }

            Vector3d currentPos = playerRef.getTransform().getPosition();
            PlayerData data = playerDataMap.computeIfAbsent(uuid, k -> new PlayerData(currentPos.clone()));

            if (data.kicked) {
                continue;
            }

            // Debug logging for position
            // LOGGER.atInfo().log(String.format("DEBUG: Checking player %s. Current Pos: %s, Last Pos: %s", 
            //    playerRef.getUsername(), currentPos.toString(), data.lastPosition.toString()));

            // Check if player moved - Using a small threshold to avoid floating point issues
            double distSq = currentPos.distanceSquaredTo(data.lastPosition);
            if (distSq > 0.01) { // If moved more than 0.1 blocks
                // LOGGER.atInfo().log(String.format("DEBUG: Player %s moved (distSq: %f). Resetting timer.", 
                //    playerRef.getUsername(), distSq));
                data.lastPosition = currentPos.clone();
                data.lastActivityTime = currentTime;
                data.warned = false;
                data.preKickWarned = false;
                continue;
            }

            long afkTime = currentTime - data.lastActivityTime;
            // LOGGER.atInfo().log(String.format("DEBUG: Player %s AFK Time: %d ms (Threshold: %d ms)", 
            //    playerRef.getUsername(), afkTime, config.getAfkThresholdMs()));

            // Check if player should be warned (configured time before kick)
            if (!data.warned && afkTime > (config.getAfkThresholdMs() - config.getWarningThresholdMs())) {
                playerRef.sendMessage(Message.raw(config.getWarningMessage()));
                data.warned = true;
                // LOGGER.atInfo().log("Sent AFK warning to " + playerRef.getUsername());
            }

            // Check if player should get the "you have been kicked" message (2 seconds before kick)
            if (!data.preKickWarned && afkTime > (config.getAfkThresholdMs() - config.getChatPreKickDelayMs())) {
                playerRef.sendMessage(Message.raw(config.getChatPreKickMessage()));
                data.preKickWarned = true;
                // LOGGER.atInfo().log("Sent pre-kick message to " + playerRef.getUsername());
            }

            // Check if player has been AFK for too long
            if (afkTime > config.getAfkThresholdMs()) {
                // LOGGER.atInfo().log("Kicking " + playerRef.getUsername() + " for being AFK.");
                data.kicked = true;
                playerRef.getPacketHandler().disconnect(config.getKickMessage());
            }
        }
    }

    private static class PlayerData {
        Vector3d lastPosition;
        long lastActivityTime;
        boolean warned = false;
        boolean preKickWarned = false;
        boolean kicked = false;

        PlayerData(Vector3d position) {
            this.lastPosition = position.clone();
            this.lastActivityTime = System.currentTimeMillis();
        }
    }
}
