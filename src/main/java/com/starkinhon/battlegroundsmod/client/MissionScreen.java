// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import static com.starkinhon.battlegroundsmod.client.ui.Px.GH;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GOLD;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GW;
import static com.starkinhon.battlegroundsmod.client.ui.Px.MUTED;
import static com.starkinhon.battlegroundsmod.client.ui.Px.P;
import static com.starkinhon.battlegroundsmod.client.ui.Px.R;
import static com.starkinhon.battlegroundsmod.client.ui.Px.T;
import static com.starkinhon.battlegroundsmod.client.ui.Px.TEXT;
import static com.starkinhon.battlegroundsmod.client.ui.Px.WHITE;
import static com.starkinhon.battlegroundsmod.client.ui.Px.clamp;
import static com.starkinhon.battlegroundsmod.client.ui.Px.ink;
import static com.starkinhon.battlegroundsmod.client.ui.Px.lerp;
import static com.starkinhon.battlegroundsmod.client.ui.Px.mix;
import static com.starkinhon.battlegroundsmod.client.ui.Px.rgb;
import static com.starkinhon.battlegroundsmod.client.ui.Px.tw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.ui.Art;
import com.starkinhon.battlegroundsmod.client.ui.Canvas;
import com.starkinhon.battlegroundsmod.client.ui.Journal;
import com.starkinhon.battlegroundsmod.client.ui.Journal.Mission;
import com.starkinhon.battlegroundsmod.client.ui.Px;
import com.starkinhon.battlegroundsmod.client.ui.SkinDraw;
import com.starkinhon.battlegroundsmod.client.ui.UiIcons;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

// The Diário de Missões (J). The universe's map as a pixel art diorama of its own structures in blocks (Stark
// 2026-11-13: "é isso, ficou muito legal"), drag to walk, wheel to zoom, pins too close merged with a count until zoomed
// in. Since 2026-11-22 (web preview "Diário Sem Cara de IA", approved) each universe has its own material instead of the
// dark veil, gold lines and diamonds: Naruto's pins are kunai with paper tags and the mission a scroll with brush ink;
// Dragon Ball's pins are radar blips and the mission a Capsule Corp gadget; Jujutsu and the universes with no theme yet
// use a school dossier (client/ui/Art). The universes hang from the top as ribbons. Clicking a pin flies the camera to
// it and slides the mission in from the right: the NPC, the line it says, the objectives, the rewards, when it comes
// back, the distance and Rastrear (the corner HUD then shows it).
public class MissionScreen extends Screen {
    private record Uni(String id, String nome, int cor, String npc) {
    }

    private static final List<Uni> UNIS = List.of(new Uni("dragonball", "Dragon Ball", 0xFF9A2E, "npc_bulma"), new Uni("naruto", "Naruto", 0xF28C28, "npc_jiraiya"),
            new Uni("jujutsu", "Jujutsu", 0x7A5CFF, "npc_yaga"), new Uni("bleach", "Bleach", 0xE8E8F0, "npc_urahara"),
            new Uni("demonslayer", "Demon Slayer", 0xC7A2FF, "npc_urokodaki"));

    // The map pictures (635 x 320 pixel art, drawn as 2540 x 1280) and where each structure sits on them.
    private static final Map<String, Map<String, float[]>> SITES = new HashMap<>();

    static {
        Map<String, float[]> db = new HashMap<>();
        db.put("kame", new float[] {0.9203f, 0.7933f});
        db.put("capsula", new float[] {0.3931f, 0.4395f});
        db.put("goku", new float[] {0.7816f, 0.6573f});
        db.put("karin", new float[] {0.0882f, 0.2216f});
        db.put("nave", new float[] {0.2185f, 0.7641f});
        db.put("babidi", new float[] {0.2844f, 0.8163f});
        db.put("buu", new float[] {0.3563f, 0.2346f});
        db.put("cell", new float[] {0.4724f, 0.3859f});
        db.put("raditz", new float[] {0.6913f, 0.4883f});
        db.put("guru", new float[] {0.2742f, 0.1758f});
        db.put("vila", new float[] {0.2925f, 0.184f});
        SITES.put("dragonball", db);
        Map<String, float[]> na = new HashMap<>();
        na.put("folha", new float[] {0.399f, 0.611f});
        na.put("morte", new float[] {0.6186f, 0.3281f});
        na.put("vale", new float[] {0.4928f, 0.2533f});
        na.put("chuva", new float[] {0.23f, 0.2068f});
        na.put("myoboku", new float[] {0.7484f, 0.185f});
        na.put("nevoa", new float[] {0.836f, 0.6968f});
        SITES.put("naruto", na);
    }

    private static final float MAP_W = 2540, MAP_H = 1280;
    private static final int[] ST_C = {GOLD, rgb(120, 200, 255), rgb(120, 230, 120), rgb(200, 170, 255), rgb(110, 100, 130)};
    private static final String[] ST_K = {"pronta", "andamento", "feita", "concluida", "oculta"};
    private static int lastUni;
    // the Caminho tab open (the first time the Diário opens with the path on, it opens there)
    private static Boolean lastPath;
    private boolean showPath = lastPath != null ? lastPath : PathClient.on();
    private float pathT;

    private int dUni = lastUni;
    private Mission mapSel;
    private Mission mapHov;
    // the page still sliding out after closing
    private Mission shownSel;
    private float[] cam = {0.5f, 0.47f, 1}, view = {0.5f, 0.47f, 1};
    private float pan, mapT;
    private boolean dragging;
    private double dragX, dragY;
    private long last;

    public MissionScreen() {
        super(Component.translatable("screen.battlegroundsmod.diario"));
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("screen.battlegroundsmod.diario." + key, args).getString();
    }

    private static int stIndex(String state) {
        for (int i = 0; i < ST_K.length; i++) {
            if (ST_K[i].equals(state)) {
                return i;
            }
        }
        return 0;
    }

    private static String stName(String state) {
        return tr("estado." + state);
    }

    @Override
    protected void init() {
        Journal.ask();
        last = System.nanoTime();
        if (list(UNIS.get(dUni).id()).isEmpty()) {
            dUni = 0;
        }
    }

    private static List<Mission> list(String uni) {
        List<Mission> l = Journal.catalog().get(uni);
        return l == null ? List.of() : l;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        Px.begin(graphics, mouseX, mouseY);
        Px.hits.clear();
        try {
            // two tabs side by side at the top, CAMINHO | MAPA (Stark: with one toggle he thought the map was gone);
            // registered first so they win the click
            float tw = 64, th = 18, by = 5, gap = 4;
            float cx = GW / 2f - tw - gap / 2f, mx = GW / 2f + gap / 2f;
            Px.hit(cx, by, tw, th, () -> {
                if (!showPath) {
                    showPath = true;
                    lastPath = true;
                    click();
                }
            });
            Px.hit(mx, by, tw, th, () -> {
                if (showPath) {
                    showPath = false;
                    lastPath = false;
                    click();
                }
            });
            if (showPath) {
                drawPath(dt);
            } else {
                Uni u = UNIS.get(dUni);
                drawMap(dt, u, list(u.id()));
            }
            tab(cx, by, tw, th, tr("caminho"), showPath);
            tab(mx, by, tw, th, tr("mapa"), !showPath);
            Px.drawTip();
        } finally {
            Px.end();
        }
    }

    // A plain tab: the open one is a cream card with dark letters, the other a dark one.
    private void tab(float x, float y, float w, float h, String label, boolean on) {
        boolean hot = Px.over(x, y, w, h);
        R(x + 1, y + 2, w, h, Px.BLACK, 0.35f);
        R(x, y, w, h, on ? rgb(240, 232, 214) : rgb(28, 24, 30), on ? 1 : hot ? 0.9f : 0.75f);
        R(x, y + h - 1, w, 1, on ? rgb(190, 176, 150) : rgb(10, 8, 12), 1);
        T(label, x + w / 2f, y + 5, on ? rgb(44, 32, 24) : hot ? WHITE : rgb(190, 184, 176), 7, true, 0, false, 1);
    }

    // ------------------------------------------------------------------------------------------------ the map

    private float fit() {
        return Math.max(GW / MAP_W, GH / MAP_H);
    }

    private float[] toScreen(float fx, float fy) {
        float sc = fit() * view[2];
        return new float[] {GW / 2f + (fx - view[0]) * MAP_W * sc, GH / 2f + (fy - view[1]) * MAP_H * sc};
    }

    private void clampCam(float[] c) {
        float sc = fit() * c[2], hw = GW / 2f / (MAP_W * sc), hh = GH / 2f / (MAP_H * sc);
        c[0] = clamp(c[0], hw, 1 - hw);
        c[1] = clamp(c[1], hh, 1 - hh);
    }

    private void drawMap(float dt, Uni u, List<Mission> list) {
        mapT += dt;
        Map<String, float[]> sites = SITES.getOrDefault(u.id(), Map.of());
        float panelW = Math.min(200, GW * 0.42f);
        float[] tgt = cam;
        if (mapSel != null && sites.containsKey(mapSel.id())) {
            float[] sp = sites.get(mapSel.id());
            float z = Math.max(1.7f, cam[2]);
            float sc = fit() * z;
            tgt = new float[] {sp[0] + (panelW / 2 + 6) / (MAP_W * sc), sp[1] + 10 / (MAP_H * sc), z};
            // a pin near the edge: the map's end never shows (the pin just sits off the middle)
            clampCam(tgt);
        }
        float k = 1 - (float) Math.pow(0.004, dt);
        view[0] = lerp(view[0], tgt[0], k);
        view[1] = lerp(view[1], tgt[1], k);
        view[2] = lerp(view[2], tgt[2], k);
        R(0, 0, GW, GH, rgb(0x61, 0x8e, 0x92), 1);
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/gui/mapa_" + u.id() + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(tex).isPresent()) {
            float sc = fit() * view[2];
            float[] o = toScreen(0, 0);
            Px.g.flush();
            RenderSystem.enableBlend();
            Px.g.blit(tex, Math.round(o[0]), Math.round(o[1]), Math.round(MAP_W * sc), Math.round(MAP_H * sc), 0, 0, 635, 320, 635, 320);
        }
        // the edge of the diorama fades in the mist; a light vignette
        for (int i = 0; i < GH; i += 2) {
            float t = i / (float) GH;
            R(0, i, GW, 2, rgb(8, 10, 20), 0.55f * (float) Math.pow(Math.max(0, 0.22f - t) / 0.22f, 1.5) + 0.45f * (float) Math.pow(Math.max(0, t - 0.8f) / 0.2f, 2));
        }
        for (int i = 0; i < GW * 0.12f; i += 2) {
            float a = 0.5f * (float) Math.pow(1 - i / (GW * 0.12f), 2);
            R(i, 0, 2, GH, rgb(8, 10, 20), a);
            R(GW - i - 2, 0, 2, GH, rgb(8, 10, 20), a);
        }
        // ---- pins ----
        int found = 0;
        for (Mission m : list) {
            if (Journal.entry(m.id()).found()) {
                found++;
            }
        }
        String title = tr("titulo");
        String sub = tr("sub", u.nome(), String.format("%02d", found), String.format("%02d", list.size()));
        float[][] hdr = {{6, 2, 14 + tw(title, 12, true), 18}, {6, 18, 14 + tw(sub, 6), 30}, {GW - 170, 0, GW, 27}};
        mapHov = null;
        List<Mission> order = new ArrayList<>();
        for (Mission m : list) {
            if (sites.containsKey(m.id())) {
                order.add(m);
            }
        }
        order.sort((a, b) -> Float.compare(sites.get(a.id())[1], sites.get(b.id())[1]));
        // fog over the structures not found yet
        for (Mission m : order) {
            if (!"oculta".equals(Journal.entry(m.id()).state())) {
                continue;
            }
            float[] s = toScreen(sites.get(m.id())[0], sites.get(m.id())[1]);
            if (s[0] < -40 || s[0] > GW + 40 || s[1] < -40 || s[1] > GH + 40) {
                continue;
            }
            boolean namek = m.id().equals("guru") || m.id().equals("vila");
            for (int i = 0; i < 9; i++) {
                float a = i * 2.39f + mapT * 0.08f, r = 10 + ((i * 37) % 14);
                float fx = s[0] + (float) Math.cos(a) * r * 1.3f, fy = s[1] + (namek ? -10 : 16) + (float) Math.sin(a) * r * 0.6f;
                Px.glow(fx, fy, (16 + i % 3 * 5) * view[2], rgb(214, 222, 236), 0.62f);
            }
        }
        // pins too close (Guru and the village in Namek) become one with the number, until zoomed in
        List<float[]> keptPos = new ArrayList<>();
        List<String> keptId = new ArrayList<>();
        Set<String> merged = new HashSet<>();
        Map<String, Integer> cnt = new HashMap<>();
        for (Mission m : order) {
            float[] s = toScreen(sites.get(m.id())[0], sites.get(m.id())[1]);
            int near = -1;
            for (int i = 0; i < keptPos.size(); i++) {
                if (Math.abs(keptPos.get(i)[0] - s[0]) < 22 && Math.abs(keptPos.get(i)[1] - s[1]) < 14) {
                    near = i;
                    break;
                }
            }
            if (near >= 0 && mapSel != m) {
                merged.add(m.id());
                cnt.merge(keptId.get(near), 2, (a, b) -> a + 1);
            } else {
                keptPos.add(s);
                keptId.add(m.id());
            }
        }
        float panL = mapSel != null ? GW - (panelW + 8) * Px.easeOut(pan) : GW + 99;
        for (Mission m : order) {
            Journal.Entry e = Journal.entry(m.id());
            boolean hid = "oculta".equals(e.state());
            float[] s = toScreen(sites.get(m.id())[0], sites.get(m.id())[1]);
            float sx = s[0], sy = s[1];
            if (sx < -40 || sx > GW + 40 || sy < -40 || sy > GH + 40 || merged.contains(m.id())) {
                continue;
            }
            boolean sel = mapSel == m;
            int cx = Math.round(sx), bob = Math.round((float) Math.sin(mapT * 2.4f + sx * 0.7f) * 1.5f);
            boolean over0 = Math.hypot(Px.mx - sx, Px.my - (sy - 20)) < 13 || (Px.mx > sx - 30 && Px.mx < sx + 30 && Px.my > sy - 8 && Px.my < sy + 26);
            int rr = sel ? 11 : over0 ? 10 : 9, tail = 7;
            int py = Math.round(sy - (rr + tail + 3) + bob);
            float[] box = {cx - rr - 1, py - rr - 1, cx + rr + 1, py + rr + tail};
            if (!sel && (overlaps(box, hdr) || box[2] > panL - 2)) {
                continue;
            }
            boolean over = over0;
            if (over) {
                mapHov = m;
            }
            int col = hid ? rgb(128, 122, 150) : ST_C[stIndex(e.state())];
            String state = hid ? "oculta" : e.state();
            Art.Theme theme = Art.theme(u.id());
            if (theme == Art.Theme.SCROLL) {
                // a kunai stuck in the map with a paper tag in the state's ink
                Art.kunaiPin(cx, Math.round(sy), stIndex(state), sel || over0);
                if (hid) {
                    T("?", cx + 11, Math.round(sy) - 27, rgb(40, 30, 30), 8, true, 0, false, 1);
                }
            } else if (theme == Art.Theme.DEVICE) {
                // a blip on the radar
                Art.radarBlip(cx, Math.round(sy) - 4, stIndex(state), sel, mapT);
            } else {
                // the little shadow on the ground, under the tip
                for (int yy = -1; yy <= 1; yy++) {
                    int ww = yy != 0 ? 3 : 5;
                    R(cx - ww, Math.round(sy - 1) + yy, ww * 2 + 1, 1, Px.BLACK, 0.32f);
                }
                int k6 = Math.round(rr * 0.6f), w0 = Math.round((float) Math.sqrt(rr * rr - k6 * k6));
                int rim = sel ? GOLD : hid ? rgb(190, 186, 206) : mix(col, WHITE, 0.55f), dark = rgb(22, 16, 28);
                for (int yy = -rr - 1; yy <= rr + tail; yy++) {
                    int w = half(yy, rr, rr, k6, w0, tail) + 1;
                    if (w >= 0) {
                        R(cx - w, py + yy, w * 2 + 1, 1, dark, 1);
                    }
                }
                for (int yy = -rr; yy <= rr + tail - 1; yy++) {
                    int w = half(yy, rr, rr, k6, w0, tail);
                    if (w >= 0) {
                        R(cx - w, py + yy, w * 2 + 1, 1, rim, 1);
                    }
                }
                for (int yy = -rr + 2; yy <= k6 + 1; yy++) {
                    int w = yy <= k6 ? Math.round((float) Math.sqrt(Math.max(0, (rr - 2) * (rr - 2) - yy * yy))) : half(yy, rr, rr, k6, w0, tail) - 2;
                    if (w < 0) {
                        continue;
                    }
                    float t = (yy + rr) / (2f * rr);
                    R(cx - w, py + yy, w * 2 + 1, 1, t < 0.35f ? mix(col, WHITE, 0.18f) : t > 0.75f ? mix(col, Px.BLACK, 0.3f) : col, 1);
                }
                R(cx - Math.round(rr * 0.45f), py - rr + 3, 2, 1, WHITE, 0.8f);
                R(cx - Math.round(rr * 0.6f), py - rr + 4, 1, 2, WHITE, 0.6f);
                Px.stateGlyph(state, cx + 1, py + 1, dark, 1, sel ? 11 : 10);
                Px.stateGlyph(state, cx, py, WHITE, 1, sel ? 11 : 10);
                if (cnt.containsKey(m.id())) {
                    float bx = cx + rr - 2, by = py - rr - 3;
                    R(bx - 1, by - 1, 10, 9, rgb(22, 16, 28), 1);
                    R(bx, by, 8, 7, GOLD, 1);
                    T(String.valueOf(cnt.get(m.id())), bx + 4, by, rgb(30, 20, 10), 6, true, 0, false, 1);
                }
                if ("pronta".equals(state) && !sel) {
                    float pr = (mapT * 1.1f) % 1, rad = rr + 2 + pr * 9;
                    for (int a = 0; a < 56; a++) {
                        double an = a / 56.0 * Math.PI * 2;
                        R(Math.round(cx + Math.cos(an) * rad), Math.round(py + Math.sin(an) * rad), 1, 1, GOLD, 0.75f * (1 - pr));
                    }
                }
            }
            if (cnt.containsKey(m.id()) && Art.theme(u.id()) != Art.Theme.DOSSIER) {
                // pins merged into this one: their count on a little tag
                boolean scroll = Art.theme(u.id()) == Art.Theme.SCROLL;
                float bx = cx + 6, by = Math.round(sy) - (scroll ? 40 : 16);
                R(bx, by, 9, 9, scroll ? Art.N_PAPER : rgb(12, 30, 24), 1);
                R(bx, by, 9, 1, scroll ? Art.N_RED : Art.D_DIM, 1);
                T(String.valueOf(cnt.get(m.id())), bx + 5, by + 2, scroll ? Art.N_INK : Art.D_TXT, 6, true, 0, false, 1);
            }
            if (m.id().equals(Journal.tracked)) {
                Px.sight(cx + rr + 3, py - 4, rgb(255, 236, 140), 1);
            }
            // the name plaque
            String nmT = hid ? "???" : m.lugar();
            int wT = tw(nmT, 7) + 10;
            boolean underPanel = mapSel != null && !sel && sx + wT / 2f > GW - (panelW + 8) * Px.easeOut(pan) - 2;
            float ny = sy + 12;
            float[] nb = {sx - wT / 2f, ny, sx + wT / 2f, ny + 11};
            boolean nameHdr = overlaps(nb, hdr);
            boolean showName = sel || (!nameHdr && (over || (view[2] > 1.45f && !underPanel)));
            if (showName && Art.theme(u.id()) == Art.Theme.SCROLL) {
                // a strip of paper with the name in ink
                int w = wT + 2;
                Art.paperSlip(Math.round(sx - w / 2f), ny, w, 12, 11 + (m.id().hashCode() & 63));
                T(nmT, sx + 1, ny + 3, hid ? Art.N_GREY : Art.N_INK, 7, false, 0, false, 1);
            } else if (showName && Art.theme(u.id()) == Art.Theme.DEVICE) {
                // a label on the radar
                int w = wT;
                R(Math.round(sx - w / 2f), ny, w, 11, rgb(12, 30, 24), 0.92f);
                R(Math.round(sx - w / 2f), ny, w, 1, Art.D_DIM, 1);
                T(nmT, sx, ny + 2.5f, hid ? Art.D_DIM : Art.D_TXT, 7, false, 0, false, 1);
            } else if (showName) {
                int w = wT + 10;
                boolean hot = sel || over;
                for (int q = 0; q < w; q++) {
                    float ed = Math.min(1, Math.min(q / 6f, (w - 1 - q) / 6f));
                    R(Math.round(sx - w / 2f) + q, ny, 1, 11, Px.VEIL, (hot ? 0.88f : 0.72f) * ed);
                    P(Math.round(sx - w / 2f) + q, ny + 10, hot ? GOLD : col, 0.85f * (float) Math.pow(1 - Math.abs(q - w / 2f) / (w / 2f), 0.6));
                }
                T(nmT, sx, ny + 2, hid ? rgb(170, 166, 190) : WHITE, 7, false, 0, true, 1);
            }
            if (over && !sel) {
                final Mission pick = m;
                List<String> tip = hid ? List.of(tr("nao_descoberta"), m.dica().isEmpty() ? tr("explore") : m.dica()) : List.of(m.nome(), stName(state));
                List<Integer> tc = hid ? List.of(rgb(170, 166, 190), MUTED) : List.of(WHITE, col);
                Px.hit(sx - 30, py - rr - 2, 60, sy + 26 - py + rr + 2, () -> {
                    mapSel = pick;
                    click();
                }, tip, tc);
            }
        }
        // ---- top: title and the universe tabs ----
        for (int i = 0; i < 30; i += 2) {
            R(0, i, GW, 2, rgb(6, 7, 14), 0.6f * (1 - i / 30f));
        }
        T(title, 10, 7, rgb(255, 246, 230), 12, true);
        T(sub, 10, 21, rgb(214, 206, 196), 6);
        if (mapSel == null) {
            T(tr("arraste"), GW - 10, GH - 13, rgb(190, 186, 206), 6, false, 1, true, 1);
        }
        // the universes as ribbons hanging from the top (the chosen one longer, the NPC's face at its end)
        float tx = GW - 6;
        for (int i = UNIS.size() - 1; i >= 0; i--) {
            Uni un = UNIS.get(i);
            boolean act = i == dUni, emp = list(un.id()).isEmpty();
            boolean hot = Px.over(tx - 14, 0, 14, 30);
            int rh = act ? 28 : hot && !emp ? 22 : 19;
            int c = emp ? rgb(70, 64, 80) : mix(un.cor(), Px.BLACK, act ? 0 : 0.38f);
            for (int j = 0; j < rh; j++) {
                int row = j % 2 == 1 ? c : mix(c, Px.BLACK, 0.08f);
                if (j > rh - 5) {
                    // the V cut at the end
                    float cut = (j - (rh - 5)) * 1.75f;
                    R(tx - 14, j, Math.max(0, 7 - cut), 1, row, 1);
                    R(tx - 7 + cut, j, Math.max(0, 7 - cut), 1, row, 1);
                } else {
                    R(tx - 14, j, 14, 1, row, 1);
                }
            }
            R(tx - 14, 0, 1, rh - 4, mix(c, Px.BLACK, 0.6f), 1);
            R(tx - 1, 0, 1, rh - 4, mix(c, Px.BLACK, 0.6f), 1);
            R(tx - 12, 0, 1, rh - 5, mix(c, WHITE, 0.25f), 0.6f);
            SkinDraw.head(un.npc(), tx - 7, rh - 11, 1, emp ? 0xD80A0810 : act ? 0xFFFFFFFF : 0xFFA0A0A0);
            if (!emp) {
                final int idx = i;
                Px.hit(tx - 14, 0, 14, 30, () -> {
                    dUni = idx;
                    lastUni = idx;
                    mapSel = null;
                    shownSel = null;
                    cam = new float[] {0.5f, 0.47f, 1};
                    view = new float[] {0.5f, 0.47f, 1};
                    click();
                }, act ? null : List.of(un.nome()), List.of(WHITE));
            } else {
                Px.hit(tx - 14, 0, 14, 30, null, List.of(un.nome(), tr("em_breve")), List.of(WHITE, MUTED));
            }
            tx -= 18;
        }
        T(u.nome(), tx - 2, 6, rgb(255, 246, 230), 8, true, 1, true, 1);
        // ---- the legend at the bottom ----
        float lx = 10, ly = GH - 14;
        Art.Theme lt = Art.theme(u.id());
        int legendW = 0;
        for (int i = 0; i < ST_K.length; i++) {
            legendW += tw(stName(ST_K[i]).toLowerCase(), 7) + 17;
        }
        if (lt == Art.Theme.SCROLL) {
            Art.paperSlip(4, GH - 17, legendW + 6, 13, 77);
        } else if (lt == Art.Theme.DEVICE) {
            R(4, GH - 17, legendW + 6, 13, rgb(12, 30, 24), 0.9f);
            R(4, GH - 17, legendW + 6, 1, Art.D_DIM, 1);
        } else {
            R(4, GH - 17, legendW + 6, 13, Art.J_PAPER, 1);
            R(4, GH - 14, legendW + 6, 1, rgb(230, 150, 150), 0.6f);
        }
        for (int i = 0; i < ST_K.length; i++) {
            String nm = stName(ST_K[i]).toLowerCase();
            if (lt == Art.Theme.SCROLL) {
                P(lx + 1, ly - 1, Art.N_RED, 1);
                Art.paperTag(lx + 1, ly - 1, i, 9);
                T(nm, lx + 9, ly + 0.5f, Art.N_INK, 7, false, -1, false, 1);
            } else if (lt == Art.Theme.DEVICE) {
                R(lx - 1, ly + 2, 3, 3, Art.RADAR_STATE[i], 1);
                P(lx, ly + 2, WHITE, 1);
                T(nm, lx + 6, ly + 0.5f, Art.D_TXT, 7, false, -1, false, 1);
            } else {
                R(lx, ly + 1, 5, 5, Art.J_STAMP, 1);
                R(lx + 1, ly + 2, 3, 3, Art.INK_STATE[i], 1);
                T(nm, lx + 8, ly + 0.5f, Art.J_TYPE, 7, false, -1, false, 1);
            }
            lx += tw(nm, 7) + 17;
        }
        // ---- the chosen mission's page (slides in from the right) ----
        pan = lerp(pan, mapSel != null ? 1 : 0, 1 - (float) Math.pow(0.002, dt));
        if (mapSel != null) {
            shownSel = mapSel;
        }
        if (pan > 0.01f && shownSel != null) {
            float px = GW - (panelW + 12) * Px.easeOut(pan), py = 28, ph = GH - py - 18;
            // the panel takes the clicks off the map under it; registered first so its buttons (Rastrear, rewards) win
            Px.hit(px, py, panelW, ph, null);
            drawMissionPage(shownSel, px, py, panelW, ph, u.cor(), () -> {
                mapSel = null;
                click();
            });
        }
    }

    private static boolean overlaps(float[] b, float[][] boxes) {
        for (float[] h : boxes) {
            if (b[0] < h[2] && b[2] > h[0] && b[1] < h[3] && b[3] > h[1]) {
                return true;
            }
        }
        return false;
    }

    // The width of the pin at a row (round head, then the tip).
    private static int half(int yy, float r, int rr, int k6, int w0, int tail) {
        if (yy < -r) {
            return -1;
        }
        if (yy <= k6) {
            return Math.round((float) Math.sqrt(Math.max(0, r * r - yy * yy)));
        }
        return Math.round(w0 * (1 - (yy - k6) / (float) (rr + tail - k6)) * (r / rr));
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    // ------------------------------------------------------------------------------------------------ the mission page

    // When it comes back: dawn for the dailies (minutes to the next day), forever for the once only ones.
    private static String reset(Mission m, String state) {
        Minecraft mc = Minecraft.getInstance();
        long day = mc.level == null ? 0 : m.uni().equals("naruto") ? mc.level.getDayTime() % 24000L : mc.level.getGameTime() % 24000L;
        int min = (int) Math.ceil((24000 - day) / 20.0 / 60.0);
        return switch (state) {
            case "feita" -> tr("volta_amanhecer", min);
            case "concluida" -> tr("pra_sempre");
            default -> com.starkinhon.battlegroundsmod.client.ui.Journal.daily(m) ? tr("diaria_hoje") : tr("uma_vez");
        };
    }

    // The chosen mission's page, in its universe's material (Stark 2026-11-22, approved in the web preview "Diário Sem
    // Cara de IA"): Naruto a scroll, Dragon Ball a Capsule Corp gadget, Jujutsu (and the universes with no theme yet) a
    // school dossier. close closes the page.
    public static void drawMissionPage(Mission m, float x, float y, float w, float h, int uc, Runnable close) {
        switch (Art.theme(m.uni())) {
            case SCROLL -> pageScroll(m, x, y, Math.round(w), Math.round(h), close);
            case DEVICE -> pageDevice(m, x, y, Math.round(w), Math.round(h), close);
            default -> pageDossier(m, x, y, Math.round(w), Math.round(h), close);
        }
    }

    private record Rew(Journal.Reward r, String name, String sub, net.minecraft.world.item.ItemStack stack) {
    }

    private static List<Rew> rewards(Mission m) {
        List<Rew> out = new ArrayList<>();
        for (Journal.Reward r : m.rec()) {
            var stack = r.stack();
            String name = !r.name().isEmpty() ? r.name() : r.id().equals(UiIcons.PODER) ? tr("poder") : stack.isEmpty() ? r.id() : stack.getHoverName().getString();
            String sub = !r.desc().isEmpty() ? r.desc() : r.id().equals(UiIcons.PODER) ? tr("poder_sub") : Journal.daily(m) ? tr("toda_vez") : tr("uma_vez_r");
            out.add(new Rew(r, name, sub, stack));
        }
        return out;
    }

    private static void rewardIcon(Rew r, float x, float y) {
        if (UiIcons.special(r.r().id())) {
            UiIcons.icon(r.r().id(), x, y);
        } else {
            UiIcons.item(r.stack(), x, y, 1);
        }
        List<String> tip = new ArrayList<>();
        tip.add(r.name());
        if (!r.r().desc().isEmpty()) {
            tip.add(r.r().desc());
        }
        Px.hit(x - 2, y - 2, 20, 20, null, tip, List.of(WHITE, MUTED));
    }

    private static void trackToggle(Mission m) {
        Journal.track(m.id().equals(Journal.tracked) ? "" : m.id());
        click();
    }

    private static boolean pressed() {
        return org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    // How many rewards fit as rows between top and bottom (22 per row); the rest go side by side.
    private static boolean compact(int n, float top, float bottom, float row) {
        return n * row > bottom - top;
    }

    // ---------------------------------------------------------------- Naruto: the scroll

    private static void pageScroll(Mission m, float x, float y, int w, int h, Runnable close) {
        Journal.Entry e = Journal.entry(m.id());
        String state = e.state();
        Art.scroll(x, y, w, h);
        float cx = x + 10, right = x + w - 10;
        // close: an ink cross in the top right corner of the paper
        boolean ovc = Px.over(right - 8, y + 9, 10, 10);
        Px.cross(right - 7, y + 10, ovc ? Art.N_RED : Art.N_GREY, 1);
        Px.hit(right - 8, y + 9, 10, 10, close);
        if ("oculta".equals(state)) {
            hiddenPage(m, x, y, w, h, Art.N_INK, Art.N_GREY);
            return;
        }
        // the face in an ink wash
        float fx = x + w - 30, fy = y + 34;
        Art.blot(fx, fy, 14, Art.N_INK, 8, 0.22f);
        SkinDraw.head(m.skin(), fx, fy, 2, 0xFFFFFFFF);
        float tcw = fx - 18 - cx;
        float yy = y + 14;
        T(Px.fit(m.lugar().toUpperCase(), tcw, 7), cx, yy, Art.N_RED, 7, true, -1, false, 1);
        yy += 9;
        List<String> nl = Px.wrap(m.nome(), tcw, 14, true);
        for (int i = 0; i < Math.min(2, nl.size()); i++) {
            T(nl.get(i), cx, yy, Art.N_INK, 14, true, -1, false, 1);
            yy += 15;
        }
        Art.brush(cx, yy + 1, Math.round(Math.min(96, tcw)), 3, Art.N_INK, 4, 0.92f, 0.7f);
        yy += 6;
        String sn = stName(state).toUpperCase();
        Art.stamp(sn, cx, yy, Art.N_RED, false, 0.88f);
        float hx = cx + tw(sn, 8, true) + 12;
        Art.hanko(hx, yy + 2);
        T(Px.fit(m.tipo(), right - hx - 14, 7), hx + 13, yy + 4, Art.N_GREY, 7, false, -1, false, 1);
        yy += 20;
        // the line it says, with an ink stroke beside it
        List<String> q = Px.wrap("“" + m.fala() + "”", w - 30, 8);
        int nq = Math.min(4, q.size());
        for (int i = 0; i < nq * 9; i++) {
            Px.P(cx + 1 + (i > nq * 9 - 6 ? 1 : 0), yy + i - 1, Art.N_INK, 0.55f + 0.4f * Canvas.vnoise(i * 0.4f, 2, 6));
        }
        for (int i = 0; i < nq; i++) {
            T(q.get(i), cx + 6, yy + i * 9, Art.N_GREY, 8, false, -1, false, 1);
        }
        yy += nq * 9 + 6;
        // objectives: ink rings, a red check and a stroke over the done ones
        yy = inkSection(tr("objetivos"), cx, yy, 6);
        List<Journal.Step> steps = Journal.steps(m);
        for (int i = 0; i < steps.size(); i++) {
            Journal.Step st = steps.get(i);
            int c = Journal.progress(m, i), n = st.goal();
            boolean ok = c >= n;
            Art.inkRing(cx + 5, yy + 3, 3.6f, Art.N_INK, 20 + i, 0.9f);
            String text = Px.fit(st.text(), right - cx - 34, 8);
            T(text, cx + 14, yy, ok ? Art.N_GREY : Art.N_INK, 8, false, -1, false, 1);
            if (ok) {
                Art.check(cx + 3, yy + 1, Art.N_RED, 1);
                int tw2 = tw(text, 8);
                for (int k = 0; k < tw2 + 2; k++) {
                    Px.P(cx + 14 + k, yy + 3 + Math.round((Canvas.vnoise(k * 0.15f, 9, i) - 0.5f) * 1.4f), Art.N_INK, 0.75f);
                }
            } else if (n > 1) {
                T(c + "/" + n, right, yy, Art.N_GREY, 7, false, 1, false, 1);
            }
            yy += 12;
        }
        yy += 4;
        // rewards on sumi blots
        yy = inkSection(tr("recompensas"), cx, yy, 7);
        float by = y + h - 30;
        List<Rew> rs = rewards(m);
        boolean comp = compact(rs.size(), yy, by - 8, 23);
        for (int i = 0; i < rs.size(); i++) {
            Rew r = rs.get(i);
            float ix = comp ? cx + 1 + i * 24 : cx + 1, iy = yy;
            Art.blot(ix + 8, iy + 8, 10, Art.N_INK, 40 + i, 0.16f);
            rewardIcon(r, ix, iy);
            if (r.r().n() > 1) {
                T("x" + r.r().n(), ix + 18, iy + 12, Art.N_RED, 7, true, 1, false, 1);
            }
            if (!comp) {
                T(Px.fit(r.name(), right - ix - 24, 8), ix + 23, iy + 1, Art.N_INK, 8, true, -1, false, 1);
                T(Px.fit(r.sub(), right - ix - 24, 7), ix + 23, iy + 10, Art.N_GREY, 7, false, -1, false, 1);
                yy += 23;
            }
        }
        // footer: when it comes back, the distance and the wooden Rastrear plaque
        for (int k = 0; k < w - 24; k++) {
            if (Canvas.hash(k, 1, 77) > 0.25f) {
                Px.P(cx + 2 + k, by - 5 + Math.round((Canvas.vnoise(k * 0.08f, 1, 7) - 0.5f) * 1.2f), Art.N_INK, 0.35f);
            }
        }
        if (comp ? yy + 24 <= by - 14 : yy <= by - 14) {
            T(reset(m, state), cx, by - 14, "feita".equals(state) ? Art.INK_STATE[2] : Art.N_GREY, 7, false, -1, false, 1);
        }
        if (e.found()) {
            int[] way = Journal.way(m.id());
            if (way != null) {
                Px.arrow(cx, by + 2, way[1], Art.N_INK, 1);
                T(way[0] + " m", cx + 12, by + 3, Art.N_INK, 9, true, -1, false, 1);
            } else {
                T(tr("outro_mundo"), cx, by + 4, Art.N_GREY, 7, false, -1, false, 1);
            }
            boolean isT = m.id().equals(Journal.tracked);
            String label = isT ? tr("rastreando") : tr("rastrear");
            int bw = Math.max(64, tw(label, 8, true) + 16), bh = 16;
            float bx = right - bw, byy = by - 1;
            boolean hot = Px.over(bx - 3, byy, bw + 3, bh), pr = hot && pressed();
            float ty = byy + (pr ? 1 : hot ? -1 : 0);
            // cord and tassel at the left end (it swings under the mouse)
            int sw = hot ? Math.round((float) Math.sin(Px.time * 9)) : 0;
            for (int i = 0; i < 9; i++) {
                Px.P(bx - 1 + (i > 5 ? sw : 0), ty + 4 + i, Art.N_RED, 1);
            }
            Px.R(bx - 2 + sw, ty + 13, 3, 4, Art.N_RED, 1);
            Px.R(bx - 2 + sw, ty + 13, 3, 1, rgb(200, 70, 50), 1);
            Art.woodTag(bx, ty, bw, bh, hot);
            T(label, bx + bw / 2f + 1, ty + bh / 2f - 3, rgb(196, 146, 90), 8, true, 0, false, 1);
            T(label, bx + bw / 2f + 1, ty + bh / 2f - 4, rgb(52, 30, 14), 8, true, 0, false, 1);
            Px.hit(bx - 3, byy, bw + 3, bh, () -> trackToggle(m));
        }
    }

    // A section title in red brush ink with a short stroke after it.
    private static float inkSection(String lab, float cx, float yy, int seed) {
        T(lab, cx, yy, Art.N_RED, 8, true, -1, false, 1);
        Art.brush(cx + tw(lab, 8, true) + 5, yy + 4, 30, 2, Art.N_RED, seed, 0.85f, 0.5f);
        return yy + 12;
    }

    private static void hiddenPage(Mission m, float x, float y, int w, int h, int ink, int soft) {
        float yc = y + h * 0.3f;
        T("???", x + w / 2f, yc, ink, 22, true, 0, false, 1);
        float yy = yc + 30;
        for (String l : Px.wrap("“" + (m.dica().isEmpty() ? tr("explore") : m.dica()) + "”", w - 40, 8)) {
            T(l, x + w / 2f, yy, soft, 8, false, 0, false, 1);
            yy += 10;
        }
        yy += 8;
        for (String l : Px.wrap(tr("ache"), w - 40, 7)) {
            T(l, x + w / 2f, yy, soft, 7, false, 0, false, 1);
            yy += 9;
        }
    }

    // ---------------------------------------------------------------- Dragon Ball: the Capsule Corp gadget

    private static void pageDevice(Mission m, float x, float y, int w, int h, Runnable close) {
        Journal.Entry e = Journal.entry(m.id());
        String state = e.state();
        Art.device(x, y, w, h);
        boolean blink = (int) (Px.time * 2) % 2 == 0;
        Px.R(x + w - 16, y + 7, 3, 3, blink ? rgb(90, 230, 120) : rgb(30, 90, 50), 1);
        Px.P(x + w - 16, y + 7, rgb(200, 255, 210), 1);
        T("CAPSULE CORP.", x + 12, y + 6, Art.D_BLUE, 8, true, -1, false, 1);
        float sx = x + 8, sy = y + 18;
        int sw = w - 16, sh = h - 52;
        Art.scan(sx, sy, sw, sh, Px.time);
        // the buttons on the shell: × closes, RASTREAR tracks
        boolean ovc = Px.over(x + 12, y + h - 27, 15, 15);
        Art.rubber(x + 12, y + h - 27, 15, 15, "×", rgb(120, 124, 134), rgb(70, 72, 80), ovc, ovc && pressed());
        Px.hit(x + 12, y + h - 27, 15, 15, close);
        if ("oculta".equals(state)) {
            hiddenPage(m, sx, sy, sw, sh, Art.D_TXT, Art.D_DIM);
            return;
        }
        // the video call: the NPC's face on its own little screen
        Px.R(sx + sw - 32, sy + 5, 28, 32, rgb(4, 18, 14), 1);
        Px.R(sx + sw - 32, sy + 5, 28, 1, Art.D_DIM, 1);
        SkinDraw.head(m.skin(), sx + sw - 18, sy + 21, 2.4f, 0xFFC8FFDC);
        for (int j = 0; j < 32; j += 2) {
            Px.R(sx + sw - 32, sy + 5 + j, 28, 1, 0, 0.18f);
        }
        float tcw = sw - 44;
        float yy = sy + 6;
        T(Px.fit("> " + m.lugar().toUpperCase(), tcw, 7), sx + 6, yy, Art.D_AMB, 7, true, -1, false, 1);
        yy += 9;
        List<String> nl = Px.wrap(m.nome(), tcw, 14, true);
        for (int i = 0; i < Math.min(2, nl.size()); i++) {
            T(nl.get(i), sx + 6, yy, Art.D_TXT, 14, true, -1, false, 1);
            yy += 15;
        }
        yy += 2;
        String sn = stName(state).toUpperCase();
        Px.R(sx + 8, yy + 1, 3, 3, Art.D_AMB, blink ? 1 : 0.4f);
        T(sn, sx + 14, yy, Art.D_AMB, 7, true, -1, false, 1);
        T(Px.fit("· " + m.tipo(), tcw - tw(sn, 7, true) - 10, 7), sx + 18 + tw(sn, 7, true), yy, Art.D_DIM, 7, false, -1, false, 1);
        yy = Math.max(yy + 12, sy + 42);
        List<String> q = Px.wrap("“" + m.fala() + "”", sw - 12, 8);
        for (int i = 0; i < Math.min(3, q.size()); i++) {
            T(q.get(i), sx + 6, yy + i * 9, Art.D_DIM, 8, false, -1, false, 1);
        }
        yy += Math.min(3, q.size()) * 9 + 5;
        T("> " + tr("objetivos"), sx + 6, yy, Art.D_AMB, 8, true, -1, false, 1);
        yy += 11;
        List<Journal.Step> steps = Journal.steps(m);
        for (int i = 0; i < steps.size(); i++) {
            Journal.Step st = steps.get(i);
            int c = Journal.progress(m, i), n = st.goal();
            boolean ok = c >= n;
            int segs = Math.min(6, Math.max(1, n));
            int lit = ok ? segs : Math.round(segs * Math.min(1, c / (float) Math.max(1, n)));
            Art.segBar(sx + 8, yy + 2, segs, lit, 6);
            T(Px.fit(st.text(), sw - 70, 8), sx + 52, yy, ok ? Art.D_DIM : Art.D_TXT, 8, false, -1, false, 1);
            T(ok ? "OK" : c + "/" + n, sx + sw - 6, yy, ok ? Art.D_TXT : Art.D_DIM, 8, ok, 1, false, 1);
            yy += 12;
        }
        yy += 2;
        T("> " + tr("recompensas"), sx + 6, yy, Art.D_AMB, 8, true, -1, false, 1);
        yy += 11;
        float bottom = sy + sh - 22;
        List<Rew> rs = rewards(m);
        boolean comp = compact(rs.size(), yy, bottom, 22);
        for (int i = 0; i < rs.size(); i++) {
            Rew r = rs.get(i);
            float cxp = comp ? sx + 8 + i * 32 : sx + 8;
            Art.capsule(cxp, yy, 28, 18, Art.D_DIM);
            rewardIcon(r, cxp + 6, yy + 1);
            if (r.r().n() > 1) {
                T("x" + r.r().n(), cxp + 27, yy + 10, Art.D_AMB, 7, true, 1, false, 1);
            }
            if (!comp) {
                T(Px.fit(r.name(), sw - 50, 8), sx + 42, yy + 2, Art.D_TXT, 8, true, -1, false, 1);
                T(Px.fit(r.sub(), sw - 50, 7), sx + 42, yy + 10, Art.D_DIM, 7, false, -1, false, 1);
                yy += 22;
            }
        }
        // the mini radar with the distance
        float ry = sy + sh - 11;
        for (int a = 0; a < 40; a++) {
            double an = a / 40.0 * Math.PI * 2;
            Px.P(Math.round(sx + 12 + Math.cos(an) * 7), Math.round(ry + Math.sin(an) * 7), Art.D_DIM, 1);
        }
        Px.R(sx + 12, ry - 5, 1, 10, Art.D_GRID, 1);
        Px.R(sx + 7, ry, 10, 1, Art.D_GRID, 1);
        int[] way = e.found() ? Journal.way(m.id()) : null;
        if (way != null) {
            double an = way[1] * Math.PI / 4 - Math.PI / 2;
            for (int k = 0; k < 6; k++) {
                Px.P(Math.round(sx + 12 + Math.cos(an) * k), Math.round(ry + Math.sin(an) * k), Art.D_AMB, 1);
            }
            T(way[0] + " m", sx + 24, ry - 4, Art.D_TXT, 10, true, -1, false, 1);
        } else if (e.found()) {
            T(tr("outro_mundo"), sx + 24, ry - 3, Art.D_DIM, 7, false, -1, false, 1);
        }
        T(Px.fit(reset(m, state), sw - 100, 7), sx + sw - 6, ry - 3, "feita".equals(state) ? Art.D_TXT : Art.D_DIM, 7, false, 1, false, 1);
        if (e.found()) {
            boolean isT = m.id().equals(Journal.tracked);
            String label = (isT ? tr("rastreando") : tr("rastrear")).toUpperCase();
            int bw = Math.max(70, tw(label, 8, true) + 14);
            float bx = x + w - 10 - bw, byy = y + h - 27;
            boolean hot = Px.over(bx, byy, bw, 15);
            Art.rubber(bx, byy, bw, 15, label, isT ? rgb(70, 160, 80) : Art.D_BTN, isT ? rgb(30, 90, 40) : Art.D_BTND, hot, hot && pressed());
            Px.hit(bx, byy, bw, 15, () -> trackToggle(m));
        }
    }

    // ---------------------------------------------------------------- Jujutsu (and the rest): the dossier

    private static void pageDossier(Mission m, float x, float y, int w, int h, Runnable close) {
        Journal.Entry e = Journal.entry(m.id());
        String state = e.state();
        Art.dossier(x, y, w, h);
        T(Px.fit((m.quem().isEmpty() ? m.lugar() : m.quem()).toUpperCase(), 62, 7), x + 14, y + 2, rgb(90, 66, 30), 7, true, -1, false, 1);
        float px = x + 6, py = y + 14;
        int pw = w - 12, ph = h - 22;
        // close: a small × typed on the folder's corner
        boolean ovc = Px.over(x + w - 12, y + 1, 10, 10);
        Px.cross(x + w - 11, y + 2, ovc ? Art.J_STAMP : rgb(120, 90, 50), 1);
        Px.hit(x + w - 12, y + 1, 10, 10, close);
        if ("oculta".equals(state)) {
            hiddenPage(m, px, py, pw, ph, Art.J_TYPE, Art.J_GREY);
            return;
        }
        // the index tab sticking out on the right is the Rastrear button
        if (e.found()) {
            boolean isT = m.id().equals(Journal.tracked);
            float tx = x + w - 2, ty = y + 70;
            int tbw = 12, th = Math.max(56, tw((isT ? tr("rastreando") : tr("rastrear")).toUpperCase(), 7, true) + 12);
            boolean hot = Px.over(tx, ty, tbw + 3, th), pr = hot && pressed();
            int out = hot ? 3 : 0;
            int col = isT ? rgb(40, 110, 60) : Art.J_STAMP;
            Px.R(tx, ty, tbw + out, th, pr ? mix(col, Px.BLACK, 0.2f) : col, 1);
            Px.R(tx, ty, tbw + out, 1, mix(col, WHITE, 0.4f), 1);
            Px.R(tx + tbw + out - 1, ty + 1, 1, th - 1, mix(col, Px.BLACK, 0.4f), 1);
            Px.g.pose().pushPose();
            Px.g.pose().translate(tx + (tbw + out) / 2f + 4, ty + th / 2f, 0);
            Px.g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));
            T((isT ? tr("rastreando") : tr("rastrear")).toUpperCase(), 0, 0, rgb(255, 240, 236), 7, true, 0, false, 1);
            Px.g.pose().popPose();
            Px.hit(tx, ty, tbw + 3, th, () -> trackToggle(m));
        }
        // the photo with a clip
        float fx = px + pw - 40, fy = py + 10;
        Px.R(fx + 1, fy + 1, 32, 38, 0, 0.25f);
        Px.R(fx, fy, 32, 38, rgb(250, 250, 246), 1);
        Px.R(fx + 3, fy + 3, 26, 26, rgb(60, 64, 70), 1);
        Px.scissor(fx + 3, fy + 3, fx + 29, fy + 29);
        SkinDraw.head(m.skin(), fx + 16, fy + 17, 2.6f, 0xFFE6E6E6);
        Px.endScissor();
        Art.clip(fx + 10, fy - 5, 12);
        float tcw = fx - px - 14;
        T(Px.fit(tr("relatorio"), tcw, 8), px + 8, py + 8, Art.J_TYPE, 8, true, -1, false, 1);
        Px.R(px + 8, py + 18, tcw, 1, Art.J_TYPE, 1);
        T(tr("local"), px + 8, py + 23, Art.J_GREY, 6, true, -1, false, 1);
        T(Px.fit(m.lugar(), tcw, 8), px + 8, py + 30, Art.J_TYPE, 8, false, -1, false, 1);
        T(tr("missao"), px + 8, py + 42, Art.J_GREY, 6, true, -1, false, 1);
        float yy = py + 49;
        List<String> nl = Px.wrap(m.nome(), pw - 16, 12, true);
        for (int i = 0; i < Math.min(2, nl.size()); i++) {
            T(nl.get(i), px + 8, yy, Art.J_TYPE, 12, true, -1, false, 1);
            yy += 13;
        }
        Art.stamp(stName(state).toUpperCase(), Math.min(px + 56, fx - tw(stName(state).toUpperCase(), 8, true) - 12), py + 21, Art.J_STAMP, true, 0.82f);
        T(Px.fit(m.tipo(), pw - 16, 7), px + 8, yy, Art.J_GREY, 7, false, -1, false, 1);
        yy += 11;
        List<String> q = Px.wrap("“" + m.fala() + "”", pw - 18, 8);
        for (int i = 0; i < Math.min(3, q.size()); i++) {
            T(q.get(i), px + 8, yy + i * 9, Art.J_GREY, 8, false, -1, false, 1);
        }
        yy += Math.min(3, q.size()) * 9 + 5;
        T(tr("objetivos"), px + 8, yy, Art.J_TYPE, 8, true, -1, false, 1);
        Px.R(px + 8, yy + 9, pw - 16, 1, Art.J_P2, 1);
        yy += 14;
        List<Journal.Step> steps = Journal.steps(m);
        for (int i = 0; i < steps.size(); i++) {
            Journal.Step st = steps.get(i);
            int c = Journal.progress(m, i), n = st.goal();
            boolean ok = c >= n;
            Px.R(px + 9, yy, 7, 7, Art.J_TYPE, 1);
            Px.R(px + 10, yy + 1, 5, 5, Art.J_PAPER, 1);
            if (ok) {
                Art.check(px + 9, yy + 2, Art.J_PEN, 1);
            }
            String text = st.text() + (n > 1 && !ok ? " " + c + "/" + n : "");
            T(Px.fit(text, pw - 30, 8), px + 21, yy, ok ? Art.J_GREY : Art.J_TYPE, 8, false, -1, false, 1);
            yy += 12;
        }
        yy += 3;
        T(tr("anexos"), px + 8, yy, Art.J_TYPE, 8, true, -1, false, 1);
        Px.R(px + 8, yy + 9, pw - 16, 1, Art.J_P2, 1);
        yy += 13;
        float bottom = py + ph - 14;
        List<Rew> rs = rewards(m);
        boolean comp = compact(rs.size(), yy, bottom, 23);
        for (int i = 0; i < rs.size(); i++) {
            Rew r = rs.get(i);
            float bx = comp ? px + 8 + i * 28 : px + 8;
            // an evidence bag: clear plastic with a red strip on top
            Px.R(bx, yy, 24, 21, rgb(230, 236, 242), 0.55f);
            Px.R(bx, yy, 24, 2, rgb(200, 60, 60), 1);
            Px.R(bx, yy + 2, 1, 19, rgb(190, 200, 212), 1);
            Px.R(bx + 23, yy + 2, 1, 19, rgb(190, 200, 212), 1);
            Px.R(bx, yy + 20, 24, 1, rgb(190, 200, 212), 1);
            rewardIcon(r, bx + 4, yy + 4);
            Px.P(bx + 3, yy + 5, WHITE, 1);
            Px.P(bx + 4, yy + 6, WHITE, 1);
            if (r.r().n() > 1) {
                T("x" + r.r().n(), bx + 23, yy + 13, Art.J_STAMP, 7, true, 1, false, 1);
            }
            if (!comp) {
                T(Px.fit(r.name(), pw - 46, 8), px + 38, yy + 2, Art.J_TYPE, 8, true, -1, false, 1);
                T(Px.fit(r.sub(), pw - 46, 7), px + 38, yy + 11, Art.J_GREY, 7, false, -1, false, 1);
                yy += 23;
            }
        }
        // the typed footer: distance and when it comes back
        int[] way = e.found() ? Journal.way(m.id()) : null;
        String foot = way != null ? tr("distancia", way[0], tr("dir." + way[1])) : e.found() ? tr("outro_mundo") : "";
        T(Px.fit(foot, pw - 16, 7), px + 8, py + ph - 9, Art.J_TYPE, 7, false, -1, false, 1);
        if (comp ? yy + 24 <= bottom - 10 : yy <= bottom - 10) {
            T(Px.fit(reset(m, state), pw - 16, 7), px + 8, bottom - 8, "feita".equals(state) ? Art.INK_STATE[2] : Art.J_GREY, 7, false, -1, false, 1);
        }
    }

    // ------------------------------------------------------------------------------------------------ the Caminho

    private static final String[] PATH_FACE = {"npc_mestre_kame", "npc_kakashi", "npc_yaga"};
    private static final int[] PATH_COLOR = {0xFF9A2E, 0xF28C28, 0x7A5CFF};
    private static final String[] PATH_SHORT = {"db", "nr", "jj"};

    // The three paths side by side, each a line of 7 beads: the done ones with a check, the current one lit (with
    // what to do), the next ones "???". Clicking a path's head makes it the one the corner shows. Under them, the last one.
    private void drawPath(float dt) {
        pathT += dt;
        R(0, 0, GW, GH, rgb(8, 7, 14), 0.94f);
        for (int i = 0; i < 30; i += 2) {
            R(0, i, GW, 2, rgb(6, 7, 14), 0.6f * (1 - i / 30f));
        }
        T(tr("caminho.titulo"), 10, 7, rgb(255, 246, 230), 12, true);
        T(tr("caminho.sub"), 10, 21, rgb(190, 186, 206), 6);
        if (!PathClient.on()) {
            T(tr("caminho.vazio"), GW / 2f, GH / 2f - 4, MUTED, 8, false, 0, true, 1);
            return;
        }
        float colW = Math.min(150, (GW - 40) / 3f), gap = (GW - colW * 3) / 4f;
        float top = 38;
        for (int u = 0; u < 3; u++) {
            String uni = PathClient.UNIS.get(u);
            float x = gap + u * (colW + gap);
            int col = PATH_COLOR[u];
            int step = PathClient.step(u);
            boolean done = step >= PathClient.STEPS, act = uni.equals(PathClient.active);
            // the head: the master who opens it, its name, ACOMPANHANDO on the one the corner shows
            boolean over = Px.over(x, top, colW, 22);
            card(x, top, colW, 22, col, act || over);
            SkinDraw.head(PATH_FACE[u], x + 11, top + 11, 1.1f, 0xFFFFFFFF);
            T(tr("caminho." + uni), x + 22, top + 4, act ? ink(col) : WHITE, 8, true);
            T(done ? tr("caminho.completo") : act ? tr(PathClient.hidden ? "caminho.oculto" : "caminho.seguindo")
                    : tr("caminho.passo", Math.min(step + 1, PathClient.STEPS), PathClient.STEPS),
                    x + 22, top + 13, act ? PathClient.hidden ? MUTED : GOLD : MUTED, 6);
            if (!act && !done) {
                final String pick = uni;
                Px.hit(x, top, colW, 22, () -> {
                    PacketDistributor.sendToServer(new ModPayloads.PathPick(pick));
                    PathClient.active = pick;
                    PathClient.hidden = false;
                    click();
                }, List.of(tr("caminho.seguir")), List.of(WHITE));
            } else if (act) {
                // the one being followed: take it off the screen or put it back (Stark 2026-11-20); the path keeps going
                final boolean show = PathClient.hidden;
                Px.hit(x, top, colW, 22, () -> {
                    PacketDistributor.sendToServer(new ModPayloads.PathTrack(show));
                    PathClient.hidden = !show;
                    click();
                }, List.of(tr(show ? "caminho.mostrar" : "caminho.ocultar")), List.of(WHITE));
            }
            // the line of steps
            float y = top + 32, lineX = x + 9;
            for (int i = 0; i < PathClient.STEPS; i++) {
                String id = "cam_" + PATH_SHORT[u] + (i + 1);
                boolean ok = i < step, now = i == step;
                float rowH = now ? 30 : 19;
                if (i < PathClient.STEPS - 1) {
                    R(lineX, y + 5, 1, rowH, ok ? col : rgb(70, 64, 86), ok ? 0.8f : 0.6f);
                }
                if (ok) {
                    bead(lineX, y + 4, 4, mix(col, Px.BLACK, 0.2f), true);
                    Px.check(lineX - 2, y + 2, WHITE, 1);
                    String nm = Px.fit(Component.translatable("quest.battlegroundsmod." + id + ".titulo").getString(), colW - 20, 7);
                    T(nm, x + 18, y + 1, rgb(150, 145, 170), 7);
                    R(x + 18, y + 4, tw(nm, 7), 1, rgb(150, 145, 170), 0.7f);
                } else if (now) {
                    float pulse = 0.5f + 0.5f * (float) Math.sin(pathT * 4);
                    Px.glow(lineX, y + 4, 9, col, 0.35f + 0.25f * pulse);
                    bead(lineX, y + 4, 5, col, true);
                    bead(lineX, y + 4, 5, mix(col, WHITE, 0.5f), false);
                    String nm = Px.fit(Component.translatable("quest.battlegroundsmod." + id + ".titulo").getString(), colW - 20, 8);
                    T(nm, x + 18, y, WHITE, 8, true);
                    List<String> w = Px.wrap(Component.translatable("quest.battlegroundsmod." + id).getString(), colW - 20, 6);
                    for (int k = 0; k < Math.min(2, w.size()); k++) {
                        T(w.get(k), x + 18, y + 11 + k * 8, rgb(200, 196, 214), 6);
                    }
                } else {
                    bead(lineX, y + 4, 3, rgb(60, 56, 76), true);
                    bead(lineX, y + 4, 3, rgb(110, 100, 130), false);
                    T("???", x + 18, y + 1, rgb(110, 100, 130), 7);
                }
                y += rowH;
            }
        }
        // the last one: Os mais fortes
        int last = PathClient.last();
        float fy = GH - 30, fw = Math.min(260, GW - 40), fx = GW / 2f - fw / 2f;
        card(fx, fy, fw, 20, last == 0 ? rgb(70, 64, 80) : 0xD8352A, last == 1);
        String nm = last == 0 ? tr("caminho.fim_fechado") : Component.translatable("quest.battlegroundsmod.cam_fim.titulo").getString();
        T(nm, GW / 2f, fy + 3, last == 0 ? MUTED : WHITE, 8, true, 0, true, 1);
        String sub = last == 0 ? tr("caminho.fim_dica") : last == 2 ? tr("caminho.completo")
                : Component.translatable("quest.battlegroundsmod.cam_fim").getString();
        T(Px.fit(sub, fw - 12, 6), GW / 2f, fy + 12, last == 1 ? GOLD : MUTED, 6, false, 0, true, 1);
    }

    // A round bead (filled or a ring) of radius r centered on (cx, cy).
    private static void bead(float cx, float cy, int r, int rgb, boolean fill) {
        for (int j = -r; j <= r; j++) {
            int w = Math.round((float) Math.sqrt(Math.max(0, r * r - j * j + r * 0.6f)));
            if (fill) {
                R(cx - w, cy + j, w * 2 + 1, 1, rgb, 1);
            } else {
                P(cx - w, cy + j, rgb, 1);
                P(cx + w, cy + j, rgb, 1);
            }
        }
        if (!fill) {
            R(cx - Math.round(r * 0.5f), cy - r, Math.round(r * 0.5f) * 2 + 1, 1, rgb, 1);
            R(cx - Math.round(r * 0.5f), cy + r, Math.round(r * 0.5f) * 2 + 1, 1, rgb, 1);
        }
    }

    // A flat card with a stripe of color on its left edge.
    private static void card(float x, float y, float w, float h, int col, boolean lit) {
        R(x + 1, y + 2, w, h, Px.BLACK, 0.35f);
        R(x, y, w, h, lit ? rgb(36, 30, 40) : rgb(22, 18, 26), 0.92f);
        R(x, y, 3, h, col, lit ? 1 : 0.7f);
        R(x + 3, y + h - 1, w - 3, 1, Px.BLACK, 0.5f);
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            float gx = Px.toGrid(mouseX), gy = Px.toGrid(mouseY);
            if (Px.click(gx, gy)) {
                return true;
            }
            dragging = true;
            dragX = gx;
            dragY = gy;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && button == 0) {
            float gx = Px.toGrid(mouseX), gy = Px.toGrid(mouseY);
            if (mapSel != null && Math.hypot(gx - dragX, gy - dragY) > 3) {
                // dragging the map lets go of the chosen one
                cam = view.clone();
                mapSel = null;
            }
            if (mapSel == null) {
                float sc = fit() * cam[2];
                cam[0] -= (gx - dragX) / (MAP_W * sc);
                cam[1] -= (gy - dragY) / (MAP_H * sc);
                clampCam(cam);
                view[0] = cam[0];
                view[1] = cam[1];
            }
            dragX = gx;
            dragY = gy;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mapSel == null) {
            float gx = Px.toGrid(mouseX), gy = Px.toGrid(mouseY);
            float before = fit() * cam[2];
            float fxm = cam[0] + (gx - GW / 2f) / (MAP_W * before), fym = cam[1] + (gy - GH / 2f) / (MAP_H * before);
            cam[2] = clamp(cam[2] * (scrollY > 0 ? 1.18f : 1 / 1.18f), 1, 3);
            float after = fit() * cam[2];
            cam[0] = fxm - (gx - GW / 2f) / (MAP_W * after);
            cam[1] = fym - (gy - GH / 2f) / (MAP_H * after);
            clampCam(cam);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Keybinds.MISSIONS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
