package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.skill.VfxSpec.Efeito;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Effects that live in the world, drawn without any stored state: each one is a function of its age and of a
// fixed random number per part (hash), like the big anime mods do. Nothing is simulated, so a hundred rays cost
// the same as one and they never drift between frames.
public final class FxWorld {
    private record Piece(Efeito e, Vec3 pos, Vec3 dir, long start, int life, int seed) {
    }

    private static final List<Piece> PIECES = new ArrayList<>();
    private static final java.util.SequencedMap<RenderType, ByteBufferBuilder> FIXED = new java.util.LinkedHashMap<>();
    private static MultiBufferSource.BufferSource buffers;
    private static int seeds;

    private FxWorld() {
    }

    private static MultiBufferSource.BufferSource buffers() {
        if (buffers == null) {
            for (RenderType type : List.of(FxRenderTypes.VFX_ALPHA, FxRenderTypes.VFX_ADD, FxRenderTypes.FLAT_ALPHA, FxRenderTypes.FLAT_ADD)) {
                FIXED.put(type, new ByteBufferBuilder(type.bufferSize()));
            }
            buffers = MultiBufferSource.immediateWithBuffers(FIXED, new ByteBufferBuilder(256));
        }
        return buffers;
    }

    public static void spawn(Efeito e, Vec3 pos, Vec3 dir, int life) {
        PIECES.add(new Piece(e, pos, dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : dir.normalize(), ScreenFx.now(), Math.max(1, life), seeds++ * 7919));
    }

    public static void tick() {
        long now = ScreenFx.now();
        PIECES.removeIf(p -> now - p.start() > p.life());
    }

    public static void clear() {
        PIECES.clear();
    }

    // End of the level: world effects, then the screen bending and blur on top of them.
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        FxPost.newFrame();
        ScreenFx.capture(event);
        if (PIECES.isEmpty() && !ScreenFx.needsScene()) {
            return;
        }
        boolean flat = FxShaders.flat();
        if (!flat) {
            FxPost.captureScene();
        }
        if (!PIECES.isEmpty()) {
            draw(event, flat);
        }
        if (!flat) {
            ScreenFx.levelPasses();
        }
    }

    private static void draw(RenderLevelStageEvent event, boolean flat) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);

        MultiBufferSource.BufferSource source = buffers();
        FxDraw.begin(source, event.getCamera(), flat);
        Vec3 camera = event.getCamera().getPosition();
        double now = ScreenFx.now() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Piece piece : PIECES) {
            double age = now - piece.start();
            if (age >= 0 && age < piece.life()) {
                render(piece, piece.pos().subtract(camera).toVector3f(), (float) age, (float) (age / piece.life()));
            }
        }
        if (flat) {
            source.endBatch(FxRenderTypes.FLAT_ALPHA);
            source.endBatch(FxRenderTypes.FLAT_ADD);
        } else {
            source.endBatch(FxRenderTypes.VFX_ALPHA);
            FxPost.beginGlow();
            source.endBatch(FxRenderTypes.VFX_ADD);
            FxPost.endGlow(FxConfig.on(FxConfig.BLOOM));
        }

        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void render(Piece p, Vector3f at, float age, float t) {
        Efeito e = p.e();
        Vector3f dir = p.dir().toVector3f();
        at.add(new Vector3f(dir).mul((float) d(e.deslocamento(), 0))).add(0, (float) d(e.altura(), 0), 0);
        float[] cor = FxDraw.rgb(FxDraw.parse(e.cor(), 0xFF5020));
        float[] cor2 = FxDraw.rgb(FxDraw.parse(e.cor2(), 0xFFFFFF));
        float size = (float) d(e.tamanho(), 2);
        float fade = (1 - t) * (1 - t);
        switch (e.tipo()) {
            case "clarao" -> {
                // A punch of light: blows up fast, then dies away.
                float grow = 0.35f + 0.65f * easeOut(Math.min(1, t * 4));
                float rays = Mth.clamp(i(e.quantidade(), 6) / 32f, 0, 1);
                FxDraw.billboard(at, size * grow * 2.2f, age * 0.01f, FxDraw.FLARE, rays, cor, fade, true);
                FxDraw.billboard(at, size * grow, 0, FxDraw.SOFT, 0.8f, cor2, fade * 1.2f, true);
            }
            case "estrela" -> {
                // Long thin rays shot out of one point in every direction.
                int count = i(e.quantidade(), 24);
                float length = (float) d(e.comprimento(), 8) * (0.3f + 0.7f * easeOut(Math.min(1, t * 2.5f)));
                for (int k = 0; k < count; k++) {
                    Vector3f ray = sphere(p.seed(), k);
                    float l = length * (0.5f + 0.7f * hash(p.seed(), k, 3));
                    Vector3f from = new Vector3f(ray).mul(l * 0.15f).add(at);
                    Vector3f to = new Vector3f(ray).mul(l).add(at);
                    FxDraw.ribbon(from, to, size * 0.12f * (0.6f + hash(p.seed(), k, 4)), FxDraw.STREAK, 0.7f, cor, fade, true);
                }
            }
            case "anelChao" -> {
                // Shock ring running over the ground.
                float radius = (float) d(e.raio(), 8) * easeOut(t);
                float thick = Mth.clamp(size / Math.max(0.5f, radius * 2), 0.03f, 1);
                FxDraw.disk(new Vector3f(at).add(0, 0.08f, 0), new Vector3f(0, 1, 0), radius * 2, FxDraw.RING, thick, cor, (1 - t), true);
                FxDraw.disk(new Vector3f(at).add(0, 0.06f, 0), new Vector3f(0, 1, 0), radius * 1.4f, FxDraw.SOFT, 0, cor, fade * 0.35f, true);
            }
            case "anel" -> {
                // Shock ring standing across the direction of the move (a sonic boom).
                float radius = (float) d(e.raio(), 4) * easeOut(t);
                float thick = Mth.clamp(size / Math.max(0.5f, radius * 2), 0.03f, 1);
                FxDraw.disk(at, dir, radius * 2, FxDraw.RING, thick, cor, 1 - t, true);
            }
            case "raios" -> bolts(p, e, at, age, t, cor, cor2, size);
            case "feixe" -> {
                // A beam along the direction: grows out fast, holds, then thins away. Outer glow plus white core.
                float length = (float) d(e.comprimento(), 20) * easeOut(Math.min(1, t * 6));
                float width = size * (t < 0.7f ? 1 : 1 - (t - 0.7f) / 0.3f) * (0.92f + 0.08f * Mth.sin(age * 2.1f));
                Vector3f end = new Vector3f(dir).mul(length).add(at);
                FxDraw.ribbon(at, end, width * 2.2f, FxDraw.BEAM, 0.15f, cor, 0.8f, true);
                FxDraw.ribbon(at, end, width, FxDraw.BEAM, 0.7f, cor2, 1, true);
                FxDraw.billboard(at, width * 3, age * 0.2f, FxDraw.FLARE, 0.25f, cor, 0.9f, true);
            }
            case "esfera" -> {
                // Plasma ball: halo, hot core and arcs running around it.
                float pulse = 1 + 0.06f * Mth.sin(age * 1.7f);
                float in = easeOut(Math.min(1, t * 5)) * (t > 0.85f ? (1 - t) / 0.15f : 1);
                FxDraw.billboard(at, size * 2.6f * pulse * in, 0, FxDraw.SOFT, 0.2f, cor, 0.7f, true);
                FxDraw.billboard(at, size * pulse * in, 0, FxDraw.SOFT, 0.9f, cor2, 1, true);
                int arcs = i(e.quantidade(), 6);
                for (int k = 0; k < arcs; k++) {
                    Vector3f axis = sphere(p.seed(), k);
                    float a = age * (0.25f + 0.2f * hash(p.seed(), k, 1)) + k;
                    Vector3f x = new Vector3f(axis).cross(Math.abs(axis.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
                    Vector3f y = new Vector3f(axis).cross(x);
                    float r = size * 0.62f * in;
                    Vector3f from = new Vector3f(x).mul(Mth.cos(a) * r).add(new Vector3f(y).mul(Mth.sin(a) * r)).add(at);
                    Vector3f to = new Vector3f(x).mul(Mth.cos(a + 1.1f) * r).add(new Vector3f(y).mul(Mth.sin(a + 1.1f) * r)).add(at);
                    FxDraw.ribbon(from, to, size * 0.08f, FxDraw.STREAK, 0.8f, cor2, 0.9f, true);
                }
            }
            case "fumaca" -> {
                // Smoke pushed outwards and up, growing and thinning.
                int count = i(e.quantidade(), 14);
                float reach = (float) d(e.raio(), 4);
                float[] grey = FxDraw.rgb(FxDraw.parse(e.cor(), 0x3A3436));
                for (int k = 0; k < count; k++) {
                    float a = hash(p.seed(), k, 0) * Mth.TWO_PI;
                    float out = reach * (0.3f + 0.7f * hash(p.seed(), k, 1)) * easeOut(t);
                    Vector3f pos = new Vector3f(Mth.cos(a) * out, t * reach * 0.4f * (0.5f + hash(p.seed(), k, 2)), Mth.sin(a) * out).add(at);
                    float s = size * (0.6f + t * 1.2f) * (0.7f + 0.6f * hash(p.seed(), k, 3));
                    FxDraw.billboard(pos, s, hash(p.seed(), k, 4) * 6 + age * 0.02f, FxDraw.SMOKE, t, grey, (1 - t) * 0.75f, false);
                }
            }
            case "faiscas" -> {
                // Sparks thrown out and falling, drawn as streaks along their speed.
                int count = i(e.quantidade(), 30);
                float speed = (float) d(e.velocidade(), 0.6);
                float length = (float) d(e.comprimento(), 1.2);
                for (int k = 0; k < count; k++) {
                    float life = 0.5f + 0.5f * hash(p.seed(), k, 5);
                    if (t > life) {
                        continue;
                    }
                    Vector3f v = sphere(p.seed(), k).add(0, 0.5f, 0).mul(speed * (0.5f + hash(p.seed(), k, 6)));
                    Vector3f pos = new Vector3f(v).mul(age).add(at).add(0, -0.02f * age * age, 0);
                    Vector3f vel = new Vector3f(v).add(0, -0.04f * age, 0);
                    Vector3f tail = new Vector3f(vel).normalize().mul(-length).add(pos);
                    FxDraw.ribbon(tail, pos, size * 0.06f, FxDraw.STREAK, 0.9f, FxDraw.mix(cor2, cor, t / life), 1 - t / life, true);
                }
            }
            case "fogo" -> {
                // Balls of fire rolling up and burning out, white-yellow at first, cor at the end.
                int count = i(e.quantidade(), 10);
                float reach = (float) d(e.raio(), 2);
                for (int k = 0; k < count; k++) {
                    float delay = hash(p.seed(), k, 7) * 0.35f;
                    float lt = (t - delay) / (1 - delay);
                    if (lt <= 0 || lt >= 1) {
                        continue;
                    }
                    Vector3f off = sphere(p.seed(), k).mul(reach * (0.2f + 0.8f * easeOut(lt)));
                    off.y = Math.abs(off.y) + lt * reach * 0.8f;
                    float s = size * (0.5f + lt) * (0.7f + 0.6f * hash(p.seed(), k, 8));
                    FxDraw.billboard(new Vector3f(at).add(off), s, hash(p.seed(), k, 9) * 6, FxDraw.FIRE, lt, FxDraw.mix(cor2, cor, Math.min(1, lt * 1.6f)),
                            1, true);
                }
            }
            default -> {
            }
        }
    }

    // Jagged lightning from the point outwards, re-rolled every two ticks so it crackles.
    private static void bolts(Piece p, Efeito e, Vector3f at, float age, float t, float[] cor, float[] cor2, float size) {
        int count = i(e.quantidade(), 6);
        float length = (float) d(e.comprimento(), 6);
        int epoch = (int) (age / 2);
        for (int k = 0; k < count; k++) {
            int seed = p.seed() + epoch * 131 + k * 17;
            if (hash(seed, k, 11) > 0.85f) {
                continue; // a missing bolt now and then, so it flickers
            }
            Vector3f dir = sphere(p.seed(), k);
            if (dir.y < -0.2f) {
                dir.y = -dir.y;
            }
            Vector3f side = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            Vector3f up = new Vector3f(dir).cross(side);
            Vector3f last = new Vector3f(at);
            int segments = 8;
            for (int s = 1; s <= segments; s++) {
                float f = s / (float) segments;
                float jag = length * 0.12f * (s == segments ? 0.5f : 1);
                Vector3f next = new Vector3f(dir).mul(length * f).add(at)
                        .add(new Vector3f(side).mul((hash(seed, s, 1) * 2 - 1) * jag))
                        .add(new Vector3f(up).mul((hash(seed, s, 2) * 2 - 1) * jag));
                float o = (1 - t) * (1 - f * 0.5f);
                FxDraw.ribbon(last, next, size * 0.25f, FxDraw.BEAM, 0.25f, cor, o * 0.8f, true);
                FxDraw.ribbon(last, next, size * 0.09f, FxDraw.BEAM, 0.9f, cor2, o, true);
                last = next;
            }
        }
    }

    // Fixed random direction for part k (golden spiral jittered by the hash).
    private static Vector3f sphere(int seed, int k) {
        float y = hash(seed, k, 20) * 2 - 1;
        float a = hash(seed, k, 21) * Mth.TWO_PI;
        float r = Mth.sqrt(Math.max(0, 1 - y * y));
        return new Vector3f(Mth.cos(a) * r, y, Mth.sin(a) * r);
    }

    private static float hash(int seed, int k, int salt) {
        int h = seed * 374761393 + k * 668265263 + salt * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float easeOut(float t) {
        float u = 1 - Mth.clamp(t, 0, 1);
        return 1 - u * u * u;
    }

    static double d(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    static int i(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
