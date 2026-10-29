package com.fixpot47.parkour;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scans only blocks the client has received. Already-visited positions are never
 * considered again during the same attempt, including platforms visited 2+ jumps ago.
 */
final class PlatformScanner {
    private PlatformScanner() {}

    static boolean isPlatform(ClientLevel level, BlockPos block) {
        if (!level.hasChunkAt(block)) return false;
        BlockState state = level.getBlockState(block);
        if (!Block.isShapeFullBlock(state.getCollisionShape(level, block))) return false;
        return level.getBlockState(block.above()).isAir()
                && level.getBlockState(block.above(2)).isAir();
    }

    static BlockPos standingBlock(ClientLevel level, double x, double y, double z) {
        BlockPos block = BlockPos.containing(x, y - 0.12, z);
        return isPlatform(level, block) ? block : null;
    }

    static BlockPos nearestPlate(ClientLevel level, BlockPos origin) {
        BlockPos best = null;
        double min = Double.MAX_VALUE;
        for (int y = -2; y <= 2; y++)
            for (int x = -7; x <= 7; x++)
                for (int z = -7; z <= 7; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.hasChunkAt(pos)) continue;
                    if (!level.getBlockState(pos).is(Blocks.STONE_PRESSURE_PLATE)) continue;
                    double dist = pos.distSqr(origin);
                    if (dist < min) { best = pos; min = dist; }
                }
        return best;
    }

    static BlockPos next(ClientLevel level, BlockPos current,
                         BlockPos previous, Set<BlockPos> visited) {
        // A defensive copy avoids accidental mutation while considering targets.
        Set<BlockPos> excluded = new HashSet<>(visited);
        excluded.add(current);
        if (previous != null) excluded.add(previous);

        java.util.List<BlockPos> targets = new java.util.ArrayList<>();
        for (int y = -4; y <= 1; y++)
            for (int x = -5; x <= 5; x++)
                for (int z = -5; z <= 5; z++) {
                    BlockPos pos = current.offset(x, y, z);
                    if (excluded.contains(pos) || !isPlatform(level, pos)) continue;
                    double distance = Math.hypot(x, z);
                    if (distance < 1.10 || distance > maxRange(y)) continue;
                    targets.add(pos);
                }

        return targets.stream().min(Comparator.comparingDouble(p -> {
            double dx = p.getX() - current.getX();
            double dz = p.getZ() - current.getZ();
            double distance = Math.hypot(dx, dz);
            double dy = p.getY() - current.getY();
            // Penalize turning around toward the previous platform; random courses
            // may still legitimately turn 90+ degrees, so do not forbid it.
            double turnPenalty = 0;
            if (previous != null) {
                double ax = current.getX() - previous.getX();
                double az = current.getZ() - previous.getZ();
                double divisor = Math.max(0.01, Math.hypot(ax, az) * distance);
                double cosine = (ax * dx + az * dz) / divisor;
                turnPenalty = Math.max(0, -cosine) * 0.65;
            }
            return distance + Math.max(0, dy) * 0.75
                    + Math.max(0, -dy) * 0.1 + turnPenalty;
        })).orElse(null);
    }

    /** Conservative reach estimates in platform-centre blocks, to be tuned in-game. */
    static double maxRange(int heightDifference) {
        return switch (heightDifference) {
            case 1 -> 3.35;
            case 0 -> 4.25;
            case -1 -> 4.65;
            case -2 -> 5.05;
            case -3 -> 5.25;
            default -> 5.40;
        };
    }
}
