package com.fixpot47.parkour;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Lightweight deterministic parkour prototype. Uses only normal movement
 * key mappings and world blocks received by the Minecraft client.
 */
final class ParkourController {
    private enum State { SEEK_START, RUNNING, FALLEN }

    private boolean enabled;
    private State state = State.SEEK_START;
    private ClientLevel knownWorld;

    private final Set<BlockPos> visited = new HashSet<>();
    private BlockPos current, previous, target;
    private Vec3 lastPosition;
    private int blocks, best, attempts, ticks, idleTicks;

    void toggle(Minecraft mc) {
        enabled = !enabled;
        release(mc);
        if (enabled) {
            best = attempts = 0;
            clearAttempt();
            notify(mc, "§aParkourBot47 ON §7— ищу начало паркура");
        } else {
            clearAttempt();
            notify(mc, "§cParkourBot47 OFF");
        }
    }

    /** A new generated course must not inherit visited blocks or movement goals. */
    private void clearAttempt() {
        state = State.SEEK_START;
        current = previous = target = null;
        visited.clear();
        blocks = idleTicks = 0;
    }

    /** Never steer or jump while falling. Wait for the server to return us. */
    private void failed(Minecraft mc, String reason) {
        if (state == State.FALLEN) return;
        best = Math.max(best, blocks);
        state = State.FALLEN;
        target = null;
        release(mc);
        notify(mc, "§cПадение: " + reason + " §7| блоков: " + blocks +
                " §7| лучший: " + best + " §7| жду возвращения на спавн");
    }

    private void begin(Minecraft mc, BlockPos block) {
        state = State.RUNNING;
        attempts++;
        current = block;
        previous = null;
        target = null;
        visited.clear();
        visited.add(block);
        blocks = 1;
        idleTicks = 0;
        best = Math.max(best, blocks);
        notify(mc, "§aПопытка #" + attempts + " §7| первая платформа найдена");
    }

    void tick(Minecraft mc) {
        if (!enabled) return;
        if (mc.player == null || mc.level == null) {
            release(mc);
            knownWorld = null;
            lastPosition = null;
            clearAttempt();
            return;
        }
        if (knownWorld != mc.level) {
            knownWorld = mc.level;
            lastPosition = null;
            clearAttempt();
        }

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        Vec3 position = player.position();
        ticks++;

        if (mc.gui.screen() != null || player.isSpectator()) {
            release(mc);
            lastPosition = position;
            return;
        }

        // Handle both the spawn -> course teleport and the fall -> spawn teleport.
        // In particular: never preserve a target or visited history across teleports.
        boolean teleported = lastPosition != null
                && lastPosition.distanceToSqr(position) > 36.0; // >6 blocks/tick
        lastPosition = position;
        if (teleported) {
            boolean returning = state == State.FALLEN || state == State.RUNNING;
            release(mc);
            clearAttempt();
            if (returning) notify(mc, "§eТелепортация обнаружена §7— новая попытка");
        }

        BlockPos under = player.onGround()
                ? PlatformScanner.standingBlock(level, player.getX(), player.getY(), player.getZ())
                : null;
        BlockPos plate = PlatformScanner.nearestPlate(level, player.blockPosition());

        if (state == State.FALLEN) {
            release(mc);
            // Some servers teleport a player only a few blocks after falling.
            // In that case a grounded player beside the stone plate is also a reset.
            if (player.onGround() && plate != null) {
                clearAttempt();
                notify(mc, "§eВернулся к плите §7— начинаю новую попытку");
            } else {
                if (ticks % 35 == 0) {
                    notify(mc, "§cПадение §7— жду телепорта на спавн");
                }
                return;
            }
        }

        if (state == State.SEEK_START) {
            if (plate != null) {
                double x = plate.getX() + 0.5;
                double z = plate.getZ() + 0.5;
                Vec3 centre = new Vec3(x, player.getY(), z);
                double dist = Math.hypot(x - player.getX(), z - player.getZ());
                steer(mc, player, centre, dist > 0.11, false, false);
                if (ticks % 40 == 0) notify(mc, "§eИду к каменной нажимной плите...");
                return;
            }

            // When the plate teleports us into the course the player may be in
            // the air for a tick; do not try to jump again until fully grounded.
            if (under == null || PlatformScanner.next(level, under, null, Set.of(under)) == null) {
                release(mc);
                if (ticks % 70 == 0) notify(mc, "§eОжидаю стартовую платформу");
                return;
            }
            begin(mc, under);
        }

        if (state != State.RUNNING || current == null) {
            release(mc);
            return;
        }

        // If the player fell BELOW the target platform top, landing on it is
        // impossible. Also catches the case where a course block disappears.
        double lowestLanding = target != null
                ? target.getY() + 1.0 : current.getY() + 1.0;
        if (!player.onGround() && player.getDeltaMovement().y < -0.03
                && player.getY() < lowestLanding - 0.15) {
            failed(mc, "пролетел ниже платформы");
            return;
        }
        if (player.onGround() && under == null) {
            // Landing on a normal spawn-floor block is NOT a successful jump.
            // Some servers respawn nearby without a large teleport.
            if (plate != null) {
                release(mc);
                clearAttempt();
                notify(mc, "§eНа спавне: начинаю заново через плиту");
            } else {
                failed(mc, "приземлился вне паркура");
            }
            return;
        }

        if (under != null) {
            if (!under.equals(current)) {
                if (visited.contains(under)) {
                    failed(mc, "вернулся на старую платформу");
                    return;
                }
                previous = current;
                current = under;
                target = null;
                visited.add(under);
                blocks++;
                best = Math.max(best, blocks);
                idleTicks = 0;
                notify(mc, "§aПлатформа " + blocks + "/200 §7| лучший результат " + best);
            }
        }

        // Keep the current target while jumping instead of switching mid-flight.
        if (target != null && !PlatformScanner.isCoursePlatform(level, target)) {
            target = null;
            if (!player.onGround()) {
                failed(mc, "целевая платформа исчезла");
                return;
            }
        }
        if (target == null && player.onGround()) {
            target = PlatformScanner.next(level, current, previous, visited);
        }

        if (target == null) {
            release(mc);
            idleTicks++;
            if (!player.onGround() && idleTicks > 8) {
                failed(mc, "не вижу платформу для приземления");
                return;
            }
            if (idleTicks % 40 == 0) notify(mc, "§eЖду новую платформу...");
            return;
        }
        idleTicks = 0;

        Vec3 goal = new Vec3(target.getX() + 0.5,
                target.getY() + 1.0, target.getZ() + 0.5);
        double dx = goal.x - player.getX();
        double dz = goal.z - player.getZ();
        double distance = Math.hypot(dx, dz);

        if (!player.onGround()) {
            // Hold sprint but release jump in the air; small midair correction.
            steer(mc, player, goal, distance > 0.48, false, true);
        } else {
            // Accelerate toward the edge in the direction of the new platform.
            double centreX = current.getX() + 0.5;
            double centreZ = current.getZ() + 0.5;
            double fromX = goal.x - centreX;
            double fromZ = goal.z - centreZ;
            double total = Math.max(0.001, Math.hypot(fromX, fromZ));
            double nx = fromX / total;
            double nz = fromZ / total;
            double edgeOffset = total < 2.6 ? 0.05 : (total > 3.4 ? 0.53 : 0.38);
            double progress = (player.getX() - centreX) * nx +
                    (player.getZ() - centreZ) * nz;
            boolean launch = progress >= edgeOffset - 0.08;
            steer(mc, player, goal, true, launch, true);
        }

        if (ticks % 20 == 0) {
            notify(mc, "§bParkourBot47 §7| блоков: §f" + blocks +
                    "/200 §7| лучший: §f" + best +
                    " §7| цель: §f" + target.getX() + " " +
                    target.getY() + " " + target.getZ());
        }
    }

    private void steer(Minecraft mc, LocalPlayer player, Vec3 goal,
                       boolean forward, boolean jump, boolean sprint) {
        double dx = goal.x - player.getX();
        double dz = goal.z - player.getZ();
        if (Math.hypot(dx, dz) > 0.01) {
            player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        mc.options.keyUp.setDown(forward);
        mc.options.keySprint.setDown(sprint);
        mc.options.keyJump.setDown(jump);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyDown.setDown(false);
    }

    private void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    private void notify(Minecraft mc, String message) {
        if (mc.player != null)
            mc.gui.hud.setOverlayMessage(Component.literal(message), false);
    }
}
