package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

// Gaara's real sand, drawn the way the approved page draws it (forja/gaara-kit/gaara.js v1): every piece of sand (the giant
// hand with its fingers, the coffin, the streams running along the ground, the wall, the platform, the Taiso wave) is
// ONE shell in the page's own sand material (souls_sand: pushed by flowing noise, three bands of light, grains per cell,
// streaks running down, crumbling), built here in the page's shapes (spheres, cylinders, lathes, tubes) in the page's units
// (0.9 blocks), and the loose grains are each their own little lit stone with its own size, color, spin, speed and life.
// NrFx queues pieces while it walks the Gaara effects; FxWorld draws them once a frame after KiMesh, never into the glow.
public final class GaaraSand {
    static final float PU = 0.9f;
    static final float[] BASE = FxDraw.rgb(0xD9B673), SHADE = FxDraw.rgb(0xB0874A), DEEP = FxDraw.rgb(0x7A5830), LIGHT = FxDraw.rgb(0xF2DCA4);
    private static final RandomSource RNG = RandomSource.createThreadSafe();

    // One shell: its material numbers and what builds it.
    private record Piece(float lump, float flow, float scale, float dis, float riseY, float prog, Consumer<Mesh> build) {
    }

    private static final List<Piece> QUEUE = new ArrayList<>();
    private static boolean broken;
    private static double lastFrame = -1;
    // Seconds since the last frame (NrFx spawns grains at a rate per second with it).
    static float dt;

    private GaaraSand() {
    }

    // ---------- queueing (NrFx, alpha pass) ----------

    // riseY: camera-relative height (blocks) above which the sand hasn't come up yet (99 = all of it); prog: how far along a
    // path it has run (uv.x, 9 = all); dis: how far it has crumbled (0 whole, 1 gone).
    static void piece(float lump, float flow, float scale, float dis, float riseY, float prog, Consumer<Mesh> build) {
        if (dis < 1 && !broken) {
            QUEUE.add(new Piece(lump, flow, scale, dis, riseY, prog, build));
        }
    }

    // A frame in the page's units: origin (camera-relative), turned so the page's -z points along fwd (flat), scaled 0.9.
    static Matrix4f frame(Vector3f origin, Vector3f fwd) {
        float yaw = (float) Math.atan2(-fwd.x, -fwd.z);
        return new Matrix4f().translation(origin).rotateY(yaw).scale(PU);
    }

    // ---------- the loose grains ----------

    static final class Grain {
        double x, y, z, vx, vy, vz, floor;
        float age, life, s, grav = 14, drag = 0.4f, spin;
        float[] col;
        Quaternionf q = new Quaternionf();
        Vector3f axis;
        // spiralling up round a center (the coffin): center, angle, radius, angular speed, height, rise speed
        boolean orbit;
        double cx, cz, oa, or, ow, oy, ovy;
    }

    private static final List<Grain> GRAINS = new ArrayList<>();
    private static final int MAX = 2400;

    private static float[] grainColor() {
        float[][] base = {BASE, SHADE, LIGHT};
        float[] a = base[RNG.nextInt(3)];
        float k = RNG.nextFloat() * RNG.nextFloat(), m = 0.88f + 0.24f * RNG.nextFloat();
        return new float[] {(a[0] + (DEEP[0] - a[0]) * k) * m, (a[1] + (DEEP[1] - a[1]) * k) * m, (a[2] + (DEEP[2] - a[2]) * k) * m};
    }

    // A grain at pos (world) with velocity vel (blocks/s), the ground at floor (world y).
    static Grain grain(Vec3 pos, Vec3 vel, double floor) {
        if (GRAINS.size() >= MAX) {
            GRAINS.remove(0);
        }
        Grain g = new Grain();
        g.x = pos.x;
        g.y = pos.y;
        g.z = pos.z;
        g.vx = vel.x;
        g.vy = vel.y;
        g.vz = vel.z;
        g.floor = floor;
        g.life = 0.8f + RNG.nextFloat() * 0.8f;
        g.s = (0.025f + 0.05f * RNG.nextFloat() * RNG.nextFloat()) * PU;
        g.col = grainColor();
        g.q.rotationXYZ(RNG.nextFloat() * 6, RNG.nextFloat() * 6, RNG.nextFloat() * 6);
        g.axis = new Vector3f(RNG.nextFloat() - 0.5f, RNG.nextFloat() - 0.5f, RNG.nextFloat() - 0.5f).normalize();
        g.spin = 2 + RNG.nextFloat() * 14;
        g.grav = 14 * PU;
        GRAINS.add(g);
        return g;
    }

    // A grain spiralling up round c (world), radius r and speed w (rad/s), from height y (world) rising vy (blocks/s).
    static void orbit(Vec3 c, double r, double w, double y, double vy, float life) {
        Grain g = grain(new Vec3(c.x + r, y, c.z), Vec3.ZERO, -1e9);
        g.orbit = true;
        g.cx = c.x;
        g.cz = c.z;
        g.oa = RNG.nextFloat() * Math.PI * 2;
        g.or = r;
        g.ow = w;
        g.oy = y;
        g.ovy = vy;
        g.life = life;
    }

    // n grains thrown from at (world) along dir in a cone, each with its own strength (pow in blocks/s).
    // floor NaN = the top of the ground under at.
    static void spray(Vec3 at, Vec3 dir, int n, double pow, double cone, double floor) {
        if (Double.isNaN(floor)) {
            floor = ground(at);
        }
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        if (d.lengthSquared() < 1.0E-6f) {
            d.set(0, 1, 0);
        }
        d.normalize();
        Vector3f s = new Vector3f(d).cross(Math.abs(d.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
        Vector3f u = new Vector3f(s).cross(d).normalize();
        for (int i = 0; i < n; i++) {
            double a = RNG.nextDouble() * 6.283, c = cone * Math.sqrt(RNG.nextDouble());
            Vector3f v = new Vector3f(d).add(new Vector3f(s).mul((float) (Math.cos(a) * c))).add(new Vector3f(u).mul((float) (Math.sin(a) * c))).normalize();
            double p = pow * (0.4 + 0.9 * RNG.nextDouble());
            grain(at.add((RNG.nextDouble() - 0.5) * 0.22, (RNG.nextDouble() - 0.5) * 0.22, (RNG.nextDouble() - 0.5) * 0.22), new Vec3(v.x * p, v.y * p, v.z * p), floor);
        }
    }

    // The top of the blocks under pos (client level), or pos.y - 2 without a level.
    static double ground(Vec3 pos) {
        net.minecraft.client.multiplayer.ClientLevel lv = net.minecraft.client.Minecraft.getInstance().level;
        if (lv == null) {
            return pos.y - 2;
        }
        int h = lv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
        return h <= pos.y + 1 ? h : pos.y - 2;
    }

    static RandomSource rng() {
        return RNG;
    }

    private static void step(float dt) {
        for (int i = GRAINS.size() - 1; i >= 0; i--) {
            Grain p = GRAINS.get(i);
            p.age += dt;
            if (p.age > p.life) {
                GRAINS.remove(i);
                continue;
            }
            if (p.orbit) {
                p.oa += p.ow * dt;
                p.oy += p.ovy * dt;
                p.x = p.cx + Math.cos(p.oa) * p.or;
                p.y = p.oy;
                p.z = p.cz + Math.sin(p.oa) * p.or;
            } else {
                p.vy -= p.grav * dt;
                double k = Math.max(0, 1 - p.drag * dt);
                p.vx *= k;
                p.vy *= k;
                p.vz *= k;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.z += p.vz * dt;
                if (p.y < p.floor + p.s && p.vy < 0) {
                    p.y = p.floor + p.s;
                    p.vx *= 0.25;
                    p.vy = 0;
                    p.vz *= 0.25;
                    p.spin *= 0.3f;
                }
            }
            p.q.premul(new Quaternionf().rotationAxis(p.spin * dt, p.axis));
        }
    }

    static boolean active() {
        return !GRAINS.isEmpty() || !QUEUE.isEmpty();
    }

    static void clear() {
        GRAINS.clear();
        QUEUE.clear();
    }

    // ---------- drawing (FxWorld, after KiMesh, never into the glow) ----------

    public static void flush(Vec3 camera, double now, boolean glow) {
        if (glow) {
            return;
        }
        dt = lastFrame < 0 ? 0 : (float) Math.max(0, Math.min(0.1, (now - lastFrame) / 20.0));
        lastFrame = now;
        step(dt);
        ShaderInstance sh = FxShaders.sand;
        if (sh == null || broken || (QUEUE.isEmpty() && GRAINS.isEmpty())) {
            QUEUE.clear();
            return;
        }
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(() -> sh);
            double w = 900;
            set3(sh, "CamOff", (float) (camera.x - Math.floor(camera.x / w) * w), (float) (camera.y - Math.floor(camera.y / w) * w),
                    (float) (camera.z - Math.floor(camera.z / w) * w));
            set(sh, "Time", (float) (now / 20.0));
            set3(sh, "Base", BASE);
            set3(sh, "Shade", SHADE);
            set3(sh, "Deep", DEEP);
            set3(sh, "Light", LIGHT);
            set(sh, "Grain", 0);
            for (Piece p : QUEUE) {
                set(sh, "Lump", p.lump());
                set(sh, "Flow", p.flow());
                set(sh, "Scale", p.scale());
                set(sh, "Dis", p.dis());
                set(sh, "RiseY", p.riseY());
                set(sh, "Prog", p.prog());
                Mesh m = new Mesh();
                p.build().accept(m);
                m.draw();
            }
            if (!GRAINS.isEmpty()) {
                set(sh, "Grain", 1);
                Mesh m = new Mesh();
                for (Grain g : GRAINS) {
                    float k = g.age / g.life, s = g.s * (k > 0.75f ? (1 - k) / 0.25f : 1);
                    if (s > 0.001f) {
                        m.grain((float) (g.x - camera.x), (float) (g.y - camera.y), (float) (g.z - camera.z), s, g.q, g.col);
                    }
                }
                m.draw();
                set(sh, "Grain", 0);
            }
        } catch (RuntimeException e) {
            broken = true;
            BattlegroundsMod.LOGGER.error("Gaara's sand failed to draw; it is off until restart", e);
        } finally {
            QUEUE.clear();
            RenderSystem.enableCull();
            GL11.glCullFace(GL11.GL_BACK);
        }
    }

    private static void set(ShaderInstance s, String name, float v) {
        s.safeGetUniform(name).set(v);
    }

    private static void set3(ShaderInstance s, String name, float x, float y, float z) {
        s.safeGetUniform(name).set(x, y, z);
    }

    private static void set3(ShaderInstance s, String name, float[] c) {
        s.safeGetUniform(name).set(c[0], c[1], c[2]);
    }

    // ---------- the page's shapes (three.js conventions), in a frame that maps page units to camera-relative blocks ----------

    static final class Mesh {
        private BufferBuilder b;
        private int verts;

        private BufferBuilder buf() {
            if (b == null) {
                b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            }
            return b;
        }

        private void v(Matrix4f m, Matrix3f nm, float x, float y, float z, float nx, float ny, float nz, float u, float w, int r, int g, int bl) {
            Vector3f p = m.transformPosition(new Vector3f(x, y, z));
            Vector3f n = nm.transform(new Vector3f(nx, ny, nz));
            if (n.lengthSquared() > 1.0E-12f) {
                n.normalize();
            }
            buf().addVertex(p.x, p.y, p.z).setUv(u, w).setColor(r, g, bl, 255).setNormal(n.x, n.y, n.z);
            verts++;
        }

        private void v(Matrix4f m, Matrix3f nm, float[] q) {
            v(m, nm, q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], 255, 255, 255);
        }

        private void quad(Matrix4f m, Matrix3f nm, float[] a, float[] b2, float[] c, float[] d) {
            v(m, nm, a);
            v(m, nm, c);
            v(m, nm, b2);
            v(m, nm, b2);
            v(m, nm, c);
            v(m, nm, d);
        }

        private static Matrix3f normals(Matrix4f m) {
            return new Matrix3f(m).invert().transpose();
        }

        // A unit sphere (radius 1) under m.
        void sphere(Matrix4f m, int ws, int hs) {
            Matrix3f nm = normals(m);
            for (int i = 0; i < hs; i++) {
                for (int j = 0; j < ws; j++) {
                    float[] a = sp(j, i, ws, hs), b2 = sp(j + 1, i, ws, hs), c = sp(j, i + 1, ws, hs), d = sp(j + 1, i + 1, ws, hs);
                    quad(m, nm, a, b2, c, d);
                }
            }
        }

        private static float[] sp(int j, int i, int ws, int hs) {
            float u = (float) j / ws, w = (float) i / hs;
            float phi = u * (float) Math.PI * 2, th = w * (float) Math.PI;
            float x = (float) (-Math.cos(phi) * Math.sin(th)), y = (float) Math.cos(th), z = (float) (Math.sin(phi) * Math.sin(th));
            return new float[] {x, y, z, x, y, z, u, 1 - w};
        }

        // three's CylinderGeometry: centered, y from -h/2 to h/2, x = r sin(theta), z = r cos(theta).
        void cylinder(Matrix4f m, float rTop, float rBot, float h, int radial, int hseg, boolean open, float th0, float thLen) {
            Matrix3f nm = normals(m);
            float slope = (rBot - rTop) / h;
            for (int y = 0; y < hseg; y++) {
                for (int x = 0; x < radial; x++) {
                    float[] a = cy(x, y, radial, hseg, rTop, rBot, h, th0, thLen, slope), b2 = cy(x + 1, y, radial, hseg, rTop, rBot, h, th0, thLen, slope);
                    float[] c = cy(x, y + 1, radial, hseg, rTop, rBot, h, th0, thLen, slope), d = cy(x + 1, y + 1, radial, hseg, rTop, rBot, h, th0, thLen, slope);
                    quad(m, nm, a, b2, c, d);
                }
            }
            if (!open) {
                for (int top = 0; top < 2; top++) {
                    float r = top == 0 ? rTop : rBot, yy = top == 0 ? h / 2 : -h / 2, ny = top == 0 ? 1 : -1;
                    if (r <= 0) {
                        continue;
                    }
                    for (int x = 0; x < radial; x++) {
                        float t0 = th0 + thLen * x / radial, t1 = th0 + thLen * (x + 1) / radial;
                        float[] c = {0, yy, 0, 0, ny, 0, 0.5f, 0.5f};
                        float[] p0 = {r * (float) Math.sin(t0), yy, r * (float) Math.cos(t0), 0, ny, 0, 0.5f, 0.5f};
                        float[] p1 = {r * (float) Math.sin(t1), yy, r * (float) Math.cos(t1), 0, ny, 0, 0.5f, 0.5f};
                        v(m, nm, c);
                        v(m, nm, p0);
                        v(m, nm, p1);
                    }
                }
            }
        }

        private static float[] cy(int x, int y, int radial, int hseg, float rTop, float rBot, float h, float th0, float thLen, float slope) {
            float u = (float) x / radial, v = (float) y / hseg;
            float r = rTop + (rBot - rTop) * v, th = th0 + u * thLen;
            float sn = (float) Math.sin(th), cs = (float) Math.cos(th);
            float yy = h / 2 - v * h;
            return new float[] {r * sn, yy, r * cs, sn, slope, cs, u, 1 - v};
        }

        // three's LatheGeometry: profile (r, y) turned round y.
        void lathe(Matrix4f m, float[][] pts, int segs) {
            Matrix3f nm = normals(m);
            int n = pts.length;
            float[][] pn = new float[n][2];
            for (int i = 0; i < n; i++) {
                float nr = 0, ny = 0;
                if (i > 0) {
                    float dr = pts[i][0] - pts[i - 1][0], dy = pts[i][1] - pts[i - 1][1], l = (float) Math.hypot(dr, dy) + 1e-6f;
                    nr += dy / l;
                    ny += -dr / l;
                }
                if (i < n - 1) {
                    float dr = pts[i + 1][0] - pts[i][0], dy = pts[i + 1][1] - pts[i][1], l = (float) Math.hypot(dr, dy) + 1e-6f;
                    nr += dy / l;
                    ny += -dr / l;
                }
                pn[i][0] = nr;
                pn[i][1] = ny;
            }
            for (int s = 0; s < segs; s++) {
                float p0 = (float) (Math.PI * 2 * s / segs), p1 = (float) (Math.PI * 2 * (s + 1) / segs);
                for (int i = 0; i < n - 1; i++) {
                    float[] a = la(pts[i], pn[i], p0, (float) s / segs, (float) i / (n - 1)), b2 = la(pts[i], pn[i], p1, (float) (s + 1) / segs, (float) i / (n - 1));
                    float[] c = la(pts[i + 1], pn[i + 1], p0, (float) s / segs, (float) (i + 1) / (n - 1)),
                            d = la(pts[i + 1], pn[i + 1], p1, (float) (s + 1) / segs, (float) (i + 1) / (n - 1));
                    quad(m, nm, a, b2, c, d);
                }
            }
        }

        private static float[] la(float[] p, float[] n, float phi, float u, float v) {
            float sn = (float) Math.sin(phi), cs = (float) Math.cos(phi);
            return new float[] {p[0] * sn, p[1], p[0] * cs, n[0] * sn, n[1], n[0] * cs, u, v};
        }

        // A tube along a Catmull-Rom curve through pts (page units, in m), radius rf(t), flat squashes one side; uv.x = 0..1
        // along it (the page's tubeVar).
        void tube(Matrix4f m, Vector3f[] pts, java.util.function.DoubleUnaryOperator rf, int seg, int sides, float flat) {
            Matrix3f nm = normals(m);
            int fine = seg * 8;
            Vector3f[] f = new Vector3f[fine + 1];
            float[] len = new float[fine + 1];
            for (int i = 0; i <= fine; i++) {
                f[i] = catmull(pts, (float) i / fine);
                len[i] = i == 0 ? 0 : len[i - 1] + f[i].distance(f[i - 1]);
            }
            float total = Math.max(1e-5f, len[fine]);
            Vector3f[] c = new Vector3f[seg + 1];
            int k = 0;
            for (int i = 0; i <= seg; i++) {
                float want = total * i / seg;
                while (k < fine && len[k + 1] < want) {
                    k++;
                }
                float a = k >= fine ? 1 : (want - len[k]) / Math.max(1e-6f, len[k + 1] - len[k]);
                c[i] = k >= fine ? new Vector3f(f[fine]) : new Vector3f(f[k]).lerp(f[k + 1], a);
            }
            // frames carried along (parallel transport)
            Vector3f[] tg = new Vector3f[seg + 1], nr = new Vector3f[seg + 1], bn = new Vector3f[seg + 1];
            for (int i = 0; i <= seg; i++) {
                Vector3f t = new Vector3f(c[Math.min(seg, i + 1)]).sub(c[Math.max(0, i - 1)]);
                tg[i] = t.lengthSquared() < 1e-10f ? new Vector3f(0, 0, 1) : t.normalize();
            }
            Vector3f n0 = new Vector3f(tg[0]).cross(Math.abs(tg[0].y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
            nr[0] = n0;
            bn[0] = new Vector3f(tg[0]).cross(n0).normalize();
            for (int i = 1; i <= seg; i++) {
                Vector3f n = new Vector3f(nr[i - 1]);
                n.sub(new Vector3f(tg[i]).mul(n.dot(tg[i]))).normalize();
                nr[i] = n;
                bn[i] = new Vector3f(tg[i]).cross(n).normalize();
            }
            for (int i = 0; i < seg; i++) {
                for (int j = 0; j < sides; j++) {
                    float[] a = tv(c, nr, bn, rf, i, j, seg, sides, flat), b2 = tv(c, nr, bn, rf, i, j + 1, seg, sides, flat);
                    float[] cc = tv(c, nr, bn, rf, i + 1, j, seg, sides, flat), d = tv(c, nr, bn, rf, i + 1, j + 1, seg, sides, flat);
                    quad(m, nm, a, b2, cc, d);
                }
            }
        }

        private static float[] tv(Vector3f[] c, Vector3f[] nr, Vector3f[] bn, java.util.function.DoubleUnaryOperator rf, int i, int j, int seg, int sides, float flat) {
            float t = (float) i / seg, a = (float) (j * Math.PI * 2 / sides), r = (float) rf.applyAsDouble(t);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            Vector3f n = new Vector3f(nr[i]).mul(ca).add(new Vector3f(bn[i]).mul(sa * flat));
            Vector3f p = new Vector3f(c[i]).add(new Vector3f(nr[i]).mul(ca * r)).add(new Vector3f(bn[i]).mul(sa * r * flat));
            return new float[] {p.x, p.y, p.z, n.x, n.y, n.z, t, (float) j / sides};
        }

        private static Vector3f catmull(Vector3f[] p, float t) {
            int n = p.length - 1;
            float x = t * n;
            int i = Math.min(n - 1, (int) Math.floor(x));
            float u = x - i;
            Vector3f p0 = p[Math.max(0, i - 1)], p1 = p[i], p2 = p[i + 1], p3 = p[Math.min(n, i + 2)];
            float u2 = u * u, u3 = u2 * u;
            return new Vector3f(
                    0.5f * (2 * p1.x + (-p0.x + p2.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u3),
                    0.5f * (2 * p1.y + (-p0.y + p2.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u3),
                    0.5f * (2 * p1.z + (-p0.z + p2.z) * u + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * u2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * u3));
        }

        // One loose grain: a little stone (an octahedron, the page's low icosahedron) at camera-relative x, y, z.
        void grain(float x, float y, float z, float s, Quaternionf q, float[] col) {
            Matrix4f m = new Matrix4f().translation(x, y, z).rotate(q).scale(s);
            Matrix3f nm = new Matrix3f(m).normalize3x3();
            int r = (int) (Math.min(1, col[0]) * 255), g = (int) (Math.min(1, col[1]) * 255), bl = (int) (Math.min(1, col[2]) * 255);
            float[][] P = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            int[][] F = {{0, 2, 4}, {2, 1, 4}, {1, 3, 4}, {3, 0, 4}, {2, 0, 5}, {1, 2, 5}, {3, 1, 5}, {0, 3, 5}};
            for (int[] f : F) {
                float[] a = P[f[0]], b2 = P[f[1]], c = P[f[2]];
                float nx = (a[0] + b2[0] + c[0]) / 3, ny = (a[1] + b2[1] + c[1]) / 3, nz = (a[2] + b2[2] + c[2]) / 3;
                v(m, nm, a[0], a[1], a[2], nx, ny, nz, 0, 0, r, g, bl);
                v(m, nm, b2[0], b2[1], b2[2], nx, ny, nz, 0, 0, r, g, bl);
                v(m, nm, c[0], c[1], c[2], nx, ny, nz, 0, 0, r, g, bl);
            }
        }

        void draw() {
            if (b == null || verts == 0) {
                return;
            }
            MeshData mesh = b.build();
            b = null;
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }
    }
}
