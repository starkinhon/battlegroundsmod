package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// What the screen is doing right now: impact frames, flashes, radial blur, space bending and the FOV punch.
// Started by SkillEffects; the passes themselves run in FxPost. The bending and blur run at the end of the level
// (so the hand and HUD stay sharp); impact frames and flashes run under the HUD, over the hand.
public final class ScreenFx {
    public static final int MAX_WARPS = 8;
    // Every bending is scaled by this: the files were tuned too strong.
    private static final float WARP_SCALE = 0.5f;

    private record Impact(int[] modes, int ticksPerFrame, float[] tint, Vec3 pos, long start) {
    }

    private record Timed(float amount, float[] color, Vec3 pos, long start, int life) {
        float k(double now) {
            double t = (now - start) / life;
            return t < 0 || t >= 1 ? 0 : (float) (amount * (1 - t) * (1 - t));
        }
    }

    // Space bending in a sphere: mode 0 ripple, 1 swirl, 2 lens, 3 whole screen.
    // follow/seguir keep it on an entity (lifted by up); otherwise it stays at pos.
    public record Warp(Vec3 pos, float radius, float strength, float speed, int mode, Vec3 axis, float[] tint, float tintAmount, long start, int life,
            int follow, String seguir, float up) {
        float envelope(double now) {
            double t = (now - start) / life;
            if (t < 0 || t >= 1) {
                return 0;
            }
            return (float) (smooth(0, 0.08, t) * (1 - smooth(0.55, 1, t)));
        }
    }

    private static long ticks;
    private static Impact impact;
    private static Timed flash;
    private static Timed radial;
    private static Timed fov;
    private static Timed vignette;
    private static final net.minecraft.resources.ResourceLocation VIGNETTE = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, "textures/fx/vignette.png");
    private static final List<Warp> WARPS = new ArrayList<>();

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static float levelPartial;

    private ScreenFx() {
    }

    public static long now() {
        return ticks;
    }

    public static void tick() {
        ticks++;
        WARPS.removeIf(w -> ticks - w.start() > w.life());
        if (impact != null && ticks - impact.start() >= (long) impact.modes().length * impact.ticksPerFrame()) {
            impact = null;
        }
    }

    public static void impactFrame(int[] modes, int ticksPerFrame, int tint, Vec3 pos, int delay) {
        if (modes.length > 0) {
            impact = new Impact(modes, Math.max(1, ticksPerFrame), rgb(tint), pos, ticks + delay);
        }
    }

    public static void flash(int color, float alpha, int life) {
        flash = new Timed(Mth.clamp(alpha, 0, 1), rgb(color), Vec3.ZERO, ticks, Math.max(1, life));
    }

    public static void radialBlur(Vec3 pos, float strength, int life) {
        radial = new Timed(strength, null, pos, ticks, Math.max(1, life));
    }

    public static void fovKick(float degrees, int life) {
        fov = new Timed(degrees, null, Vec3.ZERO, ticks, Math.max(1, life));
    }

    public static void warp(Vec3 pos, float radius, float strength, float speed, int mode, Vec3 axis, int tint, float tintAmount, int life,
            int follow, String seguir, float up) {
        WARPS.add(new Warp(pos, radius, strength * WARP_SCALE, speed, mode, axis, rgb(tint), tintAmount, ticks, Math.max(1, life), follow, seguir, up));
    }

    // Orange edges of the screen (being burned): comes in fast, holds, fades over the last part.
    public static void vignette(int color, float strength, int life) {
        float a = Mth.clamp(strength, 0, 1);
        if (vignette == null || vignette.k(ticks) < a * 0.8f) {
            vignette = new Timed(a, rgb(color), Vec3.ZERO, ticks, Math.max(1, life));
        } else {
            vignette = new Timed(vignette.amount(), vignette.color(), Vec3.ZERO, ticks - Math.max(1, life) / 8, Math.max(1, life));
        }
    }

    static boolean needsScene() {
        return impact != null || !WARPS.isEmpty() || radial != null && radial.k(ticks) > 0;
    }

    // The level's camera for this frame, so world positions can be found on screen later (also from the GUI).
    static void capture(RenderLevelStageEvent event) {
        VIEW.set(event.getModelViewMatrix());
        PROJ.set(event.getProjectionMatrix());
        camera = event.getCamera().getPosition();
        levelPartial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
    }

    // Bending and blur, at the end of the level. With a shader pack these move to the GUI, without depth.
    static void levelPasses() {
        if (!FxShaders.ready()) {
            return;
        }
        double now = ticks + levelPartial;
        boolean warp = FxConfig.on(FxConfig.DISTORTION) && !WARPS.isEmpty();
        float blur = FxConfig.on(FxConfig.RADIAL_BLUR) && radial != null ? radial.k(now) : 0;
        if (!warp && blur <= 0) {
            return;
        }
        FxPost.beginPasses();
        if (warp) {
            warpPass(now);
        }
        if (blur > 0) {
            radialPass(blur);
        }
        FxPost.endPasses();
    }

    // Layer under the HUD.
    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        double now = ticks + delta.getGameTimeDeltaPartialTick(false);
        boolean reduced = FxConfig.on(FxConfig.REDUCED_FLASH);
        if (FxShaders.ready()) {
            int mode = impactMode();
            boolean flat = FxShaders.flat();
            boolean warp = flat && FxConfig.on(FxConfig.DISTORTION) && !WARPS.isEmpty();
            float blur = flat && FxConfig.on(FxConfig.RADIAL_BLUR) && radial != null ? radial.k(now) : 0;
            if (mode >= 0 || warp || blur > 0) {
                graphics.flush();
                FxPost.beginPasses();
                if (warp) {
                    warpPass(now);
                }
                if (blur > 0) {
                    radialPass(blur);
                }
                if (mode >= 0) {
                    impactPass(mode, reduced);
                }
                FxPost.endPasses();
            }
        }
        if (vignette != null) {
            double t = (now - vignette.start()) / vignette.life();
            float a = t < 0 || t >= 1 ? 0 : (float) (vignette.amount() * Math.min(1, t * 8) * (t < 0.6 ? 1 : 1 - (t - 0.6) / 0.4));
            if (a > 0.004f) {
                float[] c = vignette.color();
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                graphics.setColor(c[0], c[1], c[2], a);
                graphics.blit(VIGNETTE, 0, 0, 0, 0, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(), graphics.guiHeight());
                graphics.setColor(1, 1, 1, 1);
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            }
        }
        if (flash != null) {
            float a = flash.k(now) * (reduced ? 0.35f : 1);
            if (a > 0.004f) {
                float[] c = flash.color();
                int argb = (int) (a * 255) << 24 | (int) (c[0] * 255) << 16 | (int) (c[1] * 255) << 8 | (int) (c[2] * 255);
                graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb);
            }
        }
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (fov != null && event.usedConfiguredFov()) {
            float k = fov.k(ticks + event.getPartialTick());
            if (k != 0) {
                event.setFOV(event.getFOV() + k);
            }
        }
    }

    private static int impactMode() {
        if (impact == null || !FxConfig.on(FxConfig.IMPACT_FRAMES)) {
            return -1;
        }
        long age = ticks - impact.start();
        if (age < 0) {
            return -1;
        }
        int frame = (int) (age / impact.ticksPerFrame());
        return frame < impact.modes().length ? impact.modes()[frame] : -1;
    }

    private static void impactPass(int mode, boolean reduced) {
        var shader = FxShaders.impact;
        var target = Minecraft.getInstance().getMainRenderTarget();
        float[] center = toScreen(impact.pos());
        shader.safeGetUniform("Screen").set((float) target.width, (float) target.height);
        shader.safeGetUniform("Center").set(center[0], center[1]);
        shader.safeGetUniform("Tint").set(impact.tint()[0], impact.tint()[1], impact.tint()[2]);
        shader.safeGetUniform("Mode").set((float) mode);
        shader.safeGetUniform("Seed").set((float) (ticks % 997));
        shader.safeGetUniform("DepthOn").set(FxPost.sceneDepth() > 0 ? 1f : 0f);
        shader.safeGetUniform("DepthRange").set(0.05f, Minecraft.getInstance().gameRenderer.getDepthFar());
        shader.safeGetUniform("Reduced").set(reduced ? 1f : 0f);
        FxPost.pass(shader);
    }

    private static void radialPass(float strength) {
        var shader = FxShaders.radial;
        var target = Minecraft.getInstance().getMainRenderTarget();
        float[] center = toScreen(radial.pos());
        shader.safeGetUniform("Screen").set((float) target.width, (float) target.height);
        shader.safeGetUniform("Center").set(center[0], center[1]);
        shader.safeGetUniform("Strength").set(strength);
        FxPost.pass(shader);
    }

    private static void warpPass(double now) {
        var shader = FxShaders.warp;
        List<Warp> live = new ArrayList<>(WARPS);
        live.sort(Comparator.comparingDouble(w -> -w.strength() * w.envelope(now)));
        int count = 0;
        for (Warp w : live) {
            if (count >= MAX_WARPS) {
                break;
            }
            float env = w.envelope(now);
            if (env <= 0) {
                continue;
            }
            Vec3 at = FxAnchor.point(w.follow(), w.seguir(), levelPartial);
            at = at == null ? w.pos() : at.add(0, w.up(), 0);
            Vector3f c = VIEW.transformPosition(at.subtract(camera).toVector3f(), new Vector3f());
            Vector3f axis = VIEW.transformDirection(w.axis().toVector3f(), new Vector3f());
            float age = (float) ((now - w.start()) / 20.0);
            Matrix4f m = new Matrix4f(
                    c.x, c.y, c.z, w.radius(),
                    w.strength() * env, age, w.speed(), w.mode(),
                    axis.x, axis.y, axis.z, 0,
                    w.tint()[0], w.tint()[1], w.tint()[2], w.tintAmount() * env);
            shader.safeGetUniform("Src" + count).set(m);
            count++;
        }
        if (count == 0) {
            return;
        }
        shader.safeGetUniform("Count").set((float) count);
        shader.safeGetUniform("WarpProj").set(new Matrix4f(PROJ));
        shader.safeGetUniform("InverseProj").set(new Matrix4f(PROJ).invert());
        shader.safeGetUniform("DepthOn").set(FxPost.sceneDepth() > 0 ? 1f : 0f);
        FxPost.pass(shader);
    }

    // Where a world point is on screen (0..1, y up); the middle of the screen if it is behind the camera.
    private static float[] toScreen(Vec3 pos) {
        Vector3f rel = pos.subtract(camera).toVector3f();
        Vector4f clip = new Vector4f(rel, 1);
        VIEW.transform(clip);
        PROJ.transform(clip);
        if (clip.w <= 0.01f) {
            return new float[] {0.5f, 0.5f};
        }
        return new float[] {Mth.clamp(clip.x / clip.w * 0.5f + 0.5f, -0.5f, 1.5f), Mth.clamp(clip.y / clip.w * 0.5f + 0.5f, -0.5f, 1.5f)};
    }

    private static float[] rgb(int color) {
        return new float[] {((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f};
    }

    private static double smooth(double a, double b, double x) {
        double t = Mth.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
