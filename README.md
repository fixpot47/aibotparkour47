# AIbotParkour47 🏃🤖

Experimental **client-side Fabric mod for Minecraft 26.2**. It tries to traverse procedurally generated 1×1 full-block parkour platforms by reading nearby client-visible blocks and using ordinary movement controls.

## What version 0.1.1 actually does

- **P** toggles the controller (Settings → Controls → AIbotParkour47).
- Finds full-sized platforms within a short distance, accounting for platforms higher/lower than the current one.
- Selects the nearest reachable-looking platform; excludes the previously landed platform.
- Aims at the landing block, runs forward, sprints, performs edge jumps and makes *basic* midair corrections.
- Shows a local landing counter in the action bar (goal: 200).
- Can approach a nearby **stone pressure plate** to start/restart after a teleport.
- Runs entirely on the client. No server commands, packet manipulation or anti-cheat bypass.

> **Important:** This is a proof of concept, **not** a trained neural network and **not** a guaranteed 200-block solver. The reach estimates and timing are preliminary; true reliability requires real-game tests and more accurate physics simulation. The landing counter is local, not the server leaderboard.

## Install

1. Use Minecraft Java **26.2** with Fabric Loader **0.19.3+** and Java **25**.
2. Install **Fabric API** for Minecraft 26.2 (for example 0.156.0+26.2).
3. Go to **Actions → Build AIbotParkour47** and open a *successful* workflow run.
4. Download artifact `AIbotParkour47-0.1.1-Minecraft-26.2`, unzip it and move the **non-sources** `.jar` into `.minecraft/mods`.
5. Load a test parkour and press **P** near the stone pressure plate or while standing on an isolated parkour platform. Press **P** again to stop instantly.

## Testing / known limits

- First test in an isolated local world with 1×1 blocks, including 1- and 2-block gaps.
- The bot may fail at diagonal jumps, jumps to higher platforms, network lag, or when the next platform spawns late.
- If the pressure plate isn't stone or isn't within seven blocks, step on it manually.
- A successful CI build proves compilation, not 200 consecutive successful jumps.
- Use automation on multiplayer servers only with the owner's permission.

## Developers

Built with Gradle 9.5.1, Java 25 and Fabric Loom 1.17. Run `gradle build` to compile from source. The CI workflow uploads the JAR as an artifact.
