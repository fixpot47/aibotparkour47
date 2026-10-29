package com.fixpot47.parkour;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic prototype, not a trained AI model.
 * The controller sees only client-loaded blocks and controls normal movement keys.
 */
final class ParkourController {
    private boolean enabled;
    private boolean inCourse;
    private BlockPos current, previous, target;
    private Vec3 lastPosition;
    private int landings, ticks, stationaryTicks;

    void toggle(Minecraft mc) {
        enabled = !enabled;
        if (!enabled) {
            release(mc);
            notify(mc, "§cParkourBot47 OFF");
            reset(false);
        } else {
            reset(true);
            notify(mc, "§aParkourBot47 ON §7— ищу плиту или платформы...");
        }
    }

    private void reset(boolean clearProgress) {
        current = previous = target = null;
        lastPosition = null;
        inCourse = false;
        stationaryTicks = 0;
        if (clearProgress) landings = 0;
    }

    void tick(Minecraft mc) {
        if (!enabled || mc.player == null || mc.level == null) return;
        if (mc.screen != null) { release(mc); return; }

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        Vec3 position = player.position();
        ticks++;

        // A sudden teleport almost always means starting the course or returning to spawn.
        if (lastPosition != null && lastPosition.distanceToSqr(position) > 64) {
            current = previous = target = null;
            inCourse = false;
            stationaryTicks = 0;
        }
        lastPosition = position;

        BlockPos under = player.onGround()
                ? PlatformScanner.standingBlock(level, player.getX(), player.getY(), player.getZ())
                : null;

        // Auto-start at a stone pressure plate. Activate while standing near it.
        if (!inCourse) {
            BlockPos plate = PlatformScanner.nearestPlate(level, player.blockPosition());
            if (plate != null) {
                Vec3 plateCentre = new Vec3(plate.getX() + .5, plate.getY(), plate.getZ() + .5);
                steer(mc, player, plateCentre, true, false, false);
                if (ticks % 30 == 0) notify(mc, "§eИду к нажимной плите...");
                return;
            }
            // Also allow activation while already standing on an isolated parkour platform.
            if (under == null || PlatformScanner.next(level, under, null) == null) {
                release(mc);
                if (ticks % 60 == 0) notify(mc, "§eОжидаю паркур или каменную плиту");
                return;
            }
            inCourse = true;
        }

        if (under != null) {
            if (current == null) {
                current = under;
                target = null;
            } else if (!under.equals(current)) {
                previous = current;
                current = under;
                target = null;
                landings++;
                stationaryTicks = 0;
                notify(mc, "§aПриземление #" + landings + " §7(цель — 200)");
            }
        }

        if (current == null) { release(mc); return; }

        if (target != null && !PlatformScanner.isPlatform(level, target)) target = null;
        if (target == null) target = PlatformScanner.next(level, current, previous);

        if (target == null) {
            release(mc);
            stationaryTicks++;
            if (stationaryTicks % 40 == 0) notify(mc, "§eЖду следующую платформу...");
            return;
        }
        stationaryTicks = 0;

        Vec3 goal = new Vec3(target.getX() + .5, target.getY() + 1.0, target.getZ() + .5);
        double dx = goal.x - player.getX();
        double dz = goal.z - player.getZ();
        double distance = Math.hypot(dx, dz);

        if (!player.onGround()) {
            // Basic air correction: stop pushing forward when above the landing centre.
            steer(mc, player, goal, distance > 0.48, false, true);
        } else {
            // For a longer jump use a small sprint run-up to the takeoff edge.
            double centreX = current.getX() + 0.5;
            double centreZ = current.getZ() + 0.5;
            double fromX = goal.x - centreX;
            double fromZ = goal.z - centreZ;
            double total = Math.hypot(fromX, fromZ);
            double nx = fromX / Math.max(total, 0.001);
            double nz = fromZ / Math.max(total, 0.001);
            double edgeOffset = total < 2.6 ? 0.05 : 0.38;
            Vec3 takeoff = new Vec3(centreX + nx * edgeOffset,
                    player.getY(), centreZ + nz * edgeOffset);
            double approach = Math.hypot(player.getX() - takeoff.x, player.getZ() - takeoff.z);
            boolean launch = approach < 0.16;
            // Always look towards the *landing* target, including during run-up.
            steer(mc, player, goal, true, launch, true);
        }

        if (ticks % 20 == 0) {
            notify(mc, "§bParkourBot47 §7| приземлений: §f" + landings +
                    " §7| цель: §f" + target.getX() + " " + target.getY() + " " + target.getZ());
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
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(message), true);
    }
}
