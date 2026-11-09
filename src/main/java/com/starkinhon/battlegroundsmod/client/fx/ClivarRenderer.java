package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

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
import com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaClivar;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// Sukuna's X, Clivar (捌) (server side in power/powers/sukuna/Clivar). Ref: the Mimiko scene: the held body is measured
// by cuts, then it snaps. Stark 2026-11-09 found the first port ugly ("é tudo PNG repetido": a lattice of lines every 1/9
// block lit all over at once, 140 copies of one zigzag sprite, chips and dashes), so every piece is its own shape now:
//   - The measure: twelve planes through the body (planes(): slices across at different heights and tilts, a few
//     lengthwise, a few diagonal), each traced on the model by souls_clivar as a line running across from one side with
//     a white-hot head, leaving a thin black slit rimmed in white, one after another.
//   - The snap: the body is a black silhouette under the cuts; each plane's cut shoots out of the body on both sides as a
//     long Shift+Z stroke along that plane (its own length, taper and tearing), white sparks fly along the slits, dark
//     smoke and a fine red mist burst out (Fumaça Nova, SmokeParticle); then the cuts fade.
//   - Against a wall, every plane cuts the wall where it meets it (a stroke on the wall's face, drawn with the body's
//     line), the slam throws dust and grit off it, and its middle 3x3 opens (the real blocks, nothing
//     spawned to look like them) with the wall's dust bursting through.
//   - Lethal: no throw: after the silhouette the body comes apart into cells cut from its own model, which part, crumble
//     to the ground and vanish over a pool of blood.
//   - The sprint kicks dust off the ground under his feet and leaves wind lines along his body.
public final class ClivarRenderer {
    private static final int SIL = 5, FADE = 30, PLANES = 12, RIBBON = 24;
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
        int wallColor = -1;
        // the cuts, in the body's frame (x its left, y up, z ahead, from its feet), and what they were in the world at
        // the snap (for the strokes that shoot out)
        List<Plane> planes;
        double height = 1.8, yaw;
        boolean burst, pinDust;

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
            return snapAt + (lethal ? SIL + 64 : SIL + FADE + 2) + (breakAt >= 0 ? 2 : 0);
        }
    }

    // One cell of a lethal Clivar: a box on the body's surface, its centre and half extents along its own axes.
    private record Cell(Vec3 c, Vec3 x, Vec3 y, Vec3 z, double h0, double h1, double h2, double h3) {
    }

    // One cut through the body: normal and offset from the body's middle (body frame), when it runs (start and length in
    // Measure), its width in blocks, which way it runs across (angle in the plane) and its own noise.
    private record Plane(Vec3 n, double d, double t0, double dur, double w, double ang, double h) {
        Vec3 along() {
            Vec3 ax = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 u1 = n.cross(ax).normalize();
            Vec3 u2 = n.cross(u1);
            return u1.scale(Math.cos(ang)).add(u2.scale(Math.sin(ang)));
        }

        double progress(double measure) {
            return cl((measure - t0) / dur, 0, 1);
        }
    }

    private static final class Trail {
        final int id, ticks, seed;
        final double born;
        int spawned;

        Trail(int id, double born, int ticks, int seed) {
            this.id = id;
            this.born = born;
            this.ticks = ticks;
            this.seed = seed;
        }
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
                    c.height = e.getBbHeight();
                    c.planes = planes(seed, e.getBbWidth(), e.getBbHeight());
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
                    if (c.planes == null) {
                        c.height = e.getBbHeight();
                        c.planes = planes(seed, e.getBbWidth(), e.getBbHeight());
                    }
                    c.yaw = e.yBodyRot;
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
                    c.wallColor = wallColor(a, b.normalize());
                    wallDust(a, b.normalize(), c.wallColor);
                }
            }
            case SukunaClivar.WALL_BREAK -> {
                Cut c = CUTS.get(id);
                if (c != null) {
                    c.breakAt = now;
                    // no flying block copies (Stark: destruction cuts the real blocks, nothing spawned to look like
                    // them): the hole just opens, and the wall's dust bursts out through it, away from Sukuna
                    Vec3 n = b.normalize();
                    int color = c.wallColor >= 0 ? c.wallColor : wallColor(a, n);
                    wallDust(a.subtract(n.scale(0.5)), n.scale(-1), color);
                    wallDust(a, n, color);
                }
            }
            case SukunaClivar.TRAIL -> TRAILS.add(new Trail(id, now, Math.max(1, ticks), seed));
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
                shader.safeGetUniform("Center").set((float) (0.52 * c.height));
                shader.safeGetUniform("Radius").set((float) (0.55 * c.height));
                planeUniforms(shader, c.planes);
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
            return bodyTexture(texture) ? new Grow(buffers.getBuffer(FxRenderTypes.clivarGrid(texture))) : new Nothing();
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
        render(dispatcher, entity, feet.subtract(camera), partial, type -> bodyTexture(FxRenderTypes.textureOf(type)) ? capture : new Nothing());
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

    // A new one each time it is asked for: an enchanted item in the hand joins its glint buffer with its own, and the
    // game refuses joining one buffer to itself ("Duplicate delegates").
    private static final class Nothing implements VertexConsumer {
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
    }

    // ---------- bits, cells, blood, the wall, the trail (FxWorld, end of the level) ----------

    private static ShaderInstance shader;
    private static Vec3 cam = Vec3.ZERO;
    private static Vec3 camRight = new Vec3(1, 0, 0), camUp = new Vec3(0, 1, 0);

    static void render(Vec3 camera) {
        double now = now();
        CUTS.values().removeIf(c -> now > c.end() || now < c.born - 40);
        TRAILS.removeIf(t -> now - t.born > t.ticks + 6 || now < t.born - 40);
        shader = FxShaders.sukunaFx;
        if (!active() || shader == null) {
            return;
        }
        cam = camera;
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = c.getLeftVector(), up = c.getUpVector();
        camRight = new Vec3(-left.x(), -left.y(), -left.z());
        camUp = new Vec3(up.x(), up.y(), up.z());
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
            trailTick(t, now);
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

    private static void drawCut(Cut c, double now) {
        double measure = c.snapAt >= 0 ? 1 : cl((now - c.born) / c.measure, 0, 1);
        // against a wall: each cut goes on into the wall's face, drawn with the body's line, until the middle breaks
        if (c.wall != null && c.breakAt < 0 && c.planes != null && c.entity != null) {
            wallCuts(c, measure, c.snapAt >= 0 ? now - c.snapAt : -1);
        }
        if (c.snapAt < 0 || c.planes == null) {
            return;
        }
        double st = now - c.snapAt;
        if (!c.burst) {
            c.burst = true;
            snapBurst(c);
        }
        // each cut shoots out of the body along its own plane, both ways: a long stroke that flashes, thins and tears
        if (!c.lethal || st < SIL + 4) {
            Vec3 feet = new Vec3(c.center.x, c.feet, c.center.z);
            for (int i = 0; i < c.planes.size(); i++) {
                ribbon(c, c.planes.get(i), i, feet, st);
            }
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

    // ---------- the cuts' planes ----------

    // Twelve cuts through a body w wide and h tall, as a measure is taken: five slices across from the shins to the head,
    // each tilted its own way; four lengthwise, each turned and set off the middle its own way; three diagonals. Each
    // runs in at its own moment (one after another in a shuffled order) from its own side, at its own width.
    private static List<Plane> planes(int seed, double w, double h) {
        List<Plane> out = new ArrayList<>();
        double sc = h / 1.8;
        double s = seed * 0.0137;
        int[] order = new int[PLANES];
        for (int i = 0; i < PLANES; i++) {
            order[i] = i;
        }
        for (int i = PLANES - 1; i > 0; i--) {
            int j = (int) Math.floor(hash(s + i * 7.31) * (i + 1));
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        double cy = 0.52 * h;
        for (int i = 0; i < PLANES; i++) {
            double h1 = hash(s + i * 1.91), h2 = hash(s + i * 3.73), h3 = hash(s + i * 5.17), h4 = hash(s + i * 8.29);
            Vec3 n;
            double d;
            if (i < 5) {
                double tilt = (6 + 22 * h1) * Mth.DEG_TO_RAD, around = h2 * Math.PI * 2;
                n = new Vec3(Math.sin(tilt) * Math.cos(around), Math.cos(tilt), Math.sin(tilt) * Math.sin(around));
                d = (0.13 + i * 0.185 + (h3 - 0.5) * 0.09) * h - cy;
            } else if (i < 9) {
                double yaw = (i - 5) * Math.PI / 4 + (h1 - 0.5) * 0.5, tilt = (h2 - 0.5) * 0.35;
                n = new Vec3(Math.cos(yaw) * Math.cos(tilt), Math.sin(tilt), Math.sin(yaw) * Math.cos(tilt));
                d = (i % 2 == 0 ? 1 : -1) * (0.12 + 0.3 * h3) * w;
            } else {
                double tilt = (38 + 20 * h1) * Mth.DEG_TO_RAD, around = h2 * Math.PI * 2;
                n = new Vec3(Math.sin(tilt) * Math.cos(around), Math.cos(tilt), Math.sin(tilt) * Math.sin(around));
                d = (h3 - 0.5) * 0.45 * h;
            }
            int rank = 0;
            for (int k = 0; k < PLANES; k++) {
                if (order[k] == i) {
                    rank = k;
                }
            }
            double t0 = 0.04 + 0.64 * rank / (PLANES - 1.0) + (h4 - 0.5) * 0.05;
            double dur = 0.15 + 0.13 * hash(s + i * 9.71);
            double width = (0.005 + 0.005 * hash(s + i * 4.43)) * sc;
            out.add(new Plane(n.normalize(), d, Math.max(0, t0), dur, width, hash(s + i * 6.67) * Math.PI * 2, hash(s + i * 2.39)));
        }
        return out;
    }

    private static void planeUniforms(ShaderInstance shader, List<Plane> planes) {
        for (int k = 0; k < 3; k++) {
            Matrix4f pm = new Matrix4f().zero(), qm = new Matrix4f().zero();
            for (int j = 0; j < 4; j++) {
                int i = k * 4 + j;
                if (planes == null || i >= planes.size()) {
                    continue;
                }
                Plane q = planes.get(i);
                pm.setColumn(j, new Vector4f((float) q.n().x, (float) q.n().y, (float) q.n().z, (float) q.d()));
                qm.setColumn(j, new Vector4f((float) q.t0(), (float) q.dur(), (float) q.w(), (float) q.ang()));
            }
            shader.safeGetUniform("P" + k).set(pm);
            shader.safeGetUniform("Q" + k).set(qm);
        }
    }

    // The body's frame to the world (souls_clivar.vsh turns the other way): x is the body's left, z ahead.
    private static Vec3 toWorld(Vec3 v, double yawDeg) {
        double r = yawDeg * Mth.DEG_TO_RAD, c = Math.cos(r), s = Math.sin(r);
        return new Vec3(c * v.x - s * v.z, v.y, s * v.x + c * v.z);
    }

    // One cut leaving the body: from the slit's middle out both ways along its plane, 1.6 to 3.4 blocks each side,
    // shooting out in 3 ticks, flashing white, then thinning and tearing apart (Shift+Z's stroke, mode 5).
    private static void ribbon(Cut c, Plane q, int i, Vec3 feet, double st) {
        double life = 20 + 8 * q.h();
        double s = st - q.h() * 1.5;
        if (s < 0 || s >= life) {
            return;
        }
        Vec3 n = toWorld(q.n(), c.yaw);
        Vec3 dir = toWorld(q.along(), c.yaw);
        Vec3 mid = feet.add(toWorld(new Vec3(0, 0.52 * c.height, 0).add(q.n().scale(q.d())), c.yaw));
        Vec3 view = cam.subtract(mid);
        Vec3 perp = dir.cross(view);
        if (perp.lengthSqr() < 1.0E-6) {
            return;
        }
        perp = perp.normalize();
        double reach = (1.2 + 1.3 * hash(i * 3.3 + c.seed)) * c.scale;
        double grow = out(cl(s / 3, 0, 1));
        double half = 0.55 * c.scale + reach * grow;
        double thick = (0.09 + 0.07 * q.h()) * c.scale;
        // the two halves part a little along the plane's normal, as if the cut had gone through the air too
        Vec3 at = mid.add(n.scale((q.h() - 0.5) * 0.12 * grow));
        SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(5);
        u.seed = (float) (i * 2.7 + c.seed % 53);
        u.s0 = 0;
        u.s1 = 1;
        u.skew = (float) (0.75 + 0.5 * hash(i * 5.1 + c.seed));
        u.rough = (float) (0.3 + 0.6 * hash(i * 7.9 + c.seed));
        u.grow = 1;
        u.flash = (float) (1 - cl(s / 4, 0, 1));
        u.close = (float) (cl((s - 5) / (life - 5), 0, 1) * 0.85);
        u.tear = (float) (cl((s - life * 0.45) / (life * 0.55), 0, 1) * 1.05);
        u.px = 1;
        quad(u, at, dir.scale(half), perp.scale(thick));
    }

    // The cuts on the wall: where each plane meets the wall's face, a stroke across the 3x3 drawn in as the plane's line
    // runs over the body; on the snap they flash, then they stay until the wall breaks (or fade with the body's).
    private static void wallCuts(Cut c, double measure, double st) {
        Vec3 m = c.wallNormal;
        Vec3 feet = c.entity.position();
        double yaw = c.entity.yBodyRot;
        double db = m.dot(c.wall);
        for (int i = 0; i < c.planes.size(); i++) {
            Plane q = c.planes.get(i);
            double prog = q.progress(measure);
            if (prog <= 0) {
                continue;
            }
            Vec3 n = toWorld(q.n(), yaw);
            Vec3 dir = n.cross(m);
            double len = dir.length();
            if (len < 0.2) {
                continue;
            }
            dir = dir.scale(1 / len);
            double da = n.dot(feet.add(toWorld(new Vec3(0, 0.52 * c.height, 0).add(q.n().scale(q.d())), yaw)));
            double k = n.dot(m);
            Vec3 p0 = n.scale(da - db * k).add(m.scale(db - da * k)).scale(1 / (1 - k * k));
            Vec3 p = p0.add(dir.scale(c.wall.subtract(p0).dot(dir)));
            if (p.distanceTo(c.wall) > 1.3) {
                continue;
            }
            Vec3 perp = m.cross(dir).normalize();
            SukunaCutsRenderer.U u = SukunaCutsRenderer.U.of(5);
            u.seed = (float) (i * 4.1 + 17 + c.seed % 41);
            u.s0 = 0;
            u.s1 = 1;
            u.skew = (float) (0.8 + 0.4 * q.h());
            u.rough = (float) (0.4 + 0.5 * q.h());
            u.grow = (float) prog;
            u.flash = (float) (st >= 0 ? 1 - cl(st / 4, 0, 1) : 1 - prog);
            u.close = (float) (st >= 0 ? cl((st - SIL) / FADE, 0, 1) * 0.8 : 0.35);
            u.px = 1;
            quad(u, p.add(m.scale(0.016)), dir.scale(1.6), perp.scale(0.09 * c.scale));
        }
    }

    // ---------- the procedural smoke (Fumaça Nova, SmokeParticle), spawned here on the client ----------

    private static final net.minecraft.util.RandomSource RANDOM = net.minecraft.util.RandomSource.createThreadSafe();

    private static void fx(int style, int color, double alpha, double size, double grow, int life, double friction, double gravity, double flat,
            double stretch, Vec3 at, Vec3 v) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        level.addAlwaysVisibleParticle(com.starkinhon.battlegroundsmod.fx.ModParticles.fx(style, color, (float) alpha, (float) size, (float) grow, life,
                (float) friction, (float) gravity, (float) flat, (float) stretch), true, at.x, at.y, at.z, v.x, v.y, v.z);
    }

    private static double rr() {
        return RANDOM.nextDouble();
    }

    private static double gg() {
        return RANDOM.nextGaussian();
    }

    // The snap, out of the body: white sparks thrown along every slit, a fine red mist, a few small dark puffs.
    private static void snapBurst(Cut c) {
        Vec3 feet = new Vec3(c.center.x, c.feet, c.center.z);
        double sc = c.scale, R = 0.55 * c.height;
        for (int i = 0; i < c.planes.size(); i++) {
            Plane q = c.planes.get(i);
            Vec3 n = toWorld(q.n(), c.yaw);
            Vec3 dir = toWorld(q.along(), c.yaw);
            Vec3 mid = feet.add(toWorld(new Vec3(0, 0.52 * c.height, 0).add(q.n().scale(q.d())), c.yaw));
            int sparks = 3 + RANDOM.nextInt(3);
            for (int k = 0; k < sparks; k++) {
                double side = rr() < 0.5 ? -1 : 1;
                Vec3 at = mid.add(dir.scale(side * R * (0.3 + 0.6 * rr())));
                Vec3 v = dir.scale(side * (0.3 + 0.5 * rr()) * sc).add(n.scale(gg() * 0.06)).add(0, 0.03 + rr() * 0.05, 0);
                fx(com.starkinhon.battlegroundsmod.fx.SmokeOptions.WISP, 0xFFFFFF, 0.9, (0.025 + 0.02 * rr()) * sc, 0, 5 + RANDOM.nextInt(5), 0.82, 0.02,
                        1, 6 + rr() * 6, at, v);
            }
            if (!c.lethal || rr() < 0.5) {
                Vec3 at = mid.add(dir.scale(gg() * R * 0.3));
                fx(com.starkinhon.battlegroundsmod.fx.SmokeOptions.BILLOW, 0x6E0A12, 0.55, (0.12 + 0.08 * rr()) * sc, 1.2, 12 + RANDOM.nextInt(8), 0.88,
                        0.025, 1, 0, at, n.scale((rr() < 0.5 ? -1 : 1) * (0.05 + 0.06 * rr())));
            }
        }
        // a few small dark puffs out of the middle, thin and quick (big pale ink blobs read as paper in the game)
        for (int k = 0; k < 4; k++) {
            Vec3 at = c.center.add(gg() * 0.15 * sc, gg() * 0.3 * sc, gg() * 0.15 * sc);
            Vec3 v = at.subtract(c.center).normalize().scale(0.05 + 0.05 * rr()).add(0, 0.015, 0);
            fx(com.starkinhon.battlegroundsmod.fx.SmokeOptions.BILLOW, 0x1A1418, 0.5, (0.18 + 0.1 * rr()) * sc, 1.2, 12 + RANDOM.nextInt(8), 0.86, -0.006,
                    1, 0, at, v);
        }
    }

    // Slammed into the wall: dust of the wall's own colour bursting off it, grit thrown back, a darker puff in the middle.
    private static int wallColor(Vec3 at, Vec3 normal) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0xB9AA90 : com.starkinhon.battlegroundsmod.fx.Smoke.groundColor(level, at.subtract(normal.scale(0.6)).add(0, 0.6, 0));
    }

    private static void wallDust(Vec3 at, Vec3 normal, int color) {
        Vec3 side = new Vec3(0, 1, 0).cross(normal);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        for (int k = 0; k < 22; k++) {
            double a = rr() * Math.PI * 2;
            Vec3 spread = side.scale(Math.cos(a)).add(0, Math.sin(a), 0);
            fx(com.starkinhon.battlegroundsmod.fx.SmokeOptions.BILLOW, color, 0.8, 0.25 + 0.25 * rr(), 1.3, 16 + RANDOM.nextInt(10), 0.85, -0.005, 1, 0,
                    at.add(spread.scale(0.3)), spread.scale(0.12 + 0.12 * rr()).add(normal.scale(0.05 + 0.08 * rr())));
        }
        for (int k = 0; k < 12; k++) {
            Vec3 v = normal.scale(0.18 + 0.2 * rr()).add(side.scale(gg() * 0.15)).add(0, 0.08 + rr() * 0.15, 0);
            fx(com.starkinhon.battlegroundsmod.fx.SmokeOptions.SPECK, com.starkinhon.battlegroundsmod.fx.Smoke.mix(color, 0x1A1714, 0.4), 1,
                    0.035 + 0.03 * rr(), 0, 14 + RANDOM.nextInt(8), 0.94, 0.06, 1, 0, at, v);
        }
    }

    // The sprint, a tick at a time: dust kicked off the ground under his feet and wind lines streaming back along him.
    private static void trailTick(Trail t, double now) {
        int due = (int) Math.min(t.ticks, Math.floor(now - t.born) + 1);
        Entity e = FxAnchor.entity(t.id);
        while (t.spawned < due) {
            t.spawned++;
            if (e == null) {
                continue;
            }
            Vec3 d = Vec3.directionFromRotation(0, e.getYRot());
            Vec3 side = new Vec3(-d.z, 0, d.x);
            Vec3 feet = e.position();
            ClientLevel level = Minecraft.getInstance().level;
            int color = level == null ? 0xB9AA90 : com.starkinhon.battlegroundsmod.fx.Smoke.groundColor(level, feet.add(0, 0.25, 0));
            if (e.onGround()) {
                for (int k = 0; k < 2; k++) {
                    fx(com.starkinhon.battlegroundsmod.fx.SmokeOptions.DUST, color, 0.75, 0.22 + 0.16 * rr(), 1.2, 10 + RANDOM.nextInt(6), 0.85, 0, 0.5, 0,
                            feet.add(side.scale(gg() * 0.2)).add(0, 0.1, 0), d.scale(-0.08 - 0.06 * rr()).add(side.scale(gg() * 0.05)).add(0, 0.02, 0));
                }
            }
            for (int k = 0; k < 2; k++) {
                fx(com.starkinhon.battlegroundsmod.fx.SmokeOptions.WISP, 0xFFFFFF, 0.55, 0.06 + 0.04 * rr(), 0, 5 + RANDOM.nextInt(3), 0.8, 0, 1,
                        7 + rr() * 6, feet.add(0, 0.3 + rr() * 1.4, 0).add(side.scale(gg() * 0.35)), d.scale(-0.3));
            }
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
}
