package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.skill.MeteoroSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Draws the Meteoro like the Forja does: a dark rock sphere whose side facing the fall glows corBrilho, an additive halo
// around that side, and the warning circle on the ground. The server sends where it falls from and to; the timing and
// look come from the same madara.meteoro.json, so the ball moves exactly like the server's hitbox.
public final class MeteorRenderer {
    private static final int RINGS = 28;
    private static final int SEGMENTS = 40;
    private static final int CIRCLE = 64;

    private record Falling(MeteoroSpec spec, Vec3 start, Vec3 end, Vec3 dir, long startTick, int ticks) {
    }

    private static final List<Falling> FALLING = new ArrayList<>();

    private MeteorRenderer() {
    }

    public static void start(String skill, boolean awakened, Vec3 start, Vec3 end, int delay, int fallTicks) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        MeteoroSpec spec = SkillSpecs.get(skill, MeteoroSpec.class, awakened);
        FALLING.add(new Falling(spec, start, end, end.subtract(start).normalize(), level.getGameTime() + delay, Math.max(1, fallTicks)));
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
        FALLING.removeIf(falling -> now - falling.startTick() > falling.ticks() + 1);
        for (Falling falling : FALLING) {
            double age = now - falling.startTick() + partial;
            int ticks = falling.ticks();
            if (age < 0 || age > ticks) {
                continue;
            }
            MeteoroSpec.Corpo corpo = falling.spec().meteoro();
            double k = age / ticks;
            Vec3 center = falling.start().lerp(falling.end(), k * (1 - corpo.aceleracao()) + k * k * corpo.aceleracao());
            Vec3 rel = center.subtract(camera);
            pose.pushPose();
            pose.translate(rel.x, rel.y, rel.z);
            drawBall(pose, buffers, corpo, falling.dir(), (float) Math.toRadians(corpo.girar() * age), rel.toVector3f());
            pose.popPose();
            if (falling.spec().aviso().ativo()) {
                Vec3 ground = falling.end().subtract(0, corpo.raio() * (1 - corpo.afundar()), 0).subtract(camera);
                // Gets stronger as it comes down and blinks in the last second, like the Forja.
                double blink = ticks - age < 20 ? ((now / 3) % 2 == 1 ? 1 : 0.4) : 1;
                float alpha = (float) ((0.1 + 0.35 * Math.min(1, k)) * blink);
                drawCircle(pose, buffers, ground, (float) falling.spec().impacto().raio(), parse(falling.spec().aviso().cor(), 0xFF3020), alpha);
            }
        }
        buffers.endBatch(FxRenderTypes.SOLID);
        buffers.endBatch(FxRenderTypes.GLOW);
        buffers.endBatch(FxRenderTypes.AREA);
    }

    private static void drawBall(PoseStack pose, MultiBufferSource buffers, MeteoroSpec.Corpo corpo, Vec3 dir, float spin, Vector3f toBall) {
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

        // Rock: noisy dark patches (fixed to the rock, so they spin with it), blending into corBrilho on the front.
        VertexConsumer rock = buffers.getBuffer(FxRenderTypes.SOLID);
        for (int ring = 0; ring < RINGS; ring++) {
            for (int seg = 0; seg < SEGMENTS; seg++) {
                Vector3f mid = unit(ring + 0.5f, seg + 0.5f);
                float n = hash(mid, 10, 0), n2 = hash(mid, 4, 3.1f);
                float shade = 0.5f + 0.6f * n * n2 + 0.35f * n2;
                for (float[] corner : corners(ring, seg)) {
                    Vector3f local = unit(corner[0], corner[1]);
                    Vector3f normal = rotation.transform(new Vector3f(local));
                    float heat = smoothstep(-0.05f, 0.85f, normal.dot(fall));
                    float[] c = mix(scale(dark, shade), rgb(hot), heat);
                    rock.addVertex(last, normal.x * radius, normal.y * radius, normal.z * radius).setColor(c[0], c[1], c[2], 1f);
                }
            }
        }

        // Halo: a bigger shell that shines at its edge, only on the side facing the fall.
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW);
        float shell = radius * 1.22f;
        float[] h = rgb(hot);
        for (int ring = 0; ring < RINGS; ring++) {
            for (int seg = 0; seg < SEGMENTS; seg++) {
                Vector3f mid = unit(ring + 0.5f, seg + 0.5f);
                if (mid.dot(new Vector3f(toBall).mul(-1).sub(new Vector3f(mid).mul(shell)).normalize()) < 0) {
                    continue; // back face, the Forja only draws the front of the shell
                }
                for (float[] corner : corners(ring, seg)) {
                    Vector3f normal = unit(corner[0], corner[1]);
                    Vector3f view = new Vector3f(toBall).add(new Vector3f(normal).mul(shell)).mul(-1).normalize();
                    float rim = (float) Math.pow(1 - Math.abs(normal.dot(view)), 1.6);
                    float front = smoothstep(-0.3f, 0.9f, normal.dot(fall));
                    float a = Mth.clamp(rim * front * brilho, 0, 1);
                    glow.addVertex(last, normal.x * shell, normal.y * shell, normal.z * shell).setColor(h[0], h[1], h[2], a);
                }
            }
        }
    }

    // Flat see-through disk on the ground where it will land.
    private static void drawCircle(PoseStack pose, MultiBufferSource buffers, Vec3 center, float radius, int color, float alpha) {
        VertexConsumer area = buffers.getBuffer(FxRenderTypes.AREA);
        PoseStack.Pose last = pose.last();
        float[] c = rgb(color);
        float x = (float) center.x, y = (float) center.y + 0.05f, z = (float) center.z;
        for (int i = 0; i < CIRCLE; i++) {
            float a0 = Mth.TWO_PI * i / CIRCLE, a1 = Mth.TWO_PI * (i + 1) / CIRCLE;
            area.addVertex(last, x, y, z).setColor(c[0], c[1], c[2], alpha);
            area.addVertex(last, x + Mth.cos(a1) * radius, y, z + Mth.sin(a1) * radius).setColor(c[0], c[1], c[2], alpha);
            area.addVertex(last, x + Mth.cos(a0) * radius, y, z + Mth.sin(a0) * radius).setColor(c[0], c[1], c[2], alpha);
            area.addVertex(last, x, y, z).setColor(c[0], c[1], c[2], alpha);
        }
    }

    private static float[][] corners(int ring, int seg) {
        return new float[][] {{ring, seg}, {ring, seg + 1}, {ring + 1, seg + 1}, {ring + 1, seg}};
    }

    private static Vector3f unit(float ring, float seg) {
        float theta = (float) Math.PI * ring / RINGS;
        float phi = Mth.TWO_PI * seg / SEGMENTS;
        float r = Mth.sin(theta);
        return new Vector3f(r * Mth.cos(phi), Mth.cos(theta), r * Mth.sin(phi));
    }

    // The Forja's hash(floor(normal * cells) + offset): the same value across one patch of the rock.
    private static float hash(Vector3f normal, float cells, float offset) {
        float x = Mth.floor(normal.x * cells) + offset, y = Mth.floor(normal.y * cells) + offset, z = Mth.floor(normal.z * cells) + offset;
        float v = Mth.sin(x * 12.9898f + y * 78.233f + z * 37.719f) * 43758.5453f;
        return v - Mth.floor(v);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float[] rgb(int color) {
        return new float[] {((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f};
    }

    private static float[] scale(int color, float k) {
        float[] c = rgb(color);
        return new float[] {Math.min(1, c[0] * k), Math.min(1, c[1] * k), Math.min(1, c[2] * k)};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }

    private static int parse(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
