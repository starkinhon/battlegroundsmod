package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.JujutsuBlocks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

// What Shibuya's ambience reads from the world around the player (client/fx/ShibuyaAirFx; the approved preview
// forja/jujutsu-shibuya-ambiente amb.js findSigns / buildField / findVents): the lit signs joined into panels, the lamps,
// the manholes and the roof vents, the light field the neon paints on the street, and where the metro station is.
// Chunks are read once each (a few per tick, skipping every section that has none of these blocks) and kept until they
// leave the area; the panels and the field are built again only when what was read changes or the player walks away.
final class ShibuyaScan {
    // block codes
    static final int SIGN = 1, GLYPH = 9, SHOP = 10, LAMP = 11, AVIATION = 12, TRAFFIC_R = 13, TRAFFIC_G = 14, CROWN = 15,
            MANHOLE = 16, RAIL = 17, STEP = 18, CURSE = 19, ROOF = 20;
    static final int R = 96;
    static final int FS = 2;
    static final int FN = R * 2 / FS;
    // the preview's SIGNS colors (letreiro_vermelho .. letreiro_branco)
    static final float[][] SIGN_COLORS = {c(0xe8423c), c(0xff8a1e), c(0xffd23a), c(0x38d16a), c(0x2fd6e6), c(0x3a6cff), c(0xff4fb0), c(0xf4f1ea)};
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static final class Panel {
        int d, nx, nz, plane, u0 = Integer.MAX_VALUE, u1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE, n;
        final int[] cnt = new int[10];
        boolean shop;
        float[] col;
        float in, f = 1, burst, off, next, ph, rate;
        double cx, cy, cz;
        int mode;
    }

    static final class Lamp {
        final double x, y, z;
        final int k;
        final double ph;

        Lamp(double x, double y, double z, int k, double ph) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.k = k;
            this.ph = ph;
        }
    }

    static final class Vent {
        final int kind; // 0 manhole, 1 roof, 2 curse
        final double x, y, z, power, ph, rate;
        double acc;

        Vent(int kind, double x, double y, double z, double power, double ph, double rate) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.power = power;
            this.ph = ph;
            this.rate = rate;
        }
    }

    private record Data(Map<Long, Integer> faces, List<Lamp> lamps, List<int[]> manholes, List<int[]> rails, List<int[]> steps,
            List<int[]> curses) {
    }

    private static Map<Block, Integer> codes;
    private static final Map<Long, Data> CHUNKS = new HashMap<>();
    private static ClientLevel lastLevel;
    private static boolean dirty;
    private static int builtX = Integer.MIN_VALUE, builtZ;
    private static long builtAt;

    static List<Panel> panels = new ArrayList<>();
    static List<Lamp> lamps = new ArrayList<>();
    static List<Vent> vents = new ArrayList<>();
    // the light field: per cell the 3 strongest lights (panel index, or 100000 + lamp index) and their weights, the cell's
    // street height (or MIN_VALUE when it isn't street) and the street's direction (1 runs along x, 2 along z, 3 crossing)
    static int fx0, fz0;
    static final int[] FID = new int[FN * FN * 3];
    static final float[] FWT = new float[FN * FN * 3];
    static final int[] FY = new int[FN * FN];
    static final byte[] FAX = new byte[FN * FN];
    static boolean field;
    // the metro station seen (from the rails and the platform stairs): its middle x, the avenue's middle z, the city's y
    static boolean station;
    static int stOx, stC, stCity;

    private ShibuyaScan() {
    }

    private static float[] c(int rgb) {
        return new float[] {(rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f};
    }

    // integer hash (the preview's ih: no Math.sin, it cost 5 ms a tick in the Colônia)
    static double ih(int x, int z, int s) {
        int h = x * 374761393 + z * 668265263 + s * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    private static Map<Block, Integer> codes() {
        if (codes == null) {
            Map<Block, Integer> m = new IdentityHashMap<>();
            for (int i = 0; i < 8; i++) {
                m.put(JujutsuBlocks.SIGNS.get(i).get(), SIGN + i);
            }
            m.put(JujutsuBlocks.SIGN_GLYPH.get(), GLYPH);
            m.put(JujutsuBlocks.SHOP_WINDOW.get(), SHOP);
            m.put(JujutsuBlocks.STREET_LAMP.get(), LAMP);
            m.put(JujutsuBlocks.AVIATION_LIGHT.get(), AVIATION);
            m.put(JujutsuBlocks.TRAFFIC_RED.get(), TRAFFIC_R);
            m.put(JujutsuBlocks.TRAFFIC_GREEN.get(), TRAFFIC_G);
            m.put(JujutsuBlocks.LED_CROWN.get(), CROWN);
            m.put(JujutsuBlocks.MANHOLE.get(), MANHOLE);
            m.put(JujutsuBlocks.RAIL.get(), RAIL);
            m.put(JujutsuBlocks.METRO_STEP.get(), STEP);
            m.put(JujutsuBlocks.CURSE_WINDOW.get(), CURSE);
            codes = m;
        }
        return codes;
    }

    private static int code(BlockState s) {
        Integer k = codes().get(s.getBlock());
        return k == null ? 0 : k;
    }

    static boolean street(BlockState s) {
        Block b = s.getBlock();
        return b == JujutsuBlocks.SHIBUYA_ASPHALT.get() || b == JujutsuBlocks.WHITE_LINE.get() || b == JujutsuBlocks.YELLOW_LINE.get()
                || b == JujutsuBlocks.SHIBUYA_SIDEWALK.get() || b == JujutsuBlocks.SHIBUYA_SIDEWALK_DARK.get()
                || b == JujutsuBlocks.TACTILE_PAVING.get() || b == JujutsuBlocks.CURB.get() || b == JujutsuBlocks.MANHOLE.get()
                || b == JujutsuBlocks.ALLEY_GROUND.get();
    }

    // a road block (asphalt, its paint, a manhole) under (x, y, z): where a car can drive
    static boolean road(ClientLevel level, int x, int y, int z) {
        Block b = level.getBlockState(new BlockPos(x, y - 1, z)).getBlock();
        return b == JujutsuBlocks.SHIBUYA_ASPHALT.get() || b == JujutsuBlocks.WHITE_LINE.get() || b == JujutsuBlocks.YELLOW_LINE.get()
                || b == JujutsuBlocks.MANHOLE.get();
    }

    static void clear() {
        CHUNKS.clear();
        panels = new ArrayList<>();
        lamps = new ArrayList<>();
        vents = new ArrayList<>();
        field = false;
        station = false;
        builtX = Integer.MIN_VALUE;
        lastLevel = null;
    }

    // ---------------------------------------------------------------- reading

    // Called each tick while Shibuya's ambience is on: reads up to 3 new chunks, forgets the far ones, and builds the
    // panels and the field again when something changed (at most once a second) or the player walked 24 blocks.
    static void tick(ClientLevel level, double px, double pz) {
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
        int pcx = Mth.floor(px) >> 4, pcz = Mth.floor(pz) >> 4, rc = (R >> 4) + 1;
        CHUNKS.keySet().removeIf(k -> {
            int cx = (int) (k >> 32), cz = (int) (long) k;
            boolean far = Math.abs(cx - pcx) > rc + 1 || Math.abs(cz - pcz) > rc + 1;
            if (far) {
                dirty = true;
            }
            return far;
        });
        int budget = 2;
        for (int ring = 0; ring <= rc && budget > 0; ring++) {
            for (int dx = -ring; dx <= ring && budget > 0; dx++) {
                for (int dz = -ring; dz <= ring && budget > 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int cx = pcx + dx, cz = pcz + dz;
                    long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                    if (CHUNKS.containsKey(key)) {
                        continue;
                    }
                    LevelChunk ch = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (ch == null) {
                        continue;
                    }
                    CHUNKS.put(key, read(level, ch, cx, cz));
                    dirty = true;
                    budget--;
                }
            }
        }
        long now = level.getGameTime();
        boolean moved = Math.abs(Mth.floor(px) - builtX) > 24 || Math.abs(Mth.floor(pz) - builtZ) > 24;
        if (moved || dirty && now - builtAt >= 20) {
            build(level, Mth.floor(px), Mth.floor(pz));
            builtAt = now;
            dirty = false;
        }
    }

    private static Data read(ClientLevel level, LevelChunk ch, int cx, int cz) {
        Map<Long, Integer> faces = new HashMap<>();
        List<Lamp> ls = new ArrayList<>();
        List<int[]> mh = new ArrayList<>(), rails = new ArrayList<>(), steps = new ArrayList<>(), curses = new ArrayList<>();
        LevelChunkSection[] secs = ch.getSections();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Map<Block, Integer> cs = codes();
        for (int si = 0; si < secs.length; si++) {
            LevelChunkSection sec = secs[si];
            if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(s -> cs.containsKey(s.getBlock()))) {
                continue;
            }
            int sy = ch.getSectionYFromSectionIndex(si) << 4;
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int k = code(sec.getBlockState(lx, ly, lz));
                        if (k == 0) {
                            continue;
                        }
                        int x = (cx << 4) + lx, y = sy + ly, z = (cz << 4) + lz;
                        switch (k) {
                            case LAMP -> ls.add(new Lamp(x + 0.5, y + 0.5, z + 0.5, 0, 0));
                            case AVIATION -> ls.add(new Lamp(x + 0.5, y + 0.3, z + 0.5, 1, ih(x, y, z)));
                            case TRAFFIC_R -> ls.add(new Lamp(x + 0.5, y + 0.5, z + 0.5, 2, 0));
                            case TRAFFIC_G -> ls.add(new Lamp(x + 0.5, y + 0.5, z + 0.5, 3, 0));
                            case CROWN -> {
                                if (ih(x, y * 7, z) < 0.18) {
                                    ls.add(new Lamp(x + 0.5, y + 0.5, z + 0.5, 4, 0));
                                }
                            }
                            case MANHOLE -> mh.add(new int[] {x, y, z});
                            case RAIL -> {
                                if (((x & 7) == 0)) {
                                    rails.add(new int[] {x, y, z});
                                }
                            }
                            case STEP -> steps.add(new int[] {x, y, z});
                            case CURSE -> curses.add(new int[] {x, y, z});
                            default -> {
                                // a sign (8 colors, the letter) or a shop window: each face open to the air
                                int cls = k == SHOP ? 1 : 0, sIdx = k == SHOP ? 9 : k == GLYPH ? 8 : k - SIGN;
                                for (int d = 0; d < 4; d++) {
                                    m.set(x + DIRS[d][0], y, z + DIRS[d][1]);
                                    if (level.getBlockState(m).canOcclude()) {
                                        continue;
                                    }
                                    int plane = DIRS[d][0] != 0 ? x + (DIRS[d][0] > 0 ? 1 : 0) : z + (DIRS[d][1] > 0 ? 1 : 0);
                                    int u = DIRS[d][0] != 0 ? z : x;
                                    faces.put(faceKey(d, plane, u, y), cls << 4 | sIdx);
                                }
                            }
                        }
                    }
                }
            }
        }
        // the cursed buildings breathe miasma from the top, and some roofs have an air-conditioning vent
        List<int[]> top = new ArrayList<>();
        if (!curses.isEmpty()) {
            int[] hi = curses.get(0);
            for (int[] p : curses) {
                if (p[1] > hi[1]) {
                    hi = p;
                }
            }
            top.add(new int[] {hi[0], level.getHeight(Heightmap.Types.MOTION_BLOCKING, hi[0], hi[2]), hi[2]});
        }
        return new Data(faces, ls, mh, rails, steps, top);
    }

    private static long faceKey(int d, int plane, int u, int y) {
        return ((long) d << 56) | ((long) (plane & 0x3FFFFF) << 34) | ((long) (u & 0x3FFFFF) << 12) | (y + 2048 & 0xFFF);
    }

    private static int sx22(long v) {
        int i = (int) (v & 0x3FFFFF);
        return (i << 10) >> 10;
    }

    // ---------------------------------------------------------------- panels, vents, field, station

    private static void build(ClientLevel level, int px, int pz) {
        builtX = px;
        builtZ = pz;
        Map<Long, Integer> faces = new HashMap<>();
        List<Lamp> ls = new ArrayList<>();
        List<Vent> vs = new ArrayList<>();
        int railMinZ = Integer.MAX_VALUE, railMaxZ = Integer.MIN_VALUE, railY = 0, stepMin = Integer.MAX_VALUE, stepMax = Integer.MIN_VALUE;
        for (Map.Entry<Long, Data> e : CHUNKS.entrySet()) {
            Data dt = e.getValue();
            faces.putAll(dt.faces());
            ls.addAll(dt.lamps());
            for (int[] p : dt.manholes()) {
                double h = ih(p[0], p[2], 35);
                if (h < 0.5) {
                    vs.add(new Vent(0, p[0] + 0.5, p[1] + 1.0, p[2] + 0.5, 0.6 + h, h * 40, 13 + h * 10));
                }
            }
            for (int[] p : dt.curses()) {
                for (int k = 0; k < 3; k++) {
                    vs.add(new Vent(2, p[0] + 0.5 + (ih(p[0], k, 51) - 0.5) * 4, p[1] + 1, p[2] + 0.5 + (ih(p[2], k, 52) - 0.5) * 4, 0.9, ih(k, p[0], 53) * 9, 2.5));
                }
            }
            int cx = (int) (e.getKey() >> 32), cz = (int) (long) e.getKey();
            if (ih(cx, cz, 41) < 0.3) {
                int x = (cx << 4) + 4 + (int) (ih(cx, cz, 42) * 8), z = (cz << 4) + 4 + (int) (ih(cz, cx, 43) * 8);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (level.getBlockState(new BlockPos(x, y - 1, z)).is(JujutsuBlocks.BUILDING_ROOF.get())) {
                    vs.add(new Vent(1, x + 0.5, y + 0.6, z + 0.5, 0.6, ih(x, z, 44) * 9, 4));
                }
            }
            for (int[] r : dt.rails()) {
                if (Math.abs(r[0] - px) < 140) {
                    railMinZ = Math.min(railMinZ, r[2]);
                    railMaxZ = Math.max(railMaxZ, r[2]);
                    railY = r[1];
                }
            }
            for (int[] s : dt.steps()) {
                stepMin = Math.min(stepMin, s[0]);
                stepMax = Math.max(stepMax, s[0]);
            }
        }
        // the station: the rails run at c - 7, c - 5, c + 4, c + 6 (ShibuyaGen.metro), the platform stairs from ox - 11 to
        // ox + 11 (the entrance stairs, at 22 to 29 out, are filtered by z below)
        station = false;
        if (railMaxZ - railMinZ == 13) {
            int c = railMinZ + 7;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (Data dt : CHUNKS.values()) {
                for (int[] s : dt.steps()) {
                    if ((s[2] == c - 1 || s[2] == c) && s[1] < railY + 9) {
                        lo = Math.min(lo, s[0]);
                        hi = Math.max(hi, s[0]);
                    }
                }
            }
            if (hi - lo == 22) {
                station = true;
                stOx = (lo + hi) / 2;
                stC = c;
                stCity = railY + 16;
            }
        }
        // panels: flood the faces of one direction and plane into continuous pieces (a band round a building is 4 panels)
        List<Panel> ps = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        ArrayDeque<Long> st = new ArrayDeque<>();
        for (Map.Entry<Long, Integer> e0 : faces.entrySet()) {
            long k0 = e0.getKey();
            if (!seen.add(k0)) {
                continue;
            }
            int d = (int) (k0 >>> 56), plane = sx22(k0 >>> 34), cls = e0.getValue() >> 4;
            Panel g = new Panel();
            g.d = d;
            g.nx = DIRS[d][0];
            g.nz = DIRS[d][1];
            g.plane = plane;
            g.shop = cls == 1;
            st.clear();
            st.push(k0);
            while (!st.isEmpty()) {
                long k = st.pop();
                int u = sx22(k >>> 12), y = (int) (k & 0xFFF) - 2048, f = faces.get(k);
                g.n++;
                g.cnt[f & 15]++;
                g.u0 = Math.min(g.u0, u);
                g.u1 = Math.max(g.u1, u);
                g.y0 = Math.min(g.y0, y);
                g.y1 = Math.max(g.y1, y);
                int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] q : nb) {
                    long kk = faceKey(d, plane, u + q[0], y + q[1]);
                    Integer ff = faces.get(kk);
                    if (ff != null && ff >> 4 == cls && seen.add(kk)) {
                        st.push(kk);
                    }
                }
            }
            if (g.n < 2) {
                continue;
            }
            if (g.shop) {
                g.col = c(0xffd59a);
            } else {
                int best = 0, sum = 0;
                for (int i = 0; i < 8; i++) {
                    sum += g.cnt[i];
                    if (g.cnt[i] > g.cnt[best]) {
                        best = i;
                    }
                }
                float gf = g.cnt[8] / (float) g.n;
                float[] sc = SIGN_COLORS[best];
                g.col = sum == 0 ? new float[] {1, 1, 1} : new float[] {sc[0] + (1 - sc[0]) * gf * 0.45f, sc[1] + (1 - sc[1]) * gf * 0.45f, sc[2] + (1 - sc[2]) * gf * 0.45f};
            }
            g.in = g.shop ? 0.22f : 0.8f + (float) Math.min(1.4, Math.sqrt(g.n) * 0.12);
            double uc = (g.u0 + g.u1 + 1) / 2.0, yc = (g.y0 + g.y1 + 1) / 2.0;
            g.cx = g.nx != 0 ? plane : uc;
            g.cz = g.nx != 0 ? uc : plane;
            g.cy = yc;
            // how it flickers: 68% steady (only the hum), 14% breathing, 10% a sweep running, 8% a bad contact
            double h = ih((int) (g.cx * 7), (int) (g.cz * 13) + g.y0, 77);
            g.mode = g.shop ? 0 : h < 0.68 ? 0 : h < 0.82 ? 1 : h < 0.92 ? 2 : 3;
            g.ph = (float) (ih((int) g.cx, (int) g.cz, 91) * Math.PI * 2);
            g.rate = 1.1f + (float) ih((int) g.cz, (int) g.cx, 92) * 1.6f;
            g.next = 2 + (float) ih(g.u0, g.y0, 93) * 8;
            ps.add(g);
        }
        // keep the flicker state of the panels that stay
        Map<Long, Panel> old = new HashMap<>();
        for (Panel p : panels) {
            old.put(faceKey(p.d, p.plane, p.u0, p.y0), p);
        }
        for (Panel p : ps) {
            Panel o = old.get(faceKey(p.d, p.plane, p.u0, p.y0));
            if (o != null) {
                p.f = o.f;
                p.burst = o.burst;
                p.off = o.off;
                p.next = o.next;
            }
        }
        // keep the vents' rhythm too
        Map<Long, Vent> oldV = new HashMap<>();
        for (Vent v : vents) {
            oldV.put(((long) Mth.floor(v.x * 4) << 32) ^ Mth.floor(v.z * 4), v);
        }
        for (Vent v : vs) {
            Vent o = oldV.get(((long) Mth.floor(v.x * 4) << 32) ^ Mth.floor(v.z * 4));
            if (o != null) {
                v.acc = o.acc;
            }
        }
        panels = ps;
        lamps = ls;
        vents = vs;
        buildField(level, px, pz);
    }

    // The light the neon paints on the street: each 2-block cell of street keeps the 3 strongest lights reaching it (sign,
    // shop window, lamp) and their weight; the color comes out each frame with each light's flicker.
    private static void buildField(ClientLevel level, int px, int pz) {
        fx0 = Math.floorDiv(px - R, FS) * FS;
        fz0 = Math.floorDiv(pz - R, FS) * FS;
        java.util.Arrays.fill(FID, -1);
        java.util.Arrays.fill(FWT, 0);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < FN; i++) {
            for (int j = 0; j < FN; j++) {
                int x = fx0 + i * FS + 1, z = fz0 + j * FS + 1;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                m.set(x, y - 1, z);
                FY[i * FN + j] = street(level.getBlockState(m)) ? y : Integer.MIN_VALUE;
            }
        }
        // the street's direction: the run of street along x against the run along z
        for (int i = 0; i < FN; i++) {
            for (int j = 0; j < FN; j++) {
                if (FY[i * FN + j] == Integer.MIN_VALUE) {
                    FAX[i * FN + j] = 0;
                    continue;
                }
                int ax = 0, az = 0;
                for (int k = -4; k <= 4; k++) {
                    if (i + k >= 0 && i + k < FN && FY[(i + k) * FN + j] != Integer.MIN_VALUE) {
                        ax++;
                    }
                    if (j + k >= 0 && j + k < FN && FY[i * FN + j + k] != Integer.MIN_VALUE) {
                        az++;
                    }
                }
                FAX[i * FN + j] = (byte) (ax >= 8 && az >= 8 ? 3 : ax >= az ? 1 : 2);
            }
        }
        for (int k = 0; k < panels.size() + lamps.size(); k++) {
            double sx, sz, sh, rr, in, nx = 0, nz = 0;
            int id;
            if (k < panels.size()) {
                Panel g = panels.get(k);
                sx = g.cx + g.nx * 1.2;
                sz = g.cz + g.nz * 1.2;
                rr = Math.min(26, 9 + Math.sqrt(g.n) * 1.8);
                in = g.in;
                nx = g.nx;
                nz = g.nz;
                id = k;
                int ci = Mth.clamp(Mth.floor((sx - fx0) / FS), 0, FN - 1), cj = Mth.clamp(Mth.floor((sz - fz0) / FS), 0, FN - 1);
                int gy = FY[ci * FN + cj];
                sh = g.cy - (gy == Integer.MIN_VALUE ? g.y0 : gy);
            } else {
                Lamp l = lamps.get(k - panels.size());
                if (l.k != 0) {
                    continue;
                }
                sx = l.x;
                sz = l.z;
                rr = 10;
                in = 0.6;
                id = 100000 + k - panels.size();
                int ci = Mth.clamp(Mth.floor((sx - fx0) / FS), 0, FN - 1), cj = Mth.clamp(Mth.floor((sz - fz0) / FS), 0, FN - 1);
                int gy = FY[ci * FN + cj];
                sh = l.y - (gy == Integer.MIN_VALUE ? l.y - 6 : gy);
            }
            double hk = 1 / (1 + Math.pow(Math.max(0, sh) / 20, 2));
            int i0 = Math.max(0, Mth.floor((sx - rr - fx0) / FS)), i1 = Math.min(FN - 1, Mth.floor((sx + rr - fx0) / FS));
            int j0 = Math.max(0, Mth.floor((sz - rr - fz0) / FS)), j1 = Math.min(FN - 1, Mth.floor((sz + rr - fz0) / FS));
            for (int i = i0; i <= i1; i++) {
                for (int j = j0; j <= j1; j++) {
                    int gy = FY[i * FN + j];
                    // only street, and only the street the light is over (not the one 30 blocks below or above)
                    if (gy == Integer.MIN_VALUE || (k < panels.size() ? panels.get(k).y0 < gy - 2 : false)) {
                        continue;
                    }
                    double cx = fx0 + (i + 0.5) * FS, cz = fz0 + (j + 0.5) * FS, dx = cx - sx, dz = cz - sz, d = Math.sqrt(dx * dx + dz * dz);
                    if (d > rr) {
                        continue;
                    }
                    double w = in * hk * (1 - d / rr) * (1 - d / rr);
                    if (nx != 0 || nz != 0) {
                        double dot = (dx * nx + dz * nz) / Math.max(0.5, d);
                        w *= Mth.clamp(0.2 + dot, 0, 1);
                    }
                    if (w < 0.01) {
                        continue;
                    }
                    int o = (i * FN + j) * 3, mi = 0;
                    for (int q = 1; q < 3; q++) {
                        if (FWT[o + q] < FWT[o + mi]) {
                            mi = q;
                        }
                    }
                    if (w > FWT[o + mi]) {
                        FWT[o + mi] = (float) w;
                        FID[o + mi] = id;
                    }
                }
            }
        }
        field = true;
    }

    // The field's color at cell (i, j), with the flicker of each light now.
    static void fieldAt(int i, int j, float[] out) {
        out[0] = out[1] = out[2] = 0;
        if (!field || i < 0 || j < 0 || i >= FN || j >= FN) {
            return;
        }
        int o = (i * FN + j) * 3;
        for (int q = 0; q < 3; q++) {
            int id = FID[o + q];
            if (id < 0) {
                continue;
            }
            float w = FWT[o + q];
            if (id >= 100000) {
                out[0] += 1.0f * w;
                out[1] += 0.86f * w;
                out[2] += 0.62f * w;
            } else if (id < panels.size()) {
                Panel g = panels.get(id);
                float f = g.f * w;
                out[0] += g.col[0] * f;
                out[1] += g.col[1] * f;
                out[2] += g.col[2] * f;
            }
        }
    }

    static void fieldXZ(double x, double z, float[] out) {
        fieldAt(Mth.floor((x - fx0) / FS), Mth.floor((z - fz0) / FS), out);
    }

    // The street's height at (x, z) (MIN_VALUE off the street or off the field) and its direction.
    static int streetY(double x, double z) {
        int i = Mth.floor((x - fx0) / FS), j = Mth.floor((z - fz0) / FS);
        return !field || i < 0 || j < 0 || i >= FN || j >= FN ? Integer.MIN_VALUE : FY[i * FN + j];
    }

    static int streetAxis(double x, double z) {
        int i = Mth.floor((x - fx0) / FS), j = Mth.floor((z - fz0) / FS);
        return !field || i < 0 || j < 0 || i >= FN || j >= FN ? 0 : FAX[i * FN + j];
    }
}
