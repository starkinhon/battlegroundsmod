package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

// Makima's chains, link by link, as in the approved previews (forja/makima-*/chain.js): every link forged on its own
// (half-length, bend radius, wire thickness, flattening, bulge, twist, a crooked end, three dents, sometimes a bite out
// of it, its own rust and tone), each link turned a quarter from the last along the path. Lit on the CPU with the
// previews' CHAIN_FS (fixed sun, half sky, specular, rim) into FxRenderTypes.SOLID, and the red that lights inside the
// links (glow), a pulse running along them (wave) and a surge of the whole chain. A thin additive halo strip runs along
// it in the glow pass.
final class MkChain {
    private static final int NU = 14;
    private static final int NV = 5;
    private static final float SCALE = 1.25f;
    private static final Vector3f SUN = new Vector3f(0.45f, 0.8f, 0.35f).normalize();
    private static final Vector3f HALF = new Vector3f();

    static final class Link {
        final float[] pos = new float[NU * NV * 3];
        final float[] nrm = new float[NU * NV * 3];
        final float[] col = new float[NU * NV * 3];
        final float[] inner = new float[NU * NV];
        float a;
        float r;
        float rt;
        float roll;
        float s;
    }

    private static final Map<Integer, List<Link>> CACHE = new HashMap<>();

    private MkChain() {
    }

    private static final class Rng {
        private int s;

        Rng(int seed) {
            s = seed * 0x9E3779B1 + 0x6D2B79F5;
        }

        // mulberry32, as the previews.
        float next() {
            s += 0x6D2B79F5;
            int t = s;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * (t | 61);
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296f;
        }
    }

    private static float sat(float x) {
        return Math.max(0, Math.min(1, x));
    }

    private static Link forge(Rng r) {
        Link l = new Link();
        float a = (0.068f + r.next() * 0.026f) * SCALE;
        float rr = (0.058f + r.next() * 0.016f) * SCALE;
        float rt = (0.019f + r.next() * 0.008f) * SCALE;
        float flat = 0.8f + r.next() * 0.35f;
        float bow = (r.next() - 0.5f) * 0.035f * SCALE;
        float twist = (r.next() - 0.5f) * 0.9f;
        float skew = (r.next() - 0.5f) * 0.03f * SCALE;
        float[][] dents = new float[3][4];
        for (float[] d : dents) {
            d[0] = 1 + (float) Math.floor(r.next() * 5);
            d[1] = (float) Math.floor(r.next() * 3);
            d[2] = r.next() * 6.283f;
            d[3] = 0.05f + r.next() * 0.12f;
        }
        boolean gash = r.next() < 0.35f;
        float gu = r.next(), gw = 0.04f + r.next() * 0.05f, gd = 0.25f + r.next() * 0.25f;
        float rustA = r.next(), rustF = 1 + r.next() * 3, rustP = r.next() * 6.28f;
        float tone = 0.13f + r.next() * 0.07f, warm = (r.next() - 0.5f) * 0.03f;
        float per = 4 * a + 2 * (float) Math.PI * rr;
        for (int iu = 0; iu < NU; iu++) {
            float u = iu / (float) NU, s = u * per;
            float cx, cy, ox, oy;
            if (s < 2 * a) {
                cx = -a + s;
                cy = rr;
                ox = 0;
                oy = 1;
            } else if (s < 2 * a + Math.PI * rr) {
                float th = (float) (Math.PI / 2 - (s - 2 * a) / rr);
                cx = a + (float) Math.cos(th) * rr;
                cy = (float) Math.sin(th) * rr;
                ox = (float) Math.cos(th);
                oy = (float) Math.sin(th);
            } else if (s < 4 * a + Math.PI * rr) {
                cx = a - (s - 2 * a - (float) Math.PI * rr);
                cy = -rr;
                ox = 0;
                oy = -1;
            } else {
                float th = (float) (-Math.PI / 2 - (s - 4 * a - Math.PI * rr) / rr);
                cx = -a + (float) Math.cos(th) * rr;
                cy = (float) Math.sin(th) * rr;
                ox = (float) Math.cos(th);
                oy = (float) Math.sin(th);
            }
            float st = Math.abs(cx) < a ? 1 - (cx / a) * (cx / a) : 0;
            cy += Math.signum(cy) * bow * st;
            cy += skew * (cx / (a + rr));
            for (int iv = 0; iv < NV; iv++) {
                float v = iv / (float) NV * 6.2832f;
                float k = 1;
                for (float[] d : dents) {
                    k += d[3] * (float) Math.sin(d[0] * u * 6.283f + d[1] * v + d[2]) * 0.5f;
                }
                if (gash) {
                    float du = Math.abs(((u - gu + 1.5f) % 1) - 0.5f);
                    if (du < gw) {
                        k *= 1 - gd * (1 - du / gw) * (0.5f + 0.5f * (float) Math.cos(v));
                    }
                }
                float c = (float) Math.cos(v), sn = (float) Math.sin(v);
                float x = cx + ox * c * rt * k, y = cy + oy * c * rt * k, z = sn * rt * k * flat;
                float tw = twist * (cx / (a + rr));
                float cz = (float) Math.cos(tw), sz = (float) Math.sin(tw);
                int o = (iu * NV + iv) * 3;
                l.pos[o] = x;
                l.pos[o + 1] = y * cz - z * sz;
                l.pos[o + 2] = y * sz + z * cz;
                float nx = ox * c, ny = oy * c, nz = sn / flat;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= nl;
                ny /= nl;
                nz /= nl;
                l.nrm[o] = nx;
                l.nrm[o + 1] = ny * cz - nz * sz;
                l.nrm[o + 2] = ny * sz + nz * cz;
                float rust = sat(0.5f + 0.5f * (float) Math.sin(u * 6.283f * rustF + rustP + v) * rustA * 1.4f - 0.25f);
                float edge = (float) Math.pow(Math.max(0, c), 3);
                float base = tone + edge * 0.08f;
                l.col[o] = lerp(base + warm, 0.24f, rust * 0.55f);
                l.col[o + 1] = lerp(base, 0.1f, rust * 0.6f);
                l.col[o + 2] = lerp(base - warm + 0.01f, 0.08f, rust * 0.65f);
                l.inner[iu * NV + iv] = Math.max(0, -c);
            }
        }
        l.a = a;
        l.r = rr;
        l.rt = rt;
        l.roll = (r.next() - 0.5f) * 0.35f;
        return l;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // The links of chain seed, enough for length blocks (forged once, kept).
    static List<Link> links(int seed, float length) {
        List<Link> list = CACHE.computeIfAbsent(seed, s -> new ArrayList<>());
        if (list.isEmpty() || list.get(list.size() - 1).s < length) {
            Rng r = new Rng(seed * 7919 + list.size() * 31 + 17);
            float s = list.isEmpty() ? 0 : list.get(list.size() - 1).s;
            Link prev = list.isEmpty() ? null : list.get(list.size() - 1);
            while (s < length + 0.5f) {
                Link l = forge(r);
                if (prev != null) {
                    s += prev.a + prev.r + l.a + l.r - prev.rt - l.rt - 0.004f * SCALE;
                }
                l.s = s;
                list.add(l);
                prev = l;
            }
        }
        if (CACHE.size() > 512) {
            CACHE.clear();
        }
        return list;
    }

    // How it lights: glow (the red inside the links, 0 to 1), wave (where a pulse is along it, 0 at its start, 1 at its
    // end; -1 none), surge (all of it flaring), time (for the flicker).
    record Light(float glow, float wave, float surge, float time) {
    }

    // Draws the links whose place along poly (camera-relative points) falls in [0, vis] blocks, offset by off (a chain
    // being pulled in moves its links along). Returns the length of the path.
    static float draw(VertexConsumer solid, Vector3f[] poly, float vis, float off, int seed, Light light) {
        int n = poly.length;
        if (n < 2 || vis <= 0.01f) {
            return 0;
        }
        float[] cum = new float[n];
        for (int k = 1; k < n; k++) {
            cum[k] = cum[k - 1] + poly[k].distance(poly[k - 1]);
        }
        float total = cum[n - 1];
        vis = Math.min(vis, total);
        Vector3f[] tan = new Vector3f[n];
        Vector3f[] nor = new Vector3f[n];
        for (int k = 0; k < n; k++) {
            Vector3f t = new Vector3f(poly[Math.min(k + 1, n - 1)]).sub(poly[Math.max(k - 1, 0)]);
            tan[k] = t.lengthSquared() < 1e-10f ? new Vector3f(0, 1, 0) : t.normalize();
        }
        Vector3f nn = Math.abs(tan[0].y) < 0.95f ? new Vector3f(tan[0]).cross(0, 1, 0).normalize() : new Vector3f(1, 0, 0);
        for (int k = 0; k < n; k++) {
            nn.sub(new Vector3f(tan[k]).mul(nn.dot(tan[k])));
            if (nn.lengthSquared() < 1e-8f) {
                nn.set(Math.abs(tan[k].y) < 0.95f ? new Vector3f(tan[k]).cross(0, 1, 0) : new Vector3f(1, 0, 0));
            }
            nn.normalize();
            nor[k] = new Vector3f(nn);
        }
        List<Link> links = links(seed, vis + off);
        int seg = 0;
        Vector3f p = new Vector3f(), t = new Vector3f(), nv = new Vector3f(), b = new Vector3f(), y = new Vector3f(), z = new Vector3f();
        float[] lit = new float[NU * NV * 3];
        float[] at = new float[NU * NV * 3];
        int count = links.size();
        for (int i = 0; i < count; i++) {
            Link l = links.get(i);
            float s = l.s - off;
            if (s < 0) {
                continue;
            }
            if (s > vis) {
                break;
            }
            while (seg < n - 2 && cum[seg + 1] < s) {
                seg++;
            }
            float f = sat((s - cum[seg]) / Math.max(1e-6f, cum[seg + 1] - cum[seg]));
            p.set(poly[seg]).lerp(poly[seg + 1], f);
            t.set(tan[seg]).lerp(tan[seg + 1], f).normalize();
            nv.set(nor[seg]).lerp(nor[seg + 1], f);
            nv.sub(new Vector3f(t).mul(nv.dot(t))).normalize();
            b.set(t).cross(nv);
            float ang = (i % 2) * (float) Math.PI / 2 + l.roll;
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            y.set(nv).mul(ca).add(new Vector3f(b).mul(sa));
            z.set(t).cross(y);
            float u = s / Math.max(0.01f, total);
            float wave = light.wave() < 0 ? 0 : (float) Math.exp(-Math.pow((u - light.wave()) * 16, 2));
            float flick = 0.85f + 0.15f * (float) Math.sin(light.time() * 0.35f + u * 40);
            for (int k = 0; k < NU * NV; k++) {
                int o = k * 3;
                float lx = l.pos[o], ly = l.pos[o + 1], lz = l.pos[o + 2];
                float px = p.x + t.x * lx + y.x * ly + z.x * lz;
                float py = p.y + t.y * lx + y.y * ly + z.y * lz;
                float pz = p.z + t.z * lx + y.z * ly + z.z * lz;
                at[o] = px;
                at[o + 1] = py;
                at[o + 2] = pz;
                float ax = l.nrm[o], ay = l.nrm[o + 1], az = l.nrm[o + 2];
                float nx = t.x * ax + y.x * ay + z.x * az;
                float ny = t.y * ax + y.y * ay + z.y * az;
                float nz = t.z * ax + y.z * ay + z.z * az;
                // The view runs from the point to the camera (the origin).
                float vl = (float) Math.sqrt(px * px + py * py + pz * pz) + 1e-5f;
                float vx = -px / vl, vy = -py / vl, vz = -pz / vl;
                float dif = Math.max(0, nx * SUN.x + ny * SUN.y + nz * SUN.z);
                float hemi = 0.5f + 0.5f * ny;
                HALF.set(SUN.x + vx, SUN.y + vy, SUN.z + vz).normalize();
                float spec = (float) Math.pow(Math.max(0, nx * HALF.x + ny * HALF.y + nz * HALF.z), 46);
                float fres = (float) Math.pow(1 - Math.max(0, nx * vx + ny * vy + nz * vz), 3);
                float shade = 0.32f + 0.25f * hemi + 0.8f * dif;
                float r = l.col[o] * shade + 0.9f * spec * 0.85f + 0.55f * fres * 0.35f;
                float g = l.col[o + 1] * shade + 0.86f * spec * 0.85f + 0.58f * fres * 0.35f;
                float bb = l.col[o + 2] * shade + 0.84f * spec * 0.85f + 0.64f * fres * 0.35f;
                float in = l.inner[k];
                float gl = Math.min(1.6f, light.glow() * in * in * flick + wave * (0.6f + 0.4f * in) * 1.3f + light.surge() * (0.55f + 0.45f * in));
                float hr = lerp(0.62f, 1f, sat(gl - 0.55f)), hg = lerp(0.02f, 0.42f, sat(gl - 0.55f)), hb = lerp(0.05f, 0.38f, sat(gl - 0.55f));
                float w = sat(gl - 1.15f);
                hr = lerp(hr, 1f, w);
                hg = lerp(hg, 0.9f, w);
                hb = lerp(hb, 0.86f, w);
                float m = sat(gl * 0.9f);
                float extra = Math.max(gl - 1, 0) * 0.4f;
                lit[o] = Math.min(1, lerp(r, hr, m) + hr * extra);
                lit[o + 1] = Math.min(1, lerp(g, hg, m) + hg * extra);
                lit[o + 2] = Math.min(1, lerp(bb, hb, m) + hb * extra);
            }
            for (int iu = 0; iu < NU; iu++) {
                int iu2 = (iu + 1) % NU;
                for (int iv = 0; iv < NV; iv++) {
                    int iv2 = (iv + 1) % NV;
                    vtx(solid, at, lit, iu * NV + iv);
                    vtx(solid, at, lit, iu2 * NV + iv);
                    vtx(solid, at, lit, iu2 * NV + iv2);
                    vtx(solid, at, lit, iu * NV + iv2);
                }
            }
        }
        return total;
    }

    private static void vtx(VertexConsumer out, float[] at, float[] lit, int k) {
        int o = k * 3;
        out.addVertex(at[o], at[o + 1], at[o + 2]).setColor(lit[o], lit[o + 1], lit[o + 2], 1f);
    }

    // The halo: one strip along the visible part, red, brighter where the pulse is.
    static void halo(Vector3f[] poly, float vis, Light light, float opacity) {
        int n = poly.length;
        if (n < 2 || vis <= 0.01f || opacity <= 0.01f) {
            return;
        }
        List<Vector3f> pts = new ArrayList<>();
        float run = 0;
        pts.add(poly[0]);
        for (int k = 1; k < n && run < vis; k++) {
            float d = poly[k].distance(poly[k - 1]);
            if (run + d > vis) {
                pts.add(new Vector3f(poly[k - 1]).lerp(poly[k], (vis - run) / d));
                run = vis;
                break;
            }
            run += d;
            pts.add(poly[k]);
        }
        if (pts.size() < 2) {
            return;
        }
        float g = light.glow() * 0.05f + light.surge() * 0.7f;
        Vector3f[] arr = pts.toArray(new Vector3f[0]);
        float[] w = new float[arr.length];
        float[] us = new float[arr.length];
        float acc = 0;
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                acc += arr[i].distance(arr[i - 1]);
            }
            us[i] = acc / Math.max(0.01f, run);
            float wave = light.wave() < 0 ? 0 : (float) Math.exp(-Math.pow((us[i] - light.wave()) * 12, 2)) * 0.45f;
            w[i] = 0.16f + 0.22f * Math.min(1, (g + wave) * 1.5f);
        }
        FxDraw.strip(arr, w, us, FxDraw.BEAM, 0.25f, MakimaFx.CHAIN_RED, Math.min(1, (g + 0.12f) * opacity), true);
        if (light.wave() >= 0) {
            float u = light.wave();
            int i = Math.min(arr.length - 1, Math.max(0, Math.round(u * (arr.length - 1))));
            FxDraw.billboard(arr[i], 0.5f, light.time() * 0.2f, FxDraw.SOFT, 0.6f, MakimaFx.CHAIN_HOT, 0.8f * opacity, true);
        }
    }
}
