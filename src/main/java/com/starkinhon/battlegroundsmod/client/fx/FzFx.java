package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.DbFx.bb;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.easeOut;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rel;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rib;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.smooth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.power.powers.freezaboss.FzIsland;
import com.starkinhon.battlegroundsmod.power.powers.freezaboss.FzNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

// Freeza Poder Total's own effects (power/powers/freezaboss/FzNet, SubNet kinds 460-479), drawn as the approved page
// draws them (forja/freeza-boss): the ground warnings with the page's own shaders (souls_vfx shapes 36-41: the closing
// circles, the shadows, the Voronoi cracks, the aim lines, the lava geysers), the ki rain, the tail whip, the island
// torn out of the ground (copies of the real blocks, taken before the server lifts them) and Namek's red sky. Drawn in
// DbFx's two passes, the island's blocks at the end of the level (FxWorld). Age in ticks.
public final class FzFx {
    private static final float[] PINK = FxDraw.rgb(0xFF4FD8);
    private static final float[] PINK_PALE = FxDraw.rgb(0xFFC6F3);
    private static final float[] BLITZ = FxDraw.rgb(0xC58CFF);
    private static final float[] LAVA_MARK = FxDraw.rgb(0xFF7A2A);
    private static final float[] RETICLE = FxDraw.rgb(0xFF3A7A);
    private static final float[] SHADOW = FxDraw.rgb(0x140A1C);
    private static final float[] CRACK_DARK = FxDraw.rgb(0x24152E);
    private static final float[] CRACK_LIT = FxDraw.rgb(0xD6A0FF);
    private static final float[] CRACK_POWER = FxDraw.rgb(0xC88BFF);
    private static final float[] CRACK_LAVA = FxDraw.rgb(0xFF6A1A);
    private static final float[] PSY = FxDraw.rgb(0xB77CFF);
    private static final float[] SOIL = FxDraw.rgb(0x6E5A44);
    private static final float[] WHIP = FxDraw.rgb(0xF2D6FF);
    private static final float[][] EMBERS = {FxDraw.rgb(0xFF6A1A), FxDraw.rgb(0xFFB347), FxDraw.rgb(0xE0400F), FxDraw.rgb(0xFFD27A)};
    private static final float[] LAVA_GLOW = FxDraw.rgb(0xFF7A2A);
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    // the shapes of souls_vfx.fsh
    private static final int MARK = 36, SHADE = 37, CRACKS = 38, CRACKS_HOT = 39, AIM = 40, LAVA = 41;

    private static final class Fx {
        final int kind;
        final int id;
        final Vec3 pos;
        final Vec3 dir;
        final float a;
        final float b;
        final float c;
        final double born;

        Fx(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c, double born) {
            this.kind = kind;
            this.id = id;
            this.pos = pos;
            this.dir = dir;
            this.a = a;
            this.b = b;
            this.c = c;
            this.born = born;
        }
    }

    // A block of the torn island: where it sat from the island's middle point.
    private record Piece(BlockState state, float x, float y, float z) {
    }

    private static final class Island {
        final Vec3 origin;
        final List<Piece> pieces = new ArrayList<>();
        final float radius;
        Vec3 prev;
        Vec3 pos;
        float prevYaw;
        float yaw;
        float tilt;
        double updated;
        double endAt = -1;
        int end;

        Island(Vec3 origin, float radius) {
            this.origin = origin;
            this.pos = origin;
            this.prev = origin;
            this.radius = radius;
        }
    }

    // A block of a broken or slammed island flying off (only drawn; the server drops a few real ones).
    private record Chunk(BlockState state, Vec3 from, Vec3 vel, Vector3f axis, float spin, double born) {
    }

    private static final class Doom {
        Vec3 pos;
        float radius;
        float want;
        float k;
        double updated;
    }

    private static final List<Fx> SHOTS = new ArrayList<>();
    private static final Map<Integer, Island> ISLANDS = new HashMap<>();
    private static final List<Chunk> CHUNKS = new ArrayList<>();
    private static final Map<Integer, Doom> DOOMS = new HashMap<>();
    private static float doomNow;

    private FzFx() {
    }

    static boolean handles(int kind) {
        return kind >= FzNet.FIRST && kind <= FzNet.LAST;
    }

    static boolean active() {
        return !SHOTS.isEmpty() || !ISLANDS.isEmpty() || !CHUNKS.isEmpty() || !DOOMS.isEmpty();
    }

    static void clear() {
        SHOTS.clear();
        ISLANDS.clear();
        CHUNKS.clear();
        DOOMS.clear();
        doomNow = 0;
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        switch (kind) {
            case FzNet.ISLAND -> island(id, pos, dir, a, b, c, now);
            case FzNet.DOOM -> {
                Doom d = DOOMS.computeIfAbsent(id, k -> new Doom());
                d.pos = pos;
                d.radius = b;
                d.want = a;
                d.updated = now;
            }
            default -> SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
        }
    }

    private static float life(Fx f) {
        return switch (f.kind) {
            case FzNet.AIM -> f.b + 12;
            case FzNet.MARK -> switch ((int) f.c) {
                case 4 -> f.b + 26;
                case 5 -> f.b + 50;
                case 6 -> f.b + 72000;
                default -> f.b + 2;
            };
            case FzNet.GEYSER -> 34;
            case FzNet.KI_RAIN -> Math.max(1, f.a);
            case FzNet.WHIP -> 9;
            default -> 20;
        };
    }

    private static void prune(double now) {
        // the lava cracks last as long as Namek is falling apart round them
        SHOTS.removeIf(f -> now - f.born > life(f) || f.kind == FzNet.MARK && (int) f.c == 6 && now - f.born > f.b + 40 && doomAt(f.pos) <= 0.01f);
        Iterator<Island> it = ISLANDS.values().iterator();
        while (it.hasNext()) {
            Island is = it.next();
            if (is.endAt < 0 && now - is.updated > 60) {
                is.endAt = now;
            }
            if (is.endAt >= 0) {
                it.remove();
            }
        }
        CHUNKS.removeIf(ch -> now - ch.born() > 30);
        Iterator<Doom> dt = DOOMS.values().iterator();
        while (dt.hasNext()) {
            Doom d = dt.next();
            if (now - d.updated > 60) {
                d.want = 0;
            }
            // the page's setDoom ramps over 1.4 s
            d.k += Mth.clamp(d.want - d.k, -1 / 28f, 1 / 28f);
            if (d.want <= 0 && d.k <= 0) {
                dt.remove();
            }
        }
        var player = Minecraft.getInstance().player;
        doomNow = player == null ? 0 : doomAt(player.position());
    }

    private static float doomAt(Vec3 p) {
        float best = 0;
        for (Doom d : DOOMS.values()) {
            double dist = Math.hypot(p.x - d.pos.x, p.z - d.pos.z);
            best = Math.max(best, d.k * (1 - smooth((float) ((dist - d.radius) / 40))));
        }
        return best;
    }

    static void draw(double now, float partial) {
        boolean glow = DbFx.pass;
        if (glow) {
            prune(now);
        }
        float t = (float) now + partial;
        for (Fx f : SHOTS) {
            float age = (float) (now - f.born) + partial;
            if (age < 0) {
                continue;
            }
            switch (f.kind) {
                case FzNet.AIM -> aim(f, age);
                case FzNet.MARK -> mark(f, age, t);
                case FzNet.GEYSER -> geyser(f, age);
                case FzNet.KI_RAIN -> kiRain(f, age);
                case FzNet.WHIP -> whip(f, age);
                default -> {
                }
            }
        }
        for (Island is : ISLANDS.values()) {
            islandGlow(is, now, partial, t);
        }
    }

    // ---------- helpers ----------

    // A square lying on the ground, turned by spin, in its own pass.
    private static void ground(Vector3f c, float size, float spin, int shape, float param, float[] col, float op, boolean glow) {
        if (glow == DbFx.pass && op > 0.002f) {
            FxDraw.diskSpin(c, UP, size, spin, shape, param, col, op, glow);
        }
    }

    private static float hash(int a, int b, int c) {
        long h = a * 374761393L + b * 668265263L + c * 1442695041L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    // ---------- the Rajada's aim lines ----------

    private static void aim(Fx f, float age) {
        if (!DbFx.pass) {
            return;
        }
        float show = smooth((age - f.a) / 3);
        if (show <= 0) {
            return;
        }
        float lock = age < f.b ? 0 : age < f.b + 2.6f ? 0.8f + 0.2f * Mth.sin(age * 2.5f) : 1;
        float out = age < f.b + 3 ? 1 : (float) Math.exp(-(age - f.b - 3) * 0.4f);
        Vector3f from = rel(f.pos), to = rel(f.pos.add(f.dir));
        Vec3 side = new Vec3(-f.dir.z, 0, f.dir.x).normalize().scale(0.21);
        Vector3f s = new Vector3f((float) side.x, 0, (float) side.z);
        FxDraw.quad(new Vector3f(from).sub(s), new Vector3f(to).sub(s), new Vector3f(to).add(s), new Vector3f(from).add(s), AIM, lock, PINK, show * out, true);
    }

    // ---------- the warnings on the ground ----------

    private static int crackParam(float radius, float grow) {
        int cls = radius <= 4.6f ? 0 : radius <= 8 ? 1 : radius <= 11 ? 2 : 3;
        return cls * 64 + Math.round(Mth.clamp(grow, 0, 1) * 63);
    }

    private static void mark(Fx f, float age, float t) {
        int style = (int) f.c;
        Vector3f c = rel(f.pos).add(0, 0.05f, 0);
        float R = f.a, k = Mth.clamp(age / Math.max(1, f.b), 0, 1);
        float spin = hash((int) Math.floor(f.pos.x), (int) Math.floor(f.pos.z), 7) * Mth.TWO_PI;
        switch (style) {
            case 0 -> {
                // the Blitz: the circle closing where he comes out, his shimmer flickering in it
                ground(c, R * 2, 0, MARK, 1 - k, BLITZ, 1, false);
                if (age < f.b && ((int) (age * 1.5f)) % 2 == 0) {
                    float jx = (hash((int) age, 1, 3) - 0.5f) * 0.15f, jz = (hash((int) age, 2, 3) - 0.5f) * 0.15f;
                    rib(new Vector3f(c).add(jx, 0.1f, jz), new Vector3f(c).add(jx, 2.0f, jz), 0.9f, FxDraw.SOFT, 0.2f, BLITZ, 0.45f, true);
                    rib(new Vector3f(c).add(jx, 0.3f, jz), new Vector3f(c).add(jx, 1.8f, jz), 0.35f, FxDraw.STREAK, 0.6f, WHIP, 0.5f, true);
                }
                bb(new Vector3f(c).add(0, 1.2f, 0), 2.6f, 0, FxDraw.SOFT, 0.1f, BLITZ, 0.25f * (1 - k), true);
            }
            case 1 -> ground(c, R * 2, 0, MARK, 1 - k, LAVA_MARK, age < f.b ? 1 : 0, false);
            case 2 -> ground(c, R * 2, 0, MARK, 1 - k, RETICLE, age < f.b ? 1 : 0, false);
            case 3 -> ground(c, R * 2, 0, SHADE, smooth(k), SHADOW, age < f.b ? 1 : 0, false);
            case 4 -> {
                // the cracks running out to the ring: dark lines and their purple glow
                float out = 1 - smooth((age - f.b - 18) / 8);
                int p = crackParam(R, smooth(k));
                ground(c, R * 2.2f, spin, CRACKS, p / 255f, CRACK_DARK, out, false);
                ground(c, R * 2.2f, spin, CRACKS_HOT, p / 255f, CRACK_LIT, 0.8f * out, true);
            }
            case 5 -> {
                float out = 1 - smooth((age - f.b - 30) / 20);
                ground(c, R * 2.2f, spin, CRACKS_HOT, crackParam(R, smooth(k)) / 255f, CRACK_POWER, 0.85f * out, true);
            }
            case 6 -> {
                float out = Math.min(1, doomAt(f.pos) + (age < f.b + 40 ? 1 : 0));
                float pulse = 0.85f + 0.15f * Mth.sin(t * 0.15f);
                ground(c.add(0, -0.015f, 0), R * 2.2f, spin, CRACKS_HOT, crackParam(R, easeOut(k)) / 255f, CRACK_LAVA, pulse * out, true);
            }
            default -> {
            }
        }
    }

    // ---------- lava geysers ----------

    private static void geyser(Fx f, float age) {
        float k = age < 5 ? easeOut(age / 5) : age < 20 ? 1 : 1 - smooth((age - 20) / 10);
        if (k <= 0.01f) {
            return;
        }
        Vector3f base = rel(f.pos);
        float H = f.a * k;
        int seed = (int) f.b;
        float[] tint = {0.3f + 0.6f * hash(seed, 1, 1), 0.5f, 0.3f + 0.6f * hash(seed, 2, 1)};
        rib(base, new Vector3f(base).add(0, H, 0), 2.3f * (0.6f + 0.4f * k), LAVA, k, tint, 1, false);
        bb(new Vector3f(base).add(0, 0.6f, 0), 4.5f * k, 0, FxDraw.SOFT, 0.3f, LAVA_GLOW, 0.55f * k, true);
        bb(new Vector3f(base).add(0, H * 0.6f, 0), 3f * k, 0, FxDraw.SOFT, 0.2f, LAVA_GLOW, 0.3f * k, true);
        // lava drops thrown out of the top while it stands, each its own size, way and life
        for (int i = 0; i < 22; i++) {
            float b0 = hash(seed, i, 5) * 18;
            float la = age - b0;
            float life = 9 + hash(seed, i, 6) * 9;
            if (la < 0 || la > life) {
                continue;
            }
            float ang = hash(seed, i, 7) * Mth.TWO_PI, sp = 0.05f + hash(seed, i, 8) * 0.15f, vy = 0.1f + hash(seed, i, 9) * 0.2f;
            float tt = la;
            Vector3f p = new Vector3f(base).add(Mth.cos(ang) * sp * tt, f.a * 0.9f + vy * tt - 0.012f * tt * tt, Mth.sin(ang) * sp * tt);
            float s = 0.1f + 0.18f * hash(seed, i, 10);
            float[] col = EMBERS[(int) (hash(seed, i, 11) * 3.99f)];
            bb(p, s, hash(seed, i, 12) * 6, FxDraw.SOLID, 0, col, 1 - smooth((la - life * 0.7f) / (life * 0.3f)), false);
            bb(p, s * 3, 0, FxDraw.SOFT, 0.4f, col, 0.35f, true);
        }
    }

    // ---------- the ki rain ----------

    private static void kiRain(Fx f, float age) {
        float k = Mth.clamp(age / Math.max(1, f.a), 0, 1);
        Vec3 w = f.pos.add(f.dir.scale(k));
        Vector3f p = rel(w);
        Vector3f d = new Vector3f((float) f.dir.x, (float) f.dir.y, (float) f.dir.z).normalize();
        float s = 0.8f + 0.3f * Mth.sin(age * 1.5f + f.id);
        DbFx.kiBall(p, 0.32f * s, PINK, 1, age);
        rib(new Vector3f(p).sub(new Vector3f(d).mul(1.4f)), p, 0.3f, FxDraw.STREAK, 0.7f, PINK_PALE, 0.7f, true);
    }

    // ---------- the tail whip ----------

    private static void whip(Fx f, float age) {
        if (!DbFx.pass) {
            return;
        }
        float k = Mth.clamp(age / 6.4f, 0, 1);
        float out = 1 - smooth((age - 5) / 4);
        double a0 = Math.atan2(f.dir.z, f.dir.x) + Math.PI * 0.9 * f.a;
        int n = 14;
        Vector3f[] pts = new Vector3f[n];
        float[] ws = new float[n];
        float[] us = new float[n];
        Vector3f c = rel(f.pos);
        for (int i = 0; i < n; i++) {
            float u = i / (n - 1f);
            double a = a0 - f.a * Math.PI * 1.1 * k * u;
            float r = 1.9f + 0.25f * u;
            pts[i] = new Vector3f(c).add((float) Math.cos(a) * r, 0.1f - 0.4f * u * (1 - u), (float) Math.sin(a) * r);
            ws[i] = 0.5f * (1 - Math.abs(u - 0.7f));
            us[i] = u;
        }
        FxDraw.strip(pts, ws, us, FxDraw.STREAK, 0.8f, WHIP, 0.85f * out, true);
    }

    // ---------- the island ----------

    private static void island(int id, Vec3 pos, Vec3 dir, float a, float b, float c, double now) {
        if (a == 0) {
            Island is = new Island(pos, b);
            snapshot(is, pos, b, c);
            is.updated = now;
            ISLANDS.put(id, is);
            return;
        }
        Island is = ISLANDS.get(id);
        if (is == null) {
            return;
        }
        if (a == 1) {
            is.prev = is.pos;
            is.prevYaw = is.yaw;
            is.pos = pos;
            is.yaw = (float) dir.x;
            is.tilt = (float) dir.y;
            is.updated = now;
            return;
        }
        // slammed (2) or broken in the air (3): its blocks fly off
        is.endAt = now;
        is.end = (int) a;
        var rnd = net.minecraft.util.RandomSource.create(id * 31L + 7);
        int n = Math.min(is.pieces.size(), a == 3 ? 46 : 30);
        Vec3 c0 = a == 3 ? is.pos.add(0, -1.2, 0) : pos.add(0, 0.6, 0);
        for (int i = 0; i < n; i++) {
            Piece p = is.pieces.get(rnd.nextInt(is.pieces.size()));
            double ang = rnd.nextDouble() * Math.PI * 2, e = rnd.nextDouble();
            Vec3 from = c0.add(Math.cos(ang) * is.radius * e * 0.8, -rnd.nextDouble() * 1.5, Math.sin(ang) * is.radius * e * 0.8);
            double pow = a == 3 ? 0.35 : 0.3;
            Vec3 vel = new Vec3(Math.cos(ang) * pow * (0.3 + rnd.nextDouble()), pow * (0.6 + rnd.nextDouble()), Math.sin(ang) * pow * (0.3 + rnd.nextDouble()));
            Vector3f axis = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f);
            if (axis.lengthSquared() < 1.0E-4f) {
                axis.set(1, 0, 0);
            }
            CHUNKS.add(new Chunk(p.state(), from, vel, axis.normalize(), (rnd.nextFloat() - 0.5f) * 0.6f, now));
        }
    }

    // The client copies the piece of ground before the server lifts it (FzIsland: the same shape on both sides).
    private static void snapshot(Island is, Vec3 at, float radius, float depth) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        int cx = Mth.floor(at.x), cz = Mth.floor(at.z), top = FzIsland.topY(at);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (FzIsland.Column col : FzIsland.columns(at, radius, depth)) {
            for (int y = top + FzIsland.ABOVE; y > top - col.depth(); y--) {
                p.set(cx + col.dx(), y, cz + col.dz());
                BlockState st = level.getBlockState(p);
                if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity() || st.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }
                if (is.pieces.size() < 1600) {
                    is.pieces.add(new Piece(st, (float) (p.getX() - at.x), (float) (p.getY() - at.y), (float) (p.getZ() - at.z)));
                }
            }
        }
    }

    private static Vec3 islandPos(Island is, double now, float partial) {
        float k = Mth.clamp((float) (now - is.updated) + partial, 0, 1);
        return is.prev.add(is.pos.subtract(is.prev).scale(k));
    }

    // The psychic glow round the island and the earth crumbling off it (the page's psy shell and falling soil).
    private static void islandGlow(Island is, double now, float partial, float t) {
        if (is.endAt >= 0 || is.pos.distanceToSqr(is.origin) < 0.01) {
            return;
        }
        Vec3 w = islandPos(is, now, partial);
        Vector3f c = rel(w).add(0, -1.2f, 0);
        float R = is.radius;
        bb(c, R * 3.2f, 0, FxDraw.SOFT, 0.05f, PSY, 0.32f, true);
        bb(c, R * 2.0f, t * 0.02f, FxDraw.RING, 0.15f, PSY, 0.25f, true);
        // soil dropping from under it: each crumb its own place, size and fall
        for (int i = 0; i < 16; i++) {
            float period = 14 + hash(i, 3, 9) * 10;
            float ph = (t + hash(i, 4, 9) * period) % period;
            int cyc = (int) ((t + hash(i, 4, 9) * period) / period);
            float ang = hash(i, cyc, 1) * Mth.TWO_PI, r = hash(i, cyc, 2) * R * 0.7f;
            Vector3f p = new Vector3f(c).add(Mth.cos(ang) * r, -0.6f - hash(i, cyc, 3) * 1.8f - 0.012f * ph * ph, Mth.sin(ang) * r);
            bb(p, 0.08f + 0.2f * hash(i, cyc, 5), hash(i, cyc, 6) * 6, FxDraw.SOLID, 0, SOIL, 1 - ph / period, false);
        }
    }

    // The island's blocks and the chunks flying off, at the end of the level (FxWorld).
    static void renderBlocks(Vec3 camera) {
        if (ISLANDS.isEmpty() && CHUNKS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        double now = ScreenFx.now();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        PoseStack pose = new PoseStack();
        for (Island is : ISLANDS.values()) {
            if (is.endAt >= 0 || is.pos.distanceToSqr(is.origin) < 1.0E-4) {
                continue;
            }
            Vec3 w = islandPos(is, now, partial);
            float k = Mth.clamp((float) (now - is.updated) + partial, 0, 1);
            float yaw = Mth.lerp(k, is.prevYaw, is.yaw);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(w));
            pose.pushPose();
            pose.translate(w.x - camera.x, w.y - camera.y, w.z - camera.z);
            pose.mulPose(new Quaternionf().rotationY(yaw).rotateX(is.tilt));
            for (Piece p : is.pieces) {
                pose.pushPose();
                pose.translate(p.x(), p.y(), p.z());
                blocks.renderSingleBlock(p.state(), pose, source, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            pose.popPose();
        }
        for (Chunk ch : CHUNKS) {
            float age = (float) (now - ch.born()) + partial;
            float s = 1 - smooth((age - 18) / 12);
            if (s <= 0.02f) {
                continue;
            }
            Vec3 p = ch.from().add(ch.vel().scale(age)).add(0, -0.04 * age * age, 0);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
            pose.pushPose();
            pose.translate(p.x - camera.x, p.y - camera.y, p.z - camera.z);
            pose.mulPose(new Quaternionf().rotationAxis(ch.spin() * age, ch.axis()));
            pose.scale(0.6f * s, 0.6f * s, 0.6f * s);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(ch.state(), pose, source, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        source.endBatch();
    }

    // ---------- Namek falling apart: the red sky ----------

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float s = doomNow;
        if (s <= 0) {
            return;
        }
        float k = 0.85f * s;
        event.setRed(Mth.lerp(k, event.getRed(), 0.46f));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.09f));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.06f));
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        float s = doomNow;
        if (s <= 0) {
            return;
        }
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) {
            // the sky itself takes the red
            event.setNearPlaneDistance(Mth.lerp(s, event.getNearPlaneDistance(), 0));
            event.setFarPlaneDistance(Mth.lerp(s, event.getFarPlaneDistance(), 0.01f));
        } else {
            event.setNearPlaneDistance(Mth.lerp(s, event.getNearPlaneDistance(), 20));
            event.setFarPlaneDistance(Mth.lerp(s, event.getFarPlaneDistance(), 130));
        }
        event.setCanceled(true);
    }
}
