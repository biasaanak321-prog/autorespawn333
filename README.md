# AutoRespawn Fabric Mod

A client-side Fabric mod for Minecraft 1.21.1 that automatically:

1. Detects player death
2. Waits 0.5 seconds (so death screen is visible)
3. Auto-respawns the player
4. Sends `/tpauto` command (after 1 second)
5. Sends `/home 1` command (after another 1 second)
6. Waits 7 seconds
7. Begins spamming chat with random messages every 4 seconds
8. On next death, resets and repeats the full cycle

## Building via GitHub Actions

1. Push this repository to GitHub
2. Go to **Actions** tab → **Build Mod** → **Run workflow**
3. Download the JAR from the **Artifacts** section after the build completes

## Building Locally

Requirements: Java 21, internet connection (for Gradle/Fabric downloads)

```bash
chmod +x gradlew
./gradlew build
```

The compiled JAR will be at `build/libs/autorespawn-1.0.0.jar`.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Copy `autorespawn-1.0.0.jar` into your `.minecraft/mods/` folder
4. Launch Minecraft

## How It Works

The mod uses a simple state machine driven by `ClientTickEvents.END_CLIENT_TICK`:

| State | Duration | Action |
|---|---|---|
| IDLE | — | Watching for death |
| WAITING_TO_RESPAWN | 0.5s (10 ticks) | Sends respawn packet |
| WAITING_FOR_TPAUTO | 1s (20 ticks) | Sends `/tpauto` |
| WAITING_FOR_HOME | 1s (20 ticks) | Sends `/home 1` |
| WAITING_BEFORE_SPAM | 7s (140 ticks) | Waits |
| SPAMMING | Every 4s (80 ticks) | Sends random message |

On death during SPAMMING, the cycle resets cleanly from the beginning.

## Chat Messages

500 unique random strings (5–10 lowercase letters a–z) are generated at mod init using `java.util.Random` with seed `12345`. A `LinkedHashSet` ensures uniqueness.
