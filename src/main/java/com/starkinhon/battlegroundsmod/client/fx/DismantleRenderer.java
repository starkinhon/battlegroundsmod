package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Sukuna's thrown Dismantle (power/powers/sukuna/SukunaMelee): the crescent of the approved 3D preview
// (forja/sukuna-combo, Stark's version, 2026-11-09), drawn with shaders/core/souls_dismantle. It is born white-hot in
// the hand, black with a white outline in flight, goes through the target and a little past it, then slows down and
// dissolves like Kokushibo's moons (it never breaks apart). Everything is a function of the time since it left the hand,
// exactly as the preview's crescent.
public final class DismantleRenderer {
    // After its reach it slows over DECEL ticks (going OUT times its speed further) while it dissolves over DISS ticks,
    // the dissolve starting EARLY ticks before the reach.
    private static final double DISS = 9, DECEL = 3, OUT = 2, EARLY = 2;

    // origin: where it left the hand; dir: the way it flies (unit); across: its span, the way the hand slashed (unit);
    // size: 1 for a combo cut; speed in blocks a tick; reach: how far it flies at full speed.
    private record Cut(double start, Vec3 origin, Vec3 dir, Vec3 across, double size, double speed, double reach, float seed) {
        double passTicks() {
            return reach / speed;
        }

        double end() {
            return passTicks() - EARLY + DISS + 1;
        }
    }

    private static final List<Cut> CUTS = new ArrayList<>();

    private DismantleRenderer() {
    }

    public static void add(Vec3 origin, Vec3 velocity, Vec3 span, float reach, int seed) {
        double speed = Math.max(0.05, velocity.length());
        double size = Math.max(0.05, span.length());
        Vec3 dir = velocity.scale(1 / speed);
        Vec3 across = span.subtract(dir.scale(span.dot(dir)));
        across = across.lengthSqr() < 1.0E-6 ? new Vec3(-dir.z, 0, dir.x) : across.normalize();
        CUTS.add(new Cut(now(), origin, dir, across, size, speed, reach, (seed & 0xFFFF) / 6553.6f));
    }

    public static boolean active() {
        return !CUTS.isEmpty();
    }

    public static void clear() {
        CUTS.clear();
    }

    private static double now() {
        return ScreenFx.now() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static double snap(double u) {
        return 1 - Math.pow(1 - u, 5);
    }

    private static double io(double u) {
        return u < .5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
    }

    // FxWorld calls this at the end of the level, with the model view set to the camera's turn.
    static void render(Vec3 camera) {
        ShaderInstance sh = FxShaders.dismantle;
        if (CUTS.isEmpty() || sh == null) {
            return;
        }
        double now = now();
        CUTS.removeIf(c -> now - c.start() > c.end() || now < c.start() - 40);
        if (CUTS.isEmpty()) {
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        // premultiplied: black and white both read on any sky
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        for (Cut c : CUTS) {
            draw(sh, c, Math.max(0, now - c.start()), camera);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void draw(ShaderInstance sh, Cut c, double lt, Vec3 camera) {
        double pass = c.passTicks();
        double along = lt <= pass ? c.speed() * lt : c.speed() * pass + c.speed() * OUT * (1 - Math.exp(-(lt - pass) / DECEL));
        double diss = Mth.clamp((lt - pass + EARLY) / DISS, 0, 1);
        double grow = 0.55 + 0.45 * snap(Mth.clamp(lt / 0.6, 0, 1));
        double s = 0.8 * c.size() * grow * (1 + diss * 0.12);
        double heat = 1 - io(Mth.clamp((lt - 0.3) / 0.9, 0, 1));
        sh.safeGetUniform("FTime").set((float) (lt / 20));
        sh.safeGetUniform("Heat").set((float) heat);
        sh.safeGetUniform("Diss").set((float) diss);
        sh.safeGetUniform("Seed").set(c.seed());
        sh.safeGetUniform("Alpha").set(1f);
        // the quad: its middle a little behind the blade's front, span along across, convex side ahead
        Vec3 mid = c.origin().add(c.dir().scale(along - 0.35 * s)).subtract(camera);
        Vec3 r = c.across().scale(s), f = c.dir().scale(s);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        put(b, mid.subtract(r).subtract(f), 0, 0);
        put(b, mid.add(r).subtract(f), 1, 0);
        put(b, mid.add(r).add(f), 1, 1);
        put(b, mid.subtract(r).add(f), 0, 1);
        MeshData mesh = b.build();
        if (mesh != null) {
            RenderSystem.setShader(() -> sh);
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void put(BufferBuilder b, Vec3 p, float u, float v) {
        b.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(u, v);
    }
}
