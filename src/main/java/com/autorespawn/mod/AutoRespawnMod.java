package com.autorespawn.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AutoRespawnMod implements ClientModInitializer {

    public static final String MOD_ID = "autorespawn";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // 500 unique random strings
    private final List<String> randomMessages = new ArrayList<>();

    // State machine
    private enum State {
        IDLE,
        WAITING_TO_RESPAWN,
        WAITING_FOR_TPAUTO,
        WAITING_FOR_HOME,
        WAITING_BEFORE_SPAM,
        SPAMMING
    }

    private State state = State.IDLE;
    private long stateStartTick = 0L;
    private int lastMessageIndex = -1;
    private boolean wasDead = false;

    // Tick constants (20 ticks = 1 second)
    private static final long TICKS_RESPAWN_DELAY    = 10L;  // 0.5s
    private static final long TICKS_TPAUTO_DELAY     = 20L;  // 1s after respawn
    private static final long TICKS_HOME_DELAY       = 20L;  // 1s after /tpauto
    private static final long TICKS_SPAM_START_DELAY = 140L; // 7s after /home
    private static final long TICKS_SPAM_INTERVAL    = 80L;  // 4s between messages

    @Override
    public void onInitializeClient() {
        generateMessages();
        LOGGER.info("[AutoRespawn] Initialized with {} messages.", randomMessages.size());

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // Reset state on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            state = State.IDLE;
            wasDead = false;
            LOGGER.info("[AutoRespawn] Disconnected – state reset.");
        });
    }

    private void generateMessages() {
        Random rng = new Random(12345L);
        Set<String> seen = new LinkedHashSet<>();
        String alphabet = "abcdefghijklmnopqrstuvwxyz";

        while (seen.size() < 500) {
            int len = 5 + rng.nextInt(6); // 5–10
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                sb.append(alphabet.charAt(rng.nextInt(26)));
            }
            seen.add(sb.toString());
        }
        randomMessages.addAll(seen);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        boolean isDead = player.isDead() || player.getHealth() <= 0f;

        // Detect fresh death transition
        if (isDead && !wasDead) {
            wasDead = true;
            LOGGER.info("[AutoRespawn] Player died – starting respawn cycle.");
            transitionTo(State.WAITING_TO_RESPAWN, client);
        }

        if (!isDead && wasDead && state == State.IDLE) {
            // Already handled; do nothing
        }

        long now = client.world.getTime();
        long elapsed = now - stateStartTick;

        switch (state) {
            case WAITING_TO_RESPAWN -> {
                if (elapsed >= TICKS_RESPAWN_DELAY) {
                    performRespawn(client);
                    transitionTo(State.WAITING_FOR_TPAUTO, client);
                }
            }
            case WAITING_FOR_TPAUTO -> {
                if (elapsed >= TICKS_TPAUTO_DELAY) {
                    sendCommand(client, "/tpauto");
                    transitionTo(State.WAITING_FOR_HOME, client);
                }
            }
            case WAITING_FOR_HOME -> {
                if (elapsed >= TICKS_HOME_DELAY) {
                    sendCommand(client, "/home 1");
                    transitionTo(State.WAITING_BEFORE_SPAM, client);
                }
            }
            case WAITING_BEFORE_SPAM -> {
                if (elapsed >= TICKS_SPAM_START_DELAY) {
                    wasDead = false; // ready to detect next death
                    transitionTo(State.SPAMMING, client);
                }
            }
            case SPAMMING -> {
                // If player dies again, the isDead check above fires and resets
                if (elapsed >= TICKS_SPAM_INTERVAL) {
                    sendRandomMessage(client);
                    stateStartTick = now; // reset interval
                }
            }
            default -> {}
        }
    }

    private void transitionTo(State newState, MinecraftClient client) {
        state = newState;
        if (client.world != null) {
            stateStartTick = client.world.getTime();
        }
        LOGGER.info("[AutoRespawn] → {}", newState);
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
            // Strip leading slash for sendCommand
            if (command.startsWith("/")) {
                client.player.networkHandler.sendCommand(command.substring(1));
            } else {
                client.player.networkHandler.sendCommand(command);
            }
            LOGGER.info("[AutoRespawn] Sent command: {}", command);
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
