package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.skill.DestructionSphereSpec;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Bills' Esfera da Destruição on C (power/powers/bills/DestructionSphere), drawn with shader shapes only (no textures).
// The ball is the Esfera's sun (EsferaRenderer.drawSun) at any size: tiny at his finger and while it chases the target,
// lines of light rushing into it while it implodes, a white shell and a ring blown out when it bursts to full size, and
// the screen shaking for whoever is close to it while it is huge.
// The explosion is an anime one (the reference video): a white-hot point, an orange dome full of yellow blobs with
// thin arcs of fire thrown out and rings of light over it, a pale flash, then a dome of brown smoke rising into a
// mushroom. Then all of it runs back into a point (time rewinding, light sucked in), waits a moment and blows up again,
// bigger, with a shockwave running out along the ground. The whole explosion is a function of how far along it is, so
// rewinding is just running that backwards.
public final class SphereRenderer {
    // prev/pos: the last two positions the server sent (one per tick), to slide between them.
    private record Ball(Vec3 prev, Vec3 pos, float prevRadius, float radius, boolean giant, long updated, long born, long burst) {
    }

    private record Blast(Vec3 pos, boolean awakened, long born, int seed) {
    }

    private static final Map<Integer, Ball> BALLS = new HashMap<>();
    private static final List<Blast> BLASTS = new ArrayList<>();
    private static int seeds;

    private static final float[] WHITE = {1, 1, 1};
    private static final float[] HOT = FxDraw.rgb(0xFFF3B0);
    private static final float[] YELLOW = FxDraw.rgb(0xFFE24A);
    private static final float[] ORANGE = FxDraw.rgb(0xFF7A1A);
    private static final float[] DEEP = FxDraw.rgb(0xE24A0A);
    private static final float[] PALE = FxDraw.rgb(0xFFE6C4);
    private static final float[] SMOKE_DARK = FxDraw.rgb(0x8E6A44);
    private static final float[] SMOKE = FxDraw.rgb(0xC09262);
    private static final float[] SMOKE_LIGHT = FxDraw.rgb(0xDDBB8C);
    private static final float[] SHADOW = FxDraw.rgb(0x2E2116);
    private static final float[] SKY = FxDraw.rgb(0xDDEBFF);
    private static final float[] REWIND = FxDraw.rgb(0xB8D4FF);

    private SphereRenderer() {
    }

    public static void sphere(int id, int mode, Vec3 pos, float radius, boolean giant) {
        if (mode == 2) {
            BALLS.remove(id);
            return;
        }
        long now = ScreenFx.now();
        Ball old = BALLS.get(id);
        if (old == null) {
            BALLS.put(id, new Ball(pos, pos, radius, radius, giant, now, now, -1));
            return;
        }
        long burst = giant && !old.giant() ? now : old.burst();
        BALLS.put(id, new Ball(old.pos(), pos, old.radius(), radius, giant, now, old.born(), burst));
    }

    public static void blast(Vec3 pos, boolean awakened) {
        BLASTS.add(new Blast(pos, awakened, ScreenFx.now(), (seeds++ * 7919) ^ 0x5F3759DF));
    }

    public static boolean active() {
        return !BALLS.isEmpty() || !BLASTS.isEmpty();
    }

    public static void clear() {
        BALLS.clear();
        BLASTS.clear();
    }

    static void render(Vec3 camera, double now, float partial) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        BALLS.values().removeIf(b -> now - b.updated() > 6);
        for (Map.Entry<Integer, Ball> entry : BALLS.entrySet()) {
            drawBall(entry.getValue(), entry.getKey() * 2027 + 11, camera, now, partial);
        }
        BLASTS.removeIf(b -> now - b.born() > spec(b.awakened()).explosao().total() + 2);
        for (Blast b : BLASTS) {
            drawBlast(b, camera, (float) (now - b.born()));
        }
    }

    private static DestructionSphereSpec spec(boolean awakened) {
        return DestructionSphereSpec.get(awakened);
    }

    // ---------- the ball ----------

    private static void drawBall(Ball b, int seed, Vec3 camera, double now, float partial) {
        float age = (float) (now - b.born());
        float r = Mth.lerp(partial, b.prevRadius(), b.radius());
        Vec3 world = b.prev().lerp(b.pos(), partial);
        Vector3f at = world.subtract(camera).toVector3f();
        Vec3 step = b.pos().subtract(b.prev());
        Vector3f dir = step.lengthSqr() > 0.01 ? step.normalize().toVector3f() : null;
        float distance = (float) world.distanceTo(camera);

        // Tiny and far away it would be lost: a soft glow that keeps a few pixels on screen.
        float least = distance * 0.012f;
        if (r < least) {
            FxDraw.billboard(at, least * 4, 0, FxDraw.SOFT, 0.2f, ORANGE, 0.35f, true);
            FxDraw.billboard(at, least * 1.6f, 0, FxDraw.SOFT, 0.7f, HOT, 0.7f, true);
        }

        // Imploding: lines of light rushing into it from all around.
        if (!b.giant() && b.radius() < b.prevRadius() - 1.0E-4f) {
            Camera view = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vector3f left = new Vector3f(view.getLeftVector());
            Vector3f up = new Vector3f(view.getUpVector());
            float reach = Math.max(3, r * 30);
            for (int k = 0; k < 22; k++) {
                float a = FxWorld.hash(seed, k, 21) * Mth.TWO_PI;
                float u = ((age * 0.12f + FxWorld.hash(seed, k, 22)) % 1);
                float out = reach * (1 - u);
                Vector3f d = new Vector3f(left).mul(Mth.cos(a)).add(new Vector3f(up).mul(Mth.sin(a)));
                FxDraw.ribbon(new Vector3f(d).mul(out + reach * 0.25f).add(at), new Vector3f(d).mul(out).add(at), reach * 0.012f, FxDraw.STREAK, 0.8f,
                        k % 3 == 0 ? WHITE : HOT, 0.8f * u, true);
            }
            FxDraw.billboard(at, reach * 0.5f, age * 0.2f, FxDraw.FLARE, 0.6f, HOT, 0.4f, true);
        }

        EsferaRenderer.drawSun(at, dir, r, age, seed);

        if (b.burst() >= 0) {
            float since = (float) (now - b.burst());
            if (since < 14) {
                // The burst: a white shell and a ring of light blown off it.
                float k = since / 14;
                float out = FxWorld.easeOut(k);
                FxDraw.billboard(at, r * (2.2f + 3.5f * out), 0, FxDraw.RING, 0.05f, WHITE, (1 - k) * 0.9f, true);
                FxDraw.billboard(at, r * (2 + 1.2f * out), 0, FxDraw.SOFT, 0.5f, HOT, (1 - k) * 0.6f, true);
                FxDraw.disk(new Vector3f(at), new Vector3f(0.15f, 1, 0.1f), r * (2.5f + 7 * out), FxDraw.RING, 0.03f, PALE, (1 - k) * 0.7f, true);
            }
            // Close to it while it is huge, the screen shakes (less the farther away).
            DestructionSphereSpec.Tremor tremor = spec(false).tremor();
            double reach = Math.max(1, tremor.alcance()) + r;
            if (b.giant() && distance < reach && tremor.forca() > 0) {
                ScreenShake.start((float) (tremor.forca() * (1 - distance / reach)), 4);
            }
        }
    }

    // ---------- the explosion ----------

    private static void drawBlast(Blast b, Vec3 camera, float age) {
        DestructionSphereSpec.Explosao e = spec(b.awakened()).explosao();
        Vector3f at = b.pos().subtract(camera).toVector3f();
        float r1 = (float) Math.max(2, e.raio());
        float r2 = (float) Math.max(r1, e.raio2());
        int ida = Math.max(1, e.idaTicks());
        int rewind = e.rewindAt();
        int volta = Math.max(1, e.voltaTicks());
        int second = e.secondAt();
        int segunda = Math.max(1, e.segundaTicks());
        if (age < rewind) {
            explosion(at, r1, Math.min(1, age / ida), b.seed(), age, 1, false);
        } else if (age < rewind + volta) {
            // Running back: the same explosion, backwards, faster at the end, with the light sucked into it.
            float v = (age - rewind) / volta;
            float u = 1 - v * v * (3 - 2 * v);
            explosion(at, r1, u, b.seed(), age, 1, true);
            suck(at, r1, b.seed(), age, 1 - v * 0.5f);
        } else if (age < second) {
            // The point it all ran back into, building up.
            float w = (age - rewind - volta) / Math.max(1, second - rewind - volta);
            core(at, r1, b.seed(), age, w);
        } else {
            float s = Math.min(1, (age - second) / segunda);
            float fade = 1 - smooth(0.72f, 1, s);
            explosion(at, r2, Math.min(1, s * 1.35f), b.seed() + 101, age, fade, false);
            shockwave(at, r2, (float) Math.max(r2, e.raioOnda()), age - second, b.seed());
        }
    }

    // One explosion at u (0 = the first instant, 1 = the smoke risen high), radius R, sitting on the ground at at.
    private static void explosion(Vector3f at, float big, float u, int seed, float age, float fade, boolean rewinding) {
        float g = FxWorld.easeOut(Math.min(1, u / 0.28f));
        float r = big * (0.08f + 0.92f * g);
        float fire = (1 - smooth(0.3f, 0.52f, u)) * fade;
        float pale = smooth(0.22f, 0.33f, u) * (1 - smooth(0.4f, 0.52f, u)) * fade;
        float smoke = smooth(0.32f, 0.5f, u) * fade;
        float rise = smooth(0.45f, 1, u);
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f c = new Vector3f(at).add(0, r * 0.45f, 0);

        // The shadow it throws on the ground.
        FxDraw.disk(new Vector3f(at).add(0, 0.08f, 0), up, r * 3.2f, FxDraw.SOFT, 0.2f, SHADOW, 0.4f * Math.max(fire, smoke), false);

        // The first instant: a white-hot point blowing out.
        float spark = 1 - Math.min(1, u / 0.12f);
        if (spark > 0) {
            FxDraw.billboard(c, big * 1.3f * (0.4f + g), 0, FxDraw.SOFT, 0.8f, WHITE, spark, true);
            FxDraw.billboard(c, big * 2.6f * (0.4f + g), age * 0.03f, FxDraw.FLARE, 0.4f, HOT, spark * 0.8f, true);
        }

        // Light along the ground, out fast and gone.
        float ground = 1 - smooth(0.12f, 0.38f, u);
        if (ground > 0) {
            float out = FxWorld.easeOut(Math.min(1, u / 0.35f));
            FxDraw.disk(new Vector3f(at).add(0, 0.15f, 0), up, big * (0.6f + 2.6f * out), FxDraw.RING, 0.06f, YELLOW, ground * fade, true);
            FxDraw.disk(new Vector3f(at).add(0, 0.12f, 0), up, big * (0.4f + 1.4f * out), FxDraw.SOFT, 0.3f, HOT, 0.5f * ground * fade, true);
        }

        if (fire > 0) {
            // The glow it throws all around.
            FxDraw.billboard(c, r * 3.4f, 0, FxDraw.SOFT, 0, DEEP, 0.3f * fire, true);
            FxDraw.billboard(c, r * 2.2f, 0, FxDraw.SOFT, 0.2f, YELLOW, 0.35f * fire, true);
            // The orange dome itself, on the alpha layer so the color holds over a bright sky.
            for (int k = 0; k < 42; k++) {
                Vector3f d = FxWorld.sphere(seed, k);
                d.y = Math.abs(d.y) * 0.9f - 0.15f;
                float h = FxWorld.hash(seed, k, 1);
                Vector3f pos = new Vector3f(d).mul(r * 0.55f * (0.35f + 0.65f * h)).add(c);
                float[] color = FxDraw.mix(ORANGE, DEEP, FxWorld.hash(seed, k, 2) * 0.5f);
                FxDraw.billboard(pos, r * (0.6f + 0.3f * h), FxWorld.hash(seed, k, 3) * 6, FxDraw.SMOKE, 0.15f, color, 0.9f * fire, false);
            }
            // Yellow blobs boiling on it, each popping on its own beat.
            for (int k = 0; k < 30; k++) {
                Vector3f d = FxWorld.sphere(seed + 3, k);
                d.y = Math.abs(d.y) * 0.85f - 0.05f;
                float beat = 0.6f + 0.4f * Mth.sin(age * (0.25f + 0.2f * FxWorld.hash(seed, k, 4)) + k * 2.1f);
                Vector3f pos = new Vector3f(d).mul(r * (0.55f + 0.2f * FxWorld.hash(seed, k, 5))).add(c);
                FxDraw.billboard(pos, r * (0.2f + 0.2f * FxWorld.hash(seed, k, 6)) * (0.8f + 0.3f * beat), 0, FxDraw.SOFT, 0.8f,
                        FxWorld.hash(seed, k, 7) < 0.3f ? HOT : YELLOW, 0.95f * fire * beat, true);
            }
            // Thin arcs of fire thrown out to the sides and falling back to the ground.
            float reach = FxWorld.easeOut(Mth.clamp((u - 0.04f) / 0.22f, 0, 1));
            float arcs = (1 - smooth(0.22f, 0.4f, u)) * fade;
            for (int k = 0; k < 6 && arcs > 0; k++) {
                float a = (k + FxWorld.hash(seed, k, 8) * 0.6f) / 6 * Mth.TWO_PI;
                Vector3f side = new Vector3f(Mth.cos(a), 0, Mth.sin(a));
                float far = big * (1.6f + 0.8f * FxWorld.hash(seed, k, 9));
                Vector3f last = null;
                for (int j = 0; j <= 12; j++) {
                    float s = j / 12f * reach;
                    if (s < reach - 0.45f) {
                        continue;
                    }
                    Vector3f point = new Vector3f(side).mul(big * 0.5f + far * s).add(c)
                            .add(0, big * 0.55f * Mth.sin(s * Mth.PI * 0.75f) - big * 0.95f * s * s, 0);
                    if (last != null) {
                        FxDraw.ribbon(last, point, big * 0.05f, FxDraw.STREAK, 0.5f, ORANGE, arcs, true);
                        FxDraw.ribbon(last, point, big * 0.018f, FxDraw.STREAK, 0.9f, HOT, arcs, true);
                    }
                    last = point;
                }
            }
            // Rings of light hanging over the top of the dome.
            float halo = smooth(0.06f, 0.14f, u) * (1 - smooth(0.26f, 0.36f, u)) * fade;
            if (halo > 0) {
                for (int k = 0; k < 2; k++) {
                    FxDraw.disk(new Vector3f(c).add(0, r * (1.0f + 0.12f * k), 0), up, r * (2.0f + 0.5f * k + 0.4f * u), FxDraw.RING, 0.025f, SKY,
                            0.8f * halo, true);
                }
            }
        }

        // The pale flash as the fire turns to smoke.
        if (pale > 0) {
            FxDraw.billboard(c, r * 2.3f, 0, FxDraw.SOFT, 0.6f, PALE, 0.9f * pale, false);
            FxDraw.billboard(c, r * 3, 0, FxDraw.SOFT, 0.2f, PALE, 0.4f * pale, true);
        }

        if (smoke > 0) {
            float rs = r * (1 + 0.3f * rise);
            Vector3f cap = new Vector3f(at).add(0, rs * 0.5f + rise * big * 1.7f, 0);
            // The stem it rises on, thinner at the top.
            if (rise > 0) {
                float stem = smooth(0, 0.25f, rise);
                for (int j = 0; j < 16; j++) {
                    float f = (j + 0.5f) / 16;
                    float a = FxWorld.hash(seed, j, 10) * Mth.TWO_PI;
                    float out = r * 0.22f * FxWorld.hash(seed, j, 11);
                    Vector3f pos = new Vector3f(Mth.cos(a) * out, (cap.y - at.y) * f, Mth.sin(a) * out).add(at);
                    FxDraw.billboard(pos, r * (0.55f - 0.2f * f) * (0.8f + 0.4f * FxWorld.hash(seed, j, 12)), FxWorld.hash(seed, j, 13) * 6 + age * 0.003f,
                            FxDraw.SMOKE, 0.35f, FxDraw.mix(SMOKE_DARK, SMOKE, f), 0.85f * smoke * stem, false);
                }
            }
            // Dust skirting out along the ground.
            for (int k = 0; k < 14; k++) {
                float a = (k + FxWorld.hash(seed, k, 14)) / 14 * Mth.TWO_PI;
                float out = r * (0.85f + 0.35f * FxWorld.hash(seed, k, 15)) * (1 + 0.4f * rise);
                Vector3f pos = new Vector3f(Mth.cos(a) * out, r * 0.15f, Mth.sin(a) * out).add(at);
                FxDraw.billboard(pos, r * (0.45f + 0.2f * FxWorld.hash(seed, k, 16)), FxWorld.hash(seed, k, 17) * 6, FxDraw.SMOKE, 0.4f,
                        FxDraw.mix(SMOKE, SMOKE_LIGHT, 0.4f), 0.75f * smoke * (1 - 0.5f * rise), false);
            }
            // The round cap of smoke, slowly rolling.
            for (int k = 0; k < 46; k++) {
                Vector3f d = FxWorld.sphere(seed + 11, k);
                d.y = d.y * 0.75f + 0.1f;
                float h = FxWorld.hash(seed, k, 18);
                Vector3f pos = new Vector3f(d).mul(rs * 0.6f * (0.45f + 0.55f * h)).add(cap);
                float shade = Mth.clamp(0.35f + d.y * 0.45f + (FxWorld.hash(seed, k, 19) - 0.5f) * 0.3f, 0, 1);
                float[] color = shade < 0.5f ? FxDraw.mix(SMOKE_DARK, SMOKE, shade * 2) : FxDraw.mix(SMOKE, SMOKE_LIGHT, shade * 2 - 1);
                FxDraw.billboard(pos, rs * (0.5f + 0.35f * h), FxWorld.hash(seed, k, 20) * 6 + age * 0.004f * (k % 2 == 0 ? 1 : -1), FxDraw.SMOKE,
                        0.3f, color, 0.92f * smoke, false);
            }
            // Embers still glowing inside it for a while.
            float embers = smoke * (1 - smooth(0.5f, 0.8f, u));
            for (int k = 0; k < 10 && embers > 0; k++) {
                Vector3f pos = new Vector3f(FxWorld.sphere(seed + 17, k)).mul(rs * 0.45f).add(cap);
                FxDraw.billboard(pos, rs * 0.4f, 0, FxDraw.SOFT, 0.3f, ORANGE, 0.35f * embers, true);
            }
        }

        if (rewinding) {
            // A cold tint over it while time runs backwards.
            FxDraw.billboard(c, r * 2.6f, 0, FxDraw.SOFT, 0.2f, REWIND, 0.18f * fade, true);
        }
    }

    // While it runs back: lines of light streaming in to the middle from all around.
    private static void suck(Vector3f at, float big, int seed, float age, float strength) {
        Vector3f c = new Vector3f(at).add(0, big * 0.4f, 0);
        for (int k = 0; k < 36; k++) {
            Vector3f d = FxWorld.sphere(seed + 29, k);
            d.y = Math.abs(d.y) * 0.9f;
            float u = (age * 0.07f + FxWorld.hash(seed, k, 30)) % 1;
            float out = big * 3 * (1 - u * u);
            Vector3f from = new Vector3f(d).mul(out + big * 0.6f).add(c);
            Vector3f to = new Vector3f(d).mul(out).add(c);
            FxDraw.ribbon(from, to, big * 0.025f, FxDraw.STREAK, 0.8f, k % 4 == 0 ? WHITE : REWIND, 0.7f * u * strength, true);
        }
    }

    // The point it all ran back into: tiny and bright, pulsing faster and growing until it goes again.
    private static void core(Vector3f at, float big, int seed, float age, float w) {
        Vector3f c = new Vector3f(at).add(0, big * 0.1f, 0);
        float pulse = 1 + 0.25f * Mth.sin(age * (0.6f + 1.4f * w));
        FxDraw.billboard(c, big * (0.25f + 0.35f * w) * pulse, 0, FxDraw.SOFT, 0.85f, WHITE, 1, true);
        FxDraw.billboard(c, big * (0.6f + 0.6f * w) * pulse, 0, FxDraw.SOFT, 0.3f, YELLOW, 0.6f, true);
        FxDraw.billboard(c, big * (1.2f + 1.5f * w), age * 0.05f, FxDraw.FLARE, 0.5f, HOT, 0.4f + 0.4f * w, true);
        FxDraw.billboard(c, big * (1 + 1.2f * w), -age * 0.07f + 0.5f, FxDraw.FLARE, 0.7f, REWIND, 0.3f, true);
    }

    // The second blast's shockwave: a ring of light and dust running out along the ground and a dome of air blown off.
    private static void shockwave(Vector3f at, float big, float reach, float t, int seed) {
        float life = 26;
        if (t > life) {
            return;
        }
        float k = t / life;
        float out = FxWorld.easeOut(k);
        float fade = 1 - k;
        Vector3f up = new Vector3f(0, 1, 0);
        float size = big + (reach - big) * out;
        FxDraw.disk(new Vector3f(at).add(0, 0.3f, 0), up, size * 2, FxDraw.RING, 0.03f, WHITE, fade, true);
        FxDraw.disk(new Vector3f(at).add(0, 0.25f, 0), up, size * 2.05f, FxDraw.RING, 0.08f, PALE, 0.6f * fade, true);
        // Dust kicked up along the front.
        for (int j = 0; j < 40; j++) {
            float a = (j + FxWorld.hash(seed, j, 40)) / 40 * Mth.TWO_PI;
            Vector3f pos = new Vector3f(Mth.cos(a) * size, big * 0.08f + big * 0.1f * FxWorld.hash(seed, j, 41), Mth.sin(a) * size).add(at);
            FxDraw.billboard(pos, big * (0.35f + 0.25f * FxWorld.hash(seed, j, 42)) * (0.6f + out), FxWorld.hash(seed, j, 43) * 6, FxDraw.SMOKE, 0.4f,
                    SMOKE_LIGHT, 0.6f * fade, false);
        }
        // The dome of air, a shell blown off the top.
        float shell = Math.min(1, t / 10);
        if (shell < 1) {
            Vector3f c = new Vector3f(at).add(0, big * 0.3f, 0);
            FxDraw.billboard(c, big * (2 + 3 * FxWorld.easeOut(shell)), 0, FxDraw.RING, 0.04f, WHITE, (1 - shell) * 0.9f, true);
        }
    }

    private static float smooth(float a, float b, float x) {
        float k = Mth.clamp((x - a) / (b - a), 0, 1);
        return k * k * (3 - 2 * k);
    }
}
