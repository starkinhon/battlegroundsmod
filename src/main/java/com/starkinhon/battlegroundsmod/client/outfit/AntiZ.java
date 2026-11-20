// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Faces on the same plane flicker in game (z-fighting, Stark 2026-11-14: "faces sobrepostas piscando"). The outfits
// are built piece by piece in code, so this runs once on the baked triangles (11 floats per vertex, Geo.bake), at the
// rest pose: flat patches (neighbour triangles on one plane) are placed biggest first, and a patch that would lie on
// the same plane as one already placed, facing the same way and covering the same area, steps out along its normal
// by STEP until it is free. Same algorithm as tools/zfight/zfight.py (the checker the build runs on the model files).
final class AntiZ {
    static final float STEP = 0.015f, EPS = 0.006f;
    private static final float MIN_AREA = 1e-4f;

    private AntiZ() {
    }

    private static final class Patch {
        final float[] tris;
        final int[] idx; // triangle indices in tris
        final float nx, ny, nz; // normal in the checking space
        float area;

        Patch(float[] tris, int[] idx, float nx, float ny, float nz) {
            this.tris = tris;
            this.idx = idx;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }

    // One placed triangle: its corners in the checking space, the plane offset and the bounds.
    private record Tri(float[] p, float d, float[] lo, float[] hi, float nx, float ny, float nz) {
    }

    // Rest pose of each bone, the same mapping as OutfitLayer.emitStatic + the PlayerModel pivots (y down, -z forward).
    private static float[] place(Rig.Bone bone) {
        return switch (bone) {
            case ARM_R -> new float[] {-6, 2, 0, 0};
            case ARM_L -> new float[] {6, 2, 0, 0};
            case LEG_R -> new float[] {-2, 12, 0, 0};
            case LEG_L -> new float[] {2, 12, 0, 0};
            case BODY -> new float[] {0, 0, 0, 24};
            default -> new float[] {0, 0, 0, 0};
        };
    }

    private static void world(float[] t, int v, float[] pl, float[] out) {
        int o = v * 11;
        out[0] = t[o] + pl[0];
        out[1] = pl[3] - t[o + 1] + pl[1];
        out[2] = -t[o + 2] + pl[2];
    }

    static void fix(List<Rig.Bone> bones, List<float[]> pieces) {
        List<Patch> patches = new ArrayList<>();
        List<float[]> places = new ArrayList<>();
        Map<Patch, float[]> placeOf = new HashMap<>();
        for (int k = 0; k < pieces.size(); k++) {
            float[] pl = place(bones.get(k));
            places.add(pl);
            for (Patch p : patches(pieces.get(k), pl)) {
                patches.add(p);
                placeOf.put(p, pl);
            }
        }
        patches.sort((a, b) -> Float.compare(b.area, a.area));
        Map<Long, List<Tri>> placed = new HashMap<>();
        for (Patch p : patches) {
            float[] pl = placeOf.get(p);
            int k = 0;
            List<Tri> tris = tris(p, pl, 0);
            while (k < 400 && hits(tris, placed)) {
                k++;
                tris = tris(p, pl, k * STEP);
            }
            if (k > 0) {
                // the checking space flips y and z (OutfitLayer: y0 - y, -z), so the step goes back flipped
                float s = k * STEP;
                for (int i : p.idx) {
                    for (int v = 0; v < 3; v++) {
                        int o = (i * 3 + v) * 11;
                        p.tris[o] += p.nx * s;
                        p.tris[o + 1] -= p.ny * s;
                        p.tris[o + 2] -= p.nz * s;
                    }
                }
            }
            for (Tri t : tris) {
                placed.computeIfAbsent(key(t.nx, t.ny, t.nz), x -> new ArrayList<>()).add(t);
            }
        }
    }

    private static long key(float nx, float ny, float nz) {
        long a = Math.round(nx * 1000) + 1000, b = Math.round(ny * 1000) + 1000, c = Math.round(nz * 1000) + 1000;
        return (a << 22) | (b << 11) | c;
    }

    // Neighbour triangles (sharing an edge) on the same plane of one piece move together, so a quad never cracks.
    private static List<Patch> patches(float[] t, float[] pl) {
        int n = t.length / 33;
        int[] parent = new int[n];
        float[][] norm = new float[n][];
        float[] dist = new float[n];
        Map<Long, List<Integer>> edges = new HashMap<>();
        Map<Long, Integer> ids = new HashMap<>();
        float[][] c = {new float[3], new float[3], new float[3]};
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            for (int v = 0; v < 3; v++) {
                world(t, i * 3 + v, pl, c[v]);
            }
            float[] nn = normal(c[0], c[1], c[2]);
            if (nn == null) {
                continue;
            }
            norm[i] = nn;
            dist[i] = nn[0] * c[0][0] + nn[1] * c[0][1] + nn[2] * c[0][2];
            int[] keys = new int[3];
            for (int v = 0; v < 3; v++) {
                long vk = (Math.round(c[v][0] * 1e4) + (1L << 20) << 42) ^ (Math.round(c[v][1] * 1e4) + (1L << 20) << 21)
                        ^ (Math.round(c[v][2] * 1e4) + (1L << 20));
                keys[v] = ids.computeIfAbsent(vk, x -> ids.size());
            }
            for (int e = 0; e < 3; e++) {
                int a = keys[e], b = keys[(e + 1) % 3];
                long ek = ((long) Math.min(a, b) << 32) | Math.max(a, b);
                edges.computeIfAbsent(ek, x -> new ArrayList<>()).add(i);
            }
        }
        for (List<Integer> lst : edges.values()) {
            int i = lst.get(0);
            for (int q = 1; q < lst.size(); q++) {
                int j = lst.get(q);
                if (norm[i] != null && norm[j] != null && dot(norm[i], norm[j]) > 0.99999f && Math.abs(dist[i] - dist[j]) < 1e-4f) {
                    parent[find(parent, j)] = find(parent, i);
                }
            }
        }
        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (norm[i] != null) {
                groups.computeIfAbsent(find(parent, i), x -> new ArrayList<>()).add(i);
            }
        }
        // A cap fanned from an off-centre point folds over itself: a triangle lying on another of its own patch
        // leaves the patch, so it can step out on its own.
        List<List<Integer>> split = new ArrayList<>();
        for (List<Integer> g : groups.values()) {
            if (g.size() < 3 || g.size() > 64 && !folds(t, g, pl, norm[g.get(0)])) {
                split.add(g);
                continue;
            }
            List<Integer> keep = new ArrayList<>();
            for (int i : g) {
                boolean folded = false;
                Tri ti = single(t, i, pl, norm[i]);
                for (int j : keep) {
                    if (touches(ti, single(t, j, pl, norm[j]))) {
                        folded = true;
                        break;
                    }
                }
                if (folded) {
                    split.add(new ArrayList<>(List.of(i)));
                } else {
                    keep.add(i);
                }
            }
            split.add(keep);
        }
        List<Patch> out = new ArrayList<>();
        for (List<Integer> g : split) {
            float[] nn = norm[g.get(0)];
            int[] idx = g.stream().mapToInt(Integer::intValue).toArray();
            Patch p = new Patch(t, idx, nn[0], nn[1], nn[2]);
            for (int i : idx) {
                for (int v = 0; v < 3; v++) {
                    world(t, i * 3 + v, pl, c[v]);
                }
                p.area += len(cross(sub(c[1], c[0]), sub(c[2], c[0])));
            }
            out.add(p);
        }
        return out;
    }

    // A flat patch whose triangles add up to more than its convex hull has triangles lying on each other (cheap
    // test, so the triangle by triangle check only runs on those; small patches are always checked one by one).
    private static boolean folds(float[] t, List<Integer> g, float[] pl, float[] n) {
        float[] a = Math.abs(n[0]) < 0.9f ? new float[] {1, 0, 0} : new float[] {0, 1, 0};
        float[] u = cross(n, a);
        float ul = len(u);
        u = new float[] {u[0] / ul, u[1] / ul, u[2] / ul};
        float[] v = cross(n, u);
        float[][] pts = new float[g.size() * 3][];
        float[] c = new float[3];
        double sum = 0;
        int k = 0;
        for (int i : g) {
            float[][] tri = new float[3][];
            for (int q = 0; q < 3; q++) {
                world(t, i * 3 + q, pl, c);
                tri[q] = new float[] {dot(c, u), dot(c, v)};
                pts[k++] = tri[q];
            }
            sum += Math.abs((tri[1][0] - tri[0][0]) * (tri[2][1] - tri[0][1]) - (tri[2][0] - tri[0][0]) * (tri[1][1] - tri[0][1])) / 2;
        }
        java.util.Arrays.sort(pts, (p, q) -> p[0] != q[0] ? Float.compare(p[0], q[0]) : Float.compare(p[1], q[1]));
        float[][] hull = new float[pts.length * 2][];
        int h = 0;
        for (int pass = 0; pass < 2; pass++) {
            int start = h;
            for (int i = 0; i < pts.length; i++) {
                float[] p = pts[pass == 0 ? i : pts.length - 1 - i];
                while (h >= start + 2 && turn(hull[h - 2], hull[h - 1], p) <= 0) {
                    h--;
                }
                hull[h++] = p;
            }
            h--;
        }
        double area = 0;
        for (int i = 0; i < h; i++) {
            float[] p = hull[i], q = hull[(i + 1) % h];
            area += p[0] * q[1] - q[0] * p[1];
        }
        return sum > Math.abs(area) / 2 + MIN_AREA;
    }

    private static float turn(float[] o, float[] a, float[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static List<Tri> tris(Patch p, float[] pl, float s) {
        List<Tri> out = new ArrayList<>(p.idx.length);
        for (int i : p.idx) {
            float[] pts = new float[9];
            float[] c = new float[3];
            float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int v = 0; v < 3; v++) {
                world(p.tris, i * 3 + v, pl, c);
                c[0] += p.nx * s;
                c[1] += p.ny * s;
                c[2] += p.nz * s;
                for (int a = 0; a < 3; a++) {
                    pts[v * 3 + a] = c[a];
                    lo[a] = Math.min(lo[a], c[a]);
                    hi[a] = Math.max(hi[a], c[a]);
                }
            }
            float d = p.nx * pts[0] + p.ny * pts[1] + p.nz * pts[2];
            out.add(new Tri(pts, d, lo, hi, p.nx, p.ny, p.nz));
        }
        return out;
    }

    private static boolean hits(List<Tri> tris, Map<Long, List<Tri>> placed) {
        for (Tri t : tris) {
            List<Tri> lst = placed.get(key(t.nx, t.ny, t.nz));
            if (lst == null) {
                continue;
            }
            for (Tri b : lst) {
                if (touches(t, b)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Same plane, same side, an area in common.
    private static boolean touches(Tri t, Tri b) {
        if (Math.abs(t.d - b.d) > EPS || t.nx * b.nx + t.ny * b.ny + t.nz * b.nz < 0.99999f) {
            return false;
        }
        for (int a = 0; a < 3; a++) {
            if (t.lo[a] > b.hi[a] + EPS || b.lo[a] > t.hi[a] + EPS) {
                return false;
            }
        }
        return overlap(t, b) > MIN_AREA;
    }

    private static Tri single(float[] t, int i, float[] pl, float[] n) {
        float[] pts = new float[9];
        float[] c = new float[3];
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int v = 0; v < 3; v++) {
            world(t, i * 3 + v, pl, c);
            for (int a = 0; a < 3; a++) {
                pts[v * 3 + a] = c[a];
                lo[a] = Math.min(lo[a], c[a]);
                hi[a] = Math.max(hi[a], c[a]);
            }
        }
        return new Tri(pts, n[0] * pts[0] + n[1] * pts[1] + n[2] * pts[2], lo, hi, n[0], n[1], n[2]);
    }

    // Area in common of two triangles on the same plane: both into the plane's 2D basis, one clipped by the other.
    private static float overlap(Tri a, Tri b) {
        float[] n = {a.nx, a.ny, a.nz};
        float[] t = Math.abs(n[0]) < 0.9f ? new float[] {1, 0, 0} : new float[] {0, 1, 0};
        float[] u = cross(n, t);
        float ul = len(u);
        u = new float[] {u[0] / ul, u[1] / ul, u[2] / ul};
        float[] v = cross(n, u);
        List<float[]> pa = flat(a.p, u, v), pb = flat(b.p, u, v);
        List<float[]> poly = clip(pa, pb);
        return poly.size() >= 3 ? Math.abs(area(poly)) : 0;
    }

    private static List<float[]> flat(float[] p, float[] u, float[] v) {
        List<float[]> out = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            float x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
            out.add(new float[] {x * u[0] + y * u[1] + z * u[2], x * v[0] + y * v[1] + z * v[2]});
        }
        if (area(out) < 0) {
            java.util.Collections.reverse(out);
        }
        return out;
    }

    private static float area(List<float[]> poly) {
        float s = 0;
        for (int i = 0; i < poly.size(); i++) {
            float[] p = poly.get(i), q = poly.get((i + 1) % poly.size());
            s += p[0] * q[1] - q[0] * p[1];
        }
        return s / 2;
    }

    // Sutherland-Hodgman: the subject cut by a counter-clockwise convex clipper.
    private static List<float[]> clip(List<float[]> subject, List<float[]> clipper) {
        List<float[]> out = subject;
        for (int i = 0; i < clipper.size() && !out.isEmpty(); i++) {
            float[] a = clipper.get(i), b = clipper.get((i + 1) % clipper.size());
            float ex = b[0] - a[0], ey = b[1] - a[1];
            List<float[]> in = out;
            out = new ArrayList<>();
            for (int j = 0; j < in.size(); j++) {
                float[] p = in.get(j), q = in.get((j + 1) % in.size());
                float sp = ex * (p[1] - a[1]) - ey * (p[0] - a[0]);
                float sq = ex * (q[1] - a[1]) - ey * (q[0] - a[0]);
                if (sp >= 0) {
                    out.add(p);
                }
                if ((sp >= 0) != (sq >= 0)) {
                    float k = sp / (sp - sq);
                    out.add(new float[] {p[0] + k * (q[0] - p[0]), p[1] + k * (q[1] - p[1])});
                }
            }
        }
        return out;
    }

    private static float[] normal(float[] a, float[] b, float[] c) {
        float[] n = cross(sub(b, a), sub(c, a));
        float l = len(n);
        return l < 1e-9f ? null : new float[] {n[0] / l, n[1] / l, n[2] / l};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float len(float[] a) {
        return (float) Math.sqrt(dot(a, a));
    }
}
