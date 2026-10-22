package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4fStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.fx.ModParticles;

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
public final class AshDeath {
    private static final int BURN_TICKS = 12;
    private static final int CRUMBLE_START = 14;
    private static final int CRUMBLE_END = 58;
    private static final int END = 62;

    private record Ash(LivingEntity entity, long start, float windYaw) {
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
            ASHES.put(entityId, new Ash(living, ScreenFx.now(), (float) (FxWorld.hash(entityId, 1, 3) * Math.PI * 2)));
        }
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
        ASHES.values().removeIf(a -> now - a.start() > END || a.entity().level() != level);
        for (Ash ash : ASHES.values()) {
            emit(level, ash, (int) (now - ash.start()));
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

    // While its ash plays, the vanilla body is hidden (it would tilt over and flash red).
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!drawing && ASHES.containsKey(event.getEntity().getId()) && ASHES.get(event.getEntity().getId()).entity() == event.getEntity()) {
            event.setCanceled(true);
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ASHES.isEmpty() || FxShaders.ash == null) {
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
                Vec3 at = entity.getPosition(partial);
                if (at.distanceToSqr(camera) > 160 * 160 || (entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson())) {
                    continue;
                }
                float burn = (float) Mth.clamp(age / BURN_TICKS, 0, 1);
                float progress = (float) Mth.clamp((age - CRUMBLE_START) / (CRUMBLE_END - CRUMBLE_START), 0, 1);
                Vec3 rel = at.subtract(camera);
                shader.safeGetUniform("AshOrigin").set((float) rel.x, (float) rel.y, (float) rel.z);
                shader.safeGetUniform("AshHeight").set(entity.getBbHeight());
                shader.safeGetUniform("Burn").set(burn);
                shader.safeGetUniform("Progress").set(progress);
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
