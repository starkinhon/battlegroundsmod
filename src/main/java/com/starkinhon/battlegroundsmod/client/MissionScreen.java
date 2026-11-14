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
import static com.starkinhon.battlegroundsmod.client.ui.Px.diamond;
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
import com.starkinhon.battlegroundsmod.client.ui.Journal;
import com.starkinhon.battlegroundsmod.client.ui.Journal.Mission;
import com.starkinhon.battlegroundsmod.client.ui.Px;
import com.starkinhon.battlegroundsmod.client.ui.SkinDraw;
import com.starkinhon.battlegroundsmod.client.ui.UiIcons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

// The Diário de Missões (J), approved in the preview (Stark 2026-11-13: the pixel map "é isso, ficou muito legal", the
// new panel "bem melhor"). The universe's map as a pixel art diorama of its own structures in blocks (Dragon Ball's drawn
// map; Naruto's improvised), a pin on each structure with the state of its mission (NOVA !, EM ANDAMENTO, FEITA HOJE,
// CONCLUÍDA, or ??? under the fog until found), pins too close merged with a count until zoomed in, the name plaques when
// zoomed. Drag to walk, wheel to zoom. Clicking a pin flies the camera to it and slides the mission page in from the right:
// no box, a dark veil born from the map, a gold line with diamonds, the NPC's bust coming out of the shadow, the line it
// says, the objectives with their bars, the rewards in glowing tiles, when it comes back, the distance and Rastrear (the
// corner HUD then shows it with an arrow). Drawn on the preview's pixel grid (client/ui/Px), same numbers as the preview.
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
            Uni u = UNIS.get(dUni);
            drawMap(dt, u, list(u.id()));
            Px.drawTip();
        } finally {
            Px.end();
        }
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
            String state = hid ? "oculta" : e.state();
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
            if (showName) {
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
        T(title, 10, 7, mix(GOLD, WHITE, 0.25f), 12, true);
        T(sub, 10, 21, rgb(190, 186, 206), 6);
        if (mapSel == null) {
            T(tr("arraste"), GW - 10, GH - 13, rgb(190, 186, 206), 6, false, 1, true, 1);
        }
        float tx = GW - 10;
        for (int i = UNIS.size() - 1; i >= 0; i--) {
            Uni un = UNIS.get(i);
            boolean act = i == dUni, emp = list(un.id()).isEmpty();
            int w = act ? tw(un.nome(), 7, true) + 26 : 20;
            tx -= w;
            Px.smallFrame(tx, 5, w, 18, emp ? rgb(70, 64, 80) : un.cor(), act, 0.9f);
            SkinDraw.head(un.npc(), tx + 10, 14, 1, emp ? 0xD80A0810 : 0xFFFFFFFF);
            if (act) {
                T(un.nome(), tx + 19, 10, ink(un.cor()), 7, true);
            }
            if (!emp) {
                final int idx = i;
                Px.hit(tx, 5, w, 18, () -> {
                    dUni = idx;
                    lastUni = idx;
                    mapSel = null;
                    shownSel = null;
                    cam = new float[] {0.5f, 0.47f, 1};
                    view = new float[] {0.5f, 0.47f, 1};
                    click();
                }, act ? null : List.of(un.nome()), List.of(WHITE));
            } else {
                Px.hit(tx, 5, w, 18, null, List.of(un.nome(), tr("em_breve")), List.of(WHITE, MUTED));
            }
            tx -= 3;
        }
        // ---- the legend at the bottom ----
        float lx = 10, ly = GH - 14;
        for (int i = 0; i < ST_K.length; i++) {
            int sc = ST_C[i];
            diamond(lx + 4, ly + 4, 5, mix(sc, Px.BLACK, 0.15f), 1);
            diamond(lx + 4, ly + 4, 5, mix(sc, WHITE, 0.4f), 1, false);
            Px.stateGlyph(ST_K[i], lx + 4, ly + 4, WHITE, 1, 6);
            String nm = stName(ST_K[i]).toLowerCase();
            T(nm, lx + 12, ly + 1, rgb(200, 196, 214), 6);
            lx += tw(stName(ST_K[i]), 6) + 22;
        }
        // ---- the chosen mission's page (slides in from the right) ----
        pan = lerp(pan, mapSel != null ? 1 : 0, 1 - (float) Math.pow(0.002, dt));
        if (mapSel != null) {
            shownSel = mapSel;
        }
        if (pan > 0.01f && shownSel != null) {
            float px = GW - (panelW + 8) * Px.easeOut(pan), py = 30, ph = GH - py - 22;
            // the panel takes the clicks off the map under it; registered first so its buttons (Rastrear, rewards) win
            Px.hit(px, py, panelW, ph, null);
            drawMissionPage(shownSel, px, py, panelW, ph, u.cor());
            float bx = px + panelW - 10, by = py - 1;
            boolean ov = Px.over(bx - 2, by - 2, 12, 12);
            Px.cross(bx + 1, by + 1, ov ? WHITE : rgb(170, 164, 190), 1);
            Px.hit(bx - 2, by - 2, 12, 12, () -> {
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

    public static void drawMissionPage(Mission m, float x, float y, float w, float h, int uc) {
        Journal.Entry e = Journal.entry(m.id());
        String state = e.state();
        int stc = ST_C[stIndex(state)];
        boolean hid = "oculta".equals(state);
        int acc = mix(ink(uc), WHITE, 0.2f), soft = rgb(206, 198, 224), dim = rgb(146, 140, 166);
        // the veil: clear at the map's edge, dark behind the text; fades at the top and bottom
        float fx = Math.round(x - 30);
        for (int i = 0; i < GW - fx; i += 2) {
            float t = Math.min(1, i / 34f), a = 0.9f * t * t * (3 - 2 * t);
            R(fx + i, y, 2, h, Px.VEIL, a);
            for (int j = 0; j < 18; j++) {
                float f = (float) Math.pow(1 - j / 18f, 1.6);
                R(fx + i, y - 1 - j, 2, 1, Px.VEIL, a * f);
                R(fx + i, y + h + j, 2, 1, Px.VEIL, a * f);
            }
        }
        // the gold line with diamonds
        float lx = x - 4;
        for (float yy = y + 10; yy < y + h - 10; yy++) {
            float t = Math.min(1, Math.min((yy - y - 10) / 30, (y + h - 10 - yy) / 30));
            P(lx, yy, GOLD, 0.55f * t);
        }
        diamond(lx, y + 6, 2, GOLD, 0.9f);
        diamond(lx, y + h - 6, 2, GOLD, 0.9f);
        diamond(lx, y + 6, 1, WHITE, 0.8f);
        float cx = x + 6, cw = w - 14;
        if (hid) {
            float yc = y + h * 0.3f;
            T("???", x + w / 2, yc, mix(dim, WHITE, 0.2f), 22, true, 0, true, 1);
            float yy = yc + 30;
            for (String l : Px.wrap("“" + (m.dica().isEmpty() ? tr("explore") : m.dica()) + "”", cw - 10, 7)) {
                T(l, x + w / 2, yy, soft, 7, false, 0, true, 1);
                yy += 10;
            }
            yy += 6;
            R(x + w / 2 - 30, yy, 60, 1, GOLD, 0.4f);
            diamond(x + w / 2, yy, 2, GOLD, 0.8f);
            yy += 8;
            for (String l : Px.wrap(tr("ache"), cw - 20, 6)) {
                T(l, x + w / 2, yy, dim, 6, false, 0, true, 1);
                yy += 8;
            }
            return;
        }
        // ---- header: the bust coming out of the shadow on the right, the name on the left ----
        float bw = Math.min(62, Math.round(w * 0.32f)), bh = 60, bx = x + w - bw - 4, by0 = y + 2;
        Px.glow(bx + bw / 2, by0 + bh * 0.55f, bw * 0.47f, uc, 0.4f);
        {
            float k = 2.1f;
            Px.g.enableScissor(Math.round(bx), Math.round(by0), Math.round(bx + bw), Math.round(by0 + bh));
            SkinDraw.Pose p = new SkinDraw.Pose(-0.35f, 0.08f);
            p.head = (float) Math.sin(Px.time * 0.8f) * 0.04f;
            SkinDraw.draw(m.skin(), bx + bw / 2, by0 + 6 + k * 31, k, p, SkinDraw.BUST, 0xFFFFFFFF);
            Px.g.disableScissor();
            for (int i = 0; i < 18; i += 2) {
                R(bx, by0 + bh - 18 + i, bw, 2, Px.VEIL, (i / 18f) * 0.95f);
            }
        }
        float tcw = bx - cx - 4;
        float yy = y + 6;
        diamond(cx + 2, yy + 3, 2, acc, 1);
        T(m.lugar().toUpperCase(), cx + 8, yy, acc, 6, true);
        yy += 11;
        List<String> nl = Px.wrap(m.nome(), tcw, 13, true);
        for (int i = 0; i < Math.min(2, nl.size()); i++) {
            T(nl.get(i), cx, yy, rgb(255, 246, 226), 13, true);
            yy += 15;
        }
        yy += 1;
        for (int i = 0; i < tcw; i++) {
            P(cx + i, yy, GOLD, 0.85f * (1 - i / tcw));
        }
        diamond(cx, yy, 1, WHITE, 0.9f);
        yy += 6;
        {
            float chx = cx;
            diamond(chx + 2, yy + 3, 2, stc, 1);
            String sn = stName(state);
            T(sn, chx + 7, yy, mix(stc, WHITE, 0.35f), 6, true);
            chx += tw(sn, 6, true) + 16;
            P(chx - 6, yy + 3, dim, 1);
            T(m.tipo(), chx, yy, dim, 6);
        }
        yy = Math.max(yy + 12, by0 + bh - 6);
        // the line it says
        {
            List<String> ls = Px.wrap("“" + m.fala() + "”", cw - 8, 7);
            int n = Math.min(4, ls.size());
            R(cx, yy, 1, n * 9, GOLD, 0.5f);
            for (int i = 0; i < n; i++) {
                T(ls.get(i), cx + 6, yy + i * 9, soft, 7);
            }
            yy += n * 9 + 8;
        }
        yy = section(tr("objetivos"), cx, yy, x + w - 6);
        List<Journal.Step> steps = Journal.steps(m);
        for (int i = 0; i < steps.size(); i++) {
            Journal.Step st = steps.get(i);
            int c = Journal.progress(m, i), n = st.goal();
            boolean ok = c >= n, prog = n > 1;
            diamond(cx + 3, yy + 3, 3, GOLD, ok ? 1 : 0.55f, ok);
            if (ok) {
                diamond(cx + 3, yy + 3, 1, WHITE, 0.9f);
            }
            T(Px.fit(st.text(), cw - 12, 7), cx + 10, yy, ok ? dim : rgb(236, 230, 246), 7);
            if (prog) {
                float bw2 = cw - 12, bx2 = cx + 10;
                boolean secs = st.text().contains(" s") || m.id().equals("nave");
                T(c + "/" + n + (secs ? " s" : ""), x + w - 6, yy, dim, 6, false, 1, true, 1);
                R(bx2, yy + 10, bw2, 2, WHITE, 0.09f);
                R(bx2, yy + 10, Math.round(bw2 * Math.min(1, c / (float) n)), 2, ok ? rgb(128, 230, 90) : mix(uc, WHITE, 0.3f), 1);
                yy += 17;
            } else {
                yy += 11;
            }
        }
        yy += 4;
        yy = section(tr("recompensas"), cx, yy, x + w - 6);
        float by = y + h - 22, availR = by - 12 - yy;
        int nr = Math.max(1, m.rec().size());
        float rowH = Math.max(22, Math.min(26, (float) Math.floor(availR / nr)));
        boolean showSub = rowH >= 23;
        // not room for one row each (Kame's three rewards on a small window ran into the footer): the tiles side by
        // side, the names in their tooltips
        boolean compact = availR < nr * 22;
        int ri = 0;
        for (Journal.Reward r : m.rec()) {
            float tx = compact ? cx + ri * 24 : cx, ty = yy;
            ri++;
            boolean special = com.starkinhon.battlegroundsmod.client.ui.Journal.onceFirst(m) || UiIcons.special(r.id());
            int gc = special ? GOLD : rgb(120, 190, 255);
            Px.glow(tx + 10, ty + 10, 14, gc, 0.38f + 0.1f * (float) Math.sin(Px.time * 2.5f));
            R(tx, ty, 20, 20, rgb(14, 12, 26), 0.92f);
            int bc = mix(gc, Px.BLACK, 0.25f);
            R(tx, ty, 20, 1, bc, 1);
            R(tx, ty + 19, 20, 1, bc, 1);
            R(tx, ty, 1, 20, bc, 1);
            R(tx + 19, ty, 1, 20, bc, 1);
            for (int[] cc : new int[][] {{0, 0}, {19, 0}, {0, 19}, {19, 19}}) {
                P(tx + cc[0], ty + cc[1], mix(gc, WHITE, 0.6f), 1);
            }
            var stack = r.stack();
            if (UiIcons.special(r.id())) {
                UiIcons.icon(r.id(), tx + 2, ty + 2);
            } else {
                UiIcons.item(stack, tx + 2, ty + 2, 1);
            }
            if (r.n() > 1) {
                Px.g.pose().pushPose();
                Px.g.pose().translate(0, 0, 300);
                T(String.valueOf(r.n()), tx + 19, ty + 11, WHITE, 7, false, 1, true, 1);
                Px.g.pose().popPose();
            }
            String name = !r.name().isEmpty() ? r.name() : r.id().equals(UiIcons.PODER) ? tr("poder") : stack.isEmpty() ? r.id() : stack.getHoverName().getString();
            String sub = !r.desc().isEmpty() ? r.desc() : r.id().equals(UiIcons.PODER) ? tr("poder_sub") : com.starkinhon.battlegroundsmod.client.ui.Journal.daily(m) ? tr("toda_vez") : tr("uma_vez_r");
            List<String> tip = new ArrayList<>();
            tip.add(name);
            if (!r.desc().isEmpty()) {
                tip.add(r.desc());
            }
            Px.hit(tx, ty, 20, 20, null, tip, List.of(WHITE, MUTED));
            if (compact) {
                continue;
            }
            float nw = cw - 26;
            T(Px.fit(name + (r.n() > 1 ? " x" + r.n() : ""), nw, 7), tx + 26, ty + 2, rgb(255, 246, 226), 7, true);
            if (showSub) {
                T(Px.fit(sub, nw, 6), tx + 26, ty + 11, dim, 6);
            }
            yy += rowH;
        }
        if (compact) {
            yy += 24;
        }
        // ---- footer: when it comes back, the distance and Rastrear ----
        for (float i = cx; i < x + w - 6; i++) {
            P(i, by - 8, GOLD, 0.22f);
        }
        diamond(Math.round((cx + x + w - 6) / 2), by - 8, 1, GOLD, 0.6f);
        if (yy <= by - 20) {
            T(reset(m, state), cx, by - 20, "feita".equals(state) ? rgb(130, 226, 120) : dim, 6);
        }
        if (e.found()) {
            boolean isT = m.id().equals(Journal.tracked);
            String label = isT ? tr("rastreando") : tr("rastrear");
            int bw3 = tw(label, 8, true) + 26 + (isT ? 10 : 0);
            float bx3 = x + w - 6 - bw3;
            boolean over = Px.over(bx3, by, bw3, 16);
            int[] way = Journal.way(m.id());
            if (way != null) {
                Px.arrow(cx, by + 3, way[1], rgb(150, 214, 255), 1);
                T(way[0] + " m", cx + 12, by + 4, rgb(200, 230, 255), 8, true);
            } else {
                T(tr("outro_mundo"), cx, by + 5, dim, 6);
            }
            int base = isT ? rgb(70, 140, 60) : GOLD;
            Px.button(bx3, by, bw3, 16, base, over);
            if (isT) {
                Px.sight(bx3 + 9, by + 4, rgb(210, 255, 180), 1);
            }
            T(label, bx3 + bw3 / 2f + (isT ? 5 : 0), by + 4, isT ? rgb(210, 255, 180) : rgb(255, 240, 200), 8, true, 0, true, 1);
            Px.hit(bx3, by, bw3, 16, () -> {
                Journal.track(isT ? "" : m.id());
                click();
            });
        }
    }

    private static float section(String lab, float cx, float yy, float right) {
        T(lab, cx, yy, GOLD, 6, true);
        float s0 = cx + tw(lab, 6, true) + 5;
        for (float i = s0; i < right; i++) {
            P(i, yy + 3, GOLD, 0.35f * (1 - (i - s0) / (right - s0)));
        }
        return yy + 11;
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
