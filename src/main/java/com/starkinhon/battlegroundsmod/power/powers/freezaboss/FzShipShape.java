// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.freezaboss;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

// Freeza's ship (Stark 2026-11-19: "a nave pousa na terra de Dragon Ball"), block by block, shared by the server (the
// blocks set when it lands, FzShip) and the client (the same blocks drawn coming down from the sky, EpicFx): the two can
// never disagree. The round ship of the manga: a low white dome over a wide rim with a ring of lit round windows, a
// narrower grey belly, four legs, and a ramp let down on the door's side. Only its shell, so it is cheap to set and to draw.
// Offsets from the middle of the ground under it (y 0 = the first block over the ground).
public final class FzShipShape {
    public static final int R = 16;
    // the hull: legs up to LEG, belly up to RIM, rim to DOME, dome to the top
    private static final int LEG = 3, RIM = LEG + 4, DOME = RIM + 3;
    private static final double RIM_R = 15.5, BELLY_R = 9.5, DOME_H = 7.6;
    public static final int HEIGHT = DOME + 9;
    // where Freeza steps out: the doorway's floor, R_DOOR blocks out along the door's direction
    public static final int DOOR_Y = RIM, R_DOOR = 13;

    public record Piece(int x, int y, int z, BlockState state) {
    }

    private FzShipShape() {
    }

    private static boolean inside(double x, double y, double z) {
        double r = Math.sqrt(x * x + z * z);
        if (y < LEG || y > HEIGHT) {
            return false;
        }
        if (y < RIM) {
            return r <= BELLY_R + (RIM_R - BELLY_R) * (y - LEG) / (RIM - LEG);
        }
        if (y < DOME) {
            return r <= RIM_R;
        }
        double k = (y - DOME + 0.5) / DOME_H;
        return k < 1 && r <= RIM_R * Math.sqrt(1 - k * k);
    }

    private static boolean door(int x, int y, int z, double dx, double dz) {
        double along = x * dx + z * dz, side = Math.abs(-x * dz + z * dx);
        return along > 8 && side <= 1.6 && y >= RIM && y <= RIM + 2;
    }

    // The hull less the doorway: the blocks around the opening become its frame and its floor.
    private static boolean solid(int x, int y, int z, double dx, double dz) {
        return inside(x, y, z) && !door(x, y, z, dx, dz);
    }

    // dx, dz: the door's direction (unit).
    public static List<Piece> build(double dx, double dz) {
        List<Piece> out = new ArrayList<>();
        BlockState dome = Blocks.WHITE_CONCRETE.defaultBlockState(), belly = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                band = Blocks.GRAY_CONCRETE.defaultBlockState(), trim = Blocks.SMOOTH_QUARTZ.defaultBlockState(),
                light = Blocks.OCHRE_FROGLIGHT.defaultBlockState(), hatch = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                for (int y = LEG; y <= HEIGHT; y++) {
                    if (!solid(x, y, z, dx, dz)) {
                        continue;
                    }
                    if (solid(x + 1, y, z, dx, dz) && solid(x - 1, y, z, dx, dz) && solid(x, y + 1, z, dx, dz)
                            && solid(x, y - 1, z, dx, dz) && solid(x, y, z + 1, dx, dz) && solid(x, y, z - 1, dx, dz)) {
                        continue;
                    }
                    double r = Math.sqrt(x * x + z * z);
                    BlockState s;
                    if (y < RIM) {
                        s = belly;
                    } else if (y < DOME) {
                        if (y == RIM + 1 && r > RIM_R - 1.2) {
                            // the ring of round windows: 16 lit ones around the rim
                            int a = Mth.floor((Math.atan2(z, x) + Math.PI) / (2 * Math.PI) * 32);
                            s = a % 2 == 0 ? light : band;
                        } else {
                            s = y == RIM ? band : trim;
                        }
                    } else {
                        s = r < 2.2 && y >= HEIGHT - 2 ? hatch : dome;
                    }
                    out.add(new Piece(x, y, z, s));
                }
            }
        }
        // the four legs, between the doors' sides, each on a wide foot
        for (int k = 0; k < 4; k++) {
            double a = Math.atan2(dz, dx) + Math.PI / 4 + k * Math.PI / 2;
            int lx = (int) Math.round(Math.cos(a) * 8.5), lz = (int) Math.round(Math.sin(a) * 8.5);
            for (int y = 0; y < LEG + 1; y++) {
                out.add(new Piece(lx, y, lz, Blocks.IRON_BLOCK.defaultBlockState()));
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                out.add(new Piece(lx + d.getStepX(), 0, lz + d.getStepZ(),
                        Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)));
            }
        }
        // the ramp from the door down to the ground, three wide
        double sx = -dz, sz = dx;
        for (int i = 0; i <= 2 * DOOR_Y; i++) {
            double along = RIM_R - 1 + i * 0.5;
            int y = DOOR_Y - 1 - i / 2;
            if (y < 0) {
                break;
            }
            boolean half = i % 2 == 1;
            for (int w = -1; w <= 1; w++) {
                int x = (int) Math.round(dx * along + sx * w), z = (int) Math.round(dz * along + sz * w);
                out.add(new Piece(x, y, z, half
                        ? Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                        : trim));
            }
        }
        // one per position: the last one set wins (the ramp over the hull, the legs over the belly)
        java.util.Map<Long, Piece> one = new java.util.LinkedHashMap<>();
        for (Piece p : out) {
            one.put(net.minecraft.core.BlockPos.asLong(p.x, p.y, p.z), p);
        }
        return new ArrayList<>(one.values());
    }
}
