package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.skill.VfxSpec.Efeito;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// The world effects of Bills' Destruction Touch (bills.destruction_touch.json), drawn like the rest of FxWorld from the
// age and fixed random numbers per part:
// bolinha: the tiny white ball falling from his finger, at velocidade blocks per tick along the direction.
// rachaduras: a web of glowing lava cracks over the ground, raio blocks wide, opening from the middle out and following
// the terrain's height, yellow-orange at first, red and dark at the end, with little flames on the joints.
// pilares: columns of yellow light bursting out of the ground all around, one after another.
// plasma: the explosion, like the anime's: white-hot core, pink and magenta fibers of plasma shooting out on every side
// and puffs that go from magenta to orange and peach as it dies out.
// nuvem: the soft pink and peach cloud left hanging after it.
final class DestructionFx {
    private static final float[] WHITE = {1, 1, 1};
    // The crack web of each piece, laid on the terrain once (strips, see STRIP) plus the flame spots (x, y, z, distance).
    private record Web(float[] segments, float[] flames) {
    }

    private static final Map<Integer, Web> WEBS = new HashMap<>();

    private DestructionFx() {
    }

    static void forget(java.util.function.IntPredicate alive) {
        WEBS.keySet().removeIf(seed -> !alive.test(seed));
    }

    static void clear() {
        WEBS.clear();
    }

    static void ball(Efeito e, Vector3f at, Vector3f dir, float age, float[] cor, float[] cor2, float size, float opacity) {
        float speed = (float) FxWorld.d(e.velocidade(), 0.5);
        Vector3f pos = new Vector3f(dir).mul(speed * age).add(at);
        float trail = (float) FxWorld.d(e.comprimento(), 2.5);
        float pulse = 1 + 0.12f * Mth.sin(age * 1.3f);
        Vector3f tail = new Vector3f(dir).mul(-Math.min(trail, speed * age + 0.01f)).add(pos);
        FxDraw.ribbon(tail, pos, size * 0.7f, FxDraw.STREAK, 0.8f, cor, 0.6f * opacity, true);
        FxDraw.billboard(pos, size * 5 * pulse, 0, FxDraw.SOFT, 0.1f, cor, 0.35f * opacity, true);
        FxDraw.billboard(pos, size * 2.4f * pulse, age * 0.05f, FxDraw.FLARE, 4 / 32f, cor2, 0.7f * opacity, true);
        FxDraw.billboard(pos, size * pulse, 0, FxDraw.SOFT, 0.9f, WHITE, opacity, true);
    }

    static void cracks(Efeito e, int seed, Vec3 world, Vector3f at, float age, float t, float[] cor, float[] cor2, float size, float opacity) {
        Web web = WEBS.computeIfAbsent(seed, k -> buildWeb(e, seed, world));
        float radius = (float) FxWorld.d(e.raio(), 60);
        // Opens fast from the middle out, holds, then cools down to dark red and fades.
        float reach = radius * FxWorld.easeOut(Math.min(1, t * 4)) + 1;
        float heat = 1 - smooth(0.45f, 1, t);
        float fade = 1 - smooth(0.8f, 1, t);
        float[] hot = FxDraw.mix(cor, cor2, 0.35f);
        float[] line = FxDraw.mix(FxDraw.rgb(0x5A0A04), cor, heat);
        float[] core = FxDraw.mix(FxDraw.rgb(0x8A1A06), hot, heat);
        float flicker = 0.9f + 0.1f * Mth.sin(age * 0.9f);
        Vector3f origin = new Vector3f(at).sub((float) world.x, (float) world.y, (float) world.z);
        float[] s = web.segments();
        for (int i = 0; i < s.length; i += STRIP) {
            float far = s[i + 7];
            if (far > reach) {
                continue;
            }
            // Thinner and dimmer towards the rim, and each crack glows in as the opening runs past it.
            float rim = 1 - 0.5f * far / radius;
            float open = Mth.clamp((reach - far) / 4, 0, 1);
            Vector3f a = new Vector3f(s[i], s[i + 1], s[i + 2]).add(origin);
            Vector3f b = new Vector3f(s[i + 3], s[i + 4], s[i + 5]).add(origin);
            int axis = (int) s[i + 6];
            float o = open * fade * opacity * flicker;
            strip(a, b, axis, size * 2.4f * rim, FxDraw.BEAM, 0.12f, line, o * 0.55f);
            strip(a, b, axis, size * rim, FxDraw.BEAM, 0.6f, FxDraw.mix(core, WHITE, 0.25f * heat), o);
        }
        float[] f = web.flames();
        for (int i = 0; i < f.length; i += 4) {
            float far = f[i + 3];
            if (far > reach) {
                continue;
            }
            int k = i / 4;
            Vector3f base = new Vector3f(f[i], f[i + 1], f[i + 2]).add(origin);
            // Little flames licking up out of the cracks, each on its own beat.
            float beat = (age * (0.05f + 0.04f * FxWorld.hash(seed, k, 31)) + FxWorld.hash(seed, k, 32)) % 1;
            float h = size * (1.5f + 3 * FxWorld.hash(seed, k, 33)) * (1 - 0.5f * far / radius);
            Vector3f pos = new Vector3f(base).add(0, h * (0.2f + 0.6f * beat), 0);
            FxDraw.billboard(pos, h * (1.1f - 0.6f * beat), FxWorld.hash(seed, k, 34) * 6, FxDraw.FIRE, beat,
                    FxDraw.mix(cor2, cor, beat), (1 - beat) * heat * fade * opacity, true);
            // Embers drifting up out of it and going dark.
            for (int j = 0; j < 3; j++) {
                float life = 30 + 30 * FxWorld.hash(seed, k * 3 + j, 35);
                float u = ((age + FxWorld.hash(seed, k * 3 + j, 36) * life) % life) / life;
                float drift = 2 + 3 * FxWorld.hash(seed, k * 3 + j, 37);
                float a = FxWorld.hash(seed, k * 3 + j, 38) * Mth.TWO_PI + u * 2;
                Vector3f ember = new Vector3f(base).add(Mth.cos(a) * drift * u, u * (6 + 8 * FxWorld.hash(seed, k * 3 + j, 39)), Mth.sin(a) * drift * u);
                FxDraw.billboard(ember, size * 0.35f, 0, FxDraw.SOFT, 0.7f, FxDraw.mix(cor2, FxDraw.rgb(0x501008), u), (1 - u) * fade * opacity, true);
            }
        }
        // The ground glowing hot under the web.
        FxDraw.disk(new Vector3f(at).add(0, 0.05f, 0), new Vector3f(0, 1, 0), reach * 1.4f, FxDraw.SOFT, 0, cor, 0.15f * heat * fade * opacity, true);
    }

    // A crack strip from a to b: lying on the ground (axis 0), or standing on a block's side whose width runs along x
    // (axis 1) or z (axis 2), for the steps between blocks and the tree trunks.
    private static void strip(Vector3f a, Vector3f b, int axis, float width, int shape, float param, float[] rgb, float opacity) {
        Vector3f along = new Vector3f(b).sub(a);
        if (along.lengthSquared() < 1.0E-6f) {
            return;
        }
        Vector3f side = axis == 0 ? new Vector3f(along).cross(0, 1, 0) : axis == 1 ? new Vector3f(1, 0, 0) : new Vector3f(0, 0, 1);
        if (side.lengthSquared() < 1.0E-6f) {
            return;
        }
        side.normalize(width / 2);
        FxDraw.quad(new Vector3f(a).sub(side), new Vector3f(b).sub(side), new Vector3f(b).add(side), new Vector3f(a).add(side), shape, param, rgb,
                opacity, true);
    }

    // x, y, z of both ends, the axis (see strip) and the distance of the far end from the middle.
    private static final int STRIP = 8;

    // Rings of jittered points around the middle, joined to their neighbors on the same ring and to the next ring out,
    // like dried mud, every join a little jagged. The cracks run over the blocks one column at a time: on the top of
    // the ground, down or up the side of a block where the ground steps, and up the side of a tree trunk they pass by.
    private static Web buildWeb(Efeito e, int seed, Vec3 world) {
        float radius = (float) FxWorld.d(e.raio(), 60);
        int rings = Math.max(2, FxWorld.i(e.quantidade(), 9));
        float[][][] points = new float[rings + 1][][];
        points[0] = new float[][] {{0, 0}};
        for (int r = 1; r <= rings; r++) {
            float ringRadius = radius * (float) Math.pow(r / (float) rings, 0.85);
            int count = 5 + r * 4;
            points[r] = new float[count][];
            for (int k = 0; k < count; k++) {
                float a = (k + 0.5f * (FxWorld.hash(seed, r * 97 + k, 1) - 0.5f)) / count * Mth.TWO_PI;
                float d = ringRadius * (0.85f + 0.3f * FxWorld.hash(seed, r * 97 + k, 2));
                points[r][k] = new float[] {Mth.cos(a) * d, Mth.sin(a) * d};
            }
        }
        Ground ground = new Ground(world, seed);
        for (int r = 1; r <= rings; r++) {
            float[][] ring = points[r];
            float[][] inner = points[r - 1];
            for (int k = 0; k < ring.length; k++) {
                float[] p = ring[k];
                // Out from the closest point of the ring inside (or the middle), most of the time.
                if (r == 1 || FxWorld.hash(seed, r * 131 + k, 3) < 0.75f) {
                    float[] q = inner[Math.min(inner.length - 1, Math.round(k * inner.length / (float) ring.length)) % inner.length];
                    jagged(ground, q, p, seed, r * 151 + k);
                }
                // Across to the next point of the same ring, leaving gaps now and then.
                if (FxWorld.hash(seed, r * 173 + k, 4) < 0.8f) {
                    jagged(ground, p, ring[(k + 1) % ring.length], seed, r * 191 + k);
                }
                if (FxWorld.hash(seed, r * 211 + k, 5) < 0.4f) {
                    float h = ground.top(Mth.floor(world.x + p[0]), Mth.floor(world.z + p[1]));
                    if (!Float.isNaN(h)) {
                        ground.flames.add(p[0]);
                        ground.flames.add(h);
                        ground.flames.add(p[1]);
                        ground.flames.add((float) Math.hypot(p[0], p[1]));
                    }
                }
            }
        }
        return new Web(toArray(ground.strips), toArray(ground.flames));
    }

    private static float[] toArray(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    // A crack from p to q in three pieces, bent a little off the straight line.
    private static void jagged(Ground ground, float[] p, float[] q, int seed, int k) {
        float dx = q[0] - p[0], dz = q[1] - p[1];
        float length = Mth.sqrt(dx * dx + dz * dz);
        float nx = -dz / Math.max(1.0E-4f, length), nz = dx / Math.max(1.0E-4f, length);
        float[] last = p;
        for (int s = 1; s <= 3; s++) {
            float f = s / 3f;
            float off = s == 3 ? 0 : (FxWorld.hash(seed, k, 10 + s) - 0.5f) * length * 0.3f;
            float[] next = {p[0] + dx * f + nx * off, p[1] + dz * f + nz * off};
            ground.walk(last[0], last[1], next[0], next[1]);
            last = next;
        }
    }

    // The ground around where it hit, read once per column: the top of the ground (a tree trunk doesn't count, the
    // crack climbs its side instead), and the strips laid on it.
    private static final class Ground {
        private final Vec3 world;
        private final int seed;
        private final int baseY;
        private final Map<Long, float[]> columns = new HashMap<>();
        private final java.util.Set<Long> trunks = new java.util.HashSet<>();
        final List<Float> strips = new ArrayList<>();
        final List<Float> flames = new ArrayList<>();

        Ground(Vec3 world, int seed) {
            this.world = world;
            this.seed = seed;
            this.baseY = Mth.floor(world.y);
        }

        // Height of the ground's top in a column relative to world.y, or NaN when it is too far above or below where it
        // hit (a cliff), so the crack stops there.
        float top(int x, int z) {
            return column(x, z)[0];
        }

        private float[] column(int x, int z) {
            return columns.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), key -> {
                var level = Minecraft.getInstance().level;
                if (level == null) {
                    return new float[] {0.05f, 0};
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int trunk = 0;
                var pos = new net.minecraft.core.BlockPos.MutableBlockPos(x, top - 1, z);
                while (trunk < 32 && level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) {
                    trunk++;
                    pos.move(0, -1, 0);
                }
                top -= trunk;
                if (Math.abs(top - baseY) > 10) {
                    return new float[] {Float.NaN, 0};
                }
                return new float[] {(float) (top - world.y) + 0.04f, trunk};
            });
        }

        // Lays the crack from (ax, az) to (bx, bz), column by column.
        void walk(float ax, float az, float bx, float bz) {
            float dx = bx - ax, dz = bz - az;
            int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) * 2));
            float sx = ax, sz = az;
            int cx = Mth.floor(world.x + ax), cz = Mth.floor(world.z + az);
            float h = top(cx, cz);
            for (int i = 1; i <= steps; i++) {
                float px = ax + dx * i / steps, pz = az + dz * i / steps;
                int nx = Mth.floor(world.x + px), nz = Mth.floor(world.z + pz);
                if (nx == cx && nz == cz && i < steps) {
                    continue;
                }
                float nh = top(nx, nz);
                boolean step = !Float.isNaN(h) && !Float.isNaN(nh) && Math.abs(nh - h) > 0.01f;
                if (i == steps && !step || Float.isNaN(nh) || Float.isNaN(h)) {
                    if (!Float.isNaN(h)) {
                        add(sx, h, sz, px, h, pz, 0);
                    }
                    sx = px;
                    sz = pz;
                } else if (step || nx != cx || nz != cz) {
                    // Into the next column: to the edge between them, then down or up its side if the ground steps.
                    float ex = nx != cx ? (float) (Math.max(nx, cx) - world.x) : px;
                    float ez = nx == cx ? (float) (Math.max(nz, cz) - world.z) : pz;
                    add(sx, h, sz, ex, h, ez, 0);
                    if (step) {
                        // On the face of the higher block, a hair out from it towards the lower one.
                        boolean upward = nh > h;
                        float push = 0.03f;
                        float fx = ex, fz = ez;
                        if (nx != cx) {
                            fx += (upward ? (nx > cx ? -push : push) : (nx > cx ? push : -push));
                        } else {
                            fz += (upward ? (nz > cz ? -push : push) : (nz > cz ? push : -push));
                        }
                        add(fx, h, fz, fx, nh, fz, nx != cx ? 2 : 1);
                    }
                    sx = ex;
                    sz = ez;
                    if (i == steps && !Float.isNaN(nh)) {
                        add(sx, nh, sz, px, nh, pz, 0);
                    }
                }
                if (!Float.isNaN(nh) && column(nx, nz)[1] > 0) {
                    trunk(nx, nz, nh, column(nx, nz)[1]);
                }
                cx = nx;
                cz = nz;
                h = nh;
            }
        }

        // Up the side of a trunk, on the face looking at the middle, a few blocks high, bending a little.
        private void trunk(int x, int z, float h, float height) {
            long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
            if (!trunks.add(key)) {
                return;
            }
            float rx = (float) (x + 0.5 - world.x), rz = (float) (z + 0.5 - world.z);
            boolean alongX = Math.abs(rx) > Math.abs(rz);
            float fx = alongX ? rx - Math.signum(rx) * 0.53f : rx;
            float fz = alongX ? rz : rz - Math.signum(rz) * 0.53f;
            float up = Math.min(height, 3 + 4 * FxWorld.hash(seed, x * 31 + z, 40));
            float lean = (FxWorld.hash(seed, x * 31 + z, 41) - 0.5f) * 0.6f;
            float mx = fx + (alongX ? 0 : lean), mz = fz + (alongX ? lean : 0);
            int axis = alongX ? 2 : 1;
            add(fx, h, fz, mx, h + up * 0.5f, mz, axis);
            add(mx, h + up * 0.5f, mz, fx, h + up, fz, axis);
        }

        private void add(float ax, float ay, float az, float bx, float by, float bz, int axis) {
            strips.add(ax);
            strips.add(ay);
            strips.add(az);
            strips.add(bx);
            strips.add(by);
            strips.add(bz);
            strips.add((float) axis);
            strips.add((float) Math.max(Math.hypot(ax, az), Math.hypot(bx, bz)));
        }
    }

    static void pillars(Efeito e, int seed, Vector3f at, float age, float t, float[] cor, float[] cor2, float size, float opacity) {
        int count = FxWorld.i(e.quantidade(), 40);
        float radius = (float) FxWorld.d(e.raio(), 60);
        float tall = (float) FxWorld.d(e.comprimento(), 90);
        for (int k = 0; k < count; k++) {
            // Each one bursts out at its own moment, shoots up fast and burns for a while.
            float born = FxWorld.hash(seed, k, 1) * 0.6f;
            float lt = (t - born) / (1 - born);
            if (lt <= 0 || lt >= 1) {
                continue;
            }
            float a = FxWorld.hash(seed, k, 2) * Mth.TWO_PI;
            float d = radius * Mth.sqrt(FxWorld.hash(seed, k, 3));
            float w = size * (0.5f + FxWorld.hash(seed, k, 4)) * (lt < 0.8f ? 1 : (1 - lt) / 0.2f);
            float h = tall * (0.6f + 0.4f * FxWorld.hash(seed, k, 5)) * FxWorld.easeOut(Math.min(1, lt * 5));
            Vector3f foot = new Vector3f(Mth.cos(a) * d, -4, Mth.sin(a) * d).add(at);
            Vector3f top = new Vector3f(foot).add(0, h, 0);
            float flicker = 0.85f + 0.15f * Mth.sin(age * 1.7f + k);
            FxDraw.ribbon(foot, top, w * 2.4f, FxDraw.BEAM, 0.15f, cor, 0.6f * flicker * opacity, true);
            FxDraw.ribbon(foot, top, w, FxDraw.BEAM, 0.7f, cor2, flicker * opacity, true);
            FxDraw.disk(new Vector3f(foot).add(0, 4.1f, 0), new Vector3f(0, 1, 0), w * 5, FxDraw.SOFT, 0, cor, 0.5f * flicker * opacity, true);
        }
    }

    static void plasma(Efeito e, int seed, Vector3f at, float age, float t, float[] cor, float[] cor2, float size, float opacity) {
        float radius = (float) FxWorld.d(e.raio(), 60);
        int fibers = FxWorld.i(e.quantidade(), 160);
        float grow = FxWorld.easeOut(Math.min(1, t * 2.2f));
        float r = radius * (0.15f + 0.85f * grow);
        // White-pink at first, orange and peach as it burns out (cor = magenta, cor2 = peach).
        float late = smooth(0.25f, 0.85f, t);
        float[] pink = FxDraw.rgb(0xFF6FD8);
        float[] body = FxDraw.mix(cor, cor2, late);
        float[] light = FxDraw.mix(pink, FxDraw.rgb(0xFFD0A8), late);
        float fade = 1 - smooth(0.6f, 1, t);
        Vector3f center = new Vector3f(at).add(0, r * 0.25f, 0);
        // The white-hot heart and the glow all around.
        FxDraw.billboard(center, r * 2.6f, 0, FxDraw.SOFT, 0.1f, body, 0.45f * fade * opacity, true);
        FxDraw.billboard(center, r * 1.3f * (1 - 0.4f * t), 0, FxDraw.SOFT, 0.6f, FxDraw.mix(WHITE, light, t), (1 - t) * opacity, true);
        // Puffs of colored plasma filling it, on the alpha layer too so the color holds over a bright sky.
        int puffs = Math.max(8, fibers / 3);
        for (int k = 0; k < puffs; k++) {
            Vector3f d = FxWorld.sphere(seed, k);
            d.y = Math.abs(d.y) * 0.8f - 0.1f;
            float out = r * (0.25f + 0.65f * FxWorld.hash(seed, k, 6));
            Vector3f pos = new Vector3f(d).mul(out).add(center);
            float s = r * (0.35f + 0.35f * FxWorld.hash(seed, k, 7)) * (0.8f + 0.4f * t);
            float[] c = FxDraw.mix(cor, light, FxWorld.hash(seed, k, 8));
            FxDraw.billboard(pos, s, FxWorld.hash(seed, k, 9) * 6 + age * 0.01f, FxDraw.SMOKE, t * 0.7f, c, 0.35f * fade * opacity, false);
            FxDraw.billboard(pos, s * 0.8f, FxWorld.hash(seed, k, 10) * 6, FxDraw.FIRE, 0.3f + 0.5f * t, c, 0.5f * fade * opacity, true);
        }
        // Fibers of plasma: bent streaks thrown out from the heart, re-rolled now and then so they crackle.
        int epoch = (int) (age / 3);
        for (int k = 0; k < fibers; k++) {
            Vector3f d = FxWorld.sphere(seed + 7, k);
            d.y = Math.abs(d.y) * 0.9f - 0.05f;
            float l = r * (0.55f + 0.6f * FxWorld.hash(seed, k, 11));
            Vector3f side = new Vector3f(d).cross(Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            Vector3f last = new Vector3f(d).mul(l * 0.1f).add(center);
            float[] c = FxDraw.mix(light, WHITE, 0.3f * FxWorld.hash(seed, k, 12) * (1 - late));
            for (int s = 1; s <= 3; s++) {
                float f = s / 3f;
                float bend = (FxWorld.hash(seed + epoch * 31, k, 13 + s) - 0.5f) * l * 0.18f;
                Vector3f next = new Vector3f(d).mul(l * f).add(center).add(new Vector3f(side).mul(bend));
                FxDraw.ribbon(last, next, size * (1.2f - 0.3f * s), FxDraw.STREAK, 0.7f, c, (1 - f * 0.6f) * fade * opacity, true);
                last = next;
            }
        }
    }

    static void cloud(Efeito e, int seed, Vector3f at, float age, float t, float[] cor, float[] cor2, float size, float opacity) {
        int count = FxWorld.i(e.quantidade(), 40);
        float radius = (float) FxWorld.d(e.raio(), 50);
        float in = Math.min(1, t * 6);
        float fade = 1 - smooth(0.5f, 1, t);
        for (int k = 0; k < count; k++) {
            Vector3f d = FxWorld.sphere(seed, k);
            d.y = Math.abs(d.y) * 0.6f;
            float out = radius * (0.3f + 0.7f * FxWorld.hash(seed, k, 1)) * (0.7f + 0.4f * FxWorld.easeOut(t));
            Vector3f pos = new Vector3f(d).mul(out).add(at).add(0, t * radius * 0.25f, 0);
            float s = size * (0.7f + 0.6f * FxWorld.hash(seed, k, 2)) * (0.8f + 0.5f * t);
            FxDraw.billboard(pos, s, FxWorld.hash(seed, k, 3) * 6 + age * 0.004f, FxDraw.SMOKE, 0.2f + 0.6f * t,
                    FxDraw.mix(cor, cor2, FxWorld.hash(seed, k, 4)), 0.45f * in * fade * opacity, false);
        }
    }

    private static float smooth(float a, float b, float x) {
        float k = Mth.clamp((x - a) / (b - a), 0, 1);
        return k * k * (3 - 2 * k);
    }
}
