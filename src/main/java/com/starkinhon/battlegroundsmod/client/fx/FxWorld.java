package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.skill.VfxSpec.Efeito;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Effects that live in the world, drawn without any stored state: each one is a function of its age and of a
// fixed random number per part (hash), like the big anime mods do. Nothing is simulated, so a hundred rays cost
// the same as one and they never drift between frames.
public final class FxWorld {
    // follow: the entity it sticks to (-1 for none). life covers the whole show, emission stops at emitFor.
    private record Piece(String skill, Efeito e, Vec3 pos, Vec3 dir, long start, int life, int emitFor, int seed, int follow) {
    }

    private static final List<Piece> PIECES = new ArrayList<>();
    private static final java.util.SequencedMap<RenderType, ByteBufferBuilder> FIXED = new java.util.LinkedHashMap<>();
    private static MultiBufferSource.BufferSource buffers;
    private static int seeds;

    private FxWorld() {
    }

    static MultiBufferSource.BufferSource buffers() {
        if (buffers == null) {
            for (RenderType type : List.of(FxRenderTypes.VFX_ALPHA, FxRenderTypes.VFX_ADD, FxRenderTypes.FLAT_ALPHA, FxRenderTypes.FLAT_ADD,
                    FxRenderTypes.SUS_DEPTH, FxRenderTypes.SUS_COLOR, FxRenderTypes.SUS_GLOW, FxRenderTypes.SUS_FLAT)) {
                FIXED.put(type, new ByteBufferBuilder(type.bufferSize()));
            }
            buffers = MultiBufferSource.immediateWithBuffers(FIXED, new ByteBufferBuilder(16384));
        }
        return buffers;
    }

    public static void spawn(String skill, Efeito e, Vec3 pos, Vec3 dir, int life, int follow) {
        life = Math.max(1, life);
        int total = life;
        if ("jatoFogo".equals(e.tipo())) {
            // Flames already in the air keep flying after the breath stops.
            total += (int) Math.ceil(d(e.comprimento(), 25) / Math.max(0.1, d(e.velocidade(), 1.4))) + 2;
        }
        PIECES.add(new Piece(skill, e, pos, dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : dir.normalize(), ScreenFx.now(), total, life, seeds++ * 7919,
                follow));
    }

    // Fades out every effect of this skill stuck to this entity (a counter window that closed early, say).
    public static void stopFollowing(String skill, int follow) {
        long now = ScreenFx.now();
        PIECES.replaceAll(p -> p.follow() == follow && p.skill().equals(skill) && now - p.start() < p.life() - 10
                ? new Piece(p.skill(), p.e(), p.pos(), p.dir(), now - p.life() + 10, p.life(), p.emitFor(), p.seed(), p.follow()) : p);
    }

    // Every moving piece of this skill (a slash in flight) is gone at once: the attack was pulverized.
    public static void erase(String skill) {
        PIECES.removeIf(p -> p.skill().equals(skill) && ("corte".equals(p.e().tipo()) || "bolinha".equals(p.e().tipo())));
    }

    public static void tick() {
        long now = ScreenFx.now();
        PIECES.removeIf(p -> now - p.start() > p.life());
        BIRTHS.keySet().removeIf(seed -> PIECES.stream().noneMatch(p -> p.seed() == seed));
        DestructionFx.forget(seed -> PIECES.stream().anyMatch(p -> p.seed() == seed));
        for (Piece p : PIECES) {
            if ("jatoFogo".equals(p.e().tipo())) {
                recordBirth(p, (int) (now - p.start()));
            }
        }
    }

    public static void clear() {
        PIECES.clear();
        BIRTHS.clear();
        DestructionFx.clear();
    }

    // Where the mouth was and where it aimed on each tick of a fire stream (world space), so flames already out keep
    // the path they were born on instead of swinging with the camera.
    private static final java.util.Map<Integer, Vec3[]> BIRTHS = new java.util.HashMap<>();
    private static Vec3 camera = Vec3.ZERO;

    private static void recordBirth(Piece p, int tick) {
        if (tick < 0 || tick >= p.emitFor()) {
            return;
        }
        Vec3[] births = BIRTHS.computeIfAbsent(p.seed(), k -> new Vec3[p.emitFor() * 2]);
        if (births[tick * 2] != null) {
            return;
        }
        Vec3 at = FxAnchor.point(p.follow(), p.e().seguir(), 1);
        Vec3 look = FxAnchor.look(p.follow(), p.e().seguir(), 1);
        births[tick * 2] = (at == null ? p.pos() : at).add(0, d(p.e().altura(), 0), 0);
        births[tick * 2 + 1] = look == null ? p.dir() : look.normalize();
    }

    // End of the level: world effects, then the screen bending and blur on top of them.
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        FxPost.newFrame();
        ScreenFx.capture(event);
        boolean world = !PIECES.isEmpty() || MeteorRenderer.hasTrails() || Aura.active() || KatonBallRenderer.active() || AshDeath.hasSparks()
                || SpaceScene.active() || EsferaRenderer.active() || HakaiRenderer.active() || EnergyRenderer.active() || SusanooArm.active();
        if (!world && !ScreenFx.needsScene()) {
            return;
        }
        if (SpaceScene.active()) {
            // The Esfera's space cutscene covers the world before anything else is drawn on it.
            SpaceScene.backdrop(event);
        }
        boolean flat = FxShaders.flat();
        if (!flat) {
            FxPost.captureScene();
        }
        if (world) {
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
        FxWorld.camera = camera;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = ScreenFx.now() + partial;
        // In the Esfera's outer space only the scene's own effects show: nothing from the world below follows him there.
        boolean space = SpaceScene.active();
        for (Piece piece : PIECES) {
            double age = now - piece.start();
            if (space && !SpaceScene.owns(piece.skill())) {
                continue;
            }
            if (age >= 0 && age < piece.life()) {
                Vec3 at = FxAnchor.point(piece.follow(), piece.e().seguir(), partial);
                Vec3 look = FxAnchor.look(piece.follow(), piece.e().seguir(), partial);
                float opacity = FxAnchor.firstPersonSelf(piece.follow()) ? (float) d(piece.e().primeiraPessoa(), 1) : 1;
                if (opacity > 0) {
                    render(piece, (at == null ? piece.pos() : at).subtract(camera).toVector3f(), (look == null ? piece.dir() : look).toVector3f(),
                            (float) age, (float) (age / piece.life()), opacity);
                }
            }
        }
        if (space) {
            SpaceScene.render(camera, now, partial);
        } else {
            MeteorRenderer.emitTrails(camera, now, partial);
            KatonBallRenderer.render(camera, now, partial);
            Aura.render(event.getCamera(), now, partial);
            AshDeath.sparks(camera, now, partial);
            EsferaRenderer.render(camera, now, partial);
            HakaiRenderer.render(camera, now, partial);
            EnergyRenderer.render(camera, now, partial);
            SusanooArm.render(source, flat, camera, now, partial);
        }
        if (flat) {
            source.endBatch(FxRenderTypes.SUS_FLAT);
            source.endBatch(FxRenderTypes.FLAT_ALPHA);
            source.endBatch(FxRenderTypes.FLAT_ADD);
            endDecals(source);
        } else {
            // The Susanoo arm's depth goes in first, so its color only lands on the front surface.
            source.endBatch(FxRenderTypes.SUS_DEPTH);
            source.endBatch(FxRenderTypes.SUS_COLOR);
            source.endBatch(FxRenderTypes.VFX_ALPHA);
            endDecals(source);
            boolean outline = !space && Aura.drawMask(event, camera, partial);
            FxPost.beginGlow();
            if (outline) {
                Aura.outline(now);
            }
            source.endBatch(FxRenderTypes.SUS_GLOW);
            source.endBatch(FxRenderTypes.VFX_ADD);
            FxPost.endGlow(FxConfig.on(FxConfig.BLOOM));
        }
        if (!space && HakaiRenderer.active()) {
            // Dark lines on top of the glow, so the light doesn't wash them out.
            FxDraw.begin(source, event.getCamera(), flat);
            HakaiRenderer.overlay(camera, now, partial);
            source.endBatch(flat ? FxRenderTypes.FLAT_ALPHA : FxRenderTypes.VFX_ALPHA);
        }

        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static final ResourceLocation MANGEKYO = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/mangekyo.png");

    private static void endDecals(MultiBufferSource.BufferSource source) {
        for (RenderType type : USED_DECALS) {
            source.endBatch(type);
        }
        USED_DECALS.clear();
    }

    private static void render(Piece p, Vector3f at, Vector3f dir, float age, float t, float opacity) {
        Efeito e = p.e();
        if (!"jatoFogo".equals(e.tipo()) && !"rastroBoca".equals(e.tipo())) {
            Vector3f flat = new Vector3f(dir.x, 0, dir.z);
            Vector3f along = "olho".equals(e.tipo()) && flat.lengthSquared() > 1.0E-4f ? flat.normalize() : dir;
            at.add(new Vector3f(along).mul((float) d(e.deslocamento(), 0)));
        }
        at.add(0, (float) d(e.altura(), 0), 0);
        float[] cor = FxDraw.rgb(FxDraw.parse(e.cor(), 0xFF5020));
        float[] cor2 = FxDraw.rgb(FxDraw.parse(e.cor2(), 0xFFFFFF));
        float size = (float) d(e.tamanho(), 2);
        // Long effects (an indicator for as long as a stun lasts) fade in and out instead of shrinking away.
        float fadeIn = Math.min(1, age / 5), fadeOut = Math.min(1, (p.life() - age) / 10);
        float hold = fadeIn * fadeOut * opacity;
        float fade = (1 - t) * (1 - t) * opacity;
        float spin = age * (float) d(e.girar(), 0.08);
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
                // With a speed it runs out at that pace (matching the server's shockwave), else it eases out.
                float radius = e.velocidade() != null ? Math.min((float) d(e.raio(), 8), (float) (e.velocidade() * age)) : (float) d(e.raio(), 8) * easeOut(t);
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
            case "jatoFogo" -> fireStream(p, e, at, dir, age, cor, cor2, size, opacity);
            case "rastroBoca" -> mouthTrail(p, e, at, dir, age, cor, cor2, hold);
            case "parede" -> {
                // Wall of light pillars around the body, slowly turning, bright at the middle height.
                int count = i(e.quantidade(), 22);
                float radius = (float) d(e.raio(), 1.7);
                float height = (float) d(e.comprimento(), 2.8);
                float width = Mth.TWO_PI * radius / count * 1.7f;
                for (int k = 0; k < count; k++) {
                    float a = k * Mth.TWO_PI / count + spin * 0.4f;
                    float flicker = 0.65f + 0.35f * Mth.sin(age * 0.5f + k * 2.3f);
                    Vector3f base = new Vector3f(Mth.cos(a) * radius, -0.1f, Mth.sin(a) * radius).add(at);
                    Vector3f top = new Vector3f(base).add(0, height * (0.85f + 0.15f * hash(p.seed(), k, 1)), 0);
                    FxDraw.ribbon(base, top, width, FxDraw.STREAK, 0.35f, cor, hold * 0.55f * flicker, true);
                }
                FxDraw.disk(new Vector3f(at).add(0, 0.05f, 0), new Vector3f(0, 1, 0), radius * 2.3f, FxDraw.RING, 0.12f, cor, hold * 0.8f, true);
                FxDraw.disk(new Vector3f(at).add(0, 0.04f, 0), new Vector3f(0, 1, 0), radius * 2.6f, FxDraw.SOFT, 0.1f, cor, hold * 0.35f, true);
            }
            case "anelCabeca" -> {
                // Ring of light around the head, tilting a little as it turns, with three sparks running on it.
                float radius = (float) d(e.raio(), 0.8);
                Vector3f normal = new Vector3f(Mth.sin(spin) * 0.18f, 1, Mth.cos(spin) * 0.18f);
                FxDraw.disk(new Vector3f(at), normal, radius * 2, FxDraw.RING, Mth.clamp(size / (radius * 2), 0.03f, 1), cor, hold, true);
                for (int k = 0; k < 3; k++) {
                    float a = spin * 3 + k * Mth.TWO_PI / 3;
                    Vector3f spark = new Vector3f(Mth.cos(a) * radius, -Mth.sin(spin) * 0.18f * Mth.cos(a) * radius, Mth.sin(a) * radius).add(at);
                    FxDraw.billboard(spark, radius * 0.5f, 0, FxDraw.FLARE, 4 / 32f, cor2, hold, true);
                }
            }
            case "olhoChao" -> {
                // The eye on the ground, glowing: a red halo bleeding onto the floor (bloomed), a hot rim, the eye
                // itself and a brighter copy added on top that breathes slowly.
                Vector3f floor = new Vector3f(at).add(0, 0.06f, 0);
                Vector3f up = new Vector3f(0, 1, 0);
                float[] glow = FxDraw.rgb(FxDraw.parse(e.cor(), 0xFF1A1A));
                float breathe = 0.75f + 0.25f * Mth.sin(age * 0.22f);
                FxDraw.disk(new Vector3f(floor).add(0, -0.01f, 0), up, size * 1.7f, FxDraw.SOFT, 0, glow, hold * 0.55f * breathe, true);
                FxDraw.disk(new Vector3f(floor).add(0, 0.012f, 0), up, size * 1.04f, FxDraw.RING, 0.05f, glow, hold * 0.9f * breathe, true);
                FxDraw.disk(decal(e), floor, up, size, -spin, FxDraw.SOLID, 0, new float[] {1, 1, 1}, hold);
                FxDraw.disk(decalGlow(e), new Vector3f(floor).add(0, 0.006f, 0), up, size, -spin, FxDraw.SOLID, 0, glow, hold * 0.6f * breathe);
            }
            case "olho" -> FxDraw.billboard(decal(e), at, size, spin, new float[] {1, 1, 1}, hold);
            case "bracoEspada" -> SusanooArm.sword(buffers(), FxShaders.flat(), at, dir, age, p.life(), i(e.golpeTicks(), 9), (float) d(e.tamanho(), 1),
                    opacity, p.seed());
            case "corte" -> slash(e, at, dir, age, t, cor, cor2, size, opacity);
            case "bolinha" -> DestructionFx.ball(e, at, dir, age, cor, cor2, size, opacity);
            case "rachaduras" -> DestructionFx.cracks(e, p.seed(), p.pos(), at, age, t, cor, cor2, size, opacity);
            case "pilares" -> DestructionFx.pillars(e, p.seed(), at, age, t, cor, cor2, size, opacity);
            case "plasma" -> DestructionFx.plasma(e, p.seed(), at, age, t, cor, cor2, size, opacity);
            case "nuvem" -> DestructionFx.cloud(e, p.seed(), at, age, t, cor, cor2, size, opacity);
            default -> {
            }
        }
    }

    private static final java.util.Set<RenderType> USED_DECALS = new java.util.LinkedHashSet<>();

    private static com.mojang.blaze3d.vertex.VertexConsumer decal(Efeito e) {
        RenderType type = FxRenderTypes.decal(texture(e));
        USED_DECALS.add(type);
        return buffers.getBuffer(type);
    }

    private static com.mojang.blaze3d.vertex.VertexConsumer decalGlow(Efeito e) {
        RenderType type = FxRenderTypes.decalGlow(texture(e));
        USED_DECALS.add(type);
        return buffers.getBuffer(type);
    }

    private static ResourceLocation texture(Efeito e) {
        ResourceLocation rl = e.textura() == null ? null : ResourceLocation.tryParse(e.textura());
        return rl == null ? MANGEKYO : rl;
    }

    // Katon made only of shader fire: flames leave the mouth at velocidade, swell from small white-yellow to big red,
    // burn out at the tip, a few going dark as smoke. Each flame is a fixed hash of its birth tick, so the stream
    // costs nothing to keep and never jitters.
    private static void fireStream(Piece p, Efeito e, Vector3f mouth, Vector3f dir, float age, float[] cor, float[] cor2, float size,
            float opacity) {
        float length = (float) d(e.comprimento(), 25);
        float radius = (float) d(e.raio(), 10);
        float speed = (float) Math.max(0.1, d(e.velocidade(), 1.4));
        int perTick = i(e.quantidade(), 14);
        float travel = length / speed;
        float[] smoke = FxDraw.rgb(0x2A2224);
        Vec3[] births = BIRTHS.get(p.seed());
        int newest = (int) Math.floor(age);
        for (int born = Math.max(0, newest - (int) travel - 1); born <= newest && born < p.emitFor(); born++) {
            // Older flames fly on from where the mouth was when they left it; only the newest follow the aim.
            Vector3f from = mouth;
            Vector3f d = new Vector3f(dir).normalize();
            if (births != null && born < newest && births[born * 2] != null) {
                from = births[born * 2].subtract(camera).toVector3f();
                d = births[born * 2 + 1].toVector3f();
            }
            Vector3f side = new Vector3f(d).cross(Math.abs(d.y) > 0.99f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            Vector3f up = new Vector3f(side).cross(d).normalize();
            for (int k = 0; k < perTick; k++) {
                float h = hash(p.seed() + born, k, 0);
                float a = age - born - h;
                float life = travel * (0.8f + 0.35f * hash(p.seed() + born, k, 1));
                if (a < 0 || a > life) {
                    continue;
                }
                float k01 = a / life;
                float dist = speed * a;
                float ang = hash(p.seed() + born, k, 2) * Mth.TWO_PI;
                float rr = Mth.sqrt(hash(p.seed() + born, k, 3)) * radius * Math.min(1, dist / length) * 0.85f;
                Vector3f pos = new Vector3f(d).mul(dist).add(from)
                        .add(new Vector3f(side).mul(Mth.cos(ang) * rr)).add(new Vector3f(up).mul(Mth.sin(ang) * rr + k01 * k01 * 1.2f));
                float s = Mth.lerp(k01, 0.35f, size) * (0.7f + 0.6f * hash(p.seed() + born, k, 4));
                float[] c = k01 < 0.35f ? FxDraw.mix(cor2, cor, k01 / 0.35f) : FxDraw.mix(cor, FxDraw.mix(cor, smoke, 0.6f), (k01 - 0.35f) / 0.65f);
                float o = opacity * (1 - smoothstep(0.7f, 1, k01));
                if (k % 7 == 0 && k01 > 0.55f) {
                    FxDraw.billboard(pos, s * 1.3f, h * 6, FxDraw.SMOKE, k01, smoke, o * 0.6f, false);
                } else {
                    FxDraw.billboard(pos, s, h * 6 + a * 0.05f, FxDraw.FIRE, k01 * 0.85f, c, o, true);
                }
            }
        }
    }

    // Thin flames blown back from the mouth around both sides of the face, as if the force of the breath pushed them.
    private static void mouthTrail(Piece p, Efeito e, Vector3f eyes, Vector3f look, float age, float[] cor, float[] cor2, float opacity) {
        Vector3f f = new Vector3f(look.x, 0, look.z);
        if (f.lengthSquared() < 1.0E-4f) {
            return;
        }
        f.normalize();
        Vector3f r = new Vector3f(f).cross(0, 1, 0).normalize();
        Vector3f u = new Vector3f(0, 1, 0);
        int lines = i(e.quantidade(), 6);
        float back = (float) d(e.comprimento(), 1.2);
        int segments = 12;
        for (int k = 0; k < lines; k++) {
            float s = k % 2 == 0 ? 1 : -1;
            float tilt = (hash(p.seed(), k, 1) - 0.5f) * 0.32f;
            Vector3f last = null;
            for (int j = 0; j <= segments; j++) {
                float t = j / (float) segments;
                float theta = t * Mth.PI * 0.8f;
                float rr = 0.3f + 0.12f * t;
                float wave = Mth.sin(t * 9 - age * 0.9f + k * 1.7f) * 0.05f * t;
                Vector3f point = new Vector3f(eyes)
                        .add(new Vector3f(f).mul(Mth.cos(theta) * rr - t * t * back))
                        .add(new Vector3f(r).mul(s * Mth.sin(theta) * rr))
                        .add(new Vector3f(u).mul(-0.12f + tilt * t + wave + 0.08f * t * t));
                if (last != null) {
                    float flicker = 0.7f + 0.3f * Mth.sin(age * 1.3f + k + t * 5);
                    FxDraw.ribbon(last, point, 0.07f * (1 - t) + 0.015f, FxDraw.BEAM, 0.6f, FxDraw.mix(cor2, cor, t), opacity * (1 - t * 0.85f) * flicker,
                            true);
                }
                last = point;
            }
        }
    }

    // A crescent of blue energy flying away along dir at velocidade, tamanho wide, out to comprimento blocks. Its tips
    // trail behind the middle; a few fading echoes follow it. The server sweeps the very same shape (SusanooCorte).
    private static final float SLASH_ROLL = 0.35f;

    private static void slash(Efeito e, Vector3f origin, Vector3f dir, float age, float t, float[] cor, float[] cor2, float width, float opacity) {
        float reach = (float) d(e.comprimento(), 70);
        float speed = (float) d(e.velocidade(), 3.5);
        Vector3f d = new Vector3f(dir).normalize();
        Vector3f flatSide = new Vector3f(d).cross(0, 1, 0);
        if (flatSide.lengthSquared() < 1.0E-4f) {
            flatSide.set(1, 0, 0);
        }
        flatSide.normalize();
        // Tilted like a sword swing; the server cuts the same tilted strip (SusanooCorte.ROLL).
        Vector3f normal = new Vector3f(flatSide).cross(d).normalize();
        Vector3f side = new Vector3f(flatSide).mul(Mth.cos(SLASH_ROLL)).add(normal.mul(Mth.sin(SLASH_ROLL))).normalize();
        float travel = Math.min(reach, speed * age);
        float fade = opacity * (speed * age > reach ? Math.max(0, 1 - (speed * age - reach) / (speed * 4)) : 1) * Math.min(1, age / 1.5f);
        float half = width / 2;
        float bow = width * 0.18f;
        int segments = 16;
        for (int echo = 0; echo < 4; echo++) {
            float at = travel - echo * 2.2f;
            if (at < 0) {
                continue;
            }
            float o = fade * (echo == 0 ? 1 : 0.45f / echo);
            float shrink = 1 - echo * 0.08f;
            Vector3f last = null;
            for (int s = 0; s <= segments; s++) {
                float u = s / (float) segments * 2 - 1;
                Vector3f p = new Vector3f(d).mul(at - bow * u * u).add(new Vector3f(side).mul(u * half * shrink)).add(origin);
                if (last != null) {
                    float um = (s - 0.5f) / segments * 2 - 1;
                    float thick = (1 - um * um) * 0.9f + 0.1f;
                    if (echo == 0) {
                        FxDraw.ribbon(last, p, width * 0.45f * thick, FxDraw.BEAM, 0, cor, o * 0.25f, true);
                    }
                    FxDraw.ribbon(last, p, width * 0.2f * thick, FxDraw.BEAM, 0.15f, cor, o * 0.8f, true);
                    if (echo == 0) {
                        FxDraw.ribbon(last, p, width * 0.06f * thick, FxDraw.BEAM, 0.9f, cor2, o, true);
                    }
                }
                last = p;
            }
        }
        if (fade > 0) {
            FxDraw.billboard(new Vector3f(d).mul(travel).add(origin), width * 0.5f, age * 0.3f, FxDraw.FLARE, 0.2f, cor, fade * 0.6f, true);
        }
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
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
    static Vector3f sphere(int seed, int k) {
        float y = hash(seed, k, 20) * 2 - 1;
        float a = hash(seed, k, 21) * Mth.TWO_PI;
        float r = Mth.sqrt(Math.max(0, 1 - y * y));
        return new Vector3f(Mth.cos(a) * r, y, Mth.sin(a) * r);
    }

    static float hash(int seed, int k, int salt) {
        int h = seed * 374761393 + k * 668265263 + salt * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    static float easeOut(float t) {
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
