package com.starkinhon.battlegroundsmod.client.fx.shig;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.power.powers.shigaraki.TentacleSpine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

// V, Tentáculos de Mãos: the five tentacles of the preview (buildTentacle), each made of its own hands (about 3,400,
// none the same: HandGen) around a dark core, bent along its spine every frame on the GPU (souls_shig_tent), with the
// dark outline drawn as pushed-out back faces. The spines come from TentacleSpine, the same numbers the server uses
// for what they touch. The meshes are built once, off the render thread, the first time Shigaraki shows up.
public final class TentacleFx {
    private static final int STRIDE = 10; // aAnc 4, aRel 3, aNrm 3

    private static final class Gpu {
        int vao, vbo, ebo, count;
    }

    private static final class Cast {
        final int caster;
        final double start;
        final Vec3 feet;
        final float yaw;
        // the entity the tentacles follow (+1; 0 = none)
        final int mark;
        double cutAt = -1;

        Cast(int caster, double start, Vec3 feet, float yaw, int mark) {
            this.mark = mark;
            this.caster = caster;
            this.start = start;
            this.feet = feet;
            this.yaw = yaw;
        }
    }

    private record Built(float[] verts, int[] idx, int nv, int ni) {
    }

    private static CompletableFuture<Built[]> building;
    private static Gpu[] gpu;
    private static boolean failed;
    private static final List<Cast> CASTS = new ArrayList<>();

    private TentacleFx() {
    }

    // Starts building the meshes in the background (a second or two of work, once).
    static void prepare() {
        if (building == null && !failed) {
            building = CompletableFuture.supplyAsync(() -> {
                Built[] out = new Built[TentacleSpine.DEFS.length];
                for (int i = 0; i < out.length; i++) {
                    out[i] = build(TentacleSpine.DEFS[i]);
                }
                return out;
            });
        }
    }

    static boolean active() {
        return !CASTS.isEmpty();
    }

    static void clear() {
        CASTS.clear();
    }

    // Shigaraki.TENTACLES: a = 0 starts (pos = feet, dir = facing), 1 cut short.
    static void receive(int id, Vec3 pos, Vec3 dir, float a, float b) {
        prepare();
        if (a < 0.5f) {
            CASTS.removeIf(c -> c.caster == id);
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            CASTS.add(new Cast(id, ShigFx.clock(), pos, yaw, Math.round(b)));
        } else {
            for (Cast c : CASTS) {
                if (c.caster == id && c.cutAt < 0) {
                    c.cutAt = ShigFx.clock();
                }
            }
        }
    }

    // ---------- the mesh (buildTentacle) ----------

    private static Built build(TentacleSpine.Def tc) {
        HandGen.Rng r = new HandGen.Rng(tc.seed);
        float[] V = new float[1 << 20];
        int[] I = new int[1 << 20];
        int[] n = {0, 0}; // vertices, indices
        // the dark core, so the gaps between hands are black and nothing shows through
        {
            int ring = 10;
            float step = 0.5f;
            int rings = (int) Math.ceil(tc.L / step) + 1, base = n[0];
            for (int k = 0; k < rings; k++) {
                float d = Math.min(k * step, tc.L), rr = tc.radius(d) * 0.62f;
                for (int a = 0; a < ring; a++) {
                    double an = a / (double) ring * Math.PI * 2;
                    V = vert(V, n, d / tc.L, -1, 0, 0, 0, (float) Math.cos(an) * rr, (float) Math.sin(an) * rr, 0, (float) Math.cos(an), (float) Math.sin(an));
                }
            }
            for (int k = 0; k < rings - 1; k++) {
                for (int a = 0; a < ring; a++) {
                    int a1 = (a + 1) % ring, p0 = base + k * ring + a, p1 = base + k * ring + a1, p2 = p0 + ring, p3 = p1 + ring;
                    I = idx(I, n, p0, p1, p2);
                    I = idx(I, n, p1, p3, p2);
                }
            }
        }
        for (float d = 0.6f; d < tc.L + 0.6f;) {
            float Rl = tc.radius(Math.min(d, tc.L)), u = d / tc.L, tipZone = ShigDraw.smooth((u - 0.85f) / 0.15f);
            float circ = (float) (Math.PI * 2 * Rl);
            float scMean = (0.55f + 0.6f * Math.min(Rl / 2, 1.2f)) * tc.handK;
            int count = Math.max(4, Math.round(circ * 0.8f / (scMean * 0.62f)));
            float ringPhase = (float) (r.next() * Math.PI * 2);
            for (int h = 0; h < count; h++) {
                float sc = scMean * (0.72f + r.next() * 0.6f);
                float th = (float) (ringPhase + (h + (r.next() - 0.5f) * 0.7f) / count * Math.PI * 2);
                float dd = ShigDraw.clamp(d + (r.next() - 0.5f) * 0.7f, 0.3f, tc.L + 0.3f), au = dd / tc.L;
                float rr = tc.radius(Math.min(dd, tc.L)) * (0.45f + r.next() * 0.42f);
                float[] out = {0, (float) Math.cos(th), (float) Math.sin(th)}, tan = {0, (float) Math.sin(th), (float) -Math.cos(th)};
                float back = r.next() < 0.12f - tipZone * 0.1f ? -1 : 1;
                float fwdT = back * (0.45f + r.next() * 0.75f + tipZone * 1.2f);
                float ko = 0.35f + r.next() * 0.75f - tipZone * 0.2f, kt = (r.next() - 0.5f) * 0.9f;
                float[] dir = {fwdT + out[0] * ko + tan[0] * kt, out[1] * ko + tan[1] * kt, out[2] * ko + tan[2] * kt};
                ShigDraw.norm(dir);
                float od = out[0] * dir[0] + out[1] * dir[1] + out[2] * dir[2];
                float[] up = {out[0] - dir[0] * od, out[1] - dir[1] * od, out[2] - dir[2] * od};
                if (up[0] * up[0] + up[1] * up[1] + up[2] * up[2] < 1e-3f) {
                    float[] ax = Math.abs(dir[1]) < 0.9f ? new float[] {0, 1, 0} : new float[] {1, 0, 0};
                    up = ShigDraw.cross(ax, dir);
                }
                ShigDraw.norm(up);
                up = axisAngle(up, dir, (r.next() - 0.5f) * 2.4f);
                float[] side = ShigDraw.cross(up, dir);
                ShigDraw.norm(side);
                HandGen.Mesh hg = HandGen.hand(r, 1);
                float seed = r.next();
                int base = n[0];
                float wn = (float) Math.cos(th) * rr, wb = (float) Math.sin(th) * rr;
                for (int i = 0; i < hg.nv; i++) {
                    float x = hg.pos[i * 3] * sc, y = hg.pos[i * 3 + 1] * sc, z = hg.pos[i * 3 + 2] * sc;
                    float nx = hg.nrm[i * 3], ny = hg.nrm[i * 3 + 1], nz = hg.nrm[i * 3 + 2];
                    float[] p = {side[0] * x + up[0] * y + dir[0] * z, side[1] * x + up[1] * y + dir[1] * z, side[2] * x + up[2] * y + dir[2] * z};
                    float[] q = {side[0] * nx + up[0] * ny + dir[0] * nz, side[1] * nx + up[1] * ny + dir[1] * nz, side[2] * nx + up[2] * ny + dir[2] * nz};
                    ShigDraw.norm(q);
                    V = vert(V, n, au, seed, wn, wb, p[0], p[1], p[2], q[0], q[1], q[2]);
                }
                for (int i = 0; i < hg.ni; i++) {
                    if (n[1] >= I.length) {
                        I = java.util.Arrays.copyOf(I, I.length * 2);
                    }
                    I[n[1]++] = base + hg.idx[i];
                }
            }
            d += (0.48f + r.next() * 0.3f) * scMean;
        }
        return new Built(V, I, n[0], n[1]);
    }

    private static float[] vert(float[] V, int[] n, float au, float sd, float wn, float wb, float x, float y, float z, float nx, float ny, float nz) {
        if ((n[0] + 1) * STRIDE > V.length) {
            V = java.util.Arrays.copyOf(V, V.length * 2);
        }
        int o = n[0] * STRIDE;
        V[o] = au;
        V[o + 1] = sd;
        V[o + 2] = wn;
        V[o + 3] = wb;
        V[o + 4] = x;
        V[o + 5] = y;
        V[o + 6] = z;
        V[o + 7] = nx;
        V[o + 8] = ny;
        V[o + 9] = nz;
        n[0]++;
        return V;
    }

    private static int[] idx(int[] I, int[] n, int a, int b, int c) {
        if (n[1] + 3 > I.length) {
            I = java.util.Arrays.copyOf(I, I.length * 2);
        }
        I[n[1]++] = a;
        I[n[1]++] = b;
        I[n[1]++] = c;
        return I;
    }

    // Rodrigues: v turned by angle around the unit axis k (three.js applyAxisAngle).
    private static float[] axisAngle(float[] v, float[] k, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float[] kx = ShigDraw.cross(k, v);
        float kd = k[0] * v[0] + k[1] * v[1] + k[2] * v[2];
        return new float[] {v[0] * c + kx[0] * s + k[0] * kd * (1 - c), v[1] * c + kx[1] * s + k[1] * kd * (1 - c), v[2] * c + kx[2] * s + k[2] * kd * (1 - c)};
    }

    private static boolean upload() {
        if (gpu != null) {
            return true;
        }
        if (building == null || !building.isDone() || failed) {
            return false;
        }
        Built[] built;
        try {
            built = building.join();
        } catch (RuntimeException e) {
            BattlegroundsMod.LOGGER.error("Could not build Shigaraki's tentacles", e);
            failed = true;
            return false;
        }
        Gpu[] g = new Gpu[built.length];
        for (int i = 0; i < built.length; i++) {
            Built b = built[i];
            Gpu q = new Gpu();
            q.vao = GL30.glGenVertexArrays();
            q.vbo = GL15.glGenBuffers();
            q.ebo = GL15.glGenBuffers();
            q.count = b.ni();
            GL30.glBindVertexArray(q.vao);
            FloatBuffer fb = MemoryUtil.memAllocFloat(b.nv() * STRIDE);
            fb.put(b.verts(), 0, b.nv() * STRIDE).flip();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, q.vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(fb);
            IntBuffer ib = MemoryUtil.memAllocInt(b.ni());
            ib.put(b.idx(), 0, b.ni()).flip();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, q.ebo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(ib);
            GL30.glBindVertexArray(0);
            g[i] = q;
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        BufferUploader.invalidate();
        gpu = g;
        building = null;
        return true;
    }

    // ---------- drawing ----------

    static void render(Vec3 camera) {
        double now = ShigFx.clock();
        CASTS.removeIf(c -> now - c.start > TentacleSpine.CAST_LEN + 0.3 || (c.cutAt >= 0 && now - c.cutAt > 1.0));
        ShaderInstance sh = ShigShaders.tent;
        if (CASTS.isEmpty() || sh == null || !upload()) {
            return;
        }
        int prog = sh.getId();
        float[] o = ShigDraw.origin(camera);
        float[][] S = new float[TentacleSpine.KK][3], N = new float[TentacleSpine.KK][], B = new float[TentacleSpine.KK][];
        FloatBuffer P = MemoryUtil.memAllocFloat(TentacleSpine.KK * 3), NB = MemoryUtil.memAllocFloat(TentacleSpine.KK * 3),
                BB = MemoryUtil.memAllocFloat(TentacleSpine.KK * 3);
        try {
            for (Cast c : CASTS) {
                float t = (float) (now - c.start);
                float cut = c.cutAt < 0 ? 1 : 1 - ShigDraw.smooth((float) (now - c.cutAt) / 0.8f);
                double yr = Math.toRadians(c.yaw);
                float cs = (float) Math.cos(yr), sn = (float) Math.sin(yr);
                float fx = (float) (c.feet.x - camera.x), fy = (float) (c.feet.y - camera.y), fz = (float) (c.feet.z - camera.z);
                float[] aim = null;
                var lvl = net.minecraft.client.Minecraft.getInstance().level;
                if (c.mark > 0 && lvl != null && lvl.getEntity(c.mark - 1) instanceof net.minecraft.world.entity.LivingEntity m && m.isAlive()) {
                    float pt = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                    Vec3 mp = m.getPosition(pt);
                    aim = TentacleSpine.toFrame(mp.x, mp.y + 0.4, mp.z, c.feet.x, c.feet.y, c.feet.z, c.yaw);
                }
                for (int i = 0; i < TentacleSpine.DEFS.length; i++) {
                    TentacleSpine.Def d = TentacleSpine.DEFS[i];
                    float g = d.grow(t) * cut;
                    if (g <= 0.001f) {
                        continue;
                    }
                    TentacleSpine.spine(d, t, aim, S);
                    TentacleSpine.frames(S, N, B);
                    P.clear();
                    NB.clear();
                    BB.clear();
                    for (int k = 0; k < TentacleSpine.KK; k++) {
                        float[] s = S[k], nn = N[k], bb = B[k];
                        P.put(fx + s[0] * cs - s[2] * sn).put(fy + s[1]).put(fz + s[0] * sn + s[2] * cs);
                        NB.put(nn[0] * cs - nn[2] * sn).put(nn[1]).put(nn[0] * sn + nn[2] * cs);
                        BB.put(bb[0] * cs - bb[2] * sn).put(bb[1]).put(bb[0] * sn + bb[2] * cs);
                    }
                    P.flip();
                    NB.flip();
                    BB.flip();
                    sh.safeGetUniform("L").set(d.L);
                    sh.safeGetUniform("Grow").set(g);
                    sh.safeGetUniform("Time").set((float) (now % 1000));
                    sh.safeGetUniform("Wnd").set(2.6f);
                    sh.safeGetUniform("Rad").set(d.Rr, d.Rm, d.tipK);
                    sh.safeGetUniform("Origin").set(o[0], o[1], o[2]);
                    for (int pass = 0; pass < 2; pass++) {
                        sh.safeGetUniform("Out").set(pass == 0 ? 0f : 0.045f);
                        draw(sh, prog, gpu[i], P, NB, BB, pass == 1);
                    }
                }
            }
        } finally {
            MemoryUtil.memFree(P);
            MemoryUtil.memFree(NB);
            MemoryUtil.memFree(BB);
        }
    }

    private static void draw(ShaderInstance sh, int prog, Gpu q, FloatBuffer P, FloatBuffer N, FloatBuffer B, boolean outline) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        if (outline) {
            GL11.glCullFace(GL11.GL_FRONT);
        }
        sh.setDefaultUniforms(VertexFormat.Mode.TRIANGLES, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(),
                Minecraft.getInstance().getWindow());
        sh.apply();
        GL20.glUniform3fv(GL20.glGetUniformLocation(prog, "uP"), P);
        GL20.glUniform3fv(GL20.glGetUniformLocation(prog, "uN"), N);
        GL20.glUniform3fv(GL20.glGetUniformLocation(prog, "uB"), B);
        GL30.glBindVertexArray(q.vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, q.vbo);
        attrib(prog, "aAnc", 4, 0);
        attrib(prog, "aRel", 3, 4);
        attrib(prog, "aNrm", 3, 7);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, q.ebo);
        GL11.glDrawElements(GL11.GL_TRIANGLES, q.count, GL11.GL_UNSIGNED_INT, 0L);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        sh.clear();
        BufferUploader.invalidate();
        if (outline) {
            GL11.glCullFace(GL11.GL_BACK);
        }
    }

    private static void attrib(int prog, String name, int size, int offset) {
        int loc = GL20.glGetAttribLocation(prog, name);
        if (loc < 0) {
            return;
        }
        GL20.glEnableVertexAttribArray(loc);
        GL20.glVertexAttribPointer(loc, size, GL11.GL_FLOAT, false, STRIDE * 4, offset * 4L);
    }
}
