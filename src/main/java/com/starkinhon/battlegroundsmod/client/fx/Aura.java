package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.boss.BossSpec;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.skill.AuraSpec;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// The power aura (looks in souls_auras/<power>.json). Players have it while in combat mode (the server says when),
// bosses always. It fades in slowly when it turns on, with a ring running over the floor, and out when it ends.
// What it is made of:
// - a glowing outline hugging the body, burning upwards in tongues of flame: the body is drawn flat into a mask,
//   then fx_aura_outline lights the pixels around it into the glow layer, which gets the bloom;
// - wavy lines of power rising around the body and a glow on the floor (souls_vfx shapes);
// - the mod's particles breathed out of the body;
// - bosses: from a phase on, a light trail following the eyes (Madara's sharingan), recorded where the head really is.
// Your own aura in first person keeps only the floor glow, a few low particles and a faint tint at the screen edges.
public final class Aura {
    private static final double RANGE = 96;
    private static final int MAX_EYE_SAMPLES = 96;

    private static final class State {
        String power;
        boolean on;
        float level;
        float prev;
        long onAt = Long.MIN_VALUE;
    }

    private record EyeSample(double time, Vec3 left, Vec3 right) {
    }

    // Players' auras as the server last said (entity id -> power, missing = off).
    private static final Map<Integer, String> PLAYERS = new HashMap<>();
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<Integer, ArrayDeque<EyeSample>> EYES = new HashMap<>();
    // Whose eye trail is drawn with which aura's look (bosses in their phase, players after their trigger skill).
    private static final Map<Integer, AuraSpec> EYE_LOOK = new HashMap<>();
    // Players (or anyone) whose eyes trail for a while after a skill (the Mangekyo): entity id -> power and end time.
    private record EyesOn(String power, long until) {
    }

    private static final Map<Integer, EyesOn> EYES_ON = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static MultiBufferSource.BufferSource buffers;
    private static ClientLevel lastLevel;
    private static boolean drawing;
    // Outline look of the nearest aura this frame (the outline pass draws them all in one go).
    private static AuraSpec.Contorno outlineLook;

    private Aura() {
    }

    public static void state(int entityId, String power) {
        if (power == null || power.isEmpty()) {
            PLAYERS.remove(entityId);
        } else {
            PLAYERS.put(entityId, power);
        }
    }

    static boolean active() {
        return !STATES.isEmpty() || !EYES.isEmpty();
    }

    private static String wanted(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()) {
            return null;
        }
        if (entity instanceof SoulBoss boss) {
            return BossSpec.get(boss.bossId()).poder();
        }
        return entity instanceof Player ? PLAYERS.get(entity.getId()) : null;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != lastLevel) {
            STATES.clear();
            EYES.clear();
            EYE_LOOK.clear();
            EYES_ON.clear();
            if (level == null) {
                PLAYERS.clear();
            }
            lastLevel = level;
        }
        if (level == null || minecraft.isPaused()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof SoulBoss) && !(entity instanceof Player)) {
                continue;
            }
            String power = entity.position().distanceToSqr(camera) < RANGE * RANGE ? wanted(entity) : null;
            AuraSpec spec = AuraSpec.get(power);
            State state = STATES.get(entity.getId());
            if (spec != null && state == null) {
                state = new State();
                STATES.put(entity.getId(), state);
            }
            if (state == null) {
                continue;
            }
            if (spec != null) {
                state.power = power;
            }
            boolean on = spec != null;
            if (on && !state.on && state.level <= 0) {
                state.onAt = ScreenFx.now();
                burst(level, (LivingEntity) entity, spec);
            }
            state.on = on;
        }
        long now = ScreenFx.now();
        Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, State> entry = it.next();
            State state = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            AuraSpec spec = AuraSpec.get(state.power);
            if (entity == null || spec == null) {
                it.remove();
                continue;
            }
            if (wanted(entity) == null) {
                state.on = false;
            }
            state.prev = state.level;
            state.level = state.on ? Math.min(1, state.level + 1f / Math.max(1, spec.fadeEntrarTicks()))
                    : Math.max(0, state.level - 1f / Math.max(1, spec.fadeSairTicks()));
            if (!state.on && state.level <= 0) {
                it.remove();
                continue;
            }
            breathe(level, (LivingEntity) entity, spec, ease(state.level), now);
        }
        EYES_ON.values().removeIf(on -> on.until() < now);
        EYES.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().peekLast().time() < now - 40);
        EYE_LOOK.keySet().removeIf(id -> !EYES.containsKey(id));
    }

    // Slow at first, like the power is waking up.
    private static float ease(float level) {
        return level * level * (3 - 2 * level);
    }

    private static float level(State state, float partial) {
        return ease(Mth.lerp(partial, state.prev, state.level));
    }

    // The aura turning on: particles thrown out of the body.
    private static void burst(ClientLevel level, LivingEntity entity, AuraSpec spec) {
        if (spec.entrada() == null || FxAnchor.firstPersonSelf(entity.getId())) {
            return;
        }
        int color = FxDraw.parse(spec.cor(), 0xFF2020);
        Vec3 feet = entity.position();
        for (int i = 0; i < spec.entrada().particulas(); i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double speed = 0.12 + RANDOM.nextDouble() * 0.2;
            level.addParticle(ModParticles.dust(color, 1.2f + RANDOM.nextFloat(), 18 + RANDOM.nextInt(14), 0.9f), feet.x, feet.y + 0.1 + RANDOM.nextDouble()
                    * entity.getBbHeight() * 0.5, feet.z, Math.cos(a) * speed, 0.05 + RANDOM.nextDouble() * 0.12, Math.sin(a) * speed);
        }
    }

    // What the aura breathes out this tick.
    private static void breathe(ClientLevel level, LivingEntity entity, AuraSpec spec, float k, long now) {
        if (spec.particulas() == null || k <= 0.02f) {
            return;
        }
        boolean self = FxAnchor.firstPersonSelf(entity.getId());
        float rate = self ? (spec.primeiraPessoa() == null ? 0 : (float) spec.primeiraPessoa().particulas()) : 1;
        Vec3 feet = entity.position();
        float height = entity.getBbHeight();
        float width = Math.max(0.6f, entity.getBbWidth());
        for (AuraSpec.Particula p : spec.particulas()) {
            if (self && ("anel".equals(p.tipo()) || "risco".equals(p.tipo()))) {
                continue;
            }
            double want = p.porTick() * k * rate;
            int count = (int) want + (RANDOM.nextDouble() < want - (int) want ? 1 : 0);
            int color = FxDraw.parse(p.cor(), 0xFF2020);
            for (int i = 0; i < count; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                double r = p.raio() * width / 0.6 * (0.55 + 0.45 * RANDOM.nextDouble());
                // Your own, seen from inside: only around the legs and a step out, so nothing passes before the eyes.
                double y = self ? RANDOM.nextDouble() * 0.6 : RANDOM.nextDouble() * height * p.altura();
                if (self) {
                    r = Math.max(r, 0.65);
                }
                double x = feet.x + Math.cos(a) * r;
                double z = feet.z + Math.sin(a) * r;
                double up = p.subida() * (0.7 + 0.6 * RANDOM.nextDouble());
                double out = 0.012 * RANDOM.nextDouble();
                float size = (float) p.tamanho() * (0.8f + 0.4f * RANDOM.nextFloat());
                int life = Math.max(1, (int) (p.vidaTicks() * (0.8 + 0.4 * RANDOM.nextDouble())));
                switch (p.tipo()) {
                    case "risco" -> level.addParticle(ModParticles.streak(color, size, life), x, feet.y + y, z, 0, up, 0);
                    case "fumaca" -> level.addParticle(ModParticles.puff(color, size, life, 0.92f), x, feet.y + y, z, Math.cos(a) * out,
                            up, Math.sin(a) * out);
                    case "anel" -> level.addParticle(ModParticles.ring(color, size, life), feet.x, feet.y + height * 0.5, feet.z, 0, 0, 0);
                    default -> level.addParticle(ModParticles.dust(color, size, life, 0.94f), x, feet.y + y, z, Math.cos(a) * out, up,
                            Math.sin(a) * out);
                }
            }
        }
    }

    // The faint tint at the edges of the screen while your own aura is on in first person (null = none).
    public static float[] screenEdge(float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        State state = camera == null ? null : STATES.get(camera.getId());
        if (state == null || !FxAnchor.firstPersonSelf(camera.getId())) {
            return null;
        }
        AuraSpec spec = AuraSpec.get(state.power);
        if (spec == null || spec.primeiraPessoa() == null || spec.primeiraPessoa().bordaTela() <= 0) {
            return null;
        }
        float breathe = 0.8f + 0.2f * Mth.sin((ScreenFx.now() + partial) * 0.12f);
        float[] c = FxDraw.rgb(FxDraw.parse(spec.cor(), 0xFF2020));
        return new float[] {c[0], c[1], c[2], (float) spec.primeiraPessoa().bordaTela() * level(state, partial) * breathe};
    }

    // ---- Eyes ----

    // A skill was used (the server's "olhos" moment): if an aura's eyes are switched on by it, they trail for a while.
    public static void skillUsed(String skill, int entityId) {
        for (Power power : Powers.all()) {
            AuraSpec spec = AuraSpec.get(power.id());
            AuraSpec.Olhos eyes = spec == null ? null : spec.olhos();
            if (eyes != null && skill.equals(eyes.ativarSkill()) && eyes.jogadorTicks() > 0) {
                EYES_ON.put(entityId, new EyesOn(power.id(), ScreenFx.now() + eyes.jogadorTicks()));
            }
        }
    }

    // Whose eyes trail right now, with which look: a boss from its phase on, anyone else while a trigger skill lasts.
    private static AuraSpec eyeLook(LivingEntity entity) {
        if (entity instanceof SoulBoss boss) {
            AuraSpec spec = AuraSpec.get(BossSpec.get(boss.bossId()).poder());
            if (spec != null && spec.olhos() != null && boss.phase() >= spec.olhos().aPartirDaFase()) {
                return spec;
            }
        }
        EyesOn on = EYES_ON.get(entity.getId());
        if (on != null && on.until() >= ScreenFx.now()) {
            AuraSpec spec = AuraSpec.get(on.power());
            return spec != null && spec.olhos() != null ? spec : null;
        }
        return null;
    }

    // Called while a body is drawn (AuraEyesLayer), with the pose of its model: where its eyes are right now.
    public static void recordEyes(LivingEntity entity, PoseStack pose, ModelPart head) {
        if (drawing || !entity.isAlive() || FxAnchor.firstPersonSelf(entity.getId())) {
            return;
        }
        AuraSpec spec = eyeLook(entity);
        if (spec == null) {
            return;
        }
        AuraSpec.Olhos eyes = spec.olhos();
        EYE_LOOK.put(entity.getId(), spec);
        pose.pushPose();
        head.translateAndRotate(pose);
        Matrix4f m = pose.last().pose();
        Vector3f left = m.transformPosition(new Vector3f((float) -eyes.x() / 16, (float) eyes.y() / 16, (float) eyes.z() / 16));
        Vector3f right = m.transformPosition(new Vector3f((float) eyes.x() / 16, (float) eyes.y() / 16, (float) eyes.z() / 16));
        pose.popPose();
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double time = ScreenFx.now() + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        ArrayDeque<EyeSample> samples = EYES.computeIfAbsent(entity.getId(), id -> new ArrayDeque<>());
        if (!samples.isEmpty() && samples.peekLast().time() >= time) {
            samples.pollLast();
        }
        samples.addLast(new EyeSample(time, camera.add(left.x, left.y, left.z), camera.add(right.x, right.y, right.z)));
        while (samples.size() > MAX_EYE_SAMPLES || samples.peekFirst().time() < time - eyes.rastroTicks() - 1) {
            samples.pollFirst();
        }
    }

    // ---- Drawing ----

    // The lines, floor glow and eye trails, into FxDraw's buffers (called from FxWorld while it draws).
    static void render(Camera cam, double now, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 camera = cam.getPosition();
        for (Map.Entry<Integer, State> entry : STATES.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            State state = entry.getValue();
            AuraSpec spec = AuraSpec.get(state.power);
            if (entity == null || spec == null) {
                continue;
            }
            float k = level(state, partial);
            Vector3f feet = entity.getPosition(partial).subtract(camera).toVector3f();
            float[] cor = FxDraw.rgb(FxDraw.parse(spec.cor(), 0xFF2020));
            float[] cor2 = FxDraw.rgb(FxDraw.parse(spec.cor2(), 0xFFFFFF));
            int seed = entry.getKey() * 7919;
            floor(spec, state, feet, now, k, cor);
            if (!FxAnchor.firstPersonSelf(entity.getId())) {
                lines(spec, feet, entity.getBbHeight(), now, k, seed, cor, cor2);
            }
        }
        for (Map.Entry<Integer, AuraSpec> entry : EYE_LOOK.entrySet()) {
            eyes(entry.getValue(), entry.getKey(), camera, now);
        }
    }

    private static void floor(AuraSpec spec, State state, Vector3f feet, double now, float k, float[] cor) {
        Vector3f up = new Vector3f(0, 1, 0);
        float breathe = 0.8f + 0.2f * Mth.sin((float) now * 0.15f);
        if (spec.chao() != null && k > 0) {
            float radius = (float) spec.chao().raio();
            FxDraw.disk(new Vector3f(feet).add(0, 0.04f, 0), up, radius * 2 * (0.6f + 0.4f * k), FxDraw.SOFT, 0.15f, cor,
                    (float) spec.chao().opacidade() * k * breathe, true);
            FxDraw.disk(new Vector3f(feet).add(0, 0.05f, 0), up, radius * 1.3f * (0.7f + 0.3f * breathe), FxDraw.RING, 0.08f, cor,
                    (float) spec.chao().anel() * k * 0.6f, true);
        }
        AuraSpec.Entrada entrada = spec.entrada();
        if (entrada != null && state.onAt != Long.MIN_VALUE) {
            float t = (float) ((now - state.onAt) / Math.max(1, entrada.vidaTicks()));
            if (t >= 0 && t < 1) {
                float radius = (float) entrada.raioAnel() * (1 - (1 - t) * (1 - t) * (1 - t));
                FxDraw.disk(new Vector3f(feet).add(0, 0.07f, 0), up, radius * 2, FxDraw.RING, Mth.clamp(0.25f / Math.max(0.5f, radius), 0.03f, 1), cor,
                        1 - t, true);
                FxDraw.disk(new Vector3f(feet).add(0, 0.06f, 0), up, radius * 1.5f, FxDraw.SOFT, 0, cor, (1 - t) * (1 - t) * 0.5f, true);
            }
        }
    }

    // Wavy lines of power rising around the body, each one a short stretch running up a twisting path and fading
    // at both ends, born again lower down when it reaches the top.
    private static void lines(AuraSpec spec, Vector3f feet, float bodyHeight, double now, float k, int seed, float[] cor, float[] cor2) {
        AuraSpec.Fios fios = spec.fios();
        if (fios == null || fios.quantidade() <= 0 || k <= 0.01f) {
            return;
        }
        float height = (float) fios.altura() * bodyHeight / 1.8f;
        float length = height * 0.38f;
        int segments = 10;
        for (int line = 0; line < fios.quantidade(); line++) {
            float cycle = (float) (now * fios.velocidade() * (0.8f + 0.4f * FxWorld.hash(seed, line, 1)) + FxWorld.hash(seed, line, 2));
            int round = Mth.floor(cycle);
            float t = cycle - round;
            float base = FxWorld.hash(seed + round * 31, line, 3) * Mth.TWO_PI;
            float radius = (float) fios.raio() * (0.75f + 0.5f * FxWorld.hash(seed + round * 31, line, 4));
            float headY = -0.1f + t * (height + length);
            float fade = Mth.sin(t * Mth.PI) * k * (float) fios.opacidade();
            Vector3f last = null;
            for (int j = 0; j <= segments; j++) {
                float f = j / (float) segments;
                float y = headY - length * (1 - f);
                if (y < 0) {
                    last = null;
                    continue;
                }
                float h = Math.min(1, y / height);
                float a = base + y * 0.9f + Mth.sin(y * 2.6f + (float) now * 0.11f + line) * 0.25f;
                float r = radius * (1 + 0.35f * h) + (float) fios.ondulacao() * Mth.sin(y * 4.1f - (float) now * 0.2f + line * 1.7f);
                Vector3f point = new Vector3f(Mth.cos(a) * r, y, Mth.sin(a) * r).add(feet);
                if (last != null) {
                    float width = (float) fios.largura() * (0.35f + 0.65f * f) * (1 - 0.5f * h);
                    FxDraw.ribbon(last, point, width, FxDraw.STREAK, 0.6f, FxDraw.mix(cor, cor2, f * 0.6f), fade * f * (1 - h * 0.6f), true);
                }
                last = point;
            }
        }
    }

    private static void eyes(AuraSpec spec, int id, Vec3 camera, double now) {
        ArrayDeque<EyeSample> samples = EYES.get(id);
        AuraSpec.Olhos eyes = spec.olhos();
        if (samples == null || samples.isEmpty() || eyes == null) {
            return;
        }
        float window = Math.max(1, eyes.rastroTicks());
        float[] cor = FxDraw.rgb(FxDraw.parse(eyes.cor(), 0xFF0A0A));
        float[] cor2 = FxDraw.rgb(FxDraw.parse(eyes.cor2(), 0xFFFFFF));
        EyeSample newest = samples.peekLast();
        if (now - newest.time() > 2) {
            return; // not drawn lately (behind the camera, say): no trail hanging in the air
        }
        for (int side = 0; side < 2; side++) {
            Vector3f prev = null;
            Iterator<EyeSample> it = samples.descendingIterator();
            while (it.hasNext()) {
                EyeSample s = it.next();
                float t = (float) ((now - s.time()) / window);
                if (t >= 1) {
                    break;
                }
                Vector3f point = (side == 0 ? s.left() : s.right()).subtract(camera).toVector3f();
                if (prev != null && prev.distanceSquared(point) > 1.0E-6f) {
                    float fade = (1 - t) * (1 - t);
                    float width = (float) eyes.largura() * (1 - 0.7f * t);
                    FxDraw.ribbon(point, prev, width * 2.4f, FxDraw.BEAM, 0.2f, cor, fade * 0.7f, true);
                    FxDraw.ribbon(point, prev, width, FxDraw.BEAM, 0.8f, FxDraw.mix(cor2, cor, Math.min(1, t * 2)), fade, true);
                }
                prev = point;
            }
            Vector3f at = (side == 0 ? newest.left() : newest.right()).subtract(camera).toVector3f();
            float flicker = 0.85f + 0.15f * Mth.sin((float) now * 1.7f + side);
            FxDraw.billboard(at, (float) eyes.brilho() * flicker, (float) now * 0.05f, FxDraw.FLARE, 4 / 32f, cor, 0.9f, true);
            FxDraw.billboard(at, (float) eyes.brilho() * 0.35f, 0, FxDraw.SOFT, 0.8f, cor2, 1, true);
        }
    }

    // The bodies with an aura drawn flat into the mask. Returns whether the outline pass has anything to draw.
    static boolean drawMask(RenderLevelStageEvent event, Vec3 camera, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = FxShaders.aura;
        if (STATES.isEmpty() || shader == null || FxShaders.auraOutline == null || minecraft.level == null) {
            return false;
        }
        if (buffers == null) {
            buffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        float pxPerBlock = event.getProjectionMatrix().m11() * minecraft.getMainRenderTarget().height / 2f;
        float radius = radiusPx();
        boolean any = false;
        double nearest = Double.MAX_VALUE;
        outlineLook = null;
        drawing = true;
        try {
            for (Map.Entry<Integer, State> entry : STATES.entrySet()) {
                Entity entity = minecraft.level.getEntity(entry.getKey());
                AuraSpec spec = AuraSpec.get(entry.getValue().power);
                if (!(entity instanceof LivingEntity living) || spec == null || spec.contorno() == null || !spec.contorno().ativo()
                        || FxAnchor.firstPersonSelf(entity.getId()) || !living.isAlive()) {
                    continue;
                }
                float k = level(entry.getValue(), partial);
                Vec3 rel = entity.getPosition(partial).subtract(camera);
                double distance = Math.max(0.5, rel.add(0, entity.getBbHeight() / 2, 0).length());
                if (k <= 0.01f || distance > RANGE) {
                    continue;
                }
                if (!any) {
                    FxPost.beginAuraMask();
                    any = true;
                }
                if (distance < nearest) {
                    nearest = distance;
                    outlineLook = spec.contorno();
                }
                float[] c = FxDraw.rgb(FxDraw.parse(spec.cor(), 0xFF2020));
                float bright = (float) Mth.clamp(spec.contorno().brilho() / 2, 0, 1) * k;
                float reach = (float) (spec.contorno().espessura() * pxPerBlock / distance) / radius * (0.35f + 0.65f * k);
                shader.safeGetUniform("AuraColor").set(c[0] * bright, c[1] * bright, c[2] * bright, Mth.clamp(reach, 0.03f, 1));
                draw(dispatcher, living, rel, partial);
                buffers.endBatch();
            }
        } finally {
            drawing = false;
            if (any) {
                FxPost.endAuraMask();
            }
        }
        return any;
    }

    // The outline's farthest reach in pixels: a share of the screen height, so it looks the same at any size.
    private static float radiusPx() {
        return Math.max(24, Minecraft.getInstance().getMainRenderTarget().height * 0.09f);
    }

    // Lights the glow layer around the masked bodies (call between FxPost.beginGlow and endGlow).
    static void outline(double now) {
        ShaderInstance shader = FxShaders.auraOutline;
        AuraSpec.Contorno look = outlineLook;
        if (shader == null || look == null) {
            return;
        }
        shader.safeGetUniform("Radius").set(radiusPx());
        shader.safeGetUniform("Time").set((float) (now / 20.0));
        shader.safeGetUniform("Flames").set((float) look.chamas());
        shader.safeGetUniform("Speed").set((float) look.velocidade());
        shader.safeGetUniform("Edge").set((float) look.borda());
        FxPost.auraOutline(shader);
    }

    // The entity's own model, posed as it is now, every body part sent to the mask with its own texture.
    private static void draw(EntityRenderDispatcher dispatcher, LivingEntity entity, Vec3 rel, float partial) {
        MultiBufferSource mask = type -> {
            ResourceLocation texture = FxRenderTypes.textureOf(type);
            if (texture == null) {
                return NOTHING;
            }
            String path = texture.getPath();
            // Only the body and what it wears: no shadow, fire, enchant glint, held items or name tag.
            if (!(path.startsWith("textures/") || path.startsWith("skins/")) || path.contains("shadow") || path.contains("glint")
                    || path.contains("atlas")) {
                return NOTHING;
            }
            return buffers.getBuffer(FxRenderTypes.auraMask(texture));
        };
        int hurtTime = entity.hurtTime;
        entity.hurtTime = 0;
        try {
            dispatcher.render(entity, rel.x, rel.y, rel.z, Mth.lerp(partial, entity.yRotO, entity.getYRot()), partial, new PoseStack(), mask,
                    0xF000F0);
        } catch (RuntimeException e) {
            // A renderer that can't be drawn this way just has no outline.
        } finally {
            entity.hurtTime = hurtTime;
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
}
