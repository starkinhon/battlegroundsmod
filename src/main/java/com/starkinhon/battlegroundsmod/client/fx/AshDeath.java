package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.skill.HakaiSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// Killed by fire (Katon, Meteoro): the body chars black where it stands, then crumbles to ash from the head down and
// the wind carries it off. Vanilla's death (falling over, turning red) is skipped; the body is drawn here instead,
// through the souls_ash shader, and keeps being drawn after the game has removed the entity.
// The Hakai (bills.hakai.json) uses the same body in its own style: it lights up white with purple veins and breaks
// into purple sparks, drawn here too (sparks()), stateless: every spark is a function of its seed and the time.
public final class AshDeath {
    private static final int BURN_TICKS = 12;
    private static final int CRUMBLE_START = 14;
    private static final int CRUMBLE_END = 58;
    private static final int END = 62;

    // Timing in ticks from start: the body lights up (burn), falls apart from crumbleStart to crumbleEnd, and is kept
    // hidden until end. hakai: null for fire; else the Hakai's look.
    private record Ash(LivingEntity entity, long start, float windYaw, int burn, int crumbleStart, int crumbleEnd, int end, Hakai hakai) {
    }

    private record Hakai(int sparks, float rise, float wind, float[] color, float[] white) {
    }

    private static final Map<Integer, Ash> ASHES = new HashMap<>();
    private static MultiBufferSource.BufferSource buffers;
    private static final java.util.Set<Integer> BROKEN = new java.util.HashSet<>();
    private static boolean drawing;

    private AshDeath() {
    }

    public static void start(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || FxShaders.ash == null || FxShaders.flat() || ASHES.containsKey(entityId)) {
            return;
        }
        if (minecraft.level.getEntity(entityId) instanceof LivingEntity living) {
            ASHES.put(entityId, new Ash(living, ScreenFx.now(), (float) (FxWorld.hash(entityId, 1, 3) * Math.PI * 2), BURN_TICKS, CRUMBLE_START,
                    CRUMBLE_END, END, null));
        }
    }

    // Erased by the Hakai. Plays even without the body shader (a shader pack): the body then just goes, the sparks stay.
    // The body is kept hidden a while after it is gone, so the kill's vanilla death never shows.
    public static void startHakai(int entityId, boolean awakened) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(entityId) instanceof LivingEntity living)) {
            return;
        }
        HakaiSpec.Desintegrar d = HakaiSpec.get(awakened).desintegrar();
        int burn = Math.max(1, d.brilhoTicks());
        int crumbleEnd = burn + Math.max(1, d.duracaoTicks());
        Hakai look = new Hakai(Math.max(0, d.faiscas()), (float) d.subida(), (float) d.vento(), FxDraw.rgb(FxDraw.parse(d.cor(), 0x9B30FF)),
                FxDraw.rgb(FxDraw.parse(d.cor2(), 0xFFFFFF)));
        ASHES.put(entityId, new Ash(living, ScreenFx.now(), (float) (FxWorld.hash(entityId, 1, 3) * Math.PI * 2), burn, burn, crumbleEnd,
                crumbleEnd + 40, look));
    }

    static boolean hasSparks() {
        for (Ash ash : ASHES.values()) {
            if (ash.hakai() != null) {
                return true;
            }
        }
        return false;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            if (level == null) {
                ASHES.clear();
            }
            return;
        }
        long now = ScreenFx.now();
        ASHES.values().removeIf(a -> now - a.start() > a.end() || a.entity().level() != level);
        for (Ash ash : ASHES.values()) {
            if (ash.hakai() == null) {
                emit(level, ash, (int) (now - ash.start()));
            }
        }
    }

    // Ash specks and a few embers flaking off the crumbling edge, blown sideways and up.
    private static void emit(ClientLevel level, Ash ash, int age) {
        LivingEntity entity = ash.entity();
        var random = level.getRandom();
        float height = entity.getBbHeight();
        float width = entity.getBbWidth();
        Vec3 feet = entity.position();
        double windX = Mth.cos(ash.windYaw());
        double windZ = Mth.sin(ash.windYaw());
        if (age < CRUMBLE_START) {
            // Still burning: smoke rising off the charred body.
            for (int i = 0; i < 2; i++) {
                level.addParticle(ModParticles.smoke(), feet.x + (random.nextDouble() - 0.5) * width, feet.y + random.nextDouble() * height,
                        feet.z + (random.nextDouble() - 0.5) * width, windX * 0.02, 0.04, windZ * 0.02);
            }
            return;
        }
        if (age > CRUMBLE_END) {
            return;
        }
        float progress = (age - CRUMBLE_START) / (float) (CRUMBLE_END - CRUMBLE_START);
        double front = feet.y + height * Mth.clamp(1.05 - progress * 1.35, 0, 1);
        for (int i = 0; i < 10; i++) {
            double x = feet.x + (random.nextDouble() - 0.5) * width * 1.1;
            double y = front + (random.nextDouble() - 0.5) * height * 0.18;
            double z = feet.z + (random.nextDouble() - 0.5) * width * 1.1;
            double speed = 0.05 + random.nextDouble() * 0.09;
            int gray = 0x18 + random.nextInt(0x28);
            boolean ember = random.nextFloat() < 0.18f;
            int color = ember ? 0xFF6A1A : gray << 16 | (int) (gray * 0.92) << 8 | (int) (gray * 0.85);
            level.addParticle(ModParticles.dust(color, ember ? 0.5f : 0.8f + random.nextFloat() * 0.9f, 30 + random.nextInt(30), 0.97f),
                    x, y, z, windX * speed + (random.nextDouble() - 0.5) * 0.03, 0.015 + random.nextDouble() * 0.04,
                    windZ * speed + (random.nextDouble() - 0.5) * 0.03);
        }
    }

    // The Hakai's light, drawn with the other world effects (additive, into the bloom): a purple glow around the body
    // while it lights up, a flash of sparks thrown out when it starts, then the body breaking into sparks along the
    // crumbling edge. Each spark is born white-hot where the edge passes, rises and drifts on the wind, cooling to purple
    // and twinkling out. Particles already loose stay in the world: only the birth point follows the body.
    static void sparks(Vec3 camera, double now, float partial) {
        for (Ash ash : ASHES.values()) {
            Hakai look = ash.hakai();
            if (look == null) {
                continue;
            }
            LivingEntity entity = ash.entity();
            double age = now - ash.start();
            Vec3 feet = entity.getPosition(partial);
            if (feet.distanceToSqr(camera) > 120 * 120) {
                continue;
            }
            float height = entity.getBbHeight();
            float width = entity.getBbWidth();
            Vector3f base = feet.subtract(camera).toVector3f();
            int seed = entity.getId() * 31 + (int) ash.start();
            float span = ash.crumbleEnd() - ash.crumbleStart();
            float burn = (float) Mth.clamp(age / ash.burn(), 0, 1);
            float progress = (float) Mth.clamp((age - ash.crumbleStart()) / span, 0, 1);
            float windX = Mth.cos(ash.windYaw());
            float windZ = Mth.sin(ash.windYaw());

            // Glow around the body, strongest as it lights up, going with it.
            if (age < ash.crumbleEnd()) {
                float glow = burn * (1 - progress * progress);
                Vector3f middle = new Vector3f(base).add(0, height * 0.5f * (1 - progress * 0.6f), 0);
                FxDraw.billboard(middle, height * 2.2f, 0, FxDraw.SOFT, 0.15f, look.color(), glow * 0.55f, true);
                FxDraw.billboard(middle, height * 1.1f, 0, FxDraw.SOFT, 0.7f, look.white(), glow * 0.35f, true);
                if (progress > 0) {
                    // The edge itself, a band of light running down the body.
                    float front = height * Mth.clamp(1.05f - progress * 1.35f, 0, 1);
                    Vector3f edge = new Vector3f(base).add(0, front, 0);
                    FxDraw.billboard(edge, width * 2.6f, 0, FxDraw.SOFT, 0.6f, look.color(), 0.6f * (1 - progress * 0.5f), true);
                }
            }

            // The flash of sparks thrown out when it starts.
            int burst = Math.max(0, look.sparks() / 8);
            for (int k = 0; k < burst; k++) {
                float life = 10 + 10 * FxWorld.hash(seed, k, 41);
                float s = (float) age;
                if (s >= life) {
                    continue;
                }
                Vector3f out = FxWorld.sphere(seed + 7, k);
                float reach = (1.2f + 2.2f * FxWorld.hash(seed, k, 42)) * FxWorld.easeOut(s / life);
                Vector3f at = new Vector3f(out).mul(reach).add(base).add(0, height * 0.55f, 0);
                float k01 = s / life;
                float size = 0.12f * (1 - k01) + 0.03f;
                FxDraw.billboard(at, size, 0, FxDraw.SOFT, 0.85f, FxDraw.mix(look.white(), look.color(), Math.min(1, k01 * 2)), 1 - k01, true);
            }

            // The body breaking into sparks along the edge.
            for (int k = 0; k < look.sparks(); k++) {
                float born = ash.crumbleStart() + FxWorld.hash(seed, k, 1) * span - 2;
                // A slower crumble keeps its sparks around longer too.
                float life = (18 + 34 * FxWorld.hash(seed, k, 2)) * Mth.clamp(span / 40f, 1, 1.6f);
                float s = (float) (age - born);
                if (s < 0 || s >= life) {
                    continue;
                }
                float u = (born - ash.crumbleStart()) / span;
                float y0 = height * Mth.clamp(1.05f - u * 1.35f, 0, 1) + (FxWorld.hash(seed, k, 3) - 0.5f) * height * 0.12f;
                float a = FxWorld.hash(seed, k, 4) * Mth.TWO_PI;
                float r = width * 0.55f * Mth.sqrt(FxWorld.hash(seed, k, 5));
                float x0 = Mth.cos(a) * r;
                float z0 = Mth.sin(a) * r;
                float speed = 0.6f + 0.8f * FxWorld.hash(seed, k, 6);
                float rise = look.rise() * speed * s * (1 - 0.35f * s / life);
                float drift = look.wind() * 0.018f * speed * s * s / (1 + s * 0.04f);
                float swirl = 0.15f * Mth.sin(s * 0.25f + k);
                Vector3f at = new Vector3f(base).add(x0 + windX * drift + swirl * -windZ, Math.max(0, y0) + rise, z0 + windZ * drift + swirl * windX);
                float k01 = s / life;
                float twinkle = 0.55f + 0.45f * Mth.sin(s * 0.9f + FxWorld.hash(seed, k, 7) * 6.28f);
                float size = (0.05f + 0.1f * FxWorld.hash(seed, k, 8)) * (1 - 0.6f * k01);
                float[] color = FxDraw.mix(look.white(), look.color(), Math.min(1, s / 6));
                float opacity = Math.min(1, s / 1.5f) * (1 - k01 * k01) * twinkle;
                if (k % 9 == 0) {
                    // Now and then a bigger glint with four little rays.
                    FxDraw.billboard(at, size * 3.2f, 0, FxDraw.FLARE, 4 / 32f, color, opacity * 0.9f, true);
                }
                FxDraw.billboard(at, size, 0, FxDraw.SOFT, 0.85f, color, opacity, true);
            }
        }
    }

    // While its ash plays, the vanilla body is hidden (it would tilt over and flash red).
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!drawing && ASHES.containsKey(event.getEntity().getId()) && ASHES.get(event.getEntity().getId()).entity() == event.getEntity()) {
            event.setCanceled(true);
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ASHES.isEmpty() || FxShaders.ash == null || FxShaders.flat()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = ScreenFx.now() + partial;
        ShaderInstance shader = FxShaders.ash;
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
            for (Ash ash : ASHES.values()) {
                LivingEntity entity = ash.entity();
                double age = now - ash.start();
                if (age > ash.crumbleEnd() + 2) {
                    continue;
                }
                Vec3 at = entity.getPosition(partial);
                if (at.distanceToSqr(camera) > 160 * 160 || (entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson())) {
                    continue;
                }
                float burn = (float) Mth.clamp(age / ash.burn(), 0, 1);
                float progress = (float) Mth.clamp((age - ash.crumbleStart()) / (ash.crumbleEnd() - ash.crumbleStart()), 0, 1);
                Vec3 rel = at.subtract(camera);
                shader.safeGetUniform("AshOrigin").set((float) rel.x, (float) rel.y, (float) rel.z);
                shader.safeGetUniform("AshHeight").set(entity.getBbHeight());
                shader.safeGetUniform("Burn").set(burn);
                shader.safeGetUniform("Progress").set(progress);
                shader.safeGetUniform("Style").set(ash.hakai() != null ? 1f : 0f);
                if (ash.hakai() != null) {
                    float[] c = ash.hakai().color();
                    float[] w = ash.hakai().white();
                    shader.safeGetUniform("GlowColor").set(c[0], c[1], c[2]);
                    shader.safeGetUniform("EdgeColor").set(w[0], w[1], w[2]);
                }
                draw(dispatcher, entity, rel, partial);
                buffers.endBatch();
            }
        } finally {
            drawing = false;
            ASHES.keySet().removeAll(BROKEN);
            BROKEN.clear();
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // The entity's normal model, standing, every part redirected to the ash shader with its own texture.
    @SuppressWarnings("unchecked")
    private static void draw(EntityRenderDispatcher dispatcher, LivingEntity entity, Vec3 rel, float partial) {
        ResourceLocation skin;
        try {
            skin = ((EntityRenderer<Entity>) dispatcher.getRenderer(entity)).getTextureLocation(entity);
        } catch (RuntimeException e) {
            return;
        }
        MultiBufferSource ash = type -> {
            ResourceLocation texture = FxRenderTypes.textureOf(type);
            if (texture == null) {
                texture = skin;
            }
            String path = texture.getPath();
            // Only the body and what it wears: no shadow, fire overlay, enchant glint or name tag.
            if (!(path.startsWith("textures/") || path.startsWith("skins/")) || path.contains("shadow") || path.contains("glint")
                    || path.contains("atlas")) {
                return NOTHING;
            }
            return buffers.getBuffer(FxRenderTypes.ash(texture));
        };
        int deathTime = entity.deathTime;
        int hurtTime = entity.hurtTime;
        entity.deathTime = 0;
        entity.hurtTime = 0;
        try {
            dispatcher.render(entity, rel.x, rel.y, rel.z, Mth.lerp(partial, entity.yRotO, entity.getYRot()), partial, new PoseStack(), ash,
                    0xF000F0);
        } catch (RuntimeException e) {
            // A renderer that can't be drawn this way: let vanilla draw it from now on.
            BROKEN.add(entity.getId());
        } finally {
            entity.deathTime = deathTime;
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
