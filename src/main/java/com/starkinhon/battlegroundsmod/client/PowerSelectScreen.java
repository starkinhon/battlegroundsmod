package com.starkinhon.battlegroundsmod.client;

import static com.starkinhon.battlegroundsmod.client.SoulBookDraw.a;
import static com.starkinhon.battlegroundsmod.client.SoulBookDraw.mix;
import static com.starkinhon.battlegroundsmod.client.SoulBookDraw.r;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.SoulSync;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;
import com.starkinhon.battlegroundsmod.skill.ProgressionSpec;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// Livro das Almas (thread "Menu & Hud", Stark 2026-11-12; web preview forja/menus/livro-das-almas.html, v3.1).
// Opening it, the camera dives into your character's head; inside there is a pixel universe whose color follows the
// soul you point at. In the middle stands your silhouette with the soul you wear floating over its head, the other souls
// orbit around it. Divine Souls on the first tab; the SubAlmas tab is a sky with one constellation per anime: clicking one
// is a trip of light to it, and only that universe's SubAlmas orbit. The card on the right shows the soul, its Sincronia
// (power/SoulSync: a point per level, you choose which skill to awaken, the ult last) and the hold-to-absorb button.
// Everything is drawn in GUI pixels, rect by rect, so it keeps Minecraft's pixel look at any GUI scale.
public class PowerSelectScreen extends Screen {
    private enum Phase { ENTER, IN, EXIT }

    private static final ResourceLocation NEB = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "soul_book_nebula");
    private static final int WHITE = 0xFFFFFF;
    private static final String[] KEY = {"Z", "X", "C", "V"};

    private Phase phase = Phase.ENTER;
    private float pt = 0, time = 0;
    private long last = -1;
    private int tab = 0;
    private String uni = null, uhover = null;
    private Power sel = null, hover = null;
    private float rot = 0, spin = 1, hold = 0;
    private boolean holding = false;
    private int unit = 3;
    private float[] tint = {155, 48, 255}, glow = {155, 48, 255};
    private float warpT = -1;
    private String warpUni = null;
    private boolean warpSwitched = false;
    private Power absorbing = null;
    private float absorbT = 0, flash = 0, titleT = -1;
    private Power titled = null;
    private float sx0, sy0;
    private final List<float[]> parts = new ArrayList<>(); // x, y, vx, vy, gravity, life, rgb, size

    private final List<Hit> hits = new ArrayList<>();
    private record Hit(int x, int y, int w, int h, Power soul, float depth, int tab, String cons, boolean back, boolean seal, int awaken) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // Soul positions this frame (orbit), by id.
    private final Map<String, float[]> pos = new java.util.HashMap<>();

    // Pixel nebula (re-tinted when the color drifts) and stars.
    private DynamicTexture neb;
    private int nebW, nebH;
    private float[] nebVal;
    private float[] nebTint;
    private int[][] stars;

    private record Cons(String id, String name, int color, List<Power> subs, float[][] st, int[][] ln) {
    }
    private List<Cons> cons;
    private final Map<String, int[]> consPos = new java.util.HashMap<>();

    public PowerSelectScreen() {
        super(Component.translatable("screen.battlegroundsmod.select_power"));
    }

    // U (the old constellation screen's key) opens the book too, wherever the player is.
    public static void open() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        SkillIcons.reload();
        mc.setScreen(new PowerSelectScreen());
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.4f, 0.45f));
    }

    // ---------- data ----------
    private String current() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getData(ModAttachments.POWER).power : "";
    }

    private Power currentPower() {
        return Powers.get(current());
    }

    private static int ink(Power p) {
        int c = p.color() & 0xFFFFFF;
        int lum = ((c >> 16 & 255) * 3 + (c >> 8 & 255) * 6 + (c & 255)) / 10;
        return lum < 110 ? mix(c, WHITE, 0.45f) : lum > 225 ? mix(c, 0x9FB4FF, 0.25f) : c;
    }

    private static String uniId(Power p) {
        ProgressionSpec spec = ProgressionSpec.get();
        String id = spec == null || spec.universos() == null ? null : spec.universos().get(p.id());
        return id == null ? "" : id;
    }

    private static String uniName(Power p) {
        String id = uniId(p);
        ChallengeSpec spec = ChallengeSpec.get();
        if (spec != null) {
            for (ChallengeSpec.Universe u : spec.universos()) {
                if (u.id().equals(id)) {
                    return u.nome();
                }
            }
        }
        return switch (p.id()) {
            case "rubber_boy" -> "One Piece";
            case "one_punch" -> "One Punch Man";
            case "makima" -> "Chainsaw Man";
            case "shigaraki" -> "My Hero Academia";
            default -> "";
        };
    }

    // The soul can be put on from here: outside progression all are free; with it, souls already worn (or creative).
    private boolean owned(Power p) {
        return !Progression.clientOn || minecraft.player.isCreative() || SoulSync.known(minecraft.player, p.id()) || p.id().equals(current());
    }

    private List<Power> orbit() {
        List<Power> list = new ArrayList<>(tab == 0 ? Powers.divine() : Powers.sub().stream().filter(p -> uniId(p).equals(uni)).toList());
        list.sort(Comparator.comparing(PowerSelectScreen::uniId));
        if (tab == 0 && minecraft != null && minecraft.player != null) {
            // The techniques a master taught (the Kamehameha from Mestre Kame), so the player can go back to them.
            for (Power p : Powers.innate()) {
                if (SoulSync.known(minecraft.player, p.id()) || p.id().equals(current())) {
                    list.add(0, p);
                }
            }
        }
        return list;
    }

    // ---------- setup ----------
    @Override
    protected void init() {
        unit = Math.max(2, Math.min(4, height / 90));
        Power cur = currentPower();
        if (phase == Phase.ENTER && pt == 0) {
            sel = cur;
            tab = cur != null && Powers.isSub(cur.id()) ? 1 : 0;
            uni = cur != null && tab == 1 ? uniId(cur) : null;
            if (cur != null) {
                int c = ink(cur);
                tint = rgb(c);
                glow = rgb(c);
            }
        }
        bake();
        buildConstellations();
    }

    private static float[] rgb(int c) {
        return new float[]{c >> 16 & 255, c >> 8 & 255, c & 255};
    }

    private static int toInt(float[] c) {
        return (Math.round(c[0]) << 16) | (Math.round(c[1]) << 8) | Math.round(c[2]);
    }

    private static void drift(float[] c, int to, float k) {
        float[] t = rgb(to);
        for (int i = 0; i < 3; i++) {
            c[i] += (t[i] - c[i]) * k;
        }
    }

    private void bake() {
        nebW = Math.max(16, width);
        nebH = Math.max(16, height);
        nebVal = new float[nebW * nebH];
        Random r = new Random(99);
        int[] sizes = {90, 36, 14, 5};
        float[] weights = {0.45f, 0.3f, 0.17f, 0.08f};
        float[][] grids = new float[4][];
        int[] gws = new int[4];
        for (int g = 0; g < 4; g++) {
            gws[g] = nebW / sizes[g] + 3;
            int gh = nebH / sizes[g] + 3;
            grids[g] = new float[gws[g] * gh];
            for (int i = 0; i < grids[g].length; i++) {
                grids[g][i] = r.nextFloat();
            }
        }
        for (int y = 0; y < nebH; y++) {
            for (int x = 0; x < nebW; x++) {
                float n = 0;
                for (int g = 0; g < 4; g++) {
                    float fx = x / (float) sizes[g], fy = y / (float) sizes[g];
                    int ix = (int) fx, iy = (int) fy;
                    float tx = fx - ix, ty = fy - iy;
                    float sx = tx * tx * (3 - 2 * tx), sy = ty * ty * (3 - 2 * ty);
                    float[] A = grids[g];
                    int w = gws[g];
                    float top = Mth.lerp(sx, A[iy * w + ix], A[iy * w + ix + 1]);
                    float bot = Mth.lerp(sx, A[(iy + 1) * w + ix], A[(iy + 1) * w + ix + 1]);
                    n += Mth.lerp(sy, top, bot) * weights[g];
                }
                float d = (y / (float) nebH - 0.45f) - (x / (float) nebW - 0.5f) * 0.4f;
                float band = (float) Math.exp(-d * d / 0.06f);
                nebVal[y * nebW + x] = (float) Math.pow(Mth.clamp(n * 1.45f - 0.38f, 0, 1), 1.5) * (0.3f + band);
            }
        }
        int count = nebW * nebH / 110;
        stars = new int[count][];
        for (int i = 0; i < count; i++) {
            stars[i] = new int[]{r.nextInt(nebW), r.nextInt(nebH), r.nextInt(700), r.nextFloat() < 0.08f ? 1 : 0};
        }
        if (neb != null) {
            neb.close();
        }
        neb = new DynamicTexture(nebW, nebH, false);
        minecraft.getTextureManager().register(NEB, neb);
        nebTint = null;
    }

    private void repaintNebula() {
        if (nebTint != null && Math.abs(nebTint[0] - tint[0]) + Math.abs(nebTint[1] - tint[1]) + Math.abs(nebTint[2] - tint[2]) < 6) {
            return;
        }
        int t = toInt(tint);
        int deep = mix(t, 0x020108, 0.93f), midc = mix(t, 0x0A0618, 0.45f), hot = mix(t, WHITE, 0.35f);
        NativeImage img = neb.getPixels();
        for (int y = 0; y < nebH; y++) {
            for (int x = 0; x < nebW; x++) {
                float v = Math.round(nebVal[y * nebW + x] * 6) / 6f;
                int col = v < 0.5f ? mix(deep, midc, v * 2) : mix(midc, hot, (v - 0.5f) * 2);
                // NativeImage is ABGR
                img.setPixelRGBA(x, y, 0xFF000000 | (col & 0xFF) << 16 | (col & 0xFF00) | (col >> 16 & 0xFF));
            }
        }
        neb.upload();
        nebTint = tint.clone();
    }

    private void buildConstellations() {
        cons = new ArrayList<>();
        ChallengeSpec spec = ChallengeSpec.get();
        if (spec == null) {
            return;
        }
        int i = 0;
        for (ChallengeSpec.Universe u : spec.universos()) {
            List<Power> subs = Powers.sub().stream().filter(p -> uniId(p).equals(u.id())).toList();
            Random r = new Random(31 + i * 17L);
            int n = Math.max(4, subs.size());
            float x = 0, y = 0, ang = r.nextFloat() * Mth.TWO_PI;
            float[][] st = new float[n][];
            for (int j = 0; j < n; j++) {
                st[j] = new float[]{x, y, r.nextFloat() < 0.35f ? 2 : 1};
                ang += (r.nextFloat() - 0.5f) * 2.2f;
                float d = 17 + r.nextFloat() * 12;
                x += Mth.cos(ang) * d;
                y += Mth.sin(ang) * d * 0.7f;
            }
            float mx = 0, my = 0;
            for (float[] p : st) {
                mx += p[0];
                my += p[1];
            }
            for (float[] p : st) {
                p[0] -= mx / n;
                p[1] -= my / n;
            }
            List<int[]> ln = new ArrayList<>();
            for (int j = 1; j < n; j++) {
                ln.add(new int[]{j - 1, j});
            }
            if (n > 4) {
                ln.add(new int[]{r.nextInt(n - 2), n - 1});
            }
            int color = u.color() & 0xFFFFFF;
            if (((color >> 16 & 255) + (color >> 8 & 255) + (color & 255)) > 660) {
                color = 0x9FD8FF; // Bleach's white reads as ice blue in the sky
            }
            cons.add(new Cons(u.id(), u.nome(), color, subs, st, ln.toArray(new int[0][])));
            i++;
        }
    }

    // ---------- layout ----------
    private boolean narrow() {
        return width < 380;
    }

    private int cx() {
        return narrow() ? width / 2 : Math.round(width * 0.30f);
    }

    private int gy() {
        return Math.round(height * 0.84f);
    }

    private int cardW() {
        return Math.round(Math.min(190, width * 0.38f));
    }

    private int cardX() {
        return narrow() ? width / 2 - cardW() / 2 : Math.round(width * 0.60f);
    }

    // Body parts of the silhouette: head, torso, arms, legs (Minecraft proportions).
    private int[][] body(int cx, int gy) {
        int u = unit;
        return new int[][]{
                {cx - 4 * u, gy - 32 * u, 8 * u, 8 * u},
                {cx - 4 * u, gy - 24 * u, 8 * u, 12 * u},
                {cx - 8 * u, gy - 24 * u, 4 * u, 12 * u},
                {cx + 4 * u, gy - 24 * u, 4 * u, 12 * u},
                {cx - 4 * u, gy - 12 * u, 4 * u, 12 * u},
                {cx, gy - 12 * u, 4 * u, 12 * u}};
    }

    // ---------- frame ----------
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        long now = Util.getMillis();
        float dt = last < 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        time += dt;
        Power cur = currentPower();
        int curInk = cur != null ? ink(cur) : 0x9B30FF;
        hits.clear();
        if (phase == Phase.ENTER) {
            pt += dt / 2.4f;
            float k = Math.min(1, pt);
            if (k < 0.8f) {
                head(g, k / 0.8f, curInk, cur);
            } else {
                head(g, 1, curInk, cur);
                inside(g, mx, my, dt, (k - 0.8f) / 0.2f);
            }
            if (pt >= 1) {
                phase = Phase.IN;
                pt = 1;
            }
        } else if (phase == Phase.IN) {
            inside(g, mx, my, dt, 1);
            hover = null;
            float best = -9;
            for (Hit h : hits) {
                if (h.soul != null && h.in(mx, my) && h.depth > best) {
                    best = h.depth;
                    hover = h.soul;
                }
            }
            uhover = null;
            if (warpT < 0) {
                for (Hit h : hits) {
                    if (h.cons != null && h.in(mx, my)) {
                        uhover = h.cons;
                    }
                }
            }
            if (holding && sel != null && !sel.id().equals(current()) && absorbing == null && owned(sel)) {
                hold = Math.min(1, hold + dt / 0.8f);
                if (hold >= 1) {
                    startAbsorb(sel);
                }
            } else if (absorbing == null) {
                hold = Math.max(0, hold - dt * 3);
            }
            if (absorbing != null) {
                stepAbsorb(g, dt);
            }
            if (warpT >= 0) {
                warp(g, dt);
            }
        } else {
            pt -= dt / 0.9f;
            float k = Math.max(0, pt);
            if (k > 0.75f) {
                inside(g, mx, my, dt, (k - 0.75f) / 0.25f);
            } else {
                head(g, k / 0.75f, curInk, cur);
            }
            if (pt <= 0) {
                super.onClose();
                return;
            }
        }
        stepParts(g, dt);
        if (titleT >= 0) {
            drawTitle(g, dt);
        }
    }

    // ---------- entering the head ----------
    private void head(GuiGraphics g, float k, int c, Power cur) {
        r(g, 0, 0, width, height, a(mix(0x7CA4FF, 0x0A0814, k * 1.6f), 1));
        if (k < 0.55f) {
            Random rr = new Random(5);
            float y = height * 0.62f;
            int ground = a(mix(0x568A3A, 0x0A0814, k * 1.6f), 1);
            for (int x = 0; x < width; x += 4) {
                y += (rr.nextFloat() - 0.5f) * 4;
                r(g, x, Math.round(y), 4, height, ground);
            }
            // the whole character first, then the camera dives into the head (exponential zoom, like a real camera)
            float z0 = Mth.clamp((k / 0.55f - 0.3f) / 0.7f, 0, 1);
            float z = z0 * z0 * z0;
            float u = (float) (2 * Math.pow(width / 6f / 2, z));
            float hx = width / 2f - 4 * u;
            float hy = Mth.lerp(1 - (1 - z0) * (1 - z0), height * 0.62f - 32 * u, height / 2f - 4 * u);
            if (cur != null) {
                float bob = Mth.sin(time * 3.3f) * u * 0.4f;
                item(g, cur, width / 2f, hy - 5 * u + bob, 6 * u / 16f);
            }
            int sil = 0x201020;
            r(g, Math.round(hx), Math.round(hy + 8 * u), Math.round(8 * u), Math.round(12 * u), a(sil, 1));
            r(g, Math.round(hx - 4 * u), Math.round(hy + 8 * u), Math.round(4 * u), Math.round(12 * u), a(mix(sil, 0, 0.2f), 1));
            r(g, Math.round(hx + 8 * u), Math.round(hy + 8 * u), Math.round(4 * u), Math.round(12 * u), a(mix(sil, 0, 0.2f), 1));
            r(g, Math.round(hx), Math.round(hy + 20 * u), Math.round(8 * u), Math.round(12 * u), a(mix(sil, 0, 0.35f), 1));
            for (int py = 0; py < 8; py++) {
                for (int px = 0; px < 8; px++) {
                    float shade = 0.9f + ((px * 7 + py * 13) % 5) / 40f;
                    int col = mix(mix(0x1C162A, 0x080610, py / 8f), 0, 1 - shade);
                    r(g, Math.round(hx + px * u), Math.round(hy + py * u), Math.round(u) + 1, Math.round(u) + 1, a(col, 1));
                }
            }
            int eye = a(mix(c, WHITE, 0.4f), Mth.clamp(0.4f + k * 3, 0, 1));
            int iu = Math.max(1, Math.round(u));
            r(g, Math.round(hx + u), Math.round(hy + 4 * u), iu, iu, 0xFFE8E8F0);
            r(g, Math.round(hx + 2 * u), Math.round(hy + 4 * u), iu, iu, eye);
            r(g, Math.round(hx + 5 * u), Math.round(hy + 4 * u), iu, iu, eye);
            r(g, Math.round(hx + 6 * u), Math.round(hy + 4 * u), iu, iu, 0xFFE8E8F0);
        } else {
            tunnel(g, (k - 0.55f) / 0.45f, c, width / 2f, height / 2f, 1);
        }
    }

    // Pixel streaks rushing out of a point, and rings passing by.
    private void tunnel(GuiGraphics g, float t, int c, float cx, float cy, float alpha) {
        float big = Math.max(width, height);
        for (int i = 6; i > 0; i--) {
            float rr = (1 - (1 - t) * (1 - t)) * big * 0.12f * i;
            r(g, Math.round(cx - rr), Math.round(cy - rr * 0.7f), Math.round(rr * 2), Math.round(rr * 1.4f), a(c, 0.05f * t * alpha));
        }
        Random r = new Random(9);
        for (int i = 0; i < 420; i++) {
            float an = r.nextFloat() * Mth.TWO_PI;
            float z = (r.nextFloat() + t * 2.2f) % 1;
            float d = z * z * big * 0.75f;
            int len = 1 + (int) (z * z * 14);
            int sz = 1 + (int) (z * 2.5f);
            int col = a(mix(c, WHITE, r.nextFloat() * 0.7f), Mth.clamp(z * 1.4f, 0, 1) * alpha);
            for (int j = 0; j < len; j += sz) {
                r(g, Math.round(cx + Mth.cos(an) * (d + j)), Math.round(cy + Mth.sin(an) * (d + j) * 0.8f), sz, sz, col);
            }
        }
        for (int n = 0; n < 3; n++) {
            float ring = (t * 1.6f + n / 3f) % 1;
            float rr = ring * ring * big;
            int col = a(mix(c, WHITE, 0.3f), (1 - ring) * 0.8f * alpha);
            for (int i = 0; i < 120; i++) {
                float an = i / 120f * Mth.TWO_PI;
                r(g, Math.round(cx + Mth.cos(an) * rr * 0.6f), Math.round(cy + Mth.sin(an) * rr * 0.45f), 2, 2, col);
            }
        }
    }

    // ---------- inside the head ----------
    private void inside(GuiGraphics g, int mx, int my, float dt, float alpha) {
        Power cur = currentPower();
        Power show = sel != null ? sel : cur;
        boolean map = tab == 1 && uni == null;
        int want;
        if (map && uhover != null) {
            want = cons.stream().filter(c -> c.id.equals(uhover)).findFirst().map(Cons::color).orElse(0x9B30FF);
        } else if (hover != null) {
            want = ink(hover);
        } else {
            want = show != null ? ink(show) : 0x9B30FF;
        }
        drift(tint, want, 1 - (float) Math.exp(-dt * 2.2f));
        drift(glow, show != null ? ink(show) : 0x9B30FF, 1 - (float) Math.exp(-dt * 4));
        int glowC = toInt(glow);

        // universe
        repaintNebula();
        g.setColor(1, 1, 1, alpha);
        g.blit(NEB, 0, 0, 0, 0, width, height, nebW, nebH);
        g.setColor(1, 1, 1, 1);
        for (int[] s : stars) {
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * Mth.sin(time * (0.6f + s[2] % 7 * 0.3f) + s[2]));
            int col = a(mix(toInt(tint), WHITE, 0.75f), tw * alpha);
            if (s[3] == 1) {
                r(g, s[0] - 1, s[1], 3, 1, col);
                r(g, s[0], s[1] - 1, 1, 3, col);
            } else {
                r(g, s[0], s[1], 1, 1, col);
            }
        }

        int cx = cx(), gy = gy();
        String ctip = null;
        if (map) {
            ctip = constellations(g, alpha);
        } else {
            // platform
            for (int i = 0; i < 120; i++) {
                float an = i / 120f * Mth.TWO_PI;
                r(g, Math.round(cx + Mth.cos(an) * 16 * unit), Math.round(gy + 2 + Mth.sin(an) * 2.4f * unit), 1, 1, a(glowC, 0.5f * alpha));
            }
            for (int i = 0; i < 60; i += 3) {
                float an = i / 60f * Mth.TWO_PI + time * 0.3f;
                r(g, Math.round(cx + Mth.cos(an) * 12.6f * unit), Math.round(gy + 2 + Mth.sin(an) * 1.8f * unit), 1, 1, a(mix(glowC, WHITE, 0.4f), 0.7f * alpha));
            }
            // orbit
            List<Power> list = orbit();
            float k = phase == Phase.IN ? 1 : 1 - (1 - Math.min(1, pt)) * (1 - Math.min(1, pt));
            spin = Mth.lerp(1 - (float) Math.exp(-dt * 8), spin, hover != null ? 0 : 1);
            rot += dt * 0.16f * spin;
            float rx = Math.min(width * 0.27f, 50 * unit), ry = 9 * unit;
            float oy = gy - 16 * unit;
            pos.clear();
            for (int i = 0; i < list.size(); i++) {
                Power p = list.get(i);
                float an = i / (float) list.size() * Mth.TWO_PI + rot + (1 - k) * 3;
                pos.put(p.id(), new float[]{cx + Mth.cos(an) * rx * k, oy + Mth.sin(an) * ry * k, Mth.sin(an), i});
            }
            List<Power> back = list.stream().filter(p -> pos.get(p.id())[2] < 0).sorted(Comparator.comparingDouble((Power p) -> pos.get(p.id())[2])).toList();
            List<Power> front = list.stream().filter(p -> pos.get(p.id())[2] >= 0).sorted(Comparator.comparingDouble((Power p) -> pos.get(p.id())[2])).toList();
            for (Power p : back) {
                soul(g, p, alpha);
            }
            // silhouette
            float fill = absorbing != null ? Mth.clamp((absorbT - 0.7f) * 2, 0, 1) : 0.25f + hold * 0.6f;
            int[][] b = body(cx, gy + Math.round(Mth.sin(time * 1.4f)));
            silhouette(g, b, glowC, fill, alpha);
            if (absorbing == null && show != null) {
                float hx = b[0][0] + b[0][2] / 2f, hy = b[0][1] - 18 + Math.round(Mth.sin(time * 1.8f) * 2) - hold * 4;
                SoulBookDraw.disc(g, Math.round(hx), Math.round(hy), 16, a(mix(ink(show), WHITE, 0.2f), 0.06f * alpha));
                SoulBookDraw.disc(g, Math.round(hx), Math.round(hy), 12, a(mix(ink(show), WHITE, 0.2f), 0.08f * alpha));
                float jx = hold > 0.5f ? (float) Math.round((Math.random() - 0.5) * 2) : 0;
                item(g, show, hx + jx, hy, 1.25f);
                if (Math.random() < 0.3) {
                    parts.add(new float[]{hx + (float) (Math.random() - 0.5) * 10, hy + 6, 0, -8 - (float) Math.random() * 6, 0, 0.9f,
                            mix(ink(show), WHITE, (float) Math.random() * 0.5f), 1});
                }
            }
            for (Power p : front) {
                soul(g, p, alpha);
            }
        }

        // title and tabs
        int tc = a(mix(glowC, WHITE, 0.55f), alpha);
        GuiDraw.text(g, font, Component.translatable("screen.battlegroundsmod.soul_book.title"), 10, 8, 1.5f, tc, true);
        GuiDraw.text(g, font, Component.translatable("screen.battlegroundsmod.soul_book.subtitle"), 10, 23, 0.75f, a(0x9C93B4, alpha), true);
        int tx = 10;
        for (int i = 0; i < 2; i++) {
            Component nm = Component.translatable(i == 0 ? "screen.battlegroundsmod.select_power.divine" : "screen.battlegroundsmod.select_power.sub");
            int w = font.width(nm) + 14;
            boolean on = tab == i;
            SoulBookDraw.smallFrame(g, tx, 34, w, 15, on ? glowC : 0x6E648C, on);
            g.drawString(font, nm, tx + 7, 38, on ? 0xFFFFFFFF : 0xFF968CAA, false);
            if (phase == Phase.IN) {
                hits.add(new Hit(tx, 34, w, 15, null, 0, i, null, false, false, -1));
            }
            tx += w + 4;
        }
        if (tab == 1 && uni != null) {
            Cons c = cons.stream().filter(q -> q.id.equals(uni)).findFirst().orElse(null);
            int cc = c != null ? c.color : glowC;
            Component bt = Component.translatable("screen.battlegroundsmod.soul_book.back");
            int w = font.width(bt) + 14;
            boolean hv = mx >= 10 && mx < 10 + w && my >= 53 && my < 68;
            SoulBookDraw.smallFrame(g, 10, 53, w, 15, cc, hv);
            g.drawString(font, bt, 17, 57, hv ? 0xFFFFFFFF : a(mix(cc, WHITE, 0.4f), 1), false);
            if (phase == Phase.IN && warpT < 0) {
                hits.add(new Hit(10, 53, w, 15, null, 0, -1, null, true, false, -1));
            }
            if (c != null) {
                g.drawString(font, Component.translatable("screen.battlegroundsmod.soul_book.constellation", c.name), 18 + w, 57, a(mix(cc, WHITE, 0.3f), 1), true);
            }
        }

        // the worn soul's 3D outfit on/off (was on the old U screen)
        Power wornP = currentPower();
        if (wornP != null && com.starkinhon.battlegroundsmod.client.outfit.Outfits.exists(wornP.id())) {
            boolean on = com.starkinhon.battlegroundsmod.client.outfit.Outfits.selfOn();
            Component ol = Component.translatable(on ? "screen.battlegroundsmod.skills.outfit_on" : "screen.battlegroundsmod.skills.outfit_off");
            int ow = font.width(ol) + 14, ox = width - ow - 10;
            boolean ohv = mx >= ox && mx < ox + ow && my >= 8 && my < 23;
            SoulBookDraw.smallFrame(g, ox, 8, ow, 15, on ? glowC : 0x6E648C, ohv);
            g.drawString(font, ol, ox + 7, 12, on || ohv ? 0xFFFFFFFF : 0xFF968CAA, false);
            if (phase == Phase.IN) {
                hits.add(new Hit(ox, 8, ow, 15, null, 0, -2, null, false, false, -1));
            }
        }

        // card and tooltips
        List<Component> tip = null;
        if (show != null) {
            tip = card(g, show, mx, my);
        }
        if (phase == Phase.IN && absorbing == null && warpT < 0) {
            if (hover != null) {
                List<Component> ls = new ArrayList<>();
                ls.add(hover.name().copy().withStyle(s -> s.withColor(ink(hover))));
                ls.add(Component.literal(uniName(hover)).withStyle(s -> s.withColor(0xA096B9)));
                for (int slot : SoulSync.skills(hover)) {
                    boolean awake = SoulSync.awake(minecraft.player, hover, slot) && (hover.id().equals(current()) || !Progression.clientOn
                            || SoulSync.get(minecraft.player, hover.id()).has(slot) || slot == 0);
                    ls.add(Component.literal((slot >= PowerData.SLOT_SHIFT ? "Shift+" : "") + keyLabel(hover, slot) + "  ").append(hover.abilityName(slot))
                            .withStyle(s -> s.withColor(awake ? 0xFFFFFF : 0x6E6882)));
                }
                if (!owned(hover)) {
                    ls.add(Component.translatable("screen.battlegroundsmod.soul_book.not_owned").withStyle(s -> s.withColor(0xFF6060)));
                }
                g.renderComponentTooltip(font, ls, mx, my);
            } else if (tip != null) {
                g.renderComponentTooltip(font, tip, mx, my);
            } else if (ctip != null) {
                Cons c = cons.stream().filter(q -> q.id.equals(uhover)).findFirst().orElse(null);
                if (c != null) {
                    List<Component> ls = new ArrayList<>();
                    ls.add(Component.literal(c.name).withStyle(s -> s.withColor(mix(c.color, WHITE, 0.3f))));
                    for (Power p : c.subs) {
                        ls.add(p.name().copy().withStyle(s -> s.withColor(0xD7D0E4)));
                    }
                    ls.add(Component.translatable("screen.battlegroundsmod.soul_book.travel").withStyle(s -> s.withColor(0x80FF20)));
                    g.renderComponentTooltip(font, ls, mx, my);
                }
            }
        }
    }

    private void silhouette(GuiGraphics g, int[][] b, int c, float fill, float alpha) {
        int edge = a(mix(c, WHITE, 0.25f), alpha);
        int inner = a(mix(c, 0x04020A, 0.88f), alpha);
        for (int k = 3; k >= 1; k--) {
            for (int[] p : b) {
                r(g, p[0] - k, p[1] - k, p[2] + 2 * k, p[3] + 2 * k, a(c, 0.07f * (4 - k) * alpha));
            }
        }
        for (int[] p : b) {
            r(g, p[0] - 1, p[1] - 1, p[2] + 2, p[3] + 2, edge);
        }
        for (int[] p : b) {
            r(g, p[0], p[1], p[2], p[3], inner);
        }
        Random r = new Random(77);
        int i = 0;
        for (int[] p : b) {
            int n = p[2] * p[3] / 9;
            for (int j = 0; j < n; j++, i++) {
                float x = p[0] + r.nextFloat() * p[2];
                float yb = p[1] + r.nextFloat() * p[3];
                float speed = 0.5f + r.nextFloat();
                float y = p[1] + (((yb - p[1] - time * 6 * speed) % p[3]) + p[3]) % p[3];
                float al = (0.12f + 0.5f * fill) * (0.5f + 0.5f * Mth.sin(time * 3 + i));
                r(g, (int) x, (int) y, 1, 1, a(mix(c, WHITE, r.nextFloat() * 0.5f), al * alpha));
            }
        }
        int hx = b[1][0] + b[1][2] / 2, hy = Math.round(b[1][1] + b[1][3] * 0.35f);
        for (int k = 4; k >= 0; k--) {
            r(g, hx - k, hy - k, 2 * k + 1, 2 * k + 1, a(mix(c, WHITE, 1 - k / 4f), (0.12f + 0.5f * fill) * (1 - k / 5f) * alpha));
        }
        int ey = Math.round(b[0][1] + b[0][3] * 0.55f);
        int eye = a(mix(c, WHITE, 0.6f), (0.5f + 0.5f * fill) * alpha);
        r(g, Math.round(b[0][0] + b[0][2] * 0.2f), ey, 3, 2, eye);
        r(g, Math.round(b[0][0] + b[0][2] * 0.62f), ey, 3, 2, eye);
    }

    private void soul(GuiGraphics g, Power p, float alpha) {
        if (absorbing == p) {
            return;
        }
        float[] q = pos.get(p.id());
        float d = q[2];
        boolean isH = hover == p, isS = sel == p;
        float scale = (d > 0.35f ? 1.25f : d > -0.35f ? 1.05f : 0.95f) + (isH ? 0.25f : 0);
        float x = q[0], y = q[1] + Math.round(Mth.sin(time * 2 + q[3] * 3));
        int ic = ink(p);
        float depthA = Mth.lerp((d + 1) / 2, 0.75f, 1f) * alpha;
        // round glow of the soul's color behind it (pixel rows, no blur), stronger on hover: never lost in the sky
        for (int r2 = 4; r2 >= 1; r2--) {
            SoulBookDraw.disc(g, Math.round(x), Math.round(y), Math.round(8 * scale + r2 * 2), a(mix(ic, WHITE, 0.2f), (isH ? 0.1f : 0.05f) * depthA));
        }
        SoulBookDraw.disc(g, Math.round(x), Math.round(y), Math.round(6 * scale), a(0x06030E, 0.55f * depthA));
        int sz = Math.round(16 * scale);
        if (isS) {
            boolean on = Mth.sin(time * 5) > 0;
            g.pose().pushPose();
            g.pose().translate(0, 0, 0);
            SoulBookDraw.smallFrame(g, Math.round(x - sz / 2f) - 3, Math.round(y - sz / 2f) - 3, sz + 6, sz + 6, ic, on);
            g.pose().popPose();
        }
        item(g, p, x, y, scale);
        if (!owned(p)) {
            // not yours yet: a dark veil and a small padlock over the crystal
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            SoulBookDraw.disc(g, Math.round(x), Math.round(y), Math.round(7 * scale), a(0x05030A, 0.55f));
            SoulBookDraw.padlock(g, Math.round(x) - 4, Math.round(y) - 4);
            g.pose().popPose();
        }
        if (phase == Phase.IN && absorbing == null && warpT < 0) {
            hits.add(new Hit(Math.round(x - sz / 2f), Math.round(y - sz / 2f), sz, sz, p, d, -1, null, false, false, -1));
        }
    }

    private void item(GuiGraphics g, Power p, float x, float y, float scale) {
        var holder = ModItems.SOULS.get(p.id());
        if (holder == null) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(new ItemStack(holder.get()), -8, -8);
        g.pose().popPose();
    }

    private String keyLabel(Power p, int slot) {
        String k = slot == PowerData.SLOT_EXTRA || slot == PowerData.SLOT_SHIFT_EXTRA ? "B" : KEY[PowerData.baseSlot(slot) & 3];
        try {
            k = Keybinds.forSlot(p, slot).getTranslatedKeyMessage().getString();
        } catch (RuntimeException ignored) {
        }
        if (k.length() > 3) {
            k = k.substring(0, 3);
        }
        return k;
    }

    // ---------- card ----------
    private List<Component> card(GuiGraphics g, Power s, int mx, int my) {
        int x = cardX(), w = cardW(), c = ink(s);
        List<Component> tip = null;
        List<FormattedCharSequence> desc = font.split(s.description(), w - 16);
        int dl = Math.min(6, desc.size());
        List<Integer> skills = SoulSync.skills(s);
        int per = Math.min(5, skills.size());
        int rows = (skills.size() + per - 1) / per;
        int h = 66 + dl * 9 + rows * 32 + 48;
        int y = narrow() ? Math.round(height * 0.45f) : Math.max(52, Math.min(Math.round(height * 0.16f), height - h - 26));
        SoulBookDraw.frame(g, x, y, w, h, c, true, true, 0.92f);
        int yy = y + 8;
        GuiDraw.centeredText(g, font, s.name(), x + w / 2f, yy, 1.25f, a(mix(c, WHITE, 0.3f), 1), true);
        yy += 15;
        String kind = Component.translatable(Powers.isInnate(s.id()) ? "screen.battlegroundsmod.soul_book.inata"
                : Powers.isSub(s.id()) ? "screen.battlegroundsmod.soul_book.sub" : "screen.battlegroundsmod.soul_book.divine").getString();
        String un = uniName(s);
        GuiDraw.centeredText(g, font, Component.literal(kind + (un.isEmpty() ? "" : " · " + un.toUpperCase())), x + w / 2f, yy, 0.75f, 0xFFAAA0C3, false);
        yy += 10;

        // Sincronia: an XP-like green bar, per soul
        SoulSync.Entry e = SoulSync.get(minecraft.player, s.id());
        boolean gated = Progression.clientOn;
        boolean isCur = s.id().equals(current());
        int bw = w - 40, bx = x + w / 2 - bw / 2;
        if (gated) {
            GuiDraw.text(g, font, Component.translatable("screen.battlegroundsmod.soul_book.sync"), bx, yy, 0.75f, 0xFFAAA0C3, false);
            GuiDraw.rightText(g, font, Component.translatable("screen.battlegroundsmod.soul_book.level", e.level()), bx + bw, yy, 0.75f, 0xFF80FF20, false);
            yy += 8;
            r(g, bx - 1, yy - 1, bw + 2, 7, 0xFF000000);
            r(g, bx, yy, bw, 5, 0xFF1D2A10);
            int fw = Math.round(bw * Mth.clamp(e.xp(), 0, 1));
            r(g, bx, yy, fw, 5, 0xFF80FF20);
            r(g, bx, yy, fw, 1, 0xFFC8FF90);
            for (int i = 1; i < 10; i++) {
                r(g, bx + bw * i / 10, yy, 1, 5, 0x73000000);
            }
            yy += 8;
            Component line;
            int lc;
            if (!owned(s)) {
                line = Component.translatable("screen.battlegroundsmod.soul_book.not_owned");
                lc = 0xFFFF6060;
            } else if (e.points() > 0) {
                line = Component.translatable(e.points() > 1 ? "screen.battlegroundsmod.soul_book.points" : "screen.battlegroundsmod.soul_book.point", e.points());
                lc = Mth.sin(time * 5) > 0 ? 0xFF80FF20 : 0xFF5ABE1E;
            } else if (!isCur) {
                line = Component.translatable("screen.battlegroundsmod.soul_book.wear_to_sync");
                lc = 0xFF787090;
            } else {
                line = Component.translatable("screen.battlegroundsmod.soul_book.fight_to_sync");
                lc = 0xFF787090;
            }
            GuiDraw.centeredText(g, font, line, x + w / 2f, yy, 0.75f, lc, false);
        } else {
            GuiDraw.centeredText(g, font, Component.translatable("screen.battlegroundsmod.soul_book.free"), x + w / 2f, yy + 6, 0.75f, 0xFF80FF20, false);
        }
        yy += 10;
        r(g, x + 12, yy, w - 24, 1, a(c, 0.5f));
        r(g, x + w / 2 - 1, yy - 1, 3, 3, a(mix(c, WHITE, 0.4f), 1));
        yy += 6;
        for (int i = 0; i < dl; i++) {
            int lw = font.width(desc.get(i));
            g.drawString(font, desc.get(i), x + w / 2 - lw / 2, yy + i * 9, 0xFFD7D0E4, false);
        }
        yy += dl * 9 + 6;

        // skills in slots
        int sw = 24;
        int gx = x + w / 2 - (per * sw - 2) / 2;
        int awakeCount = 0;
        for (int i = 0; i < skills.size(); i++) {
            int slot = skills.get(i);
            int sx = gx + (i % per) * sw, sy = yy + (i / per) * (sw + 8);
            boolean awake = !gated || slot == 0 || e.has(slot);
            if (awake) {
                awakeCount++;
            }
            boolean next = gated && !awake && isCur && SoulSync.canAwaken(minecraft.player, s, slot);
            boolean hv = mx >= sx && mx < sx + 22 && my >= sy && my < sy + 22;
            SoulBookDraw.smallFrame(g, sx, sy, 22, 22, awake ? c : 0x5A546E, hv);
            SkillIcons.draw(g, font, s, slot, sx + 3, sy + 3, 16, awake ? 1f : 0.25f);
            if (!awake) {
                SoulBookDraw.padlock(g, sx + 7, sy + 6);
            }
            if (next) {
                int pc = Mth.sin(time * 6) > 0 ? 0xFF80FF20 : 0xFF3F8A10;
                r(g, sx + 16, sy - 2, 7, 7, pc);
                r(g, sx + 19, sy - 1, 1, 5, 0xFFFFFFFF);
                r(g, sx + 17, sy + 1, 5, 1, 0xFFFFFFFF);
            }
            if (slot >= PowerData.SLOT_SHIFT) {
                // Shift variant: a little yellow arrow in the corner (Stark 2026-11-12), same key below
                int ax = sx + 16, ay = sy + 2;
                r(g, ax + 2, ay, 1, 1, 0xFFFFD84A);
                r(g, ax + 1, ay + 1, 3, 1, 0xFFFFD84A);
                r(g, ax, ay + 2, 5, 1, 0xFFFFD84A);
                r(g, ax + 2, ay + 3, 1, 2, 0xFFFFD84A);
            }
            GuiDraw.centeredText(g, font, Component.literal(keyLabel(s, slot)), sx + 11, sy + 24, 0.75f,
                    awake ? 0xFFE6DEF0 : 0xFF6E6882, false);
            if (hv) {
                r(g, sx + 3, sy + 3, 16, 16, 0x40FFFFFF);
                tip = new ArrayList<>();
                tip.add(s.abilityName(slot).copy().withStyle(st -> st.withColor(mix(c, WHITE, 0.3f))));
                // tags and cooldown (they were on the old U screen)
                List<String> tags = new ArrayList<>();
                if (PowerData.isUlt(slot)) {
                    tags.add(Component.translatable("screen.battlegroundsmod.skills.tag.ult").getString());
                }
                if (slot >= PowerData.SLOT_SHIFT) {
                    tags.add(Component.translatable("screen.battlegroundsmod.skills.tag.shift").getString());
                }
                com.starkinhon.battlegroundsmod.power.Ability ab = com.starkinhon.battlegroundsmod.power.PowerLogic.ability(s, slot);
                if (ab != null && (ab.counter() || ab.beatenCounter())) {
                    tags.add(Component.translatable("screen.battlegroundsmod.skills.tag.counter").getString());
                }
                String tk = SkillIcons.key(s, slot, "tags");
                if (net.minecraft.client.resources.language.I18n.exists(tk)) {
                    for (String tg : net.minecraft.client.resources.language.I18n.get(tk).split(",")) {
                        if (!tg.isBlank()) {
                            tags.add(tg.trim());
                        }
                    }
                }
                if (!tags.isEmpty()) {
                    tip.add(Component.literal(String.join(" · ", tags).toUpperCase()).withStyle(st -> st.withColor(mix(c, 0x888888, 0.4f))));
                }
                if (ab != null) {
                    Component cd;
                    if (ab.cooldown() >= PowerData.SPENT / 2) {
                        cd = Component.translatable("screen.battlegroundsmod.skills.once");
                    } else {
                        float secs = ab.cooldown() / 20f;
                        cd = Component.translatable("screen.battlegroundsmod.skills.cooldown",
                                secs == Math.floor(secs) ? String.valueOf((int) secs) : String.format("%.1f", secs));
                    }
                    tip.add(cd.copy().withStyle(st -> st.withColor(0x9C93B4)));
                }
                for (FormattedCharSequence l : font.split(SkillIcons.description(s, slot), 180)) {
                    StringBuilder sb = new StringBuilder();
                    l.accept((idx, style, cp) -> {
                        sb.appendCodePoint(cp);
                        return true;
                    });
                    tip.add(Component.literal(sb.toString()).withStyle(st -> st.withColor(0xCDC6DC)));
                }
                if (!awake) {
                    String key = next ? "screen.battlegroundsmod.soul_book.click_awaken"
                            : !isCur ? "screen.battlegroundsmod.soul_book.locked_wear"
                            : PowerData.isUlt(slot) ? "screen.battlegroundsmod.soul_book.locked_ult"
                            : "screen.battlegroundsmod.soul_book.locked_sync";
                    tip.add(Component.translatable(key).withStyle(st -> st.withColor(next ? 0x80FF20 : 0xFF6060)));
                }
            }
            if (phase == Phase.IN && absorbing == null && warpT < 0) {
                hits.add(new Hit(sx, sy, 22, 22, null, 0, -1, null, false, false, next ? slot : -1));
            }
        }
        yy += rows * (sw + 8) + 2;
        GuiDraw.centeredText(g, font, Component.translatable("screen.battlegroundsmod.soul_book.awake_count", awakeCount, skills.size()),
                x + w / 2f, yy, 0.75f, 0xFF9C93B4, false);

        // hold-to-absorb button
        int bw2 = w - 24, bx2 = x + 12, by = yy + 10;
        boolean can = !isCur && owned(s);
        boolean hv = can && mx >= bx2 && mx < bx2 + bw2 && my >= by && my < by + 17;
        SoulBookDraw.smallFrame(g, bx2, by, bw2, 17, can ? c : 0x5A546E, hv || hold > 0);
        if (hold > 0) {
            r(g, bx2 + 2, by + 2, Math.round((bw2 - 4) * hold), 13, a(mix(c, WHITE, 0.2f), 0.55f));
        }
        Component label = Component.translatable(isCur ? "screen.battlegroundsmod.soul_book.yours"
                : !owned(s) ? "screen.battlegroundsmod.soul_book.not_owned_short"
                : hold > 0 ? "screen.battlegroundsmod.soul_book.absorbing" : "screen.battlegroundsmod.soul_book.hold");
        int lw = font.width(label);
        g.drawString(font, label, bx2 + bw2 / 2 - lw / 2, by + 5, can ? (hv || hold > 0 ? 0xFFFFFFFF : a(mix(c, WHITE, 0.5f), 1)) : 0xFF8C84A0, false);
        if (can && phase == Phase.IN && absorbing == null && warpT < 0) {
            hits.add(new Hit(bx2, by, bw2, 17, null, 0, -1, null, false, true, -1));
        }
        return tip;
    }

    // ---------- constellations ----------
    private String constellations(GuiGraphics g, float alpha) {
        r(g, 0, 0, width, height, a(0x030208, 0.5f * alpha));
        int ax = 14, ay = 76, aw = (narrow() ? width : cardX()) - ax - 14, ah = height - ay - 30;
        GuiDraw.text(g, font, Component.translatable("screen.battlegroundsmod.soul_book.pick_constellation"), ax, ay - 18, 0.75f, a(0x9C93B4, alpha), false);
        float[][] spots = {{.18f, .22f}, {.55f, .16f}, {.86f, .3f}, {.34f, .6f}, {.72f, .66f}, {.12f, .82f}, {.5f, .92f}};
        for (int i = 0; i < cons.size(); i++) {
            Cons c = cons.get(i);
            float[] sp = spots[i % spots.length];
            int cx = Math.round(ax + aw * sp[0]), cy = Math.round(ay + ah * sp[1]);
            consPos.put(c.id, new int[]{cx, cy});
            boolean on = !c.subs.isEmpty();
            boolean hv = c.id.equals(uhover);
            float base = (on ? (hv ? 1 : 0.8f) : 0.25f) * alpha;
            for (int[] l : c.ln) {
                float[] p = c.st[l[0]], q = c.st[l[1]];
                float len = (float) Math.hypot(q[0] - p[0], q[1] - p[1]);
                for (float t = 0; t < len; t += 2) {
                    float f = t / len;
                    r(g, Math.round(cx + p[0] + (q[0] - p[0]) * f), Math.round(cy + p[1] + (q[1] - p[1]) * f), 1, 1, a(mix(c.color, WHITE, 0.2f), base * 0.7f));
                }
            }
            for (int j = 0; j < c.st.length; j++) {
                float[] p = c.st[j];
                float tw = 0.6f + 0.4f * Mth.sin(time * 2.4f + j * 1.7f + i);
                int x = Math.round(cx + p[0]), y = Math.round(cy + p[1]);
                int z = (int) p[2] + 1 + (hv ? 1 : 0);
                for (int r2 = 3; r2 >= 1; r2--) {
                    r(g, x - r2, y - r2, 2 * r2 + 1, 2 * r2 + 1, a(c.color, (hv ? 0.1f : 0.05f) * base));
                }
                int col = a(mix(c.color, WHITE, 0.55f), base * tw);
                r(g, x - z, y, 2 * z + 1, 1, col);
                r(g, x, y - z, 1, 2 * z + 1, col);
                r(g, x, y, 1, 1, a(WHITE, base));
            }
            float maxY = -999, minX = 999, maxX = -999, minY = 999;
            for (float[] p : c.st) {
                maxY = Math.max(maxY, p[1]);
                minY = Math.min(minY, p[1]);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
            }
            int by = Math.round(cy + maxY + 8);
            GuiDraw.centeredText(g, font, Component.literal(c.name.toUpperCase()), cx, by, 0.75f,
                    on ? a(mix(c.color, WHITE, hv ? 0.6f : 0.25f), alpha) : a(0x5F5A73, alpha), true);
            GuiDraw.centeredText(g, font, on ? Component.translatable("screen.battlegroundsmod.soul_book.subs_count", c.subs.size())
                    : Component.translatable("screen.battlegroundsmod.soul_book.soon"), cx, by + 8, 0.6f, on ? a(0xA096B9, alpha) : a(0x555069, alpha), false);
            if (on && phase == Phase.IN && warpT < 0) {
                hits.add(new Hit(Math.round(cx + minX - 8), Math.round(cy + minY - 8), Math.round(maxX - minX + 16), Math.round(maxY - minY + 34),
                        null, 0, -1, c.id, false, false, -1));
            }
        }
        return uhover;
    }

    private void warp(GuiGraphics g, float dt) {
        warpT += dt / 1.1f;
        Cons c = cons.stream().filter(q -> q.id.equals(warpUni)).findFirst().orElse(null);
        if (c == null) {
            warpT = -1;
            return;
        }
        if (warpT >= 0.5f && !warpSwitched) {
            warpSwitched = true;
            uni = warpUni;
            Power cur = currentPower();
            sel = cur != null && uniId(cur).equals(uni) && Powers.isSub(cur.id()) ? cur : c.subs.isEmpty() ? null : c.subs.get(0);
        }
        float t = warpT;
        float k = t < 0.5f ? (t / 0.5f) * (t / 0.5f) * (t / 0.5f) : 1 - (1 - (1 - (t - 0.5f) / 0.5f) * (1 - (t - 0.5f) / 0.5f));
        int[] cp = consPos.getOrDefault(c.id, new int[]{width / 2, height / 2});
        float cx = t < 0.5f ? Mth.lerp(k, cp[0], width / 2f) : width / 2f;
        float cy = t < 0.5f ? Mth.lerp(k, cp[1], height / 2f) : height / 2f;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        r(g, 0, 0, width, height, a(mix(c.color, 0x04020A, 0.8f), Math.min(1, k * 1.3f) * 0.9f));
        tunnel(g, t, c.color, cx, cy, k);
        if (t < 0.5f) {
            GuiDraw.centeredText(g, font, Component.literal(c.name.toUpperCase()), width / 2f, height / 2f - 8, 2, a(mix(c.color, WHITE, 0.6f), k), true);
        }
        g.pose().popPose();
        if (warpT >= 1) {
            warpT = -1;
        }
    }

    // ---------- absorbing ----------
    private void startAbsorb(Power s) {
        absorbing = s;
        absorbT = 0;
        hold = 0;
        holding = false;
        float[] q = pos.get(s.id());
        int[][] b = body(cx(), gy());
        sx0 = q != null ? q[0] : b[0][0] + b[0][2] / 2f;
        sy0 = q != null ? q[1] : b[0][1] - 18;
        PacketDistributor.sendToServer(new ModPayloads.SelectPower(s.id()));
    }

    private void stepAbsorb(GuiGraphics g, float dt) {
        Power s = absorbing;
        absorbT += dt;
        int[][] b = body(cx(), gy());
        float hx = b[0][0] + b[0][2] / 2f, hy = b[0][1] + b[0][3] / 2f;
        int c = ink(s);
        if (absorbT < 0.7f) {
            float k = Math.min(1, absorbT / 0.6f);
            k = k * k * (3 - 2 * k);
            float x = Mth.lerp(k, sx0, hx), y = Mth.lerp(k, sy0, hy);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            item(g, s, x, y, Mth.lerp(k, 1.25f, 0.6f));
            g.pose().popPose();
            for (int i = 0; i < 3; i++) {
                float an = (float) (Math.random() * Mth.TWO_PI), rr = 30 + (float) Math.random() * 30;
                parts.add(new float[]{x + Mth.cos(an) * rr, y + Mth.sin(an) * rr, -Mth.cos(an) * rr * 2.2f, -Mth.sin(an) * rr * 2.2f, 0, 0.45f,
                        mix(c, WHITE, (float) Math.random() * 0.6f), 1});
            }
        } else if (flash == 0 && titleT < 0) {
            for (int i = 0; i < 140; i++) {
                float an = (float) (Math.random() * Mth.TWO_PI), sp = 20 + (float) Math.random() * 150;
                parts.add(new float[]{hx, hy, Mth.cos(an) * sp, Mth.sin(an) * sp - 20, 50, 0.8f + (float) Math.random() * 0.8f,
                        mix(c, WHITE, (float) Math.random() * 0.7f), Math.random() < 0.3 ? 2 : 1});
            }
            flash = 1;
            titleT = 0;
            titled = s;
            sel = s;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f));
        }
        if (flash > 0) {
            flash = Math.max(0, flash - dt * 2.2f);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            r(g, 0, 0, width, height, a(mix(c, WHITE, 0.6f), flash * 0.55f));
            g.pose().popPose();
        }
        if (absorbT > 2.2f) {
            absorbing = null;
            flash = 0;
        }
    }

    private void drawTitle(GuiGraphics g, float dt) {
        titleT += dt;
        float t = titleT;
        if (t > 2.9f || titled == null) {
            titleT = -1;
            return;
        }
        float al = t < 0.25f ? t / 0.25f : t > 2.4f ? Math.max(0, 1 - (t - 2.4f) / 0.5f) : 1;
        int c = ink(titled);
        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        float tx = narrow() ? width / 2f : cx();
        float ty = Math.max(74, gy() - 32 * unit - 70);
        GuiDraw.centeredText(g, font, Component.translatable("screen.battlegroundsmod.soul_book.absorbed"), tx, ty, 2f,
                a(mix(c, WHITE, 0.6f), al), true);
        GuiDraw.centeredText(g, font, titled.name(), tx, ty + 20, 1.25f, a(c, al), true);
        g.pose().popPose();
    }

    private void stepParts(GuiGraphics g, float dt) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 320);
        parts.removeIf(p -> {
            p[5] -= dt;
            p[3] += p[4] * dt;
            p[0] += p[2] * dt;
            p[1] += p[3] * dt;
            if (p[5] <= 0) {
                return true;
            }
            int sz = (int) p[7];
            r(g, Math.round(p[0]), Math.round(p[1]), sz, sz, a((int) p[6], Mth.clamp(p[5] * 1.6f, 0, 1)));
            return false;
        });
        g.pose().popPose();
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (phase == Phase.ENTER) {
            pt = Math.max(pt, 0.8f); // a click skips the dive
            return true;
        }
        if (phase != Phase.IN || absorbing != null || warpT >= 0 || button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        Hit best = null;
        for (Hit h : hits) {
            if (h.in(mx, my) && (best == null || h.depth > best.depth)) {
                best = h;
            }
        }
        if (best == null) {
            return false;
        }
        click();
        if (best.tab == -2) {
            Power wornP = currentPower();
            if (wornP != null) {
                com.starkinhon.battlegroundsmod.client.outfit.Outfits.toggleSelf(wornP.id());
            }
        } else if (best.tab >= 0) {
            tab = best.tab;
            if (tab == 1) {
                uni = null;
                sel = null;
            } else {
                Power cur = currentPower();
                sel = cur != null && !Powers.isSub(cur.id()) ? cur : Powers.divine().get(0);
            }
        } else if (best.cons != null) {
            warpT = 0;
            warpUni = best.cons;
            warpSwitched = false;
            uhover = null;
        } else if (best.back) {
            uni = null;
            sel = null;
        } else if (best.seal) {
            holding = true;
        } else if (best.awaken >= 0 && sel != null) {
            PacketDistributor.sendToServer(new ModPayloads.AwakenSkill(sel.id(), best.awaken));
            float x = best.x + 11, y = best.y + 11;
            for (int k = 0; k < 34; k++) {
                float an = (float) (Math.random() * Mth.TWO_PI), sp = 10 + (float) Math.random() * 50;
                parts.add(new float[]{x, y, Mth.cos(an) * sp, Mth.sin(an) * sp, 40, 0.8f, Math.random() < 0.5 ? 0x80FF20 : mix(ink(sel), WHITE, 0.4f), 1});
            }
        } else if (best.soul != null) {
            sel = best.soul;
        }
        return true;
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        holding = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Esc leaves the head the way it came (the dive backwards).
    @Override
    public void onClose() {
        if (phase == Phase.IN && absorbing == null) {
            phase = Phase.EXIT;
            pt = 1;
        } else if (phase == Phase.ENTER) {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        if (neb != null) {
            minecraft.getTextureManager().release(NEB);
            neb = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
