package com.starkinhon.battlegroundsmod.world;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

// A list of blocks to place, written relative to a place's center (x, z) and its floor (y = first air above the
// ground). MapPlaces puts them in the world a few thousand per tick, in the order they were added (later ones win).
public final class Build {
    // column: clear from pos up through everything above it in that column (MapPlaces), so no hill is left floating.
    public record Put(BlockPos pos, BlockState state, boolean column) {
        public Put(BlockPos pos, BlockState state) {
            this(pos, state, false);
        }
    }

    public final int x, y, z;
    public final RandomSource random;
    final List<Put> puts = new ArrayList<>();

    public Build(int x, int y, int z, long seed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.random = RandomSource.create(seed);
    }

    public Build set(int dx, int dy, int dz, BlockState state) {
        puts.add(new Put(new BlockPos(x + dx, y + dy, z + dz), state));
        return this;
    }

    public Build set(int dx, int dy, int dz, net.minecraft.world.level.block.Block block) {
        return set(dx, dy, dz, block.defaultBlockState());
    }

    // Solid box, both corners included.
    public Build box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int i = Math.min(x0, x1); i <= Math.max(x0, x1); i++) {
            for (int j = Math.min(y0, y1); j <= Math.max(y0, y1); j++) {
                for (int k = Math.min(z0, z1); k <= Math.max(z0, z1); k++) {
                    set(i, j, k, state);
                }
            }
        }
        return this;
    }

    // Four walls of a box (no floor, no roof).
    public Build walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int j = y0; j <= y1; j++) {
            for (int i = x0; i <= x1; i++) {
                set(i, j, z0, state);
                set(i, j, z1, state);
            }
            for (int k = z0 + 1; k < z1; k++) {
                set(x0, j, k, state);
                set(x1, j, k, state);
            }
        }
        return this;
    }

    // Air where there is something, in a cylinder (clears trees and grass where a place is built).
    public Build clear(double radius, int y0, int y1) {
        int r = (int) Math.ceil(radius);
        for (int i = -r; i <= r; i++) {
            for (int k = -r; k <= r; k++) {
                if (i * i + k * k <= radius * radius) {
                    for (int j = y0; j <= y1; j++) {
                        set(i, j, k, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        return this;
    }

    // Like clear, but the top of the cylinder takes the rest of the column with it, up to the sky: on the normal
    // world's hills a clear up to y1 only left the ground above it hanging in the air (Stark 2026-11-11, WorldSites).
    public Build clearUp(double radius, int y0, int y1) {
        int r = (int) Math.ceil(radius);
        for (int i = -r; i <= r; i++) {
            for (int k = -r; k <= r; k++) {
                if (i * i + k * k <= radius * radius) {
                    for (int j = y0; j < y1; j++) {
                        set(i, j, k, Blocks.AIR.defaultBlockState());
                    }
                    puts.add(new Put(new BlockPos(x + i, y + y1, z + k), Blocks.AIR.defaultBlockState(), true));
                }
            }
        }
        return this;
    }

    // Ground under a building put on uneven land (the normal world, WorldSites): a top block at y -1 and fill below,
    // in a cylinder, so nothing hangs over a slope.
    public Build foundation(double radius, int depth, BlockState top, BlockState fill) {
        int r = (int) Math.ceil(radius);
        for (int i = -r; i <= r; i++) {
            for (int k = -r; k <= r; k++) {
                if (i * i + k * k <= radius * radius) {
                    set(i, -1, k, top);
                    for (int j = 2; j <= depth; j++) {
                        set(i, -j, k, fill);
                    }
                }
            }
        }
        return this;
    }

    // Flat disc of one block at height dy.
    public Build disc(int cx, int dy, int cz, double radius, BlockState state) {
        int r = (int) Math.ceil(radius);
        for (int i = -r; i <= r; i++) {
            for (int k = -r; k <= r; k++) {
                if (i * i + k * k <= radius * radius) {
                    set(cx + i, dy, cz + k, state);
                }
            }
        }
        return this;
    }

    // Round wall (hollow cylinder of thickness 1).
    public Build ring(int cx, int y0, int cz, double radius, int y1, BlockState state) {
        int r = (int) Math.ceil(radius);
        for (int i = -r; i <= r; i++) {
            for (int k = -r; k <= r; k++) {
                double d = Math.sqrt(i * i + k * k);
                if (d <= radius && d > radius - 1.2) {
                    for (int j = y0; j <= y1; j++) {
                        set(cx + i, j, cz + k, state);
                    }
                }
            }
        }
        return this;
    }

    // Upper half of a hollow sphere (dome) whose base is at dy, with a band of another block and windows.
    public Build dome(int cx, int dy, int cz, double radius, BlockState shell, BlockState band, BlockState window) {
        int r = (int) Math.ceil(radius);
        for (int i = -r; i <= r; i++) {
            for (int j = 0; j <= r; j++) {
                for (int k = -r; k <= r; k++) {
                    double d = Math.sqrt(i * i + j * j + k * k);
                    if (d <= radius && d > radius - 1.3) {
                        BlockState s = shell;
                        if (band != null && j == Math.max(1, r / 3)) {
                            s = band;
                        } else if (window != null && j == Math.max(1, r / 3) + 1 && (Math.floorMod(i + k, 4) == 0)) {
                            s = window;
                        }
                        set(cx + i, dy + j, cz + k, s);
                    } else if (d <= radius - 1.3) {
                        set(cx + i, dy + j, cz + k, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        return this;
    }

    // Hollow ball (a ship, a planet's core is done by the caller).
    public Build ball(int cx, int cy, int cz, double radius, BlockState shell, BlockState band, BlockState window) {
        int r = (int) Math.ceil(radius);
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                for (int k = -r; k <= r; k++) {
                    double d = Math.sqrt(i * i + j * j + k * k);
                    if (d <= radius && d > radius - 1.3) {
                        BlockState s = shell;
                        if (band != null && j == 0) {
                            s = band;
                        } else if (window != null && j == 1 && Math.floorMod(i + k, 3) == 0) {
                            s = window;
                        }
                        set(cx + i, cy + j, cz + k, s);
                    } else if (d <= radius - 1.3) {
                        set(cx + i, cy + j, cz + k, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        return this;
    }

    // Pointed roof: square layers shrinking upward.
    public Build pyramid(int cx, int dy, int cz, int half, BlockState state) {
        for (int h = 0; half - h >= 0; h++) {
            int s = half - h;
            box(cx - s, dy + h, cz - s, cx + s, dy + h, cz + s, state);
        }
        return this;
    }

    // Round pointed roof.
    public Build cone(int cx, int dy, int cz, double radius, int height, BlockState state) {
        for (int h = 0; h < height; h++) {
            double r = radius * (1 - (double) h / height);
            disc(cx, dy + h, cz, Math.max(0.5, r), state);
        }
        return this;
    }

    // Palm tree: a slightly bent trunk with a crown of leaves.
    public Build palm(int cx, int cz) {
        BlockState log = Blocks.JUNGLE_LOG.defaultBlockState();
        BlockState leaf = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int height = 6 + random.nextInt(3);
        int bx = 0;
        for (int h = 0; h < height; h++) {
            if (h == height / 2) {
                bx = 1;
            }
            set(cx + bx, h, cz, log);
        }
        int top = height;
        int tx = cx + bx;
        set(tx, top, cz, leaf);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int s = 1; s <= 3; s++) {
                set(tx + d.getStepX() * s, top - (s == 3 ? 1 : 0), cz + d.getStepZ() * s, leaf);
            }
        }
        set(tx + 1, top, cz + 1, leaf);
        set(tx - 1, top, cz - 1, leaf);
        set(tx + 1, top, cz - 1, leaf);
        set(tx - 1, top, cz + 1, leaf);
        return this;
    }

    // Ladder going up on the given side of a column (the ladder faces away from the wall).
    public Build ladder(int lx, int y0, int lz, int y1, Direction facing) {
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
        for (int j = y0; j <= y1; j++) {
            set(lx, j, lz, ladder);
        }
        return this;
    }

    // A building drawn in a structure file (data/battlegroundsmod/structure/<path>.nbt, written by
    // tools/structures/*.py and editable in game with a structure block), its origin (0,0,0) put at (dx, dy, dz).
    // The file's "battlegroundsmod.origin" says where that origin is inside it (else its corner is the origin).
    public Build template(String path, int dx, int dy, int dz) {
        String file = "/data/battlegroundsmod/structure/" + path + ".nbt";
        try (InputStream in = Build.class.getResourceAsStream(file)) {
            if (in == null) {
                throw new IllegalStateException("Construção não encontrada: " + file);
            }
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            HolderGetter<Block> blocks = BuiltInRegistries.BLOCK.asLookup();
            ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
            BlockState[] states = new BlockState[palette.size()];
            for (int i = 0; i < states.length; i++) {
                states[i] = NbtUtils.readBlockState(blocks, palette.getCompound(i));
            }
            ListTag origin = tag.getCompound("battlegroundsmod").getList("origin", Tag.TAG_INT);
            int ox = origin.size() == 3 ? origin.getInt(0) : 0;
            int oy = origin.size() == 3 ? origin.getInt(1) : 0;
            int oz = origin.size() == 3 ? origin.getInt(2) : 0;
            ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag block = list.getCompound(i);
                ListTag pos = block.getList("pos", Tag.TAG_INT);
                set(dx + pos.getInt(0) - ox, dy + pos.getInt(1) - oy, dz + pos.getInt(2) - oz, states[block.getInt("state")]);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Construção ilegível: " + file, e);
        }
        StructureLoot.extraChest(this, path, dx, dy, dz);
        return this;
    }

    public int size() {
        return puts.size();
    }
}
