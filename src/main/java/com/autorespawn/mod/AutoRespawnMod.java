package com.autorespawn.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AutoRespawnMod implements ClientModInitializer {

    public static final String MOD_ID = "autorespawn";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final List<String> randomMessages = new ArrayList<>();

    private enum State {
        IDLE,
        WAITING_TO_RESPAWN,
        WAITING_FOR_TPAUTO,
        WAITING_FOR_HOME,
        WAITING_BEFORE_SPAM,
        SPAMMING
    }

    private State state = State.IDLE;
    private long stateStartMs = 0L;
    private int lastMessageIndex = -1;
    private boolean deathScreenSeen = false;
    private boolean enabled = false; // OFF by default, toggle with .starttp

    private static final long MS_RESPAWN_DELAY    = 500L;
    private static final long MS_TPAUTO_DELAY     = 1000L;
    private static final long MS_HOME_DELAY       = 1000L;
    private static final long MS_SPAM_START_DELAY = 7000L;
    private static final long MS_SPAM_INTERVAL    = 4000L;

    @Override
    public void onInitializeClient() {
        generateMessages();
        LOGGER.info("[AutoRespawn] Initialized with {} messages.", randomMessages.size());

        // Intercept chat messages to catch .starttp command
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (message.equalsIgnoreCase(".starttp")) {
                enabled = !enabled;
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    if (enabled) {
                        client.player.sendMessage(
                            Text.literal("[AutoRespawn] ").formatted(Formatting.AQUA)
                                .append(Text.literal("Mod toggled ON").formatted(Formatting.GREEN)),
                            false
                        );
                    } else {
                        client.player.sendMessage(
                            Text.literal("[AutoRespawn] ").formatted(Formatting.AQUA)
                                .append(Text.literal("Mod toggled OFF").formatted(Formatting.RED)),
                            false
                        );
                        // Reset state when turning off
                        state = State.IDLE;
                        deathScreenSeen = false;
                    }
                }
                LOGGER.info("[AutoRespawn] Toggled: {}", enabled ? "ON" : "OFF");
                return false; // Block the message from being sent to server
            }
            return true; // Allow all other messages through normally
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            state = State.IDLE;
            deathScreenSeen = false;
            LOGGER.info("[AutoRespawn] Disconnected - state reset.");
        });
    }

    private void generateMessages() {
        Random rng = new Random(12345L);
        Set<String> seen = new LinkedHashSet<>();
        String alphabet = "abcdefghijklmnopqrstuvwxyz";
        while (seen.size() < 500) {
            int len = 5 + rng.nextInt(6);
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                sb.append(alphabet.charAt(rng.nextInt(26)));
            }
            seen.add(sb.toString());
        }
        randomMessages.addAll(seen);
    }

    private void onClientTick(MinecraftClient client) {
        if (!enabled) return;
        if (client.player == null) return;

        boolean onDeathScreen = client.currentScreen instanceof DeathScreen;

        if (onDeathScreen && !deathScreenSeen && state == State.IDLE) {
            deathScreenSeen = true;
            LOGGER.info("[AutoRespawn] Death screen detected - starting cycle.");
            transitionTo(State.WAITING_TO_RESPAWN);
        }

        long elapsed = System.currentTimeMillis() - stateStartMs;

        switch (state) {
            case WAITING_TO_RESPAWN -> {
                if (elapsed >= MS_RESPAWN_DELAY) {
                    performRespawn(client);
                    transitionTo(State.WAITING_FOR_TPAUTO);
                }
            }
            case WAITING_FOR_TPAUTO -> {
                if (elapsed >= MS_TPAUTO_DELAY) {
                    sendCommand(client, "tpauto");
                    transitionTo(State.WAITING_FOR_HOME);
                }
            }
            case WAITING_FOR_HOME -> {
                if (elapsed >= MS_HOME_DELAY) {
                    sendCommand(client, "home 1");
                    transitionTo(State.WAITING_BEFORE_SPAM);
                }
            }
            case WAITING_BEFORE_SPAM -> {
                if (elapsed >= MS_SPAM_START_DELAY) {
                    deathScreenSeen = false;
                    transitionTo(State.SPAMMING);
                }
            }
            case SPAMMING -> {
                if (elapsed >= MS_SPAM_INTERVAL) {
                    sendRandomMessage(client);
                    stateStartMs = System.currentTimeMillis();
                }
            }
            default -> {}
        }
    }

    private void transitionTo(State newState) {
        state = newState;
        stateStartMs = System.currentTimeMillis();
        LOGGER.info("[AutoRespawn] -> {}", newState);
    }

    private void performRespawn(MinecraftClient client) {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(
                new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN)
            );
            LOGGER.info("[AutoRespawn] Sent respawn packet.");
        }
    }

    private void sendCommand(MinecraftClient client, String command) {
        if (client.player != null) {
            client.player.networkHandler.sendCommand(command);
            LOGGER.info("[AutoRespawn] Sent command: /{}", command);
        }
    }

    private void sendRandomMessage(MinecraftClient client) {
        if (client.player == null || randomMessages.isEmpty()) return;
        int idx;
        do {
            idx = (int) (Math.random() * randomMessages.size());
        } while (idx == lastMessageIndex && randomMessages.size() > 1);
        lastMessageIndex = idx;
        String msg = randomMessages.get(idx);
        client.player.networkHandler.sendChatMessage(msg);
        LOGGER.info("[AutoRespawn] Sent chat: {}", msg);
    }
}
