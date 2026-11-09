package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaClivar;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// Sukuna's X, Clivar (捌), as in the 3D preview Stark approved (forja/sukuna-desmantelar, "X: Clivar"; server side in
// power/powers/sukuna/Clivar). Ref: the Mimiko scene: fine white lines "measure" the held body until the grid closes
// (the body's own model drawn again through souls_clivar, each box grown a hair), then the grid snaps: for a moment the
// body is a black silhouette under the white grid and hundreds of white zigzag bits burst off it; then the cut marks
// fade. Against a wall the grid runs over the wall too and its middle 3x3 breaks into quarter blocks that fly through.
// Lethal: no throw: after the silhouette the body comes apart into the grid's own cells (black with white edges), which
// part, crumble to the ground and vanish over a pool of blood. The sprint leaves white and black dashes behind.
public final class ClivarRenderer {
    private static final int SIL = 5, FADE = 30, BITS = 140, CHIPS = 14, TRAIL = 8;
    private static final double THICK = 0.012;

    private static final class Cut {
        final int id;
        final double born;
        final int measure;
        final Vec3 wall, wallNormal;
        final int seed;
        LivingEntity entity;
        double snapAt = -1, breakAt = -1, pinAt = -1;
        boolean lethal;
        Vec3 fwd = new Vec3(0, 0, 1), center = Vec3.ZERO, pin;
        double feet, ground, scale = 1;
        List<Cell> cells;
        List<Piece> pieces = new ArrayList<>();

        Cut(int id, double born, int measure, Vec3 wall, Vec3 wallNormal, int seed) {
            this.id = id;
            this.born = born;
            this.measure = measure;
            this.wall = wall;
            this.wallNormal = wallNormal;
            this.seed = seed;
        }

        double end() {
            if (snapAt < 0) {
                return born + measure + 60;
            }
            return snapAt + (lethal ? SIL + 64 : SIL + FADE + 2) + (breakAt >= 0 ? 26 : 0);
        }
    }

    // One cell of a lethal Clivar: a box on the body's surface, its centre and half extents along its own axes.
    private record Cell(Vec3 c, Vec3 x, Vec3 y, Vec3 z, double h0, double h1, double h2, double h3) {
    }

    // A quarter of a wall block flying through.
    private record Piece(BlockState state, Vec3 at, Vec3 v, double h0, double h1, double h2, double floor) {
    }

    private record Trail(int id, double born, int ticks, int seed, Vec3[] spots) {
    }

    private static final Map<Integer, Cut> CUTS = new HashMap<>();
    private static final List<Trail> TRAILS = new ArrayList<>();
    private static MultiBufferSource.BufferSource buffers;
    private static boolean drawing;

    private ClivarRenderer() {
    }

    // ---------- from the server ----------

    public static void event(int id, int kind, int ticks, Vec3 a, Vec3 b, int seed) {
        double now = now();
        switch (kind) {
            case SukunaClivar.MEASURE -> {
                boolean wall = b.lengthSqr() > 0.5;
                Cut c = new Cut(id, now, Math.max(1, ticks), wall ? a : null, wall ? b.normalize() : null, seed);
                if (FxAnchor.entity(id) instanceof LivingEntity e) {
                    c.entity = e;
                }
                CUTS.put(id, c);
            }
            case SukunaClivar.SNAP, SukunaClivar.SNAP_LETHAL -> {
                Cut c = CUTS.get(id);
                if (c == null) {
                    c = new Cut(id, now - 1, 1, null, null, seed);
                    CUTS.put(id, c);
                }
                if (c.entity == null && FxAnchor.entity(id) instanceof LivingEntity e) {
                    c.entity = e;
                }
                c.snapAt = now;
                c.lethal = kind == SukunaClivar.SNAP_LETHAL;
                if (b.lengthSqr() > 1.0E-4) {
                    c.fwd = new Vec3(b.x, 0, b.z).normalize();
                }
                if (c.entity != null) {
                    LivingEntity e = c.entity;
                    c.scale = e.getBbHeight() / 1.8;
                    c.feet = e.getY();
                    c.center = e.position().add(0, e.getBbHeight() * 0.6, 0);
                    c.ground = groundUnder(e, e.position());
                    SukunaCutsRenderer.star(c.center, (float) (2.4 * c.scale), seed);
                }
            }
            case SukunaClivar.PIN -> {
                Cut c = CUTS.get(id);
                if (c != null) {
                    c.pinAt = now;
                    c.pin = a;
                }
            }
            case SukunaClivar.WALL_BREAK -> {
                Cut c = CUTS.get(id);
                if (c != null) {
                    c.breakAt = now;
                    c.pieces = pieces(a, b.normalize());
                }
            }
            case SukunaClivar.TRAIL -> TRAILS.add(new Trail(id, now, Math.max(1, ticks), seed, new Vec3[TRAIL]));
            case SukunaClivar.CANCEL -> CUTS.remove(id);
            default -> {
            }
        }
    }

    public static boolean active() {
        return !CUTS.isEmpty() || !TRAILS.isEmpty();
    }

    public static void clear() {
        CUTS.clear();
        TRAILS.clear();
    }

    private static double now() {
        return ScreenFx.now() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static double hash(double n) {
        double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return s - Math.floor(s);
    }

    private static double out(double u) {
        return 1 - Math.pow(1 - u, 3);
    }

    private static double cl(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    private static double groundUnder(Entity e, Vec3 at) {
        Vec3 from = at.add(0, 0.2, 0);
        var hit = e.level().clip(new ClipContext(from, from.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        return hit.getType() == HitResult.Type.MISS ? at.y : hit.getLocation().y;
    }

    // The wall's middle 3x3, as the server breaks it (Clivar.wallBlocks): four quarters per block still there.
    private static List<Piece> pieces(Vec3 center, Vec3 normal) {
        List<Piece> out = new ArrayList<>();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return out;
        }
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = up.cross(normal);
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        double floor = center.y - 1.5;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockPos pos = BlockPos.containing(center.subtract(normal.scale(0.5)).add(right.scale(i)).add(up.scale(j)));
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !state.getFluidState().isEmpty()) {
                    continue;
                }
                Vec3 mid = Vec3.atCenterOf(pos);
                for (int q = 0; q < 4; q++) {
                    double ox = q % 2 == 1 ? 0.25 : -0.25, oy = q < 2 ? 0.25 : -0.25;
                    double h0 = hash(i * 3 + j * 5 + q * 1.3), h1 = hash(i * 9 + j + q * 2.7), h2 = hash(i + j * 11 + q * 3.1);
                    Vec3 at = mid.add(right.scale(ox)).add(0, oy, 0).add(normal.scale((hash(i * 7 + j * 3 + q) - 0.5) * 0.4));
                    // through the wall, away from Sukuna (the normal points back at him)
                    Vec3 v = right.scale((ox + (h0 - 0.5) * 0.4) * 0.25).add(0, 0.05 + 0.08 * h1, 0).add(normal.scale(-(0.12 + 0.15 * h2)));
                    out.add(new Piece(state, at, v, h0, h1, h2, floor));
                }
            }
        }
        return out;
    }

    // ---------- the grid over the body (after the entities) ----------

    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (drawing) {
            return;
        }
        Cut c = CUTS.get(event.getEntity().getId());
        if (c != null && c.lethal && c.snapAt >= 0 && c.entity == event.getEntity()) {
            event.setCanceled(true);
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || CUTS.isEmpty() || FxShaders.clivar == null || FxShaders.flat()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            CUTS.clear();
            return;
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = ScreenFx.now() + partial;
        ShaderInstance shader = FxShaders.clivar;
        if (buffers == null) {
            buffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        drawing = true;
        try {
            for (Cut c : CUTS.values()) {
                LivingEntity e = c.entity;
                if (e == null || FxAnchor.firstPersonSelf(c.id)) {
                    continue;
                }
                double st = c.snapAt >= 0 ? now - c.snapAt : -1;
                if (c.lethal && st >= 0 && c.cells == null) {
                    c.cells = capture(dispatcher, e, camera, partial, c);
                }
                if (st >= SIL + FADE || c.lethal && st >= SIL) {
                    continue;
                }
                Vec3 feet = e.getPosition(partial);
                if (feet.distanceToSqr(camera) > 128 * 128) {
                    continue;
                }
                float measure = (float) (st >= 0 ? 1 : cl((now - c.born) / c.measure, 0, 1));
                Vec3 o = feet.subtract(camera);
                shader.safeGetUniform("Origin").set((float) o.x, (float) o.y, (float) o.z);
                shader.safeGetUniform("Yaw").set(Mth.lerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD);
                shader.safeGetUniform("Measure").set(measure);
                shader.safeGetUniform("Sil").set(st >= 0 && st < SIL ? 1f : 0f);
                shader.safeGetUniform("Fade").set(st < SIL ? 1f : (float) (1 - cl((st - SIL) / FADE, 0, 1)));
                shader.safeGetUniform("N").set((float) (9 / Math.max(0.3, c.scale)));
                shader.safeGetUniform("Seed").set((c.seed % 97) * 0.13f);
                draw(dispatcher, e, o, partial);
                buffers.endBatch();
            }
        } finally {
            drawing = false;
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static boolean bodyTexture(ResourceLocation texture) {
        if (texture == null) {
            return false;
        }
        String path = texture.getPath();
        return (path.startsWith("textures/") || path.startsWith("skins/")) && !path.contains("shadow") && !path.contains("glint")
                && !path.contains("atlas");
    }

    private static void draw(EntityRenderDispatcher dispatcher, LivingEntity entity, Vec3 rel, float partial) {
        MultiBufferSource grid = type -> {
            ResourceLocation texture = FxRenderTypes.textureOf(type);
            return bodyTexture(texture) ? new Grow(buffers.getBuffer(FxRenderTypes.clivarGrid(texture))) : NOTHING;
        };
        render(dispatcher, entity, rel, partial, grid);
    }

    private static void render(EntityRenderDispatcher dispatcher, LivingEntity entity, Vec3 rel, float partial, MultiBufferSource source) {
        int hurtTime = entity.hurtTime;
        int deathTime = entity.deathTime;
        entity.hurtTime = 0;
        entity.deathTime = 0;
        try {
            dispatcher.render(entity, rel.x, rel.y, rel.z, Mth.lerp(partial, entity.yRotO, entity.getYRot()), partial, new PoseStack(), source,
                    0xF000F0);
        } catch (RuntimeException e) {
            // A renderer that can't be drawn this way just gets no grid (the bits and the star still show).
        } finally {
            entity.hurtTime = hurtTime;
            entity.deathTime = deathTime;
        }
    }

    // The body's surface, cut into the grid's cells (the preview's 0.47/4 blocks for a 1.8-block body): every face of
    // the model tiled, each tile a small box set into the body. The outer skin layers make a second copy a hair out:
    // tiles that land on one already there are dropped.
    private static List<Cell> capture(EntityRenderDispatcher dispatcher, LivingEntity entity, Vec3 camera, float partial, Cut cut) {
        List<Cell> cells = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        double size = 0.47 / 4 * cut.scale;
        Vec3 feet = entity.getPosition(partial);
        VertexConsumer capture = new VertexConsumer() {
            private final Vec3[] p = new Vec3[4];
            private int n;

            @Override
            public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
                p[n] = new Vec3(x, y, z).add(camera);
                if (++n < 4) {
                    return;
                }
                n = 0;
                Vec3 a = p[1].subtract(p[0]), b = p[3].subtract(p[0]);
                double la = a.length(), lb = b.length();
                Vec3 nrm = new Vec3(nx, ny, nz);
                if (la < 1.0E-3 || lb < 1.0E-3 || nrm.lengthSqr() < 1.0E-6 || cells.size() > 1100) {
                    return;
                }
                nrm = nrm.normalize();
                Vec3 ua = a.scale(1 / la), ub = b.scale(1 / lb);
                int na = Math.max(1, Math.min(8, (int) Math.round(la / size))), nb = Math.max(1, Math.min(8, (int) Math.round(lb / size)));
                for (int i = 0; i < na; i++) {
                    for (int j = 0; j < nb; j++) {
                        Vec3 c = p[0].add(a.scale((i + 0.5) / na)).add(b.scale((j + 0.5) / nb)).subtract(nrm.scale(size / 2));
                        long key = BlockPos.asLong((int) Math.floor(c.x / 0.08), (int) Math.floor(c.y / 0.08), (int) Math.floor(c.z / 0.08));
                        if (!seen.add(key)) {
                            continue;
                        }
                        int k = cells.size();
                        cells.add(new Cell(c, ua.scale(la / na / 2), ub.scale(lb / nb / 2), nrm.scale(size / 2), hash(k * 1.3), hash(k * 2.7),
                                hash(k * 4.1), hash(k * 5.9)));
                    }
                }
            }

            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                return this;
            }

            @Override
            public VertexConsumer setColor(int r, int g, int b, int a) {
                return this;
            }

            @Override
            public VertexConsumer setUv(float u, float v) {
                return this;
            }

            @Override
            public VertexConsumer setUv1(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setUv2(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setNormal(float x, float y, float z) {
                return this;
            }
        };
        render(dispatcher, entity, feet.subtract(camera), partial, type -> bodyTexture(FxRenderTypes.textureOf(type)) ? capture : NOTHING);
        cut.center = feet.add(0, entity.getBbHeight() * 0.55, 0);
        cut.feet = feet.y;
        return cells;
    }

    // Grows every face of the model's boxes by THICK, as IceShell's: a shell over each part, not a blob over the body.
    private static final class Grow implements VertexConsumer {
        private final VertexConsumer out;
        private final float[][] v = new float[4][];
        private final int[][] iv = new int[4][];
        private int n;

        Grow(VertexConsumer out) {
            this.out = out;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float vv, int overlay, int light, float nx, float ny, float nz) {
            v[n] = new float[] {x, y, z, u, vv, nx, ny, nz};
            iv[n] = new int[] {color, overlay, light};
            if (++n < 4) {
                return;
            }
            n = 0;
            float cx = (v[0][0] + v[1][0] + v[2][0] + v[3][0]) / 4;
            float cy = (v[0][1] + v[1][1] + v[2][1] + v[3][1]) / 4;
            float cz = (v[0][2] + v[1][2] + v[2][2] + v[3][2]) / 4;
            Vector3f a = new Vector3f(v[1][0] - v[0][0], v[1][1] - v[0][1], v[1][2] - v[0][2]);
            Vector3f b = new Vector3f(v[3][0] - v[0][0], v[3][1] - v[0][1], v[3][2] - v[0][2]);
            if (a.lengthSquared() > 1.0E-10f) {
                a.normalize();
            }
            if (b.lengthSquared() > 1.0E-10f) {
                b.normalize();
            }
            float t = (float) THICK;
            for (int i = 0; i < 4; i++) {
                float[] p = v[i];
                Vector3f d = new Vector3f(p[0] - cx, p[1] - cy, p[2] - cz);
                float sa = Math.signum(d.dot(a)) * t;
                float sb = Math.signum(d.dot(b)) * t;
                out.addVertex(p[0] + a.x * sa + b.x * sb + p[5] * t, p[1] + a.y * sa + b.y * sb + p[6] * t, p[2] + a.z * sa + b.z * sb + p[7] * t,
                        iv[i][0], p[3], p[4], iv[i][1], iv[i][2], p[5], p[6], p[7]);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return out.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return out.setColor(r, g, b, a);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return out.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return out.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return out.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return out.setNormal(x, y, z);
        }
    }

    private static final VertexConsumer NOTHING = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };

    // ---------- bits, cells, blood, the wall, the trail (FxWorld, end of the level) ----------

    private static ShaderInstance shader;
    private static Vec3 cam = Vec3.ZERO;
    private static Vec3 camRight = new Vec3(1, 0, 0), camUp = new Vec3(0, 1, 0);

    static void render(Vec3 camera) {
        double now = now();
        CUTS.values().removeIf(c -> now > c.end() || now < c.born - 40);
        TRAILS.removeIf(t -> now - t.born() > t.ticks() + 6 || now < t.born() - 40);
        shader = FxShaders.sukunaFx;
        if (!active() || shader == null) {
            return;
        }
        cam = camera;
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = c.getLeftVector(), up = c.getUpVector();
        camRight = new Vec3(-left.x(), -left.y(), -left.z());
        camUp = new Vec3(up.x(), up.y(), up.z());
        // the wall's quarter blocks first, as real blocks (their own render types and blending)
        drawPieces(now);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        // the cells are opaque: they write depth, so the near ones hide the far ones
        RenderSystem.depthMask(true);
        for (Cut cut : CUTS.values()) {
            drawCells(cut, now);
        }
        RenderSystem.depthMask(false);
        for (Cut cut : CUTS.values()) {
            drawCut(cut, now);
        }
        for (Trail t : TRAILS) {
            drawTrail(t, now);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void put(BufferBuilder b, Vec3 p, float u, float v) {
        Vec3 m = p.subtract(cam);
        b.addVertex((float) m.x, (float) m.y, (float) m.z).setUv(u, v);
    }

    private static void quad(SukunaCutsRenderer.U u, Vec3 c, Vec3 r, Vec3 f) {
        u.apply(shader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        put(b, c.subtract(r).subtract(f), 0, 0);
        put(b, c.add(r).subtract(f), 1, 0);
        put(b, c.add(r).add(f), 1, 1);
        put(b, c.subtract(r).add(f), 0, 1);
        flush(b);
    }

    private static void flush(BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh != null) {
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void billboard(SukunaCutsRenderer.U u, Vec3 c, double sx, double sy, double spin) {
        double cs = Math.cos(spin), sn = Math.sin(spin);
        Vec3 r = camRight.scale(cs).add(camUp.scale(sn)).scale(sx);
        Vec3 f = camUp.scale(cs).subtract(camRight.scale(sn)).scale(sy);
        quad(u, c, r, f);
    }

    private static void drawCut(Cut c, double now) {
        // the wall's grid, measuring with the body's, until the middle breaks
        if (c.wall != null && c.breakAt < 0) {
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 right = up.cross(c.wallNormal);
            right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
            SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(8);
            u.grow = (float) (c.snapAt >= 0 ? 1 : cl((now - c.born) / c.measure, 0, 1));
            u.n = 12;
            u.seed = (c.seed % 31) * 0.7f;
            quad(u, c.wall.add(c.wallNormal.scale(0.015)), right.scale(1.5), up.scale(1.5));
        }
        // chips: on the slam against the wall, or off the body on the snap in the open
        double ct = c.wall != null ? (c.pinAt >= 0 ? now - c.pinAt : -1) : c.snapAt >= 0 ? now - c.snapAt : -1;
        if (ct >= 0) {
            Vec3 cp = c.wall != null && c.pin != null ? c.pin : c.center;
            Vec3 back = c.wallNormal != null ? c.wallNormal : c.fwd.scale(-1);
            Vec3 side = new Vec3(0, 1, 0).cross(back);
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            for (int k = 0; k < CHIPS; k++) {
                double s = ct - (k % 4) * 0.25;
                if (s < 0 || s >= 9) {
                    continue;
                }
                double h1 = hash(k * 3.1 + 2), h2 = hash(k * 1.3 + 7), h3 = hash(k * 8.9);
                Vec3 v = side.scale((h1 - 0.5) * 0.35).add(0, 0.08 + 0.12 * h2, 0).add(back.scale(0.1 + 0.15 * h3));
                Vec3 p = cp.add(v.scale(s)).subtract(0, 0.012 * s * s, 0);
                double sz = (0.12 + 0.16 * h2) * (1 - Math.pow(s / 9, 2));
                SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(2);
                u.n = 3 + (float) Math.floor(hash(k * 2.2) * 4);
                u.asp = 0.6f + (float) hash(k * 6.1) * 1.4f;
                u.rot = (float) (h1 * 6.28 + s * (h3 - 0.5));
                billboard(u, p, sz / 2, sz / 2, 0);
            }
        }
        if (c.snapAt < 0) {
            return;
        }
        double st = now - c.snapAt;
        // the white zigzag bits bursting off the body
        double w = c.entity != null ? c.entity.getBbWidth() : 0.6, h = c.entity != null ? c.entity.getBbHeight() : 1.8;
        for (int k = 0; k < BITS; k++) {
            double h0 = hash(k * 6.1), h1 = hash(k * 7.7), h2 = hash(k * 8.3), h3 = hash(k * 9.9);
            double s = (st - h3 * (c.lethal ? 14 : 2)) * 0.7;
            if (s < 0 || s >= 11) {
                continue;
            }
            Vec3 p0 = c.center.add((hash(k * 2.1) - 0.5) * w * 1.2, (hash(k * 4.3) - 0.5) * h * 0.75, (hash(k * 5.9) - 0.5) * w * 1.2);
            Vec3 dir = p0.subtract(c.center).add(0, 0.15, 0);
            dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : dir.normalize();
            double sp = (0.08 + 0.18 * h0) * (1 - Math.exp(-s / 2)) * 6 * c.scale;
            double sc = (0.12 + 0.14 * h2) * (1 - Math.pow(s / 11, 2)) * c.scale;
            SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(9);
            u.seed = (float) hash(k * 3.3);
            billboard(u, p0.add(dir.scale(sp)), sc / 2, sc / 4, h1 * 6.28 + s * (h2 - 0.5));
        }
        // lethal: the pool of blood
        double ks = st - SIL;
        if (c.lethal && ks >= 6 && ks < 62) {
            SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(11);
            u.grow = (float) (out(cl((ks - 6) / 24, 0, 1)) * 0.95);
            u.a = (float) (1 - cl((ks - 50) / 12, 0, 1));
            u.seed = (c.seed % 23) * 0.9f;
            Vec3 at = new Vec3(c.center.x, c.ground + 0.012, c.center.z).add(c.fwd.scale(-0.15 * c.scale));
            double r = 1.3 * c.scale;
            quad(u, at, new Vec3(r, 0, 0), new Vec3(0, 0, r));
        }
    }

    // Lethal: after the silhouette the cells part, then crumble to the ground and vanish.
    private static void drawCells(Cut c, double now) {
        if (!c.lethal || c.cells == null || c.snapAt < 0) {
            return;
        }
        double ks = now - c.snapAt - SIL;
        if (ks < 0 || ks >= 40 || c.cells.isEmpty()) {
            return;
        }
        SukunaCutsRenderer.U.of(10).apply(shader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        double part = out(cl(ks / 7, 0, 1)) * 0.22;
        for (Cell cell : c.cells) {
            Vec3 wpos = cell.c();
            Vec3 o = wpos.subtract(c.center);
            Vec3 p = wpos.add(o.scale(part)).add(new Vec3(cell.h0() - 0.5, cell.h1() - 0.5, cell.h2() - 0.5).scale(0.06 * part / 0.22));
            double height = (wpos.y - c.feet) / Math.max(0.3, c.scale);
            double fs = ks - 6 - cell.h3() * 4 - (1.8 - height) * 1.5;
            double rot = 0;
            double half = Math.max(cell.y().length(), Math.max(cell.x().length(), cell.z().length()));
            if (fs > 0) {
                p = p.add((cell.h0() - 0.5) * 0.05 * fs, 0, (cell.h2() - 0.5) * 0.05 * fs).add(c.fwd.scale(0.01 * fs));
                p = new Vec3(p.x, Math.max(c.ground + half, p.y - 0.018 * fs * fs), p.z);
                rot = fs * (cell.h1() - 0.5) * 0.4;
            }
            double shrink = (1 - cl((ks - 18 - cell.h0() * 14) / 6, 0, 1)) * 0.97 * (1 - 0.15 * part / 0.22);
            if (shrink <= 0.01) {
                continue;
            }
            Vec3 x = cell.x(), y = cell.y(), z = cell.z();
            if (rot != 0) {
                x = turn(x, rot);
                y = turn(y, rot);
                z = turn(z, rot);
            }
            cube(b, p, x.scale(shrink), y.scale(shrink), z.scale(shrink));
        }
        flush(b);
    }

    // Turned about the axes the preview uses (rot, 0.7 rot, 0.3 rot).
    private static Vec3 turn(Vec3 v, double rot) {
        return v.xRot((float) rot).yRot((float) (rot * 0.7)).zRot((float) (rot * 0.3));
    }

    private static void cube(BufferBuilder b, Vec3 c, Vec3 x, Vec3 y, Vec3 z) {
        face(b, c.add(z), x, y);
        face(b, c.subtract(z), x, y);
        face(b, c.add(x), z, y);
        face(b, c.subtract(x), z, y);
        face(b, c.add(y), x, z);
        face(b, c.subtract(y), x, z);
    }

    private static void face(BufferBuilder b, Vec3 c, Vec3 r, Vec3 f) {
        put(b, c.subtract(r).subtract(f), 0, 0);
        put(b, c.add(r).subtract(f), 1, 0);
        put(b, c.add(r).add(f), 1, 1);
        put(b, c.subtract(r).add(f), 0, 1);
    }

    private static void drawPieces(double now) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        MultiBufferSource.BufferSource source = minecraft.renderBuffers().bufferSource();
        boolean any = false;
        for (Cut c : CUTS.values()) {
            if (c.breakAt < 0) {
                continue;
            }
            double s = now - c.breakAt;
            if (s >= 24) {
                continue;
            }
            for (Piece q : c.pieces) {
                Vec3 p = q.at().add(q.v().scale(s));
                p = new Vec3(p.x, Math.max(q.floor() + 0.25, p.y - 0.02 * s * s), p.z);
                double sc = 0.5 * (1 - cl((s - 14) / 10, 0, 1));
                if (sc <= 0.01) {
                    continue;
                }
                PoseStack pose = new PoseStack();
                pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
                pose.mulPose(Axis.XP.rotation((float) (q.h0() * s * 0.2)));
                pose.mulPose(Axis.YP.rotation((float) (q.h1() * s * 0.25)));
                pose.scale((float) sc, (float) sc, (float) sc);
                pose.translate(-0.5, -0.5, -0.5);
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
                minecraft.getBlockRenderer().renderSingleBlock(q.state(), pose, source, light, OverlayTexture.NO_OVERLAY);
                any = true;
            }
        }
        if (any) {
            source.endBatch();
        }
    }

    // The sprint: white and black dashes left behind, each where he was when it was born.
    private static void drawTrail(Trail t, double now) {
        Entity e = FxAnchor.entity(t.id());
        for (int k = 0; k < TRAIL; k++) {
            double born = t.born() + k * (t.ticks() / (double) TRAIL), s = now - born;
            if (s < 0 || s >= 5) {
                continue;
            }
            double h1 = hash(k * 3.1 + t.seed()), h2 = hash(k * 5.3 + t.seed());
            if (t.spots()[k] == null) {
                if (e == null) {
                    continue;
                }
                Vec3 d = Vec3.directionFromRotation(0, e.getYRot());
                Vec3 side = new Vec3(-d.z, 0, d.x);
                t.spots()[k] = e.position().add(side.scale((h1 - 0.5) * 1.0)).add(0, 0.3 + h2 * 1.5, 0).subtract(d.scale(0.4));
            }
            Vec3 p = t.spots()[k];
            Vec3 d = e != null ? Vec3.directionFromRotation(0, e.getYRot()) : new Vec3(0, 0, 1);
            Vec3 view = cam.subtract(p).normalize();
            Vec3 wv = d.cross(view);
            wv = wv.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : wv.normalize();
            double L = (0.8 + 0.6 * h2) * (1 - Math.pow(s / 5, 2)), W = 0.08 + 0.05 * h1;
            SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(3);
            u.kind = k % 3 == 0 ? 1 : 0;
            u.seed = (float) h1;
            u.a = (float) (1 - s / 5);
            quad(u, p.subtract(d.scale(s * 0.05)), d.scale(L / 2), wv.scale(W / 2));
        }
    }
}
