package com.starkinhon.battlegroundsmod.client.fx;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.client.PlanetCutscene;
import com.starkinhon.battlegroundsmod.skill.DestructionPlanetSpec;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// The square Earth of the C blowing up for the Destruction Touch on a planet (client/PlanetCutscene), in the anime's
// order: a pink glow gathers on the half facing Bills and streaks out of it; a white point lights up there and bursts
// into a blue-white starburst; a quiet moment; then a huge white shockwave disc cuts through the planet, purple
// lightning cracks over that half, it breaks into cubes that fly off glowing violet and melt into a big lilac cloud,
// and black rocks are thrown far out.
// The planet is cut into pedacos x pedacos x pedacos cubes, each wrapped in its part of the Earth texture (dark rock
// inside); solid() draws them with the world's render types, render() all the light on top.
final class PlanetScene {
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] PINK = FxDraw.rgb(0xFF8AE8);
    private static final float[] HOT_PINK = FxDraw.rgb(0xFF4FD8);
    private static final float[] LILAC = FxDraw.rgb(0xE2B4FF);
    private static final float[] VIOLET = FxDraw.rgb(0xA040F0);
    private static final float[] PURPLE = FxDraw.rgb(0x7A20D0);
    private static final float[] STAR_BLUE = FxDraw.rgb(0xCFE0FF);
    private static final float[] ATMOSPHERE = FxDraw.rgb(0x59A8FF);
    private static final float[] CLOUD_LIGHT = FxDraw.rgb(0xEBC6F7);
    private static final float[] CLOUD_MID = FxDraw.rgb(0xC995EA);
    private static final float[] CLOUD_SHADOW = FxDraw.rgb(0x8C58BE);
    private static final int ROCK_TINT = 0xFF17121C;
    private static final int INSIDE_TINT = 0xFF4A1A40;
    private static final int SEED = 6113;

    // The six faces of the big cube, as SpaceScene.planet draws them: texture tile, corners (bottom-left, bottom-right,
    // top-right, top-left) and normal.
    private static final int[] TILES = {0, 1, 2, 4, 3, 5};
    private static final float[][] CORNERS = {
            {-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1},
            {1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1},
            {1, -1, 1, -1, -1, 1, -1, 1, 1, 1, 1, 1},
            {-1, -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, 1},
            {-1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1},
            {-1, -1, 1, 1, -1, 1, 1, -1, -1, -1, -1, -1}};
    private static final float[][] NORMALS = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private PlanetScene() {
    }

    // ---------- timing ----------

    private static DestructionPlanetSpec spec() {
        return PlanetCutscene.spec();
    }

    private static float size() {
        return (float) spec().espaco().tamanho();
    }

    // The planet's turn, stopped at the blast so the half that goes is the one that was glowing.
    private static Quaternionf turn(double t) {
        double turning = Math.min(t, spec().blastTick()) - spec().spaceTick();
        float yaw = (float) (Math.max(0, turning) * spec().espaco().girar()) + 30;
        return new Quaternionf().rotationY(yaw * Mth.DEG_TO_RAD).rotateX(8 * Mth.DEG_TO_RAD);
    }

    private static int pieces() {
        return Math.max(2, Math.min(10, spec().planeta().pedacos()));
    }

    // Where a cube is in the planet (its middle, -1 to 1 on each side).
    private static Vector3f middle(int i, int j, int k, int n) {
        return new Vector3f(-1 + (2 * i + 1) / (float) n, -1 + (2 * j + 1) / (float) n, -1 + (2 * k + 1) / (float) n);
    }

    // A flying cube: how far along its flight it is (in ticks), or -1 while it is still part of the planet.
    private static float flight(Quaternionf turn, Vector3f middle, int id, double t) {
        Vector3f world = turn.transform(new Vector3f(middle)).normalize();
        Vector3f blast = PlanetCutscene.blastDir().toVector3f();
        float facing = world.dot(blast);
        if (facing < 0.05f) {
            return -1;
        }
        // The middle of the glowing half goes first, its edges a few ticks later.
        float delay = (1 - facing) * 14 + FxWorld.hash(SEED, id, 1) * 3;
        float tt = (float) (t - spec().blastTick() - delay);
        return tt < 0 ? -1 : tt;
    }

    // ---------- the solid part ----------

    static void solid(MultiBufferSource buffers, Vec3 camera, float partial) {
        double t = PlanetCutscene.time(partial);
        float size = size();
        int n = pieces();
        Quaternionf turn = turn(t);
        Vec3 center = PlanetCutscene.planetCenter();
        Vector3f rel = center.subtract(camera).toVector3f();
        Vector3f blast = PlanetCutscene.blastDir().toVector3f();
        float fly = (float) spec().planeta().voo();
        float life = Math.max(1, spec().tempo().explosaoTicks());
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(SpaceScene.EARTH));
        PoseStack pose = new PoseStack();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    int id = (i * n + j) * n + k;
                    Vector3f mid = middle(i, j, k, n);
                    float tt = flight(turn, mid, id, t);
                    float scale = 1;
                    int tint = 0xFFFFFFFF;
                    pose.pushPose();
                    Vector3f at = turn.transform(new Vector3f(mid)).mul(size / 2).add(rel);
                    Quaternionf spin = new Quaternionf();
                    if (tt >= 0) {
                        // Thrown out of the planet, tumbling, glowing violet, shrinking into the cloud.
                        Vector3f dir = turn.transform(new Vector3f(mid)).normalize().mul(0.5f).add(new Vector3f(blast))
                                .add(FxWorld.sphere(SEED, id).mul(0.35f)).normalize();
                        float far = fly * size * (0.4f + 0.8f * FxWorld.hash(SEED, id, 2)) * (1 - (float) Math.exp(-tt / 22));
                        at.add(dir.mul(far));
                        spin.rotateAxis(tt * (0.03f + 0.06f * FxWorld.hash(SEED, id, 3)), FxWorld.sphere(SEED + 1, id));
                        scale = 1 - smooth(0.12f, 0.65f, tt / life);
                        if (scale <= 0.02f) {
                            pose.popPose();
                            continue;
                        }
                        tint = lerpColor(0xFFFFFFFF, 0xFFC27CFF, Math.min(1, tt / 6));
                    }
                    pose.translate(at.x, at.y, at.z);
                    pose.mulPose(turn);
                    pose.mulPose(spin);
                    pose.scale(size / 2 * scale, size / 2 * scale, size / 2 * scale);
                    box(out, pose.last(), mid, 1f / n, i, j, k, n, tint);
                    pose.popPose();
                }
            }
        }
        rocks(out, pose, rel, t, size);
    }

    // Black rocks thrown far out of the blast.
    private static void rocks(VertexConsumer out, PoseStack pose, Vector3f rel, double t, float size) {
        float tt = (float) (t - spec().blastTick());
        if (tt < 0) {
            return;
        }
        int count = Math.max(0, spec().planeta().rochas());
        Vector3f blast = PlanetCutscene.blastDir().toVector3f();
        Vector3f from = PlanetCutscene.blastPoint().subtract(PlanetCutscene.planetCenter()).toVector3f().add(rel);
        for (int r = 0; r < count; r++) {
            float delay = FxWorld.hash(SEED + 2, r, 1) * 10;
            float lt = tt - delay;
            if (lt < 0) {
                continue;
            }
            Vector3f dir = new Vector3f(blast).add(FxWorld.sphere(SEED + 2, r).mul(0.9f)).normalize();
            float speed = size * (0.012f + 0.02f * FxWorld.hash(SEED + 2, r, 2));
            float rock = size * (0.008f + 0.02f * FxWorld.hash(SEED + 2, r, 3));
            Vector3f at = new Vector3f(dir).mul(speed * lt).add(from);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(new Quaternionf().rotateAxis(lt * 0.15f + r, FxWorld.sphere(SEED + 3, r)));
            pose.scale(rock, rock * (0.6f + 0.6f * FxWorld.hash(SEED + 2, r, 4)), rock);
            box(out, pose.last(), new Vector3f(), 1, 0, 0, 0, 1, ROCK_TINT);
            pose.popPose();
        }
    }

    // One cube of the planet, half as wide as half (its corners at middle +- half), drawn around its own middle. Faces
    // on the planet's skin show their part of the texture, faces inside are dark rock.
    private static void box(VertexConsumer out, PoseStack.Pose pose, Vector3f middle, float half, int i, int j, int k, int n, int tint) {
        float[] lo = {middle.x - half, middle.y - half, middle.z - half};
        float[] hi = {middle.x + half, middle.y + half, middle.z + half};
        int[] index = {i, j, k};
        for (int f = 0; f < 6; f++) {
            float[] normal = NORMALS[f];
            int axis = normal[0] != 0 ? 0 : normal[1] != 0 ? 1 : 2;
            boolean positive = normal[axis] > 0;
            boolean skin = positive ? index[axis] == n - 1 : index[axis] == 0;
            float[] c = CORNERS[f];
            Vector3f c0 = new Vector3f(c[0], c[1], c[2]);
            Vector3f su = new Vector3f(c[3], c[4], c[5]).sub(c0);
            Vector3f sv = new Vector3f(c[9], c[10], c[11]).sub(c0);
            // This cube's patch of the face, as fractions along the face's two sides.
            Vector3f a = new Vector3f(lo[0], lo[1], lo[2]);
            Vector3f b = new Vector3f(hi[0], hi[1], hi[2]);
            float s0 = new Vector3f(a).sub(c0).dot(su) / 4, s1 = new Vector3f(b).sub(c0).dot(su) / 4;
            float t0 = new Vector3f(a).sub(c0).dot(sv) / 4, t1 = new Vector3f(b).sub(c0).dot(sv) / 4;
            float sMin = Math.min(s0, s1), sMax = Math.max(s0, s1), tMin = Math.min(t0, t1), tMax = Math.max(t0, t1);
            float plane = positive ? hi[axis] : lo[axis];
            float[][] st = {{sMin, tMin}, {sMax, tMin}, {sMax, tMax}, {sMin, tMax}};
            int tile = TILES[f];
            float inset = 4 / 768f;
            float u0 = (tile % 3) / 3f + inset, u1 = (tile % 3 + 1) / 3f - inset;
            float v0 = (tile / 3) / 2f + inset * 1.5f, v1 = (tile / 3 + 1) / 2f - inset * 1.5f;
            int color = skin ? tint : multiply(INSIDE_TINT, tint);
            for (float[] p : st) {
                Vector3f corner = new Vector3f(c0).add(new Vector3f(su).mul(p[0])).add(new Vector3f(sv).mul(p[1]));
                // Put it on this cube's face (the big face's plane is at +-1).
                if (axis == 0) {
                    corner.x = plane;
                } else if (axis == 1) {
                    corner.y = plane;
                } else {
                    corner.z = plane;
                }
                corner.sub(middle);
                float u = u0 + (u1 - u0) * p[0];
                float v = v1 + (v0 - v1) * p[1];
                out.addVertex(pose, corner.x, corner.y, corner.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                        .setNormal(pose, normal[0], normal[1], normal[2]);
            }
        }
    }

    // ---------- the light ----------

    static void render(Vec3 camera, double now, float partial) {
        if (PlanetCutscene.spec() == null) {
            return;
        }
        double t = PlanetCutscene.time(partial);
        DestructionPlanetSpec spec = spec();
        float size = size();
        Vector3f center = PlanetCutscene.planetCenter().subtract(camera).toVector3f();
        Vector3f point = PlanetCutscene.blastPoint().subtract(camera).toVector3f();
        Vector3f blast = PlanetCutscene.blastDir().toVector3f();
        Camera view = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(view.getLeftVector());
        Vector3f up = new Vector3f(view.getUpVector());
        FxDraw.billboard(center, size * 2.3f, 0, FxDraw.SOFT, 0.1f, ATMOSPHERE, t < spec.blastTick() ? 0.35f : 0.2f, true);

        if (t < spec.pauseTick()) {
            aura(center, point, blast, t, size);
        }
        if (t >= spec.flashTick() && t < spec.blastTick() + 6) {
            starburst(point, left, up, t, size);
        }
        if (t >= spec.blastTick()) {
            blast(center, point, blast, t, size, (float) now);
        }
    }

    // Pink light gathering on the half that will go, streaks fanning out of it, growing and flickering.
    private static void aura(Vector3f center, Vector3f point, Vector3f blast, double t, float size) {
        float k = (float) Mth.clamp((t - spec().spaceTick()) / Math.max(1, spec().flashTick() - spec().spaceTick()), 0, 1);
        float out = (float) Mth.clamp(1 - (t - spec().flashTick()) / Math.max(1, spec().pauseTick() - spec().flashTick()), 0, 1);
        float o = smooth(0, 0.3f, k) * out;
        if (o <= 0) {
            return;
        }
        FxDraw.billboard(point, size * (0.6f + 0.8f * k), 0, FxDraw.SOFT, 0.15f, HOT_PINK, 0.35f * o, true);
        for (int s = 0; s < 80; s++) {
            Vector3f d = new Vector3f(blast).add(FxWorld.sphere(SEED + 5, s).mul(0.85f)).normalize();
            Vector3f base = new Vector3f(d).mul(size * 0.58f).add(center);
            Vector3f outward = new Vector3f(d).add(new Vector3f(blast).mul(0.4f)).normalize();
            float flicker = 0.7f + 0.3f * Mth.sin((float) t * (0.6f + 0.5f * FxWorld.hash(SEED + 5, s, 1)) + s);
            float length = size * (0.12f + 0.35f * FxWorld.hash(SEED + 5, s, 2)) * (0.3f + 0.7f * k) * flicker;
            Vector3f tip = new Vector3f(outward).mul(length).add(base);
            float[] c = FxDraw.mix(PINK, LILAC, FxWorld.hash(SEED + 5, s, 3));
            FxDraw.ribbon(base, tip, size * 0.018f, FxDraw.STREAK, 0.6f, c, 0.8f * o, true);
            FxDraw.ribbon(base, new Vector3f(base).lerp(tip, 0.5f), size * 0.007f, FxDraw.STREAK, 0.9f, WHITE, 0.6f * o, true);
        }
    }

    // A white point on the planet's skin, then the blue-white starburst with long rays, fading into the quiet.
    private static void starburst(Vector3f point, Vector3f left, Vector3f up, double t, float size) {
        float total = Math.max(1, spec().pauseTick() - spec().flashTick());
        float k = (float) ((t - spec().flashTick()) / total);
        float pause = Math.max(1, spec().blastTick() - spec().pauseTick());
        float after = (float) Mth.clamp((t - spec().pauseTick()) / pause, 0, 1);
        float o = 1 - after;
        if (k < 0.35f) {
            // The point lighting up.
            float g = k / 0.35f;
            FxDraw.billboard(point, size * (0.03f + 0.12f * g), 0, FxDraw.SOFT, 0.9f, WHITE, 1, true);
            FxDraw.billboard(point, size * (0.1f + 0.3f * g), 0, FxDraw.FLARE, 4 / 32f, STAR_BLUE, 0.7f * g, true);
            return;
        }
        float b = Math.min(1, (k - 0.35f) / 0.25f);
        float burst = FxWorld.easeOut(b);
        FxDraw.billboard(point, size * 0.25f * (1 + burst), 0, FxDraw.SOFT, 0.9f, WHITE, o, true);
        FxDraw.billboard(point, size * 0.9f * burst, 0, FxDraw.SOFT, 0.2f, STAR_BLUE, 0.5f * o, true);
        FxDraw.billboard(point, size * 1.4f * burst, 0.3f, FxDraw.FLARE, 6 / 32f, STAR_BLUE, 0.7f * o, true);
        // Long thin rays across the screen, the longest one slanted like the anime's.
        float[] angles = {0.45f, 0.45f + Mth.HALF_PI, 1.2f, 2.6f, -0.3f};
        float[] lengths = {2.6f, 1.4f, 0.9f, 0.8f, 0.6f};
        for (int r = 0; r < angles.length; r++) {
            Vector3f dir = new Vector3f(left).mul(Mth.cos(angles[r])).add(new Vector3f(up).mul(Mth.sin(angles[r])));
            float l = size * lengths[r] * burst;
            Vector3f a = new Vector3f(dir).mul(-l).add(point);
            Vector3f c = new Vector3f(dir).mul(l).add(point);
            FxDraw.ribbon(a, c, size * 0.05f, FxDraw.STREAK, 0.5f, STAR_BLUE, 0.7f * o, true);
            FxDraw.ribbon(a, c, size * 0.015f, FxDraw.STREAK, 0.9f, WHITE, o, true);
        }
    }

    // The shockwave disc, the purple lightning, the violet glow of the flying cubes and the lilac cloud.
    private static void blast(Vector3f center, Vector3f point, Vector3f blast, double t, float size, float now) {
        float tt = (float) (t - spec().blastTick());
        float life = Math.max(1, spec().tempo().explosaoTicks() + spec().tempo().costasTicks());
        float cloudSize = (float) spec().planeta().nuvem();

        // The white disc cutting through the planet and running out far past it.
        float wave = Math.min(1, tt / 34);
        float radius = size * (0.4f + 2.8f * FxWorld.easeOut(wave));
        float wo = 1 - smooth(0.4f, 1, wave);
        Vector3f disc = new Vector3f(blast).mul(size * 0.08f).add(center);
        FxDraw.disk(disc, blast, radius * 2, FxDraw.SOFT, 0, WHITE, 0.3f * wo, true);
        FxDraw.disk(disc, blast, radius * 2, FxDraw.RING, 0.04f, WHITE, 0.85f * wo, true);
        FxDraw.disk(disc, blast, radius * 1.9f, FxDraw.RING, 0.08f, LILAC, 0.4f * wo, true);

        // Purple lightning crackling over the half as it goes.
        float zap = 1 - smooth(8, 26, tt);
        if (zap > 0) {
            FxDraw.billboard(point, size * 1.2f, now * 0.02f, FxDraw.FLARE, 8 / 32f, HOT_PINK, 0.7f * zap, true);
            FxDraw.billboard(point, size * 0.7f, 0, FxDraw.SOFT, 0.5f, VIOLET, 0.8f * zap, true);
            int epoch = (int) (tt / 2);
            for (int b = 0; b < 14; b++) {
                int seed = SEED + 9 + epoch * 131 + b * 17;
                Vector3f dir = new Vector3f(blast).add(FxWorld.sphere(SEED + 9, b + epoch * 3).mul(0.9f)).normalize();
                Vector3f side = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
                Vector3f other = new Vector3f(dir).cross(side);
                float length = size * (0.3f + 0.4f * FxWorld.hash(seed, b, 1));
                Vector3f last = new Vector3f(point);
                for (int s = 1; s <= 7; s++) {
                    float f = s / 7f;
                    float jag = length * 0.12f;
                    Vector3f next = new Vector3f(dir).mul(length * f).add(point)
                            .add(new Vector3f(side).mul((FxWorld.hash(seed, s, 2) * 2 - 1) * jag))
                            .add(new Vector3f(other).mul((FxWorld.hash(seed, s, 3) * 2 - 1) * jag));
                    FxDraw.ribbon(last, next, size * 0.03f, FxDraw.BEAM, 0.25f, VIOLET, 0.8f * zap, true);
                    FxDraw.ribbon(last, next, size * 0.01f, FxDraw.BEAM, 0.9f, LILAC, zap, true);
                    last = next;
                }
            }
        }

        // The flying cubes glow violet while they melt away.
        int n = pieces();
        Quaternionf turn = turn(t);
        float fly = (float) spec().planeta().voo();
        float pieceLife = Math.max(1, spec().tempo().explosaoTicks());
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    int id = (i * n + j) * n + k;
                    Vector3f mid = middle(i, j, k, n);
                    float ft = flight(turn, mid, id, t);
                    if (ft < 0) {
                        continue;
                    }
                    float scale = 1 - smooth(0.12f, 0.65f, ft / pieceLife);
                    float glow = Math.min(1, ft / 4) * (1 - smooth(0.5f, 0.8f, ft / pieceLife));
                    if (glow <= 0) {
                        continue;
                    }
                    Vector3f dir = turn.transform(new Vector3f(mid)).normalize().mul(0.5f).add(new Vector3f(blast))
                            .add(FxWorld.sphere(SEED, id).mul(0.35f)).normalize();
                    float far = fly * size * (0.4f + 0.8f * FxWorld.hash(SEED, id, 2)) * (1 - (float) Math.exp(-ft / 22));
                    Vector3f at = turn.transform(new Vector3f(mid)).mul(size / 2).add(center).add(dir.mul(far));
                    FxDraw.billboard(at, size / n * (1.6f + scale), 0, FxDraw.SOFT, 0.2f, VIOLET, 0.45f * glow, true);
                }
            }
        }

        // The lilac cloud billowing out of where the half was, cartoon shaded: a darker puff behind each light one.
        float grow = FxWorld.easeOut(Math.min(1, tt / 55));
        float in = Math.min(1, tt / 6);
        float fade = 1 - smooth(0.85f, 1, tt / life);
        Vector3f toCamera = new Vector3f(center).negate().normalize();
        for (int c = 0; c < 70; c++) {
            Vector3f d = new Vector3f(blast).mul(1.2f).add(FxWorld.sphere(SEED + 11, c).mul(0.95f)).normalize();
            float dist = size * cloudSize * (0.12f + 0.55f * FxWorld.hash(SEED + 11, c, 1)) * grow;
            Vector3f at = new Vector3f(d).mul(dist).add(point);
            float puff = size * cloudSize * (0.18f + 0.2f * FxWorld.hash(SEED + 11, c, 2)) * (0.25f + 0.75f * grow);
            float spin = FxWorld.hash(SEED + 11, c, 3) * 6 + tt * 0.003f;
            float o = 0.85f * in * fade;
            FxDraw.billboard(new Vector3f(toCamera).mul(-puff * 0.15f).add(at).add(0, -puff * 0.08f, 0), puff * 1.12f, spin, FxDraw.SMOKE, 0.12f,
                    CLOUD_SHADOW, o, false);
            FxDraw.billboard(at, puff, spin + 1, FxDraw.SMOKE, 0.15f, FxWorld.hash(SEED + 11, c, 4) < 0.5f ? CLOUD_LIGHT : CLOUD_MID, o, false);
        }
        // Soft violet light inside the cloud, strongest early.
        FxDraw.billboard(point, size * cloudSize * 0.9f * (0.4f + 0.6f * grow), 0, FxDraw.SOFT, 0.1f, PURPLE, 0.35f * (1 - grow * 0.6f) * fade,
                true);
    }

    private static int multiply(int a, int b) {
        int r = ((a >> 16 & 0xFF) * (b >> 16 & 0xFF)) / 255;
        int g = ((a >> 8 & 0xFF) * (b >> 8 & 0xFF)) / 255;
        int bl = ((a & 0xFF) * (b & 0xFF)) / 255;
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static int lerpColor(int a, int b, float k) {
        int r = Math.round(Mth.lerp(k, a >> 16 & 0xFF, b >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(k, a >> 8 & 0xFF, b >> 8 & 0xFF));
        int bl = Math.round(Mth.lerp(k, a & 0xFF, b & 0xFF));
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static float smooth(float a, float b, float x) {
        float k = Mth.clamp((x - a) / (b - a), 0, 1);
        return k * k * (3 - 2 * k);
    }
}
