package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.skill.MeteoroSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Draws the Meteoro's body as a real sphere: a spinning rock textured ball, darker at the back and hot at the front,
// with an additive glow shell on the leading side. The server sends where it falls from and to; the timing and look
// come from the same madara.meteoro.json, so the ball moves exactly like the server's hitbox.
public final class MeteorRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/meteor.png");
    private static final int RINGS = 18;
    private static final int SEGMENTS = 36;

    private record Falling(MeteoroSpec spec, Vec3 start, Vec3 end, Vec3 dir, long startTick) {
    }

    private static final List<Falling> FALLING = new ArrayList<>();

    private MeteorRenderer() {
    }

    public static void start(String skill, boolean awakened, Vec3 start, Vec3 end, int delay) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        MeteoroSpec spec = SkillSpecs.get(skill, MeteoroSpec.class, awakened);
        FALLING.add(new Falling(spec, start, end, end.subtract(start).normalize(), level.getGameTime() + delay));
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || FALLING.isEmpty()) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) {
            FALLING.clear();
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        long now = level.getGameTime();
        FALLING.removeIf(falling -> now - falling.startTick() > falling.spec().tempo().quedaTicks() + 1);
        for (Falling falling : FALLING) {
            double age = now - falling.startTick() + partial;
            int ticks = Math.max(1, falling.spec().tempo().quedaTicks());
            if (age < 0 || age > ticks) {
                continue;
            }
            MeteoroSpec.Corpo corpo = falling.spec().meteoro();
            double k = age / ticks;
            Vec3 center = falling.start().lerp(falling.end(), k * (1 - corpo.aceleracao()) + k * k * corpo.aceleracao());
            pose.pushPose();
            pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
            drawBall(pose, buffers, corpo, falling.dir(), (float) Math.toRadians(corpo.girar() * age));
            pose.popPose();
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(TEXTURE));
        buffers.endBatch(RenderType.lightning());
    }

    private static void drawBall(PoseStack pose, MultiBufferSource buffers, MeteoroSpec.Corpo corpo, Vec3 dir, float spin) {
        float radius = (float) corpo.raio();
        Vector3f fall = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        // Spin around the axis across the fall, so it tumbles forward like a rolling rock.
        Vector3f axis = new Vector3f(fall).cross(0, 1, 0);
        if (axis.lengthSquared() < 1.0E-4f) {
            axis.set(1, 0, 0);
        }
        Quaternionf rotation = new Quaternionf().rotateAxis(spin, axis.normalize());
        int dark = parse(corpo.corEscura(), 0x1C1012);
        int hot = parse(corpo.corBrilho(), 0xFFE6DC);
        float brilho = (float) corpo.brilho();
        PoseStack.Pose last = pose.last();

        VertexConsumer rock = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int ring = 0; ring < RINGS; ring++) {
            for (int seg = 0; seg < SEGMENTS; seg++) {
                int[][] corners = {{ring, seg}, {ring, seg + 1}, {ring + 1, seg + 1}, {ring + 1, seg}};
                for (int[] corner : corners) {
                    Vector3f normal = rotation.transform(unit(corner[0], corner[1]));
                    // Front (facing the fall) heats up towards corBrilho; brilho pushes the heat further back.
                    float heat = Mth.clamp((normal.dot(fall) + 1 - 1 / Math.max(0.1f, brilho)) * 0.8f, 0, 1);
                    int color = mix(dark, hot, heat * heat);
                    rock.addVertex(last, normal.x * radius, normal.y * radius, normal.z * radius)
                            .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255)
                            .setUv(corner[1] / (float) SEGMENTS, corner[0] / (float) RINGS)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(LightTexture.FULL_BRIGHT)
                            .setNormal(last, normal.x, normal.y, normal.z);
                }
            }
        }

        // Glow shell: a slightly bigger sphere, only visible on the leading side, drawn additively.
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float shell = radius * 1.07f;
        for (int ring = 0; ring < RINGS; ring++) {
            for (int seg = 0; seg < SEGMENTS; seg++) {
                int[][] corners = {{ring, seg}, {ring, seg + 1}, {ring + 1, seg + 1}, {ring + 1, seg}};
                for (int[] corner : corners) {
                    Vector3f normal = unit(corner[0], corner[1]);
                    float facing = Mth.clamp(normal.dot(fall), 0, 1);
                    int alpha = (int) (Mth.clamp(facing * facing * 0.55f * brilho, 0, 1) * 255);
                    glow.addVertex(last, normal.x * shell, normal.y * shell, normal.z * shell)
                            .setColor((hot >> 16) & 0xFF, (hot >> 8) & 0xFF, hot & 0xFF, alpha);
                }
            }
        }
    }

    private static Vector3f unit(int ring, int seg) {
        float theta = (float) Math.PI * ring / RINGS;
        float phi = Mth.TWO_PI * seg / SEGMENTS;
        float r = Mth.sin(theta);
        return new Vector3f(r * Mth.cos(phi), Mth.cos(theta), r * Mth.sin(phi));
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    private static int parse(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
