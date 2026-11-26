// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
import com.starkinhon.battlegroundsmod.client.ui.Px;

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

    // "Trocar de alma" (power/SoulSwapHint, Stark 2026-11-21): the server said to show the card; it waits until the dive
    // into the head is over, then stays on top of the book until "Entendi" (or Space, Enter, Esc).
    private static boolean swapHintPending = false;
    private long swapHintAt = -1;
    private float swapBx, swapBy, swapBw, swapBh;

    public static void showSwapHint() {
        // "Balões de dica" off (Experiência do jogador): the card is not shown (the server already marked it seen)
        swapHintPending = com.starkinhon.battlegroundsmod.client.fx.FxConfig.hints();
    }

    public static void clearSwapHint() {
        swapHintPending = false;
    }

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
        // Singleplayer (not open to LAN): the world freezes while the book is open (isPauseScreen), so freeze
        // the world sounds too, like the ESC menu does. Closing the screen resumes them (Minecraft.setScreen(null)).
        if (pausesWorld(mc)) {
            mc.getSoundManager().pause();
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.4f, 0.45f));
        // the server answers only when the "Trocar de alma" card is due (once per player)
        if (mc.getConnection() != null) {
            PacketDistributor.sendToServer(new ModPayloads.SoulSwapHint());
        }
    }

    // ---------- data ----------
    private String current() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getData(ModAttachments.POWER).power : "";
    }

    private Power currentPower() {
        return Powers.get(current());
    }

    private static int ink(Power p) {
        int c = SoulColors.of(p);
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
        if (p == Powers.INATA) {
            return SoulSync.innateAny(minecraft.player) || p.id().equals(current());
        }
        return !Progression.clientOn || minecraft.player.isCreative() || SoulSync.known(minecraft.player, p.id()) || p.id().equals(current());
    }

    private List<Power> orbit() {
        List<Power> list = new ArrayList<>(tab == 0 ? Powers.divine() : Powers.sub().stream().filter(p -> uniId(p).equals(uni)).toList());
        list.sort(Comparator.comparing(PowerSelectScreen::uniId));
        if (tab == 0 && minecraft != null && minecraft.player != null) {
            // The player's own kit with every technique a master taught (Kamehameha, Rasengan), to go back to it.
            for (Power p : Powers.innate()) {
                if (SoulSync.innateAny(minecraft.player) || p.id().equals(current())) {
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
                tint = rgb(SoulColors.sky(cur));
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
                nebVal[y * nebW + x] = Math.min(1, (float) Math.pow(Mth.clamp(n * 1.45f - 0.3f, 0, 1), 1.3) * (0.45f + band));
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
        // Lively in the soul's sky color (Stark 2026-11-22), and brighter still with the new chrome (Stark 2026-11-26:
        // "tá muito escuro os fundos"): the shades of the sky color's ramp, the dark end no longer near black.
        int[] sr = SoulPlate.rampOf(t, 0.55f);
        int deep = mix(sr[2], 0x04020C, 0.55f), midc = mix(sr[3], 0x0A0618, 0.2f), hot = mix(sr[5], WHITE, 0.15f);
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
        for (ChallengeSpec.Universe u : spec.universos().stream().filter(ChallengeSpec.Universe::world).toList()) {
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
            if (holding && sel != null && absorbing == null && (sel.id().equals(current()) || owned(sel))) {
                hold = Math.min(1, hold + dt / 0.8f);
                if (hold >= 1) {
                    if (sel.id().equals(current())) {
                        // the soul worn: let it go and fight with the base moves only (Stark 2026-11-19)
                        hold = 0;
                        holding = false;
                        PacketDistributor.sendToServer(new ModPayloads.SelectPower(""));
                    } else {
                        startAbsorb(sel);
                    }
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
        if (swapHintAt < 0 && swapHintPending && phase == Phase.IN && absorbing == null && warpT < 0) {
            swapHintPending = false;
            swapHintAt = now;
            holding = false;
            hold = 0;
        }
        if (swapHintAt >= 0 && phase == Phase.IN) {
            drawSwapHint(g, mx, my, now);
        }
    }

    // The teaching card of the introduction (IntroHud.CardScreen), on the menus' pixel grid: title, one line, the button.
    private void drawSwapHint(GuiGraphics g, int mx, int my, long now) {
        com.starkinhon.battlegroundsmod.client.ui.Px.begin(g, mx, my);
        try {
            float t = Mth.clamp((now - swapHintAt) / 250f, 0, 1);
            t = 1 - (1 - t) * (1 - t) * (1 - t);
            int gw = com.starkinhon.battlegroundsmod.client.ui.Px.GW, gh = com.starkinhon.battlegroundsmod.client.ui.Px.GH;
            int w = 210;
            List<String> lines = com.starkinhon.battlegroundsmod.client.ui.Px.wrap(
                    Component.translatable("screen.battlegroundsmod.soul_book.swap_hint.text").getString(), w - 24, 8);
            int h = 26 + lines.size() * 11 + 26;
            float x = Math.round(gw / 2f - w / 2f), y = Math.round(gh / 2f - h / 2f);
            com.starkinhon.battlegroundsmod.client.ui.Px.R(0, 0, gw, gh, com.starkinhon.battlegroundsmod.client.ui.Px.VEIL, 0.45f * t);
            int[] hr = SoulPlate.rampOf(0xB48CFF, 0.5f);
            g.setColor(1, 1, 1, t);
            SoulPlate.glow(x, y, w, h, 10, hr[4], 0.18f);
            SoulPlate.plate(x, y, w, h, 4, hr);
            g.setColor(1, 1, 1, 1);
            float yy = y + 8;
            com.starkinhon.battlegroundsmod.client.ui.Px.T(Component.translatable("screen.battlegroundsmod.soul_book.swap_hint").getString(),
                    x + w / 2f, yy, 0xFFF0C8, 13, true, 0, true, t);
            yy += 18;
            for (String l : lines) {
                com.starkinhon.battlegroundsmod.client.ui.Px.T(l, x + w / 2f, yy, 0xD6CEE8, 8, false, 0, true, t);
                yy += 11;
            }
            String label = Component.translatable("intro.battlegroundsmod.card.entendi").getString();
            swapBw = com.starkinhon.battlegroundsmod.client.ui.Px.tw(label, 8, true) + 30;
            swapBh = 16;
            swapBx = Math.round(x + w / 2f - swapBw / 2f);
            swapBy = yy + 2;
            boolean over = com.starkinhon.battlegroundsmod.client.ui.Px.over(swapBx, swapBy, swapBw, swapBh);
            int[] br = SoulPlate.ramp(42, 0.6f);
            g.setColor(1, 1, 1, t);
            if (over) {
                SoulPlate.glow(swapBx, swapBy, (int) swapBw, (int) swapBh, 6, br[5], 0.25f);
            }
            SoulPlate.plate(swapBx, swapBy, (int) swapBw, (int) swapBh, 2, over ? SoulPlate.lit(br, 0.4f) : br);
            g.setColor(1, 1, 1, 1);
            com.starkinhon.battlegroundsmod.client.ui.Px.T(label, x + w / 2f, swapBy + 4, 0xFFF0C8, 8, true, 0, true, 1);
        } finally {
            com.starkinhon.battlegroundsmod.client.ui.Px.end();
        }
    }

    private void closeSwapHint() {
        swapHintAt = -1;
        click();
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
            want = SoulColors.sky(hover);
        } else {
            want = show != null ? SoulColors.sky(show) : 0x9B30FF;
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
                // a dark disc and a light ring behind it: the soul no longer melts into a sky of its own color (Stark 2026-11-26)
                int[] hr = SoulPlate.ramp(show.id());
                for (int r2 = 5; r2 >= 1; r2--) {
                    SoulBookDraw.disc(g, Math.round(hx), Math.round(hy), 12 + r2 * 2, a(mix(hr[5], WHITE, 0.3f), 0.07f * alpha));
                }
                SoulBookDraw.disc(g, Math.round(hx), Math.round(hy), 12, a(0x06030E, 0.6f * alpha));
                for (int k2 = 0; k2 < 64; k2++) {
                    float an = k2 / 64f * Mth.TWO_PI;
                    r(g, Math.round(hx + Mth.cos(an) * 13), Math.round(hy + Mth.sin(an) * 13), 1, 1,
                            a(mix(hr[6], WHITE, 0.3f), (0.55f + 0.3f * Mth.sin(time * 2 + k2 * 0.4f)) * alpha));
                }
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

        // title, tabs, back and outfit: small plates on the pixel grid, like the hotbar (Stark 2026-11-26)
        g.setColor(1, 1, 1, alpha);
        Px.begin(g, mx, my);
        try {
            chrome(show != null ? show : cur, glowC);
        } finally {
            Px.end();
        }

        // card and tooltips
        List<Component> tip = null;
        if (show != null) {
            tip = card(g, show, mx, my);
        }
        g.setColor(1, 1, 1, 1);
        if (phase == Phase.IN && absorbing == null && warpT < 0) {
            if (hover != null) {
                List<Component> ls = new ArrayList<>();
                ls.add(hover.name().copy().withStyle(s -> s.withColor(ink(hover))));
                ls.add(Component.literal(uniName(hover)).withStyle(s -> s.withColor(0xA096B9)));
                for (int slot : hover == Powers.INATA ? SoulSync.taughtSlots(hover) : SoulSync.skills(hover)) {
                    boolean awake = SoulSync.awake(minecraft.player, hover, slot) && (hover.id().equals(current()) || !Progression.clientOn
                            || SoulSync.get(minecraft.player, hover.id()).has(slot) || slot == 0 || hover == Powers.INATA);
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

    // The title, the two tabs, the way back to the constellations and the outfit toggle, in grid pixels.
    private void chrome(Power s, int glowC) {
        int[] R = s != null ? SoulPlate.ramp(s.id()) : SoulPlate.rampOf(glowC, 0.5f);
        Px.T(Component.translatable("screen.battlegroundsmod.soul_book.title").getString().toUpperCase(), 8, 7,
                mix(R[5], WHITE, 0.55f), 12, true, -1, true, 1);
        float tx = 8;
        for (int i = 0; i < 2; i++) {
            String nm = Component.translatable(i == 0 ? "screen.battlegroundsmod.select_power.divine" : "screen.battlegroundsmod.select_power.sub").getString();
            int w = Px.tw(nm, 7, true) + 14;
            chip(tx, 31, w, 14, nm, R, tab == i, Px.over(tx, 31, w, 14), 7);
            if (phase == Phase.IN) {
                hits.add(chipHit(tx, 31, w, 14, i, false));
            }
            tx += w + 3;
        }
        if (tab == 1 && uni != null) {
            Cons c = cons.stream().filter(q -> q.id.equals(uni)).findFirst().orElse(null);
            int[] UR = c != null ? uniRamp(c.color) : R;
            String bt = Component.translatable("screen.battlegroundsmod.soul_book.back").getString();
            int w = Px.tw(bt, 6, true) + 14;
            chip(8, 49, w, 13, bt, UR, false, Px.over(8, 49, w, 13), 6);
            if (phase == Phase.IN && warpT < 0) {
                hits.add(chipHit(8, 49, w, 13, -1, true));
            }
            if (c != null) {
                Px.T(Component.translatable("screen.battlegroundsmod.soul_book.constellation", c.name).getString(), 12 + w, 52,
                        mix(UR[5], WHITE, 0.3f), 7, false, -1, true, 1);
            }
        }
        Power wornP = currentPower();
        if (wornP != null && com.starkinhon.battlegroundsmod.client.outfit.Outfits.exists(wornP.id())) {
            boolean on = com.starkinhon.battlegroundsmod.client.outfit.Outfits.selfOn();
            String ol = Component.translatable(on ? "screen.battlegroundsmod.skills.outfit_on" : "screen.battlegroundsmod.skills.outfit_off").getString();
            int ow = Px.tw(ol, 7, true) + 14;
            float ox = Px.GW - ow - 8;
            chip(ox, 8, ow, 14, ol, R, on, Px.over(ox, 8, ow, 14), 7);
            if (phase == Phase.IN) {
                hits.add(chipHit(ox, 8, ow, 14, -2, false));
            }
        }
    }

    // A small plate with its label: lit in the soul's shades when on, gray when off, half way under the mouse.
    private static void chip(float x, float y, int w, int h, String label, int[] R, boolean on, boolean hot, float px) {
        int[] gray = SoulPlate.ramp(230, 0.08f);
        int[] rr = on ? SoulPlate.lit(R, 0.3f) : gray.clone();
        if (!on && hot) {
            for (int i = 0; i < rr.length; i++) {
                rr[i] = mix(gray[i], R[i], 0.45f);
            }
        }
        SoulPlate.plate(x, y, w, h, 2, rr);
        Px.T(label, x + w / 2f + 0.5f, y + (h - px * 0.8f) / 2f, on || hot ? WHITE : 0xA0A0AA, px, true, 0, true, 1);
    }

    private static Hit chipHit(float x, float y, float w, float h, int tab, boolean back) {
        float f = Px.toGrid(1);
        return new Hit(Math.round(x / f), Math.round(y / f), Math.round(w / f), Math.round(h / f), null, 0, tab, null, back, false, -1);
    }

    // A universe's shades for its constellation and labels: its hue at a calm saturation, white ones as ice blue.
    private static int[] uniRamp(int rgb) {
        int r = rgb >> 16 & 255, gg = rgb >> 8 & 255, b = rgb & 255;
        int hi = Math.max(r, Math.max(gg, b)), lo = Math.min(r, Math.min(gg, b));
        if (hi + lo > 600) {
            return SoulPlate.ramp(205, 0.55f);
        }
        return SoulPlate.rampOf(rgb, Math.min(0.6f, 0.25f + (hi - lo) / 400f));
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
        // round glow of the soul's color behind it (pixel rows, no blur), stronger on hover: never lost in the sky. Full
        // strength color (SoulColors.vivid), as lively as the soul's palette (Stark 2026-11-22).
        int halo = SoulColors.vivid(p);
        for (int r2 = 4; r2 >= 1; r2--) {
            SoulBookDraw.disc(g, Math.round(x), Math.round(y), Math.round(8 * scale + r2 * 2), a(halo, (isH ? 0.17f : 0.11f) * depthA));
        }
        SoulBookDraw.disc(g, Math.round(x), Math.round(y), Math.round(8 * scale), a(mix(halo, WHITE, 0.25f), (isH ? 0.22f : 0.14f) * depthA));
        SoulBookDraw.disc(g, Math.round(x), Math.round(y), Math.round(6 * scale), a(0x06030E, 0.55f * depthA));
        int sz = Math.round(16 * scale);
        if (isS) {
            // a ring of light in its shades turning around it, no blinking frame (Stark 2026-11-26)
            int[] sr = SoulPlate.ramp(p.id());
            float rr = sz / 2f + 5;
            for (int k = 0; k < 48; k++) {
                float an = k / 48f * Mth.TWO_PI;
                boolean lit = (((an - time * 2) % Mth.TWO_PI) + Mth.TWO_PI) % Mth.TWO_PI < 1.4f;
                r(g, Math.round(x + Mth.cos(an) * rr), Math.round(y + Mth.sin(an) * rr), 1, 1, a(lit ? sr[6] : sr[4], (lit ? 0.95f : 0.4f) * depthA));
            }
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
    // The soul's card on the screens' pixel grid (client/ui/Px), same plate and glass as the hotbar and the Livro dos
    // Desafios (Stark 2026-11-26, preview "Livro das Almas"): the plate in the soul's shades with the light and the
    // stars on its edge, the Sincronia in a glass groove, each skill in a glass slot with its key on a tiny plate under
    // it, and the hold button as a lit plate with a soft glow. Layout in grid pixels; the hits go back to GUI pixels.
    private List<Component> card(GuiGraphics g, Power s, int mx, int my) {
        Px.begin(g, mx, my);
        try {
            return cardPx(s);
        } finally {
            Px.end();
        }
    }

    private Hit gridHit(float x, float y, float w, float h, int awaken, boolean seal) {
        float f = Px.toGrid(1);
        return new Hit(Math.round(x / f), Math.round(y / f), Math.round(w / f), Math.round(h / f), null, 0, -1, null, false, seal, awaken);
    }

    private static void pxLock(float x, float y, float a) {
        Px.R(x + 2, y, 4, 1, 0xAAAAB4, a);
        Px.R(x + 1, y + 1, 1, 3, 0xAAAAB4, a);
        Px.R(x + 6, y + 1, 1, 3, 0xAAAAB4, a);
        Px.R(x, y + 4, 8, 5, 0x3C3A44, a);
        Px.R(x + 1, y + 5, 6, 3, 0x6E6A78, a);
        Px.R(x + 1, y + 5, 6, 1, 0xA09CAA, a);
        Px.R(x + 3, y + 6, 2, 2, 0x14121A, a);
    }

    private static int[] dimRamp(int[] r) {
        int[] o = new int[r.length];
        for (int i = 0; i < r.length; i++) {
            o[i] = Px.mix(r[i], 0x3C3C42, 0.55f);
        }
        return o;
    }

    private List<Component> cardPx(Power s) {
        float f = Px.toGrid(1);
        int w = Math.round(cardW() * f);
        float x = Math.round(cardX() * f);
        int c = ink(s);
        int[] R = SoulPlate.ramp(s.id());
        List<Component> tip = null;
        List<String> desc = Px.wrap(s.description().getString(), w - 16, 7);
        int dl = Math.min(6, desc.size());
        List<Integer> skills = new ArrayList<>(SoulSync.skills(s));
        int counted = skills.size();
        // what a master teaches on top of the soul (Senhor Kaioh: Goku's B and Shift+V), shown after the rest
        for (int slot : SoulSync.taughtSlots(s)) {
            skills.add(slot);
        }
        int per = Math.max(1, Math.min(5, skills.size()));
        int rows = (skills.size() + per - 1) / per;
        int h = 62 + dl * 8 + rows * 30 + 42;
        float y = narrow() ? Math.round(Px.GH * 0.45f) : Math.max(40, Math.min(Math.round(Px.GH * 0.16f), Px.GH - h - 26));
        float t = Px.time;

        SoulPlate.glow(x, y, w, h, 10, R[4], 0.16f + 0.06f * Mth.sin(t * 1.3f));
        List<int[]> lit = SoulPlate.plate(x, y, w, h, 4, R);
        int tc = Px.mix(R[5], 0xFFFFFF, 0.3f), dim = Px.mix(R[5], 0x969696, 0.45f);
        float yy = y + 8;
        Px.T(Px.fit(s.name().getString(), w - 12, 10), x + w / 2f, yy, tc, 10, true, 0, true, 1);
        yy += 13;
        String kind = Component.translatable(Powers.isInnate(s.id()) ? "screen.battlegroundsmod.soul_book.inata"
                : Powers.isSub(s.id()) ? "screen.battlegroundsmod.soul_book.sub" : "screen.battlegroundsmod.soul_book.divine").getString();
        String un = uniName(s);
        Px.T(kind + (un.isEmpty() ? "" : " · " + un.toUpperCase()), x + w / 2f, yy, dim, 6, false, 0, true, 1);
        yy += 10;

        // Sincronia: a glass groove, green like the XP bar, with a glint running in the filled part
        SoulSync.Entry e = SoulSync.get(minecraft.player, s.id());
        boolean gated = Progression.clientOn;
        boolean isCur = s.id().equals(current());
        float bx = x + 12;
        int bw = w - 24;
        if (s == Powers.INATA) {
            Px.T(Component.translatable("screen.battlegroundsmod.soul_book.inata_info").getString(), x + w / 2f, yy + 3, dim, 6, false, 0, true, 1);
            yy += 14;
        } else if (gated) {
            Px.T(Component.translatable("screen.battlegroundsmod.soul_book.sync").getString(), bx, yy, R[5], 6, true, -1, true, 1);
            Px.T(Component.translatable("screen.battlegroundsmod.soul_book.level", e.level()).getString(), bx + bw, yy, 0x80FF20, 6, false, 1, true, 1);
            yy += 8;
            Px.R(bx, yy, bw, 6, R[0], 0.95f);
            Px.R(bx + 1, yy + 1, bw - 2, 4, Px.mix(R[1], R[0], 0.45f), 1);
            Px.R(bx + 1, yy + 5, bw - 2, 1, R[2], 0.6f);
            int fw = Math.round((bw - 2) * Mth.clamp(e.xp(), 0, 1));
            if (fw > 0) {
                Px.R(bx + 1, yy + 1, fw, 4, 0x5AC81E, 1);
                Px.R(bx + 1, yy + 1, fw, 1, 0xC8FF90, 1);
                int pos = (int) Math.floor((t * 40) % (fw + 30)) - 15;
                for (int k = 0; k < 6; k++) {
                    int xx = pos + k;
                    if (xx >= 0 && xx < fw) {
                        Px.R(bx + 1 + xx, yy + 1, 1, 4, 0xFFFFFF, 0.35f - Math.abs(k - 2.5f) * 0.1f);
                    }
                }
            }
            for (int i = 1; i < 10; i++) {
                Px.R(bx + 1 + (float) Math.floor(i * (bw - 2) / 10f), yy + 1, 1, 4, R[0], 0.45f);
            }
            yy += 9;
            String line;
            int lc;
            if (!owned(s)) {
                line = Component.translatable("screen.battlegroundsmod.soul_book.not_owned").getString();
                lc = 0xFF6060;
            } else if (e.points() > 0) {
                line = Component.translatable(e.points() > 1 ? "screen.battlegroundsmod.soul_book.points" : "screen.battlegroundsmod.soul_book.point", e.points()).getString();
                lc = Mth.sin(time * 5) > 0 ? 0x80FF20 : 0x5ABE1E;
            } else if (!isCur) {
                line = Component.translatable("screen.battlegroundsmod.soul_book.wear_to_sync").getString();
                lc = 0x9690AA;
            } else {
                line = Component.translatable("screen.battlegroundsmod.soul_book.fight_to_sync").getString();
                lc = 0x9690AA;
            }
            Px.T(Px.fit(line, w - 12, 6), x + w / 2f, yy, lc, 6, false, 0, true, 1);
            yy += 9;
        } else {
            Px.T(Px.fit(Component.translatable("screen.battlegroundsmod.soul_book.free").getString(), w - 12, 7), x + w / 2f, yy + 3, 0x80FF20, 7, false, 0, true, 1);
            yy += 14;
        }
        // a groove, then the description
        Px.R(x + 8, yy, w - 16, 1, R[0], 1);
        Px.R(x + 8, yy + 1, w - 16, 1, R[2], 1);
        yy += 6;
        int dc = Px.mix(R[6], 0xE6E2DC, 0.55f);
        for (int i = 0; i < dl; i++) {
            Px.T(desc.get(i), x + w / 2f, yy + i * 8, dc, 7, false, 0, true, 1);
        }
        yy += dl * 8 + 5;

        // skills: a glass slot with the icon (or the soul's crystal, faint), the key on a tiny plate under it
        int sw = 26;
        float gx = x + w / 2f - (per * sw - 6) / 2f;
        int awakeCount = 0;
        ItemStack crystal = ModItems.SOULS.containsKey(s.id()) ? new ItemStack(ModItems.SOULS.get(s.id()).get()) : ItemStack.EMPTY;
        for (int i = 0; i < skills.size(); i++) {
            int slot = skills.get(i);
            float sx = Math.round(gx + (i % per) * sw), sy = yy + (i / per) * 30;
            boolean master = SoulSync.taught(s, slot);
            boolean awake = s == Powers.INATA ? SoulSync.taughtKnown(minecraft.player, s, slot)
                    : master ? !gated || SoulSync.knowsTaught(minecraft.player) : !gated || slot == 0 || e.has(slot);
            if (awake && (!master || s == Powers.INATA)) {
                awakeCount++;
            }
            boolean next = gated && !awake && isCur && SoulSync.canAwaken(minecraft.player, s, slot);
            boolean hv = Px.over(sx - 2, sy - 2, 24, 32);
            int[] gr = hv ? SoulPlate.lit(R, 0.4f) : awake ? R : dimRamp(R);
            SoulPlate.glass(sx, sy, 18, gr);
            net.minecraft.resources.ResourceLocation icon = SkillIcons.icon(s, slot);
            if (icon != null) {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                Px.g.setColor(1, 1, 1, awake ? 1 : 0.3f);
                Px.g.blit(icon, Math.round(sx + 1), Math.round(sy + 1), 16, 16, 0, 0, 1, 1, 1, 1);
                Px.g.setColor(1, 1, 1, 1);
            } else if (!crystal.isEmpty()) {
                com.starkinhon.battlegroundsmod.client.ui.UiIcons.crystal(crystal, c, sx + 9, sy + 9, 10, awake ? 0.45f : 0.15f, false, false, 0);
            }
            Px.g.pose().pushPose();
            Px.g.pose().translate(0, 0, 250);
            if (!awake) {
                pxLock(sx + 5, sy + 4, 0.95f);
            }
            if (next) {
                int[] gn = Mth.sin(time * 6) > 0 ? SoulPlate.lit(SoulPlate.ramp(95, 0.8f), 0.3f) : SoulPlate.ramp(95, 0.8f);
                SoulPlate.plate(sx + 13, sy - 3, 7, 7, 1, gn);
                Px.R(sx + 16, sy - 2, 1, 5, 0xFFFFFF, 1);
                Px.R(sx + 14, sy, 5, 1, 0xFFFFFF, 1);
            }
            if (slot >= PowerData.SLOT_SHIFT) {
                // Shift variant: a little yellow arrow in the corner (Stark 2026-11-12), same key below
                float ax = sx + 13, ay = sy + 1;
                Px.R(ax + 2, ay, 1, 1, 0xFFD84A, 1);
                Px.R(ax + 1, ay + 1, 3, 1, 0xFFD84A, 1);
                Px.R(ax, ay + 2, 5, 1, 0xFFD84A, 1);
                Px.R(ax + 2, ay + 3, 1, 2, 0xFFD84A, 1);
            }
            if (hv) {
                Px.R(sx, sy, 18, 18, 0xFFFFFF, 0.18f);
            }
            Px.g.pose().popPose();
            String key = keyLabel(s, slot);
            int[] kc = hv ? SoulPlate.lit(R, 0.45f) : awake ? R : dimRamp(R);
            int kw = Math.max(11, Px.tw(key, 7, true) + 5);
            SoulPlate.plate(Math.round(sx + 9 - kw / 2f), sy + 20, kw, 10, 1, kc);
            Px.T(key, sx + 9.5f, sy + 21.5f, hv ? 0xFFFFFF : awake ? R[6] : 0x82808C, 7, true, 0, false, 1);
            if (hv) {
                tip = skillTip(s, slot, c, awake, master, next, isCur);
            }
            if (phase == Phase.IN && absorbing == null && warpT < 0) {
                hits.add(gridHit(sx - 2, sy - 2, 24, 32, next ? slot : -1, false));
            }
        }
        yy += rows * 30 + 1;
        Px.T(Component.translatable("screen.battlegroundsmod.soul_book.awake_count", awakeCount, s == Powers.INATA ? skills.size() : counted).getString(),
                x + w / 2f, yy, dim, 6, false, 0, true, 1);

        // the hold button: a lit plate with a soft glow, filling with a wave of light while held
        int bbw = w - 20, bh = 17;
        float bbx = x + 10, by = y + h - bh - 7;
        boolean can = isCur || owned(s);
        boolean hot = can && Px.over(bbx, by, bbw, bh);
        String label = Component.translatable(isCur ? (hold > 0 ? "screen.battlegroundsmod.soul_book.releasing" : "screen.battlegroundsmod.soul_book.release")
                : !owned(s) ? "screen.battlegroundsmod.soul_book.not_owned_short"
                : hold > 0 ? "screen.battlegroundsmod.soul_book.absorbing" : "screen.battlegroundsmod.soul_book.hold").getString();
        if (!can) {
            int[] gr = new int[7];
            for (int i = 0; i < 7; i++) {
                gr[i] = Px.mix(R[i], 0x28282C, 0.6f);
            }
            SoulPlate.plate(bbx, by, bbw, bh, 2, gr);
            Px.T(Px.fit(label, bbw - 8, 7), bbx + bbw / 2f, by + 5, 0x96929C, 7, true, 0, true, 1);
        } else {
            SoulPlate.glow(bbx, by, bbw, bh, 6, R[6], 0.45f + 0.2f * Mth.sin(t * 2.4f));
            List<int[]> bl = SoulPlate.plate(bbx, by, bbw, bh, 2, SoulPlate.lit(R, hot || hold > 0 ? 0.5f : 0.25f));
            lit.addAll(bl);
            if (hold > 0) {
                float fill = Math.min(1, hold) * (bbw - 4);
                Px.R(bbx + 2, by + 2, fill, bh - 4, R[5], 0.45f);
                float ex = bbx + 2 + fill;
                for (int j = 2; j < bh - 2; j++) {
                    Px.P(ex + Mth.sin(j * 0.9f + t * 10) * 1.2f, by + j, R[6], 0.95f);
                }
            }
            Px.T(Px.fit(label, bbw - 8, 8), bbx + bbw / 2f + 0.5f, by + 4.5f, 0xFFFFFF, 8, true, 0, true, 1);
            if (phase == Phase.IN && absorbing == null && warpT < 0) {
                hits.add(gridHit(bbx, by, bbw, bh, -1, true));
            }
        }
        SoulPlate.sweep(lit, t, R[6], 1);
        SoulPlate.twinkle(lit, t, s.id().hashCode(), R, 5);
        return tip;
    }

    // A skill's tooltip: name, tags, cooldown, what it does (blank lines in the text keep their gap) and why it is locked.
    private List<Component> skillTip(Power s, int slot, int c, boolean awake, boolean master, boolean next, boolean isCur) {
        List<Component> tip = new ArrayList<>();
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
        for (String para : SkillIcons.description(s, slot).getString().split("\n", -1)) {
            if (para.isBlank()) {
                tip.add(Component.empty());
                continue;
            }
            for (FormattedCharSequence l : font.split(Component.literal(para), 180)) {
                StringBuilder sb = new StringBuilder();
                l.accept((idx, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                tip.add(Component.literal(sb.toString()).withStyle(st -> st.withColor(0xCDC6DC)));
            }
        }
        if (s == Powers.INATA) {
            tip.add(Component.translatable((awake ? "screen.battlegroundsmod.soul_book.taught_inata." : "screen.battlegroundsmod.soul_book.locked_inata.") + slot)
                    .withStyle(st -> st.withColor(awake ? 0xFFD84A : 0xFF6060)));
        } else if (master) {
            tip.add(Component.translatable(awake ? "screen.battlegroundsmod.soul_book.taught_kaioh" : "screen.battlegroundsmod.soul_book.locked_kaioh")
                    .withStyle(st -> st.withColor(awake ? 0xFFD84A : 0xFF6060)));
        } else if (!awake) {
            String key = next ? "screen.battlegroundsmod.soul_book.click_awaken"
                    : !isCur ? "screen.battlegroundsmod.soul_book.locked_wear"
                    : PowerData.isUlt(slot) ? "screen.battlegroundsmod.soul_book.locked_ult"
                    : "screen.battlegroundsmod.soul_book.locked_sync";
            tip.add(Component.translatable(key).withStyle(st -> st.withColor(next ? 0x80FF20 : 0xFF6060)));
        }
        return tip;
    }

    // ---------- constellations ----------
    private String constellations(GuiGraphics g, float alpha) {
        r(g, 0, 0, width, height, a(0x030208, 0.25f * alpha));
        int ax = 14, ay = 76, aw = (narrow() ? width : cardX()) - ax - 14, ah = height - ay - 30;
        GuiDraw.text(g, font, Component.translatable("screen.battlegroundsmod.soul_book.pick_constellation"), ax, ay - 18, 0.75f, a(0xC8C2D6, alpha), true);
        float[][] spots = {{.18f, .22f}, {.55f, .16f}, {.86f, .3f}, {.34f, .6f}, {.72f, .66f}, {.12f, .82f}, {.5f, .92f}};
        for (int i = 0; i < cons.size(); i++) {
            Cons c = cons.get(i);
            float[] sp = spots[i % spots.length];
            int cx = Math.round(ax + aw * sp[0]), cy = Math.round(ay + ah * sp[1]);
            consPos.put(c.id, new int[]{cx, cy});
            boolean on = !c.subs.isEmpty();
            boolean hv = c.id.equals(uhover);
            // continuous lines and crisp cross stars with a white heart, no dark outline (Stark 2026-11-26)
            float base = (on ? 1 : 0.35f) * alpha;
            int[] UR = uniRamp(c.color);
            int col = UR[5];
            for (int[] l : c.ln) {
                float[] p = c.st[l[0]], q = c.st[l[1]];
                float len = (float) Math.hypot(q[0] - p[0], q[1] - p[1]);
                int lc = a(mix(col, WHITE, hv ? 0.45f : 0.25f), base * (hv ? 0.95f : 0.75f));
                for (float t = 0; t < len; t += 1) {
                    float f = t / len;
                    r(g, Math.round(cx + p[0] + (q[0] - p[0]) * f), Math.round(cy + p[1] + (q[1] - p[1]) * f), 1, 1, lc);
                }
            }
            for (int j = 0; j < c.st.length; j++) {
                float[] p = c.st[j];
                float tw = 0.6f + 0.4f * Mth.sin(time * 2.4f + j * 1.7f + i);
                int x = Math.round(cx + p[0]), y = Math.round(cy + p[1]);
                int z = (int) p[2] + (hv ? 1 : 0);
                for (int r2 = 3; r2 >= 2; r2--) {
                    r(g, x - r2, y - r2, 2 * r2 + 1, 2 * r2 + 1, a(UR[4], (hv ? 0.16f : 0.09f) * base));
                }
                int cc = mix(col, WHITE, 0.6f);
                int arm = a(cc, base * (0.75f + 0.25f * tw));
                r(g, x - z, y, 2 * z + 1, 1, arm);
                r(g, x, y - z, 1, 2 * z + 1, arm);
                int near = a(mix(cc, WHITE, 0.5f), base);
                r(g, x - 1, y, 1, 1, near);
                r(g, x + 1, y, 1, 1, near);
                r(g, x, y - 1, 1, 1, near);
                r(g, x, y + 1, 1, 1, near);
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
                    on ? a(hv ? WHITE : mix(UR[6], WHITE, 0.2f), alpha) : a(0x787682, alpha), true);
            GuiDraw.centeredText(g, font, on ? Component.translatable("screen.battlegroundsmod.soul_book.subs_count", c.subs.size())
                    : Component.translatable("screen.battlegroundsmod.soul_book.soon"), cx, by + 8, 0.6f, on ? a(mix(UR[5], 0xC8C8C8, 0.3f), alpha) : a(0x646270, alpha), true);
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
        if (swapHintAt >= 0) {
            float gx = com.starkinhon.battlegroundsmod.client.ui.Px.toGrid(mx), gy = com.starkinhon.battlegroundsmod.client.ui.Px.toGrid(my);
            if (button == 0 && Util.getMillis() - swapHintAt > 300 && gx >= swapBx && gx <= swapBx + swapBw && gy >= swapBy && gy <= swapBy + swapBh) {
                closeSwapHint();
            }
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
        if (swapHintAt >= 0) {
            if (Util.getMillis() - swapHintAt > 300 && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
                closeSwapHint();
            }
            return true;
        }
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

    // Pauses the game in singleplayer so nothing can attack the player while the book is open.
    // Minecraft itself ignores this in multiplayer and on a LAN world, where the book keeps the world running.
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // Same rule as the ESC menu: only an integrated server that is not published to LAN pauses.
    private static boolean pausesWorld(net.minecraft.client.Minecraft mc) {
        return mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null
                && !mc.getSingleplayerServer().isPublished();
    }
}
