// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmTree;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

// Whole trees moved by the Saitama (Stark 2026-11-20): the Saltos' dome fells them away from the punch, the Espirro blows
// them off in one piece. The server says which tree (its lowest log) before it takes the blocks out; here the same blocks
// are read (OpmTree.blocks, the same walk as the server's), baked into one rigid mesh like the Mesa's slab, and moved:
// falling over its foot, or flying, turning. When it hits the ground it breaks into the world's own pieces (OpmBlocks).
public final class OpmTrees {
    private static final RenderType[] LAYERS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout()};
    // Blocks baked a frame, at most (a forest felled at once is baked over a few frames, each tree waiting whole).
    private static final int BAKE_BUDGET = 2400;
    private static final int MAX_TREES = 48;
    private static final double G = 28;

    private OpmTrees() {
    }

    private static final class Tree {
        final Map<BlockPos, BlockState> cells = new HashMap<>();
        final List<BlockPos> order;
        final BlockPos base;
        final boolean fly;
        // the foot (bottom middle of the lowest log): the falling tree turns about it; the flying one about its middle
        Vec3 pivot, vel;
        final Vec3 axis, centre;
        double angle, spin, age;
        final VertexBuffer[] vbo = new VertexBuffer[3];
        ByteBufferBuilder[] bytes;
        BufferBuilder[] builders;
        int baked;
        boolean ready;

        Tree(BlockPos base, List<BlockPos> order, Vec3 dir, boolean fly, float strength, int seed, ClientLevel level) {
            this.base = base;
            this.order = order;
            this.fly = fly;
            for (BlockPos p : order) {
                BlockState st = level.getBlockState(p);
                if (!st.isAir() && st.getFluidState().isEmpty() && !st.hasBlockEntity()) {
                    cells.put(p, st);
                }
            }
            Vec3 foot = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
            double sx = 0, sy = 0, sz = 0;
            for (BlockPos p : cells.keySet()) {
                sx += p.getX() + 0.5;
                sy += p.getY() + 0.5;
                sz += p.getZ() + 0.5;
            }
            int n = Math.max(1, cells.size());
            centre = cells.isEmpty() ? foot.add(0, 3, 0) : new Vec3(sx / n, sy / n, sz / n);
            RandomSource r = RandomSource.create(seed);
            if (fly) {
                pivot = centre;
                vel = dir;
                Vec3 a = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).add(dir.normalize().cross(new Vec3(0, 1, 0)));
                axis = a.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : a.normalize();
                spin = 1.6 + r.nextDouble() * 2.2;
            } else {
                pivot = foot;
                vel = Vec3.ZERO;
                Vec3 d = new Vec3(dir.x, 0, dir.z);
                d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
                // tipping toward d: turned about up x d
                axis = new Vec3(0, 1, 0).cross(d).normalize();
                // the push it got (nearer the punch, harder)
                spin = 0.35 + 1.5 * strength + r.nextDouble() * 0.3;
            }
        }

        Matrix4f model(Vec3 cam) {
            return new Matrix4f().translation((float) (pivot.x - cam.x), (float) (pivot.y - cam.y), (float) (pivot.z - cam.z))
                    .rotate((float) angle, (float) axis.x, (float) axis.y, (float) axis.z);
        }

        // Where a block of the tree is now (its middle).
        Vec3 now(BlockPos p) {
            Vector3f v = new Vector3f((float) (p.getX() + 0.5 - pivot0().x), (float) (p.getY() + 0.5 - pivot0().y), (float) (p.getZ() + 0.5 - pivot0().z));
            new Quaternionf().rotationAxis((float) angle, (float) axis.x, (float) axis.y, (float) axis.z).transform(v);
            return pivot.add(v.x, v.y, v.z);
        }

        // The pivot as it was when the tree stood (the mesh is baked about it).
        Vec3 pivot0() {
            return fly ? centre : new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        }

        void close() {
            for (int l = 0; l < 3; l++) {
                if (vbo[l] != null) {
                    vbo[l].close();
                    vbo[l] = null;
                }
                if (bytes != null && bytes[l] != null) {
                    bytes[l].close();
                    bytes[l] = null;
                }
            }
            builders = null;
        }
    }

    private static final List<Tree> TREES = new ArrayList<>();

    static boolean active() {
        return !TREES.isEmpty();
    }

    static void clear() {
        for (Tree t : TREES) {
            t.close();
        }
        TREES.clear();
    }

    // The server's word: the tree whose lowest log is base. fly: dir is its velocity (blocks a second); else dir is the
    // way it falls and strength how hard it was pushed.
    static void receive(BlockPos base, Vec3 dir, boolean fly, float strength, int seed) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !OpmFx.near(Vec3.atCenterOf(base), 200)) {
            return;
        }
        if (TREES.size() >= MAX_TREES) {
            // the oldest goes to pieces at once
            Tree old = TREES.remove(0);
            shatter(old, 40);
            old.close();
        }
        List<BlockPos> blocks = OpmTree.blocks(level, base);
        if (blocks.isEmpty()) {
            return;
        }
        Tree t = new Tree(base, blocks, dir, fly, strength, seed, level);
        if (t.cells.isEmpty()) {
            return;
        }
        t.bytes = new ByteBufferBuilder[3];
        t.builders = new BufferBuilder[3];
        for (int l = 0; l < 3; l++) {
            t.bytes[l] = new ByteBufferBuilder(1 << 18);
            t.builders[l] = new BufferBuilder(t.bytes[l], VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        }
        TREES.add(t);
    }

    // Bakes what the budget allows this frame (the oldest first).
    private static void bake() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        RandomSource random = RandomSource.create();
        PoseStack pose = new PoseStack();
        int budget = BAKE_BUDGET;
        for (Tree t : TREES) {
            if (t.ready) {
                continue;
            }
            if (budget <= 0) {
                break;
            }
            Getter getter = new Getter(level, t);
            Vec3 o = t.pivot0();
            while (t.baked < t.order.size() && budget > 0) {
                BlockPos p = t.order.get(t.baked++);
                budget--;
                BlockState st = t.cells.get(p);
                if (st == null || st.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }
                RenderType type = ItemBlockRenderTypes.getChunkRenderType(st);
                int l = type == RenderType.solid() ? 0 : type == RenderType.cutoutMipped() ? 1 : 2;
                pose.pushPose();
                pose.translate(p.getX() - o.x, p.getY() - o.y, p.getZ() - o.z);
                try {
                    blocks.renderBatched(st, p, getter, pose, t.builders[l], true, random, ModelData.EMPTY, LAYERS[l]);
                } catch (RuntimeException e) {
                    // one odd block must not take the tree down
                }
                pose.popPose();
            }
            if (t.baked >= t.order.size()) {
                for (int l = 0; l < 3; l++) {
                    MeshData data = t.builders[l].build();
                    if (data != null) {
                        t.vbo[l] = new VertexBuffer(VertexBuffer.Usage.STATIC);
                        t.vbo[l].bind();
                        t.vbo[l].upload(data);
                        VertexBuffer.unbind();
                    }
                    t.bytes[l].close();
                    t.bytes[l] = null;
                }
                t.builders = null;
                t.bytes = null;
                t.ready = true;
            }
        }
    }

    // dt seconds (0 in a hitstop), then drawn.
    static void render(Vec3 cam, float dt) {
        if (TREES.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }
        bake();
        for (Iterator<Tree> it = TREES.iterator(); it.hasNext(); ) {
            Tree t = it.next();
            if (!t.ready) {
                continue;
            }
            t.age += dt;
            boolean down;
            if (t.fly) {
                t.vel = t.vel.add(0, -G * 0.55 * dt, 0).scale(Math.exp(-0.35 * dt));
                t.pivot = t.pivot.add(t.vel.scale(dt));
                t.angle += t.spin * dt;
                double g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(t.pivot.x), Mth.floor(t.pivot.z));
                down = t.age > 0.4 && t.vel.y < 0 && t.pivot.y < g + 2 || t.age > 7;
            } else {
                // a tipping pole: the further over, the faster
                t.spin += (2.6 * Math.sin(Math.max(t.angle, 0.04)) + 0.25) * dt;
                t.angle += t.spin * dt;
                down = t.angle >= 1.42;
                if (down) {
                    t.angle = 1.42;
                }
            }
            if (down) {
                shatter(t, 90);
                Vec3 at = t.fly ? t.pivot : t.now(t.order.get(t.order.size() / 2));
                OpmFx.shake(at, t.fly ? 0.6 : 0.45);
                t.close();
                it.remove();
                continue;
            }
            draw(t, cam);
        }
    }

    private static void draw(Tree t, Vec3 cam) {
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(t.model(cam));
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        for (int l = 0; l < 3; l++) {
            if (t.vbo[l] == null) {
                continue;
            }
            LAYERS[l].setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                t.vbo[l].bind();
                t.vbo[l].drawWithShader(view, proj, shader);
                VertexBuffer.unbind();
            }
            LAYERS[l].clearRenderState();
        }
    }

    // The tree to pieces where it is: n of its blocks as the world's own pieces, the rest a puff of leaves.
    private static void shatter(Tree t, int n) {
        if (t.order.isEmpty()) {
            return;
        }
        int step = Math.max(1, t.order.size() / n);
        Vec3 v = t.fly ? t.vel.scale(0.3) : Vec3.ZERO;
        for (int i = 0; i < t.order.size(); i += step) {
            BlockPos p = t.order.get(i);
            BlockState st = t.cells.get(p);
            if (st == null) {
                continue;
            }
            Vec3 at = t.now(p);
            boolean log = OpmTree.log(st);
            OpmBlocks.Deb d = OpmBlocks.spawn(st, at,
                    v.add((OpmBlocks.rnd() - 0.5) * 6, 2 + OpmBlocks.rnd() * (log ? 4 : 7), (OpmBlocks.rnd() - 0.5) * 6),
                    log ? 0.7 + OpmBlocks.rnd() * 0.3 : 0.35 + OpmBlocks.rnd() * 0.45, false, log ? 0.2 : 0.9);
            if (d != null) {
                d.life = (float) (4 + OpmBlocks.rnd() * 3);
            }
        }
    }

    // The world as a tree sees it while baking: its own blocks only (so its outer faces are all drawn), the world's light.
    private record Getter(ClientLevel level, Tree tree) implements BlockAndTintGetter {
        @Override
        public float getShade(Direction direction, boolean shade) {
            return level.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return level.getBlockTint(pos, resolver);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState st = tree.cells.get(pos);
            return st == null ? Blocks.AIR.defaultBlockState() : st;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }
}
