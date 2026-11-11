package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmShape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
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

// Saitama's torn-up ground with the world's own blocks (the page's debris boxes, here the real grass, dirt and stone that
// stood there): each piece its own size, spin and flight, gravity 28, a bounce, then it lies flat and later sinks away.
// The ground under a move is read once when the move starts (the server takes it out a moment later). And the Mesa's slab,
// the very blocks of the ground baked into one mesh that turns over about its far edge.
public final class OpmBlocks {
    private static final int MAX = 2400;
    private static final RandomSource RND = RandomSource.createThreadSafe();

    // One piece in the air (or lying on the ground).
    static final class Deb {
        BlockState st;
        double px, py, pz, vx, vy, vz, drag;
        float sx, sy, sz, rx, ry, rz, wx, wy, wz, age, life;
        boolean rest, flt;
        // turning round a point while floating (the Saltos)
        boolean orbit;
        double ox, oz, ow, of, oph;
    }

    private static final List<Deb> DEB = new ArrayList<>();
    // The ground as it was when a move started: column -> {top, deeper}.
    private static final Map<Long, BlockState[]> COLS = new HashMap<>();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState(), STONE = Blocks.STONE.defaultBlockState(), GRASS = Blocks.GRASS_BLOCK.defaultBlockState();

    private OpmBlocks() {
    }

    static boolean active() {
        return !DEB.isEmpty() || SLAB.vbo[0] != null || SLAB.baking;
    }

    static void clear() {
        DEB.clear();
        COLS.clear();
        SLAB.close();
    }

    static double rnd() {
        return RND.nextDouble();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    // ---------- the ground as it was ----------

    // Reads the column at world (x, z) now (before the server takes it out).
    static void remember(int x, int z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || COLS.containsKey(key(x, z))) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        BlockState top = level.getBlockState(p);
        if (!drawable(top)) {
            top = GRASS;
        }
        p.setY(y - 3);
        BlockState deep = level.getBlockState(p);
        if (!drawable(deep)) {
            deep = top == GRASS ? DIRT : top;
        }
        COLS.put(key(x, z), new BlockState[] {top, deep});
        if (COLS.size() > 60000) {
            COLS.clear();
        }
    }

    // The columns in the local rectangle of a frame.
    static void remember(OpmShape.Frame f, double x0, double x1, double z0, double z1, java.util.function.BiPredicate<Double, Double> keep) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] c : new double[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            Vec3 w = f.world(c[0], 0, c[1]);
            minX = Math.min(minX, w.x);
            maxX = Math.max(maxX, w.x);
            minZ = Math.min(minZ, w.z);
            maxZ = Math.max(maxZ, w.z);
        }
        for (int wx = Mth.floor(minX); wx <= Mth.floor(maxX); wx++) {
            for (int wz = Mth.floor(minZ); wz <= Mth.floor(maxZ); wz++) {
                double[] l = f.local(new Vec3(wx + 0.5, f.origin().y, wz + 0.5));
                if (l[0] >= x0 && l[0] <= x1 && l[2] >= z0 && l[2] <= z1 && keep.test(l[0], l[2])) {
                    remember(wx, wz);
                }
            }
        }
    }

    // What a piece torn from world (x, z) is made of (deep: from further down).
    static BlockState stateAt(double x, double z, boolean deep) {
        BlockState[] c = COLS.get(key(Mth.floor(x), Mth.floor(z)));
        if (c == null) {
            return deep ? (rnd() < 0.5 ? DIRT : STONE) : (rnd() < 0.4 ? GRASS : DIRT);
        }
        return deep ? c[1] : c[0];
    }

    private static boolean drawable(BlockState st) {
        return !st.isAir() && st.getRenderShape() == RenderShape.MODEL && st.getFluidState().isEmpty() && !st.canBeReplaced();
    }

    // ---------- the pieces ----------

    // A piece at world p with velocity v (blocks a second), size about size (each side its own), like the page's spawnDeb.
    static Deb spawn(BlockState st, Vec3 p, Vec3 v, double size, boolean flt, double drag) {
        if (DEB.size() >= MAX) {
            // the oldest lying piece makes room
            for (int i = 0; i < DEB.size(); i++) {
                if (DEB.get(i).rest) {
                    DEB.remove(i);
                    break;
                }
            }
            if (DEB.size() >= MAX) {
                return null;
            }
        }
        Deb d = new Deb();
        d.st = st;
        d.px = p.x;
        d.py = p.y;
        d.pz = p.z;
        d.vx = v.x;
        d.vy = v.y;
        d.vz = v.z;
        d.sx = (float) (size * (0.7 + rnd() * 0.6));
        d.sy = (float) (size * (0.6 + rnd() * 0.7));
        d.sz = (float) (size * (0.7 + rnd() * 0.6));
        d.rx = (float) (rnd() * 6);
        d.ry = (float) (rnd() * 6);
        d.rz = (float) (rnd() * 6);
        d.wx = (float) ((rnd() - 0.5) * 14);
        d.wy = (float) ((rnd() - 0.5) * 14);
        d.wz = (float) ((rnd() - 0.5) * 14);
        d.flt = flt;
        d.drag = drag;
        d.life = (float) (7 + rnd() * 5);
        DEB.add(d);
        return d;
    }

    // Lets the floating ones fly: each gets its own push.
    static void release(Deb d, Vec3 v, double drag, float spin) {
        d.flt = false;
        d.orbit = false;
        d.vx = v.x;
        d.vy = v.y;
        d.vz = v.z;
        d.drag = drag;
        d.wx *= spin;
        d.wy *= spin;
        d.wz *= spin;
    }

    // dt seconds (0 in a hitstop: the world holds still), time the page's clock (seconds).
    static void step(float dt, double time) {
        if (DEB.isEmpty() || dt <= 0) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            DEB.clear();
            return;
        }
        for (int i = DEB.size() - 1; i >= 0; i--) {
            Deb d = DEB.get(i);
            d.age += dt;
            if (d.age > d.life + 1) {
                DEB.remove(i);
                continue;
            }
            if (d.flt) {
                d.py += d.vy * dt;
                d.vy *= Math.exp(-dt * 0.8);
                d.rx += d.wx * dt * 0.15f;
                d.ry += d.wy * dt * 0.15f;
                d.rz += d.wz * dt * 0.15f;
                if (d.orbit) {
                    double a = d.ow * dt, x = d.px - d.ox, z = d.pz - d.oz;
                    d.px = d.ox + x * Math.cos(a) - z * Math.sin(a);
                    d.pz = d.oz + x * Math.sin(a) + z * Math.cos(a);
                    d.py += Math.sin(time * d.of + d.oph) * 0.15 * dt;
                }
                // a floating piece waits for its push (but not forever)
                d.age = Math.min(d.age, 2);
            } else if (!d.rest) {
                d.vy -= 28 * dt;
                if (d.drag > 0) {
                    double k = Math.exp(-d.drag * dt);
                    d.vx *= k;
                    d.vy *= k;
                    d.vz *= k;
                }
                d.px += d.vx * dt;
                d.py += d.vy * dt;
                d.pz += d.vz * dt;
                d.rx += d.wx * dt;
                d.ry += d.wy * dt;
                d.rz += d.wz * dt;
                double g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(d.px), Mth.floor(d.pz)) + d.sy * 0.5;
                if (d.py < g && d.py > g - 3) {
                    d.py = g;
                    if (d.vy < -5) {
                        d.vy *= -0.28;
                        d.vx *= 0.5;
                        d.vz *= 0.5;
                        d.wx *= 0.5f;
                        d.wy *= 0.5f;
                        d.wz *= 0.5f;
                    } else {
                        d.rest = true;
                        d.rx = Math.round(d.rx / 1.5708f) * 1.5708f;
                        d.rz = Math.round(d.rz / 1.5708f) * 1.5708f;
                    }
                }
                if (d.py < level.getMinBuildHeight() - 10) {
                    DEB.remove(i);
                }
            }
        }
    }

    static void render(Vec3 cam) {
        if (DEB.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        // (the view is already in the shader's ModelViewMat: positions only relative to the camera)
        PoseStack pose = new PoseStack();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        Quaternionf q = new Quaternionf();
        for (Deb d : DEB) {
            if (d.age < 0) {
                continue;
            }
            // lying pieces sink away at the end of their life
            float shrink = d.rest ? 1 - Mth.clamp(d.age - d.life, 0, 1) : 1;
            if (shrink <= 0.01f) {
                continue;
            }
            double dx = d.px - cam.x, dy = d.py - cam.y, dz = d.pz - cam.z;
            if (dx * dx + dy * dy + dz * dz > 300 * 300) {
                continue;
            }
            pose.pushPose();
            pose.translate(dx, dy - (1 - shrink) * d.sy * 0.5, dz);
            pose.mulPose(q.rotationXYZ(d.rx, d.ry, d.rz));
            pose.scale(d.sx * shrink, d.sy * shrink, d.sz * shrink);
            pose.translate(-0.5, -0.5, -0.5);
            at.set(Mth.floor(d.px), Mth.floor(d.py + 0.5), Mth.floor(d.pz));
            int light = LevelRenderer.getLightColor(level, at);
            blocks.renderSingleBlock(d.st, pose, source, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        source.endBatch();
    }

    // ---------- the Mesa's slab ----------

    static final Slab SLAB = new Slab();

    // The slab's blocks baked once (a few rows a frame while he grabs the ground) into a mesh per layer, drawn turned
    // about the far edge (the hinge, at the top of the ground).
    static final class Slab {
        private static final RenderType[] LAYERS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout()};
        private static final int SI = 2 * OpmShape.SLAB_X, SJ = OpmShape.SLAB_DEEP + OpmShape.SLAB_UP, SK = OpmShape.SLAB_Z1 - OpmShape.SLAB_Z0;
        final VertexBuffer[] vbo = new VertexBuffer[3];
        private final ByteBufferBuilder[] bytes = new ByteBufferBuilder[3];
        private final BufferBuilder[] builders = new BufferBuilder[3];
        private BlockState[] cells;
        private BlockPos o;
        private int fx, fz, row;
        private Vec3 hinge;
        boolean baking;

        void start(BlockPos origin, int fx, int fz) {
            close();
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            this.o = origin;
            this.fx = fx;
            this.fz = fz;
            this.row = 0;
            this.cells = new BlockState[SI * SJ * SK];
            Vec3 c = new Vec3(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
            this.hinge = c.add(fx * OpmShape.HINGE, 0, fz * OpmShape.HINGE);
            // every cell read now, before anything changes (cheap); the meshes are built a few rows a frame
            for (int k = 0; k < SK; k++) {
                for (int i = 0; i < SI; i++) {
                    for (int j = 0; j < SJ; j++) {
                        BlockState st = level.getBlockState(OpmShape.cell(o, fx, fz, i - OpmShape.SLAB_X, j - OpmShape.SLAB_DEEP, k + OpmShape.SLAB_Z0));
                        cells[(k * SI + i) * SJ + j] = st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity() ? null : st;
                    }
                }
            }
            for (int l = 0; l < 3; l++) {
                bytes[l] = new ByteBufferBuilder(1 << 20);
                builders[l] = new BufferBuilder(bytes[l], VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            }
            baking = true;
        }

        // A random block of the slab (for the pieces that fall from it), or null.
        BlockState any(boolean deep) {
            if (cells == null) {
                return null;
            }
            for (int n = 0; n < 6; n++) {
                int k = RND.nextInt(SK), i = RND.nextInt(SI), j = deep ? RND.nextInt(OpmShape.SLAB_DEEP) : OpmShape.SLAB_DEEP - 1;
                BlockState st = cells[(k * SI + i) * SJ + j];
                if (st != null && st.getRenderShape() == RenderShape.MODEL) {
                    return st;
                }
            }
            return null;
        }

        // In the slab (and kept): its block; anything else is air, so the slab's outer faces are all drawn.
        private BlockState at(BlockPos p) {
            int dx = p.getX() - o.getX(), dz = p.getZ() - o.getZ();
            int i = dx * fz - dz * fx + OpmShape.SLAB_X, k = dx * fx + dz * fz - OpmShape.SLAB_Z0, j = p.getY() - o.getY() + OpmShape.SLAB_DEEP;
            if (i < 0 || i >= SI || j < 0 || j >= SJ || k < 0 || k >= SK) {
                return Blocks.AIR.defaultBlockState();
            }
            BlockState st = cells[(k * SI + i) * SJ + j];
            return st == null ? Blocks.AIR.defaultBlockState() : st;
        }

        // Bakes a few rows this frame; uploads when done.
        void bake() {
            if (!baking) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) {
                close();
                return;
            }
            BlockRenderDispatcher blocks = mc.getBlockRenderer();
            Getter getter = new Getter(level, this);
            PoseStack pose = new PoseStack();
            RandomSource random = RandomSource.create();
            int rows = 5;
            for (int n = 0; n < rows && row < SK; n++, row++) {
                int k = row;
                for (int i = 0; i < SI; i++) {
                    for (int j = 0; j < SJ; j++) {
                        BlockState st = cells[(k * SI + i) * SJ + j];
                        if (st == null || st.getRenderShape() != RenderShape.MODEL) {
                            continue;
                        }
                        BlockPos p = OpmShape.cell(o, fx, fz, i - OpmShape.SLAB_X, j - OpmShape.SLAB_DEEP, k + OpmShape.SLAB_Z0);
                        RenderType type = ItemBlockRenderTypes.getChunkRenderType(st);
                        int l = type == RenderType.solid() ? 0 : type == RenderType.cutoutMipped() ? 1 : 2;
                        pose.pushPose();
                        pose.translate(p.getX() - hinge.x, p.getY() - hinge.y, p.getZ() - hinge.z);
                        try {
                            blocks.renderBatched(st, p, getter, pose, builders[l], true, random, ModelData.EMPTY, LAYERS[l]);
                        } catch (RuntimeException e) {
                            // one odd block must not take the slab down
                        }
                        pose.popPose();
                    }
                }
            }
            if (row >= SK) {
                for (int l = 0; l < 3; l++) {
                    MeshData data = builders[l].build();
                    if (data != null) {
                        vbo[l] = new VertexBuffer(VertexBuffer.Usage.STATIC);
                        vbo[l].bind();
                        vbo[l].upload(data);
                        VertexBuffer.unbind();
                    }
                    bytes[l].close();
                    bytes[l] = null;
                    builders[l] = null;
                }
                baking = false;
            }
        }

        // Turned by angle about its far edge.
        void draw(Vec3 cam, double angle) {
            if (vbo[0] == null && vbo[1] == null && vbo[2] == null) {
                return;
            }
            Vec3 left = new Vec3(fz, 0, -fx);
            Matrix4f model = new Matrix4f().translation((float) (hinge.x - cam.x), (float) (hinge.y - cam.y), (float) (hinge.z - cam.z))
                    .rotate((float) angle, (float) left.x, 0, (float) left.z);
            Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(model);
            Matrix4f proj = RenderSystem.getProjectionMatrix();
            for (int l = 0; l < 3; l++) {
                if (vbo[l] == null) {
                    continue;
                }
                LAYERS[l].setupRenderState();
                ShaderInstance shader = RenderSystem.getShader();
                if (shader != null) {
                    vbo[l].bind();
                    vbo[l].drawWithShader(view, proj, shader);
                    VertexBuffer.unbind();
                }
                LAYERS[l].clearRenderState();
            }
        }

        void close() {
            for (int l = 0; l < 3; l++) {
                if (vbo[l] != null) {
                    vbo[l].close();
                    vbo[l] = null;
                }
                if (bytes[l] != null) {
                    bytes[l].close();
                    bytes[l] = null;
                }
                builders[l] = null;
            }
            baking = false;
            cells = null;
        }
    }

    // The world as the slab sees it while baking: its own blocks only, the world's light.
    private record Getter(ClientLevel level, Slab slab) implements BlockAndTintGetter {
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
            return slab.at(pos);
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
