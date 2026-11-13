package com.starkinhon.battlegroundsmod.client;

import static com.starkinhon.battlegroundsmod.client.ui.Px.GH;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GOLD;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GW;
import static com.starkinhon.battlegroundsmod.client.ui.Px.MUTED;
import static com.starkinhon.battlegroundsmod.client.ui.Px.P;
import static com.starkinhon.battlegroundsmod.client.ui.Px.R;
import static com.starkinhon.battlegroundsmod.client.ui.Px.RED;
import static com.starkinhon.battlegroundsmod.client.ui.Px.T;
import static com.starkinhon.battlegroundsmod.client.ui.Px.TEXT;
import static com.starkinhon.battlegroundsmod.client.ui.Px.WHITE;
import static com.starkinhon.battlegroundsmod.client.ui.Px.c;
import static com.starkinhon.battlegroundsmod.client.ui.Px.clamp;
import static com.starkinhon.battlegroundsmod.client.ui.Px.diamond;
import static com.starkinhon.battlegroundsmod.client.ui.Px.ink;
import static com.starkinhon.battlegroundsmod.client.ui.Px.lerp;
import static com.starkinhon.battlegroundsmod.client.ui.Px.mix;
import static com.starkinhon.battlegroundsmod.client.ui.Px.rgb;
import static com.starkinhon.battlegroundsmod.client.ui.Px.tw;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.client.ui.BookData;
import com.starkinhon.battlegroundsmod.client.ui.BookData.Ch;
import com.starkinhon.battlegroundsmod.client.ui.BookData.Uni;
import com.starkinhon.battlegroundsmod.client.ui.IdlePoses;
import com.starkinhon.battlegroundsmod.client.ui.Px;
import com.starkinhon.battlegroundsmod.client.ui.SkinDraw;
import com.starkinhon.battlegroundsmod.client.ui.UiIcons;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// The Livro dos Desafios as a fight select screen (approved in the preview, Stark 2026-11-13: "você pegou perfeitamente a
// vibe", the Souls look: every challenger carries its soul, beating it = taking the soul). One tab per universe (its boss's
// head and how many were beaten), the ladder of challengers at the bottom (weak to boss, the bosses behind a chain until
// enough SubAlmas are beaten), the challenger in 3D in the middle over a gradient of its color, standing in its own still
// pose with its little moves (Dragon Ball for now, client/ui/IdlePoses), its soul floating over its head, and the panel on
// the right: name, tier, the place of its arena, what its power is about, its skills (Z X C V B), the rewards, the best time
// with the medals, and TOMAR A ALMA, held down. Holding it plays the VS (the screen split on a diagonal, you against it,
// the arena's name, a white flash) and the flash takes you to the arena (challenge/ChallengeArenas). Inside the arenas the
// book also offers the way back. Drawn on the preview's pixel grid (client/ui/Px), same numbers as the preview.
public class ChallengeScreen extends Screen {
    private static final int SOUL_C = rgb(200, 170, 255);
    private static final String[] KEYS = {"Z", "X", "C", "V", "B"};

    private final String universeId;
    private int uIdx;
    private Ch sel, hov, shownFor, prevThumb;
    private float shownT, prevT = 1, bgT, fightHold;
    private boolean down;
    private float frameDt;
    private long last;
    // the VS playing (null = the book)
    private Ch vs;
    private float vsT;
    private boolean vsSent;

    public ChallengeScreen(String universe) {
        super(Component.translatable("screen.battlegroundsmod.challenges"));
        this.universeId = universe;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("screen.battlegroundsmod.livro." + key, args).getString();
    }

    private boolean inArena() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ChallengeArenas.isArena(mc.player.level().dimension());
    }

    @Override
    protected void init() {
        List<Uni> unis = BookData.unis();
        if (sel == null && !unis.isEmpty()) {
            int start = 0;
            for (int i = 0; i < unis.size(); i++) {
                if (unis.get(i).id.equals(universeId)) {
                    start = i;
                }
            }
            pickUni(start);
        }
        last = System.nanoTime();
    }

    private void pickUni(int i) {
        uIdx = i;
        Uni u = BookData.unis().get(i);
        sel = u.desafios.get(0);
        for (Ch c : u.desafios) {
            if (!BookData.won(c.id()) && !BookData.locked(c)) {
                sel = c;
                break;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // the book draws its own background
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        frameDt = dt;
        if (BookData.unis().isEmpty()) {
            return;
        }
        Px.begin(graphics, mouseX, mouseY);
        Px.hits.clear();
        try {
            if (vsWhite) {
                R(0, 0, GW, GH, WHITE, 1);
            } else if (vs != null) {
                drawVS(dt);
            } else {
                drawLivro(dt);
                Px.drawTip();
            }
        } finally {
            Px.end();
        }
    }

    // ------------------------------------------------------------------------------------------------ the book

    private void drawLivro(float dt) {
        List<Uni> unis = BookData.unis();
        Uni u = unis.get(uIdx);
        Ch show = hov != null ? hov : sel;
        if (show != shownFor) {
            prevThumb = shownFor;
            prevT = 0;
            shownFor = show;
            shownT = 0;
        }
        prevT += dt * 3;
        shownT += dt;
        bgT += dt;
        R(0, 0, GW, GH, rgb(7, 6, 12), 1);
        // the gradient of the character's color: light around it, almost black at the edges (changes softly)
        {
            int pw0 = Math.min(196, Math.round(GW * 0.4f)), px0 = GW - pw0 - 10, fy0 = Math.round(GH - 60 - 46);
            float k0 = Math.min((fy0 - 40 - 30) / 33f, (px0 - 20) / 20f);
            float cx0 = Math.round((px0 - 10) * 0.5f) + 8, cy0 = fy0 - 17 * k0;
            int col = prevThumb != null && prevT < 1 ? mix(prevThumb.cor(), show.cor(), clamp(prevT, 0, 1)) : show.cor();
            float pulse = 1 + 0.03f * (float) Math.sin(bgT * 1.1f), r1 = Math.max(GW, GH) * 0.62f * pulse;
            R(0, 0, GW, GH, rgb(4, 3, 8), 1);
            Px.glow(cx0, cy0, r1 * 1.25f, mix(col, Px.BLACK, 0.78f), 1);
            Px.glow(cx0, cy0, r1 * 0.75f, mix(col, Px.BLACK, 0.45f), 0.9f);
            Px.glow(cx0, cy0, r1 * 0.36f, mix(col, Px.BLACK, 0.18f), 0.8f);
            // the floor: an ellipse of light under the feet
            var pose = Px.g.pose();
            pose.pushPose();
            pose.translate(cx0, fy0, 0);
            pose.scale(1, 0.25f, 1);
            Px.glow(0, 0, 30 * k0, mix(col, WHITE, 0.25f), 0.35f);
            pose.popPose();
            for (int i = 0; i < GH; i += 2) {
                float kk = i / (float) GH;
                R(0, i, GW, 2, rgb(5, 4, 10), 0.35f * (float) Math.pow(Math.abs(kk - 0.45f) * 1.8f, 2));
            }
        }
        for (int i = 0; i < GW * 0.5f; i += 2) {
            R(GW - i - 2, 0, 2, GH, rgb(5, 4, 10), 0.6f * (float) Math.pow(1 - i / (GW * 0.5f), 1.4));
        }
        // ---- top: title and tabs (one line; the open tab shows its name) ----
        String title = tr("titulo");
        T(title, 10, 6, mix(GOLD, WHITE, 0.25f), 12, true);
        List<Ch> all = new ArrayList<>();
        for (Uni un : unis) {
            all.addAll(un.desafios);
        }
        int won = 0;
        for (Ch c : all) {
            if (BookData.won(c.id())) {
                won++;
            }
        }
        UiIcons.crystal(null, rgb(0xb0, 0x7a, 0xff), 14, 24, 7, 1, false, false, 0);
        String taken = tr("almas", won, all.size());
        T(taken, 22, 20, SOUL_C, 7);
        float segW = Math.min(140, GW * 0.28f);
        for (int i = 0; i < all.size(); i++) {
            float sx = 10 + (float) Math.floor(i * segW / all.size());
            R(sx, 29, Math.max(1, (float) Math.floor(segW / all.size()) - 1), 2, BookData.won(all.get(i).id()) ? c(GOLD, 1) : c(WHITE, 0.12f));
        }
        int[] widths = new int[unis.size()];
        int tot = 0;
        for (int i = 0; i < unis.size(); i++) {
            widths[i] = i == uIdx ? Math.max(tw(unis.get(i).nome, 8, true), tw(tr("derrotados", 0, 0), 6)) + 32 : 24;
            tot += widths[i] + 3;
        }
        float tx = Math.max(10 + tw(title, 12, true) + 14, GW - 10 - tot);
        int ty = 6;
        for (int i = 0; i < unis.size(); i++) {
            Uni un = unis.get(i);
            int col = un.cor, w = widths[i], hh = 24;
            boolean act = i == uIdx, over = Px.over(tx, ty, w, hh);
            Px.smallFrame(tx, ty, w, hh, col, act || over, act ? 0.95f : 0.8f);
            Ch boss = un.desafios.get(un.desafios.size() - 1);
            for (Ch d : un.desafios) {
                if (d.boss()) {
                    boss = d;
                    break;
                }
            }
            int b = 0;
            for (Ch d : un.desafios) {
                if (BookData.won(d.id())) {
                    b++;
                }
            }
            boolean full = b == un.desafios.size();
            SkinDraw.head(boss.skin(), tx + 12, ty + 10, act ? 1.25f : 1.1f, 0xFFFFFFFF);
            if (act) {
                T(un.nome, tx + 24, ty + 4, ink(col), 8, true);
                T(tr("derrotados", b, un.desafios.size()), tx + 24, ty + 14, full ? GOLD : MUTED, 6);
                R(tx + 2, ty + hh - 2, w - 4, 1, col, 1);
            } else {
                T(b + "/" + un.desafios.size(), tx + 12, ty + 17, full ? GOLD : MUTED, 5, false, 0, true, 1);
            }
            final int idx = i;
            Px.hit(tx, ty, w, hh, () -> {
                pickUni(idx);
                click();
            }, act ? null : List.of(un.nome, tr("de_derrotados", b, un.desafios.size())), List.of(WHITE, MUTED));
            tx += w + 3;
        }
        // ---- the panel on the right: the challenger ----
        int top = 38, ladderY = GH - 60;
        int pw = Math.min(196, Math.round(GW * 0.4f)), px = GW - pw - 10, py = top + 6;
        // the longest ladder of all the universes sets the layout, so the squares are the same size in every tab
        int nL = 1;
        for (Uni other : BookData.unis()) {
            nL = Math.max(nL, other.desafios.size());
        }
        int availL = px - 36 - 14 - 38, fitL = availL / nL - 4;
        boolean under = fitL < 24;
        int ladW = under ? GW - 60 : Math.min(availL, nL * 46), ph = ladderY - py - (under ? 20 : 8);
        drawDetail(show, px, py, pw, ph);
        // ---- the challenger in 3D in the middle ----
        float lx = Math.round((px - 10) * 0.5f) + 8, fy = Math.round(ladderY - 46f);
        drawStage(show, lx, fy, Math.min((fy - top - 2 - 30) / 33f, (px - 20) / 20f));
        drawWisps(show.cor(), 0.9f);
        // ---- the ladder (bottom) ----
        drawLadder(u, 36, ladderY, ladW);
        // ---- inside the arenas: the way back ----
        if (inArena()) {
            String back = tr("voltar");
            int w = tw(back, 8) + 20;
            float bx = GW - w - 10, by = GH - 22;
            boolean over = Px.over(bx, by, w, 14);
            Px.button(bx, by, w, 14, rgb(140, 150, 170), over);
            T(back, bx + w / 2f, by + 3, over ? WHITE : TEXT, 8, false, 0, true, 1);
            Px.hit(bx, by, w, 14, () -> {
                PacketDistributor.sendToServer(new ModPayloads.ChallengeLeave());
                onClose();
            });
        }
    }

    // Soul wisps rising over the screen (the color of the soul in focus).
    private void drawWisps(int col, float a) {
        for (int i = 0; i < 70; i++) {
            long s0 = i * 7L + 1;
            float[] r = rng(s0, 5);
            float x0 = r[0], sp = 0.3f + r[1] * 0.7f, off = r[2], w = r[3] * 6.28f, z = r[4];
            float t = (Px.time * 0.05f * sp + off) % 1;
            float x = x0 * GW + (float) Math.sin(Px.time * sp + w) * 8, y = GH * (1.05f - t * 1.1f);
            float al = (float) Math.sin(t * Math.PI) * (0.25f + z * 0.45f) * a;
            int cc = mix(col, WHITE, 0.4f + z * 0.4f);
            P(x, y, cc, al);
            if (z > 0.5f) {
                P(x, y + 1, cc, al * 0.6f);
                P(x, y + 2, cc, al * 0.3f);
            }
        }
    }

    // The preview's small xorshift random (same numbers for the same seed).
    private static float[] rng(long seed, int n) {
        int s = (int) (seed == 0 ? 1 : seed);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            s ^= s << 13;
            s ^= s >>> 17;
            s ^= s << 5;
            out[i] = (Integer.toUnsignedLong(s) % 100000) / 100000f;
        }
        return out;
    }

    private void drawStage(Ch c, float cx, float fy, float k) {
        int col = c.cor();
        boolean lk = BookData.locked(c);
        float t = shownT;
        // the base: rings of light on the floor in the character's color
        for (int i = 0; i < 3; i++) {
            float r = (8 + i * 2.2f) * k + (float) Math.sin(Px.time * 2 + i) * 1.5f;
            for (int a = 0; a < 96; a++) {
                double an = a / 96.0 * Math.PI * 2;
                P(cx + (float) Math.cos(an) * r, fy + (float) Math.sin(an) * r * 0.28f, mix(col, WHITE, 0.3f), 0.5f - i * 0.15f);
            }
        }
        R(cx - 6 * k, fy - 1, 12 * k, 3, Px.BLACK, 0.45f);
        float intro = Px.easeBack(t / 0.35f);
        float breathe = (float) Math.sin(Px.time * 2.2f) * 0.04f;
        SkinDraw.Pose ip = lk ? null : IdlePoses.pose(c.id(), Px.time);
        SkinDraw.Pose pose;
        float lift = 0;
        if (ip != null) {
            pose = ip.copy();
            pose.yaw = -0.55f + ip.yaw;
            pose.pitch = 0.12f;
            lift = ip.lift;
            pose.lift = 0;
        } else {
            pose = new SkinDraw.Pose(-0.55f + (float) Math.sin(Px.time * 0.5f) * 0.08f, 0.12f);
            pose.rarm = new float[] {-0.15f + breathe, -0.12f};
            pose.larm = new float[] {0.12f - breathe, 0.12f};
            pose.head = (float) Math.sin(Px.time * 0.7f) * 0.04f;
        }
        float kk = k * clamp(intro, 0.6f, 1);
        SkinDraw.draw(c.skin(), cx, fy - (1 - intro) * 10 - lift * kk, kk, pose, SkinDraw.ALL, lk ? 0xF5080610 : 0xFFFFFFFF);
        if (!lk) {
            // its soul floating over its head; taken = it is yours
            boolean wonS = BookData.won(c.id());
            float ax = cx, ay = fy - 34 * k - 14 + (float) Math.sin(Px.time * 1.6f) * 2;
            float sz = Math.round(clamp(k * 6, 14, 26));
            if (!wonS) {
                for (int i = 0; i < 5; i++) {
                    float an = Px.time * 1.4f + i * 1.256f, rr = sz * 0.95f;
                    P(ax + (float) Math.cos(an) * rr, ay + (float) Math.sin(an) * rr * 0.4f, mix(col, WHITE, 0.6f), 0.8f);
                }
            }
            UiIcons.crystal(c.soul(), col, ax, ay, sz, wonS ? 0.35f : 1, !wonS, false, c.id().length());
            T(wonS ? tr("alma_tomada") : tr("alma"), ax, ay - sz / 2 - 10, wonS ? rgb(150, 140, 170)
                    : mix(ink(col), WHITE, 0.45f), 6, false, 0, true, 1);
        } else {
            // two crossed chains over the silhouette, link by link, and the padlock in the middle
            for (int dir : new int[] {1, -1}) {
                float x0 = cx - 15 * k * dir, y0 = fy - 29 * k, x1 = cx + 15 * k * dir, y1 = fy - 5 * k;
                float len = (float) Math.hypot(x1 - x0, y1 - y0);
                int n = (int) Math.floor(len / 4.5f);
                for (int i = 0; i <= n; i++) {
                    float p = i / (float) n, x = lerp(x0, x1, p), y = lerp(y0, y1, p) + (float) Math.sin(Px.time * 1.5f + i * 0.7f) * 0.4f;
                    boolean big = i % 2 == 0;
                    R(x - (big ? 3 : 1), y - 2, big ? 6 : 2, big ? 4 : 3, big ? rgb(0xa4, 0xa4, 0xb2) : rgb(0x70, 0x70, 0x7c), 1);
                    if (big) {
                        R(x - 1, y - 1, 2, 2, rgb(0x24, 0x22, 0x2e), 1);
                        P(x - 3, y - 2, rgb(0xe0, 0xe0, 0xec), 1);
                    }
                }
            }
            padlock(Math.round(cx - 6), Math.round(fy - 21 * k));
        }
        String nm = lk ? "???" : c.nome();
        T(nm, cx, fy + 6, lk ? MUTED : ink(col), 14, true, 0, true, 1);
        T(lk ? tr("chefe_trancado") : (c.boss() ? tr("chefe") : tr("subalma")) + " · " + c.alma(), cx, fy + 22, lk ? rgb(255, 120, 110) : MUTED, 7,
                false, 0, true, 1);
    }

    private static void padlock(int lx, int ly) {
        int gold = rgb(0xc9, 0xa2, 0x4a);
        R(lx + 2, ly - 6, 8, 2, gold, 1);
        R(lx + 1, ly - 5, 2, 6, gold, 1);
        R(lx + 9, ly - 5, 2, 6, gold, 1);
        R(lx - 1, ly, 14, 11, gold, 1);
        R(lx, ly + 1, 12, 9, rgb(0x8a, 0x6a, 0x2a), 1);
        R(lx + 5, ly + 3, 2, 4, rgb(0x1a, 0x14, 0x08), 1);
        P(lx, ly + 1, rgb(0xff, 0xe9, 0xa8), 1);
    }

    private static int tierChip(float x, float y, int tier, int col) {
        String t = ("Tier " + tier + " · " + BookData.tierName(tier)).toUpperCase();
        int w = tw(t, 7) + 8;
        Px.smallFrame(x, y, w, 11, col, true, 1);
        T(t, x + 4, y + 2, mix(ink(col), WHITE, 0.3f), 7);
        return w;
    }

    private static String fmt(int secs) {
        return secs / 60 + ":" + String.format("%02d", secs % 60);
    }

    private void drawDetail(Ch c, int x, int y, int w, int h) {
        int col = c.cor();
        boolean lk = BookData.locked(c), won = BookData.won(c.id());
        Uni u = c.uni();
        Px.frame(x, y, w, h, col, 0.9f, true, true, null);
        float yy = y + 8;
        T(lk ? "???" : c.nome(), x + w / 2f, yy, lk ? MUTED : mix(ink(col), WHITE, 0.15f), 10, true, 0, true, 1);
        yy += 13;
        String chip = ("Tier " + c.tier() + " · " + BookData.tierName(c.tier())).toUpperCase();
        int cw = tw(chip, 7) + 8;
        tierChip(Math.round(x + w / 2f - cw / 2f), yy, c.tier(), col);
        yy += 16;
        {
            String place = lk ? tr("arena_escondida") : c.lugar().isEmpty() ? tr("arena") : c.lugar();
            int pw = tw(place, 7);
            Px.sight(x + w / 2f - pw / 2f - 10, yy, rgb(255, 236, 180), 1);
            T(place, x + w / 2f + 1, yy, rgb(255, 236, 180), 7, false, 0, true, 1);
        }
        yy += 12;
        R(x + 8, yy, w - 16, 1, col, 0.4f);
        yy += 6;
        // what the character is about: loses lines before the skills and the rewards go under the button (big GUI)
        int nSk0 = lk ? 0 : c.skills().size();
        float minNeed = 22 + (nSk0 > 0 ? (float) Math.ceil(nSk0 / 2f) * 11 + 3 + 11 : 0) + 12;
        List<String> fl = new ArrayList<>(Px.wrap(lk ? tr("chefe_espera") : c.foco(), w - 16, 7));
        while (fl.size() > 4) {
            fl.remove(fl.size() - 1);
        }
        while (!fl.isEmpty() && (y + h - 26) - (yy + fl.size() * 9 + 2) < minNeed) {
            if (fl.size() > 1) {
                fl.set(fl.size() - 2, fl.get(fl.size() - 2).replaceAll("[.,]?$", "…"));
            }
            fl.remove(fl.size() - 1);
        }
        for (String l : fl) {
            T(l, x + 8, yy, rgb(214, 206, 228), 7);
            yy += 9;
        }
        yy += 2;
        // the space left decides how the record, the rewards and the skills are laid out
        int nSk = lk ? 0 : c.skills().size();
        int nRw = 2 + c.itens().size();
        float avail = (y + h - 26) - yy - (nSk > 0 ? 11 : 0) - 10 - 8;
        String[] lay = {"line", "slots", "grid"};
        String[][] tries = {{"0", "big"}, {"1", "rows"}, {"2", "list"}, {"2", "desc"}};
        for (String[] tryv : tries) {
            int idx = Integer.parseInt(tryv[0]);
            String old = lay[idx];
            lay[idx] = tryv[1];
            if (need(lay, nRw, nSk) > avail) {
                lay[idx] = old;
            }
        }
        if (!lk && nSk > 0) {
            T(tr("golpes"), x + 8, yy, GOLD, 7, true);
            yy += 11;
            boolean withDesc = lay[2].equals("desc"), list = !lay[2].equals("grid");
            if (!list) {
                int colW = (w - 16) / 2;
                for (int i = 0; i < c.skills().size(); i++) {
                    BookData.Skill sk = c.skills().get(i);
                    float kx = x + 8 + (i % 2) * colW, ky = yy + (i / 2) * 11;
                    Px.smallFrame(kx, ky, 10, 10, col, true, 1);
                    T(KEYS[sk.key()], kx + 5, ky + 1, WHITE, 7, true, 0, true, 1);
                    T(Px.fit(sk.name(), colW - 16, 7), kx + 13, ky + 1, TEXT, 7);
                    skillTip(sk, col, kx, ky, colW - 2, 10);
                }
                yy += (float) Math.ceil(nSk / 2f) * 11 + 3;
            } else {
                for (BookData.Skill sk : c.skills()) {
                    int rh = withDesc ? 18 : 11;
                    Px.smallFrame(x + 8, yy, 12, 12, col, true, 1);
                    T(KEYS[sk.key()], x + 14, yy + 2, WHITE, 8, true, 0, true, 1);
                    T(Px.fit(sk.name(), w - 34, 8), x + 24, yy + 1, mix(ink(col), WHITE, 0.55f), 8);
                    if (withDesc) {
                        T(Px.fit(sk.desc(), w - 34, 6), x + 24, yy + 10, MUTED, 6);
                    }
                    skillTip(sk, col, x + 8, yy, w - 16, rh);
                    yy += rh + 2;
                }
            }
            yy += 2;
        }
        R(x + 8, yy, w - 16, 1, col, 0.4f);
        yy += 5;
        // rewards: one per row with the name when it fits, else just the slots
        T(tr("recompensa"), x + 8, yy, GOLD, 7, true);
        yy += 10;
        boolean rowsFit = lay[1].equals("rows");
        List<Object[]> rewards = new ArrayList<>();
        rewards.add(new Object[] {null, c.soul(), 0, tr("alma_de_nome", c.nome()), won ? tr("alma_sua") : tr("alma_derrote"), SOUL_C, Boolean.TRUE});
        rewards.add(new Object[] {UiIcons.PODER, ItemStack.EMPTY, 0, tr("poder"), tr("poder_sub"), rgb(128, 255, 32), Boolean.FALSE});
        for (ItemStack it : c.itens()) {
            rewards.add(new Object[] {null, it, it.getCount(), it.getHoverName().getString(), won ? tr("item_ja") : tr("item_primeira"), WHITE, Boolean.FALSE});
        }
        for (int i = 0; i < rewards.size(); i++) {
            Object[] rw = rewards.get(i);
            float rx = rowsFit ? x + 8 : x + 8 + i * 20, ry = rowsFit ? yy + i * 20 : yy;
            boolean soulRow = (Boolean) rw[6];
            int n = (Integer) rw[2];
            ItemStack st = (ItemStack) rw[1];
            UiIcons.slot(rx, ry, (String) rw[0], soulRow ? ItemStack.EMPTY : st, n, soulRow && !won && !lk ? c.cor() : -1, lk);
            if (soulRow) {
                UiIcons.crystal(st, c.cor(), rx + 9, ry + 9, 16, won ? 0.5f : 1, false, lk, 0);
            }
            Px.hit(rx, ry, 18, 18, null, List.of((String) rw[3], (String) rw[4]), List.of((Integer) rw[5], MUTED));
            if (rowsFit) {
                T((String) rw[3] + (n > 1 ? " x" + n : ""), rx + 22, ry + 1, lk ? MUTED : (Integer) rw[5], 8);
                T((String) rw[4], rx + 22, ry + 10, MUTED, 6);
            }
        }
        yy += rowsFit ? rewards.size() * 20 + 2 : 22;
        // BEST TIME: the best time big, medals for the time (bronze/silver/gold) for those who like to chase it
        int[] rec = BookData.record(c.id());
        boolean has = rec != null && rec[0] > 0 && rec[1] > 0;
        float bottom = y + h - 26;
        if (lk) {
            T(tr("trancado_falta", BookData.subsNeeded(u), u.nome, BookData.subsBeaten(u), BookData.subsNeeded(u)), x + 8, yy, rgb(255, 130, 120), 7);
        } else if (lay[0].equals("line")) {
            float ry = rowsFit ? Math.max(yy, bottom - 12) : yy - 17;
            T(has ? tr("melhor") : tr("ainda_nao"), x + w - 8, ry, MUTED, 6, false, 1, true, 1);
            T(has ? fmt(rec[1]) : tr("derrotado"), x + w - 8, ry + 7, has ? WHITE : MUTED, has ? 10 : 6, true, 1, true, 1);
        } else {
            float ry = Math.max(yy + 2, bottom - 64);
            R(x + 8, ry - 4, w - 16, 1, col, 0.4f);
            int gold = 40 + (10 - c.tier()) * 9;
            int[] medT = {gold, Math.round(gold * 1.6f), Math.round(gold * 2.4f)};
            String[] medN = {tr("ouro"), tr("prata"), tr("bronze")};
            int[] medC = {rgb(255, 210, 80), rgb(200, 210, 225), rgb(205, 130, 70)};
            int secs = has ? rec[1] : -1;
            T(tr("melhor_tempo"), x + 8, ry, GOLD, 7, true);
            T(has ? fmt(secs) : "--:--", x + 8, ry + 9, has ? WHITE : MUTED, 18, true);
            T(has ? tr(rec[0] > 1 ? "vitorias" : "vitoria", rec[0]) : tr("ainda_nao_derrotado"), x + 8, ry + 29, has ? rgb(150, 230, 150) : MUTED, 6);
            float mx0 = x + w - 8 - 3 * 26;
            for (int i = 0; i < 3; i++) {
                boolean got = secs >= 0 && secs <= medT[i];
                float cx2 = mx0 + i * 26 + 12, cy2 = ry + 14;
                int mc = medC[i];
                for (int a = 0; a < 16; a++) {
                    for (int b = 0; b < 16; b++) {
                        float d = (float) Math.hypot(a - 7.5, b - 7.5);
                        if (d < 7.5f) {
                            P(cx2 - 8 + a, cy2 - 8 + b, d > 6.3f ? mix(mc, Px.BLACK, got ? 0.35f : 0.75f)
                                    : got ? mix(mc, WHITE, (7.5f - d) / 12) : mix(mc, rgb(20, 18, 26), 0.8f), 1);
                        }
                    }
                }
                if (got) {
                    P(cx2 - 3, cy2 - 4, WHITE, 1);
                    P(cx2 - 4, cy2 - 3, WHITE, 1);
                }
                R(cx2 - 5, cy2 + 7, 3, 5, got ? rgb(200, 50, 50) : rgb(70, 40, 40), 1);
                R(cx2 + 2, cy2 + 7, 3, 5, got ? rgb(60, 90, 200) : rgb(40, 40, 70), 1);
                T(fmt(medT[i]), cx2, cy2 + 14, got ? mc : MUTED, 6, false, 0, true, 1);
                Px.hit(cx2 - 9, cy2 - 9, 18, 28, null, List.of(tr("medalha_de", medN[i].toLowerCase()), tr("venca_ate", fmt(medT[i])),
                        got ? tr("conquistada") : tr("ainda_nao_m")), List.of(mc, MUTED, got ? rgb(120, 230, 120) : rgb(255, 130, 120)));
            }
            int nextI = -1;
            for (int i = 2; i >= 0; i--) {
                if (secs < 0 || secs > medT[i]) {
                    nextI = i;
                    break;
                }
            }
            if (nextI >= 0) {
                String line = secs < 0 ? tr("bronze_venca", fmt(medT[2]))
                        : tr("tire_mais", medN[nextI], fmt(secs - medT[nextI]));
                T(line, x + 8, ry + 38, medC[nextI], 6);
            } else {
                T(tr("todas_medalhas"), x + 8, ry + 38, rgb(255, 210, 80), 6);
            }
        }
        // TOMAR A ALMA (hold), the ornate gold button
        float bx = x + 8, by = y + h - 22, bw = w - 16, bh = 16;
        boolean over = !lk && Px.over(bx, by, bw, bh);
        if (over && down && vs == null) {
            fightHold += frameDt;
            if (fightHold >= 0.7f) {
                startVS(c);
                fightHold = 0;
            }
        } else if (vs == null) {
            fightHold = Math.max(0, fightHold - frameDt * 3);
        }
        int base = lk ? rgb(70, 64, 80) : GOLD;
        Px.button(bx, by, bw, bh, base, over);
        if (fightHold > 0) {
            R(bx + 1, by + 1, (bw - 2) * fightHold / 0.7f, bh - 2, mix(col, GOLD, 0.5f), 0.75f);
        }
        T(lk ? tr("trancado") : won ? tr("lutar_de_novo") : tr("tomar_alma"), bx + bw / 2, by + 4, lk ? MUTED : over ? WHITE : rgb(255, 236, 180), 8, true, 0,
                true, 1);
        Px.hit(bx, by, bw, bh, null);
    }

    private static float need(String[] lay, int nRw, int nSk) {
        return (lay[0].equals("big") ? 66 : 0) + (lay[1].equals("rows") ? nRw * 20 + 2 : 22)
                + (lay[2].equals("desc") ? nSk * 20 : lay[2].equals("list") ? nSk * 13 : (float) Math.ceil(nSk / 2f) * 11 + 3);
    }

    private static void skillTip(BookData.Skill sk, int col, float x, float y, float w, float h) {
        List<String> tip = new ArrayList<>();
        List<Integer> cs = new ArrayList<>();
        tip.add(sk.name());
        cs.add(mix(ink(col), WHITE, 0.3f));
        for (String l : Px.wrap(sk.desc(), 150, 8)) {
            tip.add(l);
            cs.add(MUTED);
        }
        Px.hit(x, y, w, h, null, tip, cs);
    }

    private void drawLadder(Uni u, float x, float y, float w) {
        List<Ch> list = u.desafios;
        int n = list.size(), need = BookData.subsNeeded(u), got = BookData.subsBeaten(u);
        boolean hasBoss = false;
        for (Ch ch : list) {
            hasBoss |= ch.boss();
        }
        // the same square size in every universe (Stark 2026-11-13: Jujutsu's came out bigger than Dragon Ball's): the
        // longest ladder of all decides it, counting the chain's gap before the bosses
        int most = n;
        for (Uni other : BookData.unis()) {
            most = Math.max(most, other.desafios.size());
        }
        int cell = (int) Math.min(42, Math.floor((w - 14) / most) - 4);
        float cx = x;
        boolean firstBoss = true;
        hov = null;
        for (Ch c : list) {
            if (c.boss() && firstBoss) {
                firstBoss = false;
                // the chain that keeps the bosses apart
                boolean lk = got < need && com.starkinhon.battlegroundsmod.power.Progression.clientOn;
                for (int j = 0; j < 6; j++) {
                    float yy = y + 4 + j * 8;
                    R(cx + 3, yy, 6, 5, lk ? rgb(0x8a, 0x8a, 0x96) : rgb(0xc9, 0xa2, 0x4a), 1);
                    R(cx + 5, yy + 1, 2, 3, rgb(0x1a, 0x18, 0x20), 1);
                }
                if (lk) {
                    T(got + "/" + need, cx + 6, y + 52, rgb(255, 130, 120), 6, false, 0, true, 1);
                } else {
                    Px.check(cx + 3, y + 52, GOLD, 1);
                }
                cx += 14;
            }
            int col = c.cor();
            boolean lk = BookData.locked(c), won = BookData.won(c.id()), isSel = sel == c;
            boolean over = Px.over(cx, y, cell, cell + 8);
            if (over) {
                hov = c;
            }
            float lift = isSel ? -3 : over ? -2 : 0;
            Px.smallFrame(cx, y + lift, cell, cell + 8, lk ? rgb(70, 64, 80) : won && !isSel && !over ? rgb(96, 96, 100) : col, isSel || over, 0.92f);
            // the portrait's background: its arena
            Px.g.enableScissor(Math.round(cx + 1), Math.round(y + lift + 1), Math.round(cx + cell - 1), Math.round(y + lift + cell + 7));
            arenaBg(c, cx - cell * 0.4f, y + lift - 2, cell * 1.8f, cell * 1.1f, lk ? 0.15f : 0.45f, won);
            // head and chest framed in the square: the head's top 3 px under the edge, the waist at the name bar
            SkinDraw.Pose bp = new SkinDraw.Pose(-0.45f, 0.18f);
            float bk = cell / 22f;
            SkinDraw.draw(c.skin(), cx + cell / 2f, y + lift + 4 + 32 * bk, bk, bp, SkinDraw.BUST, lk ? 0xF2080610 : won ? 0xFF6E6E72 : 0xFFFFFFFF);
            Px.g.disableScissor();
            if (won) {
                // defeated: gray (the mob and its background)
                R(cx + 1, y + lift + 1, cell - 2, cell + 6, rgb(58, 58, 62), 0.42f);
            }
            if (lk) {
                T("?", cx + cell / 2f, y + lift + cell / 2f - 6, rgb(190, 180, 210), 12, true, 0, true, 1);
            }
            R(cx + 1, y + lift + cell - 2, cell - 2, 9, Px.BLACK, 0.6f);
            T(lk ? "???" : c.nome().split(" ")[0], cx + cell / 2f, y + lift + cell - 1, lk || won ? MUTED : ink(col), 6, false, 0, true, 1);
            if (won) {
                // the DERROTADO stamp, tilted
                var pose = Px.g.pose();
                pose.pushPose();
                float sx = cx + cell / 2f, sy = y + lift + cell * 0.45f;
                pose.translate(sx, sy, 0);
                pose.mulPose(Axis.ZP.rotation(-0.3f));
                pose.translate(-sx, -sy, 0);
                float sw = cell - 4;
                R(cx + 2, sy - 5, sw, 10, rgb(40, 6, 6), 0.7f);
                R(cx + 2, sy - 5, sw, 1, RED, 1);
                R(cx + 2, sy + 4, sw, 1, RED, 1);
                if (cell >= 34) {
                    T(tr("alma_tomada"), sx, sy - 4, RED, 6, true, 0, true, 1);
                } else {
                    Px.cross(sx - 3, sy - 3, RED, 1);
                }
                pose.popPose();
            }
            if (!lk && !won) {
                UiIcons.crystal(c.soul(), col, cx + cell - 6, y + lift + 7, 9, 1, true, false, c.id().length());
            }
            if (isSel) {
                float a = Px.time * 6;
                for (int kk = 0; kk < 3; kk++) {
                    P(cx + cell / 2f - 2 + kk * 2, y + lift - 4 + Math.round(Math.sin(a + kk)), GOLD, 1);
                }
            }
            final Ch pick = c;
            Px.hit(cx, y, cell, cell + 8, () -> {
                sel = pick;
                click();
            });
            cx += cell + 4;
        }
    }

    // The arena's picture (textures/gui/arenas/<id>.png), stretched without filtering like the preview.
    private static void arenaBg(Ch c, float x, float y, float w, float h, float a, boolean gray) {
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/gui/arenas/" + c.id() + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(tex).isEmpty()) {
            Px.vgrad(x, y, w, h, rgb(0x20, 0x30, 0x50), a, rgb(0x50, 0x60, 0x80), a);
            return;
        }
        float sc = Math.max(w / 160f, h / 90f), dw = 160 * sc, dh = 90 * sc;
        Px.g.flush();
        RenderSystem.enableBlend();
        float v = gray ? 0.55f : 1;
        RenderSystem.setShaderColor(v, v, v, a);
        Px.g.blit(tex, Math.round(x + (w - dw) / 2), Math.round(y + (h - dh) / 2), Math.round(dw), Math.round(dh), 0, 0, 320, 180, 320, 180);
        Px.g.flush();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    // ------------------------------------------------------------------------------------------------ the VS

    // The arena is ready (the player is in it) or was refused (ChallengeArenas.start). Stark 2026-11-13: the VS stays on
    // while the arena loads and opens straight onto it, instead of closing into the world with "Arena carregando".
    private int vsReady;
    private float vsReadyAt = -1;

    public static void status(boolean ok) {
        ChallengeScreen screen = Minecraft.getInstance().screen instanceof ChallengeScreen open ? open : holding;
        if (screen != null && screen.vs != null) {
            if (ok) {
                screen.vsReady = 1;
            } else {
                screen.finishVS();
            }
        }
    }

    // Changing dimension opens the game's "loading terrain" screen over this one: the VS is drawn over it while it is up,
    // and comes back after it if the arena is not ready yet.
    private static ChallengeScreen holding;

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Opening e) -> {
            if (e.getNewScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen
                    && e.getCurrentScreen() instanceof ChallengeScreen cs && cs.vs != null) {
                holding = cs;
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Post e) -> {
            if (holding != null && e.getScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) {
                holding.render(e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick());
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Closing e) -> {
            if (holding != null && e.getScreen() instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) {
                ChallengeScreen h = holding;
                holding = null;
                h.vsWhite = false;
                if (h.vs != null) {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> {
                        if (mc.screen == null && h.vs != null) {
                            mc.setScreen(h);
                        }
                    });
                }
            }
        });
    }

    private void finishVS() {
        vs = null;
        if (Minecraft.getInstance().screen == this) {
            onClose();
        } else if (holding == this) {
            // the terrain screen is still up: stay white over it until it goes (no flash of the dirt screen)
            vsWhite = true;
        }
    }

    private boolean vsWhite;

    private void startVS(Ch c) {
        vs = c;
        vsT = 0;
        vsSent = false;
        vsReady = 0;
        vsReadyAt = -1;
        down = false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENDER_DRAGON_GROWL, 1.4f, 0.35f));
    }

    private void drawVS(float dt) {
        vsT += dt;
        float t = vsT;
        Ch c = vs;
        int col = c.cor(), me = rgb(74, 163, 255);
        float slide = Px.easeOut(t / 0.35f);
        R(0, 0, GW, GH, rgb(4, 3, 8), 1);
        // the two halves, cut on the diagonal (row by row)
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/gui/arenas/" + c.id() + ".png");
        boolean hasTex = Minecraft.getInstance().getResourceManager().getResource(tex).isPresent();
        float ax = GW * 0.3f, aw = GW * 0.8f;
        float isc = Math.max(aw / 320f, GH / 180f) * 1.06f, idw = 320 * isc, idh = 180 * isc;
        float iox = ax + (aw - idw) / 2, ioy = (GH - idh) / 2;
        for (int i = 0; i < GH; i += 2) {
            float f = (i + 1) / (float) GH;
            float edge = lerp(GW * 0.58f, GW * 0.42f, f);
            float le = edge - GW * (1 - slide), re = edge + GW * (1 - slide);
            // left: blue, with speed lines
            if (le > 0) {
                R(0, i, le, 2, mix(rgb(10, 20, 46), rgb(30, 70, 140), f), 1);
            }
            // right: the arena
            if (re < GW) {
                float x0 = Math.max(re, 0);
                if (hasTex) {
                    float u0 = (x0 - iox) / idw * 320, v0 = (i - ioy) / idh * 180;
                    float uw = (GW - x0) / idw * 320, vh = 2 / idh * 180;
                    Px.g.flush();
                    RenderSystem.enableBlend();
                    RenderSystem.setShaderColor(0.62f, 0.6f, 0.78f, 1);
                    blitPart(tex, x0, i, GW - x0, 2, u0, v0, uw, vh);
                    Px.g.flush();
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                } else {
                    R(x0, i, GW - x0, 2, mix(col, Px.BLACK, 0.5f), 1);
                }
                R(Math.max(re, GW * 0.3f), i, GW - Math.max(re, GW * 0.3f), 2, mix(col, Px.BLACK, 0.55f), 0.45f);
            }
        }
        float[] lines = rng(3, 160);
        for (int i = 0; i < 40; i++) {
            float r0 = lines[i * 4], r1 = lines[i * 4 + 1], r2 = lines[i * 4 + 2], r3 = lines[i * 4 + 3];
            float yy = (r0 * GH + t * 180 * (1 + r1)) % GH;
            float xx = r2 * GW * 0.6f, ww = 1 + r3 * 30;
            float edge = lerp(GW * 0.58f, GW * 0.42f, yy / GH) - GW * (1 - slide);
            if (xx < edge) {
                R(xx, yy, Math.min(ww, edge - xx), 1, rgb(140, 200, 255), 0.15f + r3 * 0.3f);
            }
        }
        // the two fighters (you on the left with your own skin)
        float leftEdge = GW * 0.42f - GW * (1 - slide), rightEdge = GW * 0.58f + GW * (1 - slide);
        Minecraft mc = Minecraft.getInstance();
        if (leftEdge > 0 && mc.player != null) {
            Px.g.enableScissor(0, 0, Math.round(Math.max(1, leftEdge)), GH);
            SkinDraw.Pose p = new SkinDraw.Pose(0.55f, 0.1f);
            p.rarm = new float[] {-1.2f, -0.3f};
            p.larm = new float[] {-0.4f, 0.2f};
            p.rleg = new float[] {0.3f, 0};
            p.lleg = new float[] {-0.3f, 0};
            SkinDraw.draw(mc.player.getSkin().texture(), GW * 0.24f, GH * 0.86f, GH / 52f, p, SkinDraw.ALL, 0xFFFFFFFF);
            Px.g.disableScissor();
        }
        if (rightEdge < GW) {
            Px.g.enableScissor(Math.round(rightEdge), 0, GW, GH);
            SkinDraw.Pose p = new SkinDraw.Pose(-0.55f, 0.1f);
            p.rarm = new float[] {-1.4f, 0.4f};
            p.larm = new float[] {-0.3f, -0.2f};
            p.rleg = new float[] {-0.3f, 0};
            p.lleg = new float[] {0.3f, 0};
            SkinDraw.draw(c.skin(), GW * 0.76f, GH * 0.86f, GH / 52f, p, SkinDraw.ALL, 0xFFFFFFFF);
            Px.g.disableScissor();
        }
        // the white stripe on the diagonal
        float k = Px.easeOut((t - 0.3f) / 0.2f);
        if (k > 0) {
            for (int i = 0; i < GH; i++) {
                float e = lerp(GW * 0.58f, GW * 0.42f, i / (float) GH);
                R(e - 1, i, 3, 1, WHITE, k);
            }
        }
        // VS lands
        float vt = (t - 0.45f) / 0.25f;
        if (vt > 0) {
            float sc = vt < 1 ? lerp(3, 1, Px.easeOut(vt)) : 1 + (float) Math.sin(Px.time * 10) * 0.02f;
            var pose = Px.g.pose();
            pose.pushPose();
            pose.translate(GW / 2f, GH * 0.42f, 0);
            pose.scale(sc, sc, 1);
            pose.translate(-GW / 2f, -GH * 0.42f, 0);
            T("VS", GW / 2f, GH * 0.42f - 16, rgb(255, 236, 140), 34, true, 0, true, 1);
            pose.popPose();
            float a = clamp(vt - 0.6f, 0, 1);
            UiIcons.crystal(c.soul(), col, GW / 2f, GH * 0.42f + 38 + (float) Math.sin(Px.time * 2) * 2, 22, a, true, false, 3);
            T(tr("alma_em_jogo"), GW / 2f, GH * 0.42f + 54, rgb(220, 200, 255), 8, true, 0, true, a);
            if (vt < 0.05f) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 0.6f, 0.4f));
            }
        }
        float sh = vt > 0 && vt < 0.6f ? (rng((long) Math.floor(Px.time * 60), 1)[0] - 0.5f) * 4 : 0;
        Px.g.pose().pushPose();
        Px.g.pose().translate(sh, 0, 0);
        float nt = Px.easeOut((t - 0.7f) / 0.3f);
        int myTier = mc.player == null ? Tiers.HUMANO : Tiers.of(mc.player);
        T(tr("voce"), GW * 0.06f - (1 - nt) * 80, GH * 0.1f, me, 16, true);
        T("Tier " + myTier + " · " + BookData.tierName(myTier), GW * 0.06f - (1 - nt) * 80, GH * 0.1f + 20, rgb(180, 210, 255), 8);
        T(c.nome().toUpperCase(), GW * 0.94f + (1 - nt) * 80, GH * 0.1f, ink(col), 16, true, 1, true, 1);
        T("Tier " + c.tier() + " · " + BookData.tierName(c.tier()), GW * 0.94f + (1 - nt) * 80, GH * 0.1f + 20, mix(ink(col), WHITE, 0.3f), 8, false, 1,
                true, 1);
        float lt = Px.easeOut((t - 1.1f) / 0.3f);
        if (lt > 0 && !c.lugar().isEmpty()) {
            int lw = tw(c.lugar(), 10, true) + 30;
            Px.frame(GW / 2f - lw / 2f, GH * 0.92f - 14, lw, 20, GOLD, 0.9f * lt, false, false, null);
            T(c.lugar(), GW / 2f, GH * 0.92f - 9, rgb(255, 236, 180), 10, true, 0, true, lt);
        }
        Px.g.pose().popPose();
        // the arena starts loading while the VS plays; the screen holds until it is ready
        if (t > 1.6f && !vsSent) {
            vsSent = true;
            PacketDistributor.sendToServer(new ModPayloads.ChallengeStart(c.id()));
        }
        if (vsReady == 1 && vsReadyAt < 0 && t >= 2.4f) {
            vsReadyAt = t;
        }
        if (vsReadyAt < 0 && t > 2.4f) {
            // still loading: a quiet line at the bottom, the VS keeps going
            String dots = ".".repeat(1 + (int) (Px.time * 3) % 3);
            T(tr("arena_carregando") + dots, GW / 2f, GH - 10, rgb(200, 190, 220), 6, false, 0, true, clamp((t - 2.4f) * 3, 0, 1));
        }
        if (vsSent && vsReadyAt < 0 && t > 45) {
            // the server never answered (the arena failed to build): back to the world
            finishVS();
            return;
        }
        // the white flash takes you to the arena
        if (vsReadyAt >= 0) {
            float ft = t - vsReadyAt;
            float f = ft < 0.2f ? ft / 0.2f : 1 - (ft - 0.2f) / 0.5f;
            Px.g.pose().pushPose();
            Px.g.pose().translate(0, 0, 500);
            R(0, 0, GW, GH, WHITE, clamp(f, 0, 1));
            Px.g.pose().popPose();
            if (ft > 0.3f) {
                finishVS();
            }
        }
    }

    // A part of a texture (u, v, w, h in texture pixels of the 320 x 180 picture) into a box on the grid.
    private static void blitPart(ResourceLocation tex, float x, float y, float w, float h, float u, float v, float uw, float vh) {
        var pose = Px.g.pose();
        var matrix = pose.last().pose();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        var buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        float u0 = u / 320f, u1 = (u + uw) / 320f, v0 = v / 180f, v1 = (v + vh) / 180f;
        buffer.addVertex(matrix, x, y, 0).setUv(u0, v0);
        buffer.addVertex(matrix, x, y + h, 0).setUv(u0, v1);
        buffer.addVertex(matrix, x + w, y + h, 0).setUv(u1, v1);
        buffer.addVertex(matrix, x + w, y, 0).setUv(u1, v0);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (vs != null) {
            return true;
        }
        if (button == 0) {
            down = true;
            if (Px.click(Px.toGrid(mouseX), Px.toGrid(mouseY))) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            down = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // the wheel walks the ladder
        if (vs == null && sel != null) {
            List<Ch> list = sel.uni().desafios;
            int i = list.indexOf(sel) + (scrollY < 0 ? 1 : -1);
            if (i >= 0 && i < list.size()) {
                sel = list.get(i);
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (vs != null) {
            return true;
        }
        // tabs with the arrows up/down, the ladder with left/right
        List<Uni> unis = BookData.unis();
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            pickUni((uIdx + 1) % unis.size());
            return true;
        }
        if (sel != null && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)) {
            List<Ch> list = sel.uni().desafios;
            int i = list.indexOf(sel) + (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT ? 1 : -1);
            if (i >= 0 && i < list.size()) {
                sel = list.get(i);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return vs == null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
