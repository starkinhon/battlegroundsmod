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
import static com.starkinhon.battlegroundsmod.client.ui.Px.lerp;
import static com.starkinhon.battlegroundsmod.client.ui.Px.mix;
import static com.starkinhon.battlegroundsmod.client.ui.Px.rgb;
import static com.starkinhon.battlegroundsmod.client.ui.Px.tw;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.client.ui.Art;
import com.starkinhon.battlegroundsmod.client.ui.Journal;
import com.starkinhon.battlegroundsmod.client.ui.Px;
import com.starkinhon.battlegroundsmod.client.ui.SkinDraw;
import com.starkinhon.battlegroundsmod.client.ui.UiIcons;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

// The missions on screen, in each universe's material (Stark 2026-11-22, web preview "Diário Sem Cara de IA", aba
// Rastreador): Naruto the toad scroll (red roller with the face on its label, tan band with frogs and tadpoles), Dragon
// Ball a little Capsule Corp gadget, Jujutsu and the universes with no theme yet a lined index card with a clip.
// - The corner panel: the face of who gives the mission, the place, what to do, the progress and,
//   when it points to a place, an arrow turning with the camera and the distance (the Dragon Ball chain, universe/DbQuests,
//   or the structure the player is in, universe/StructureMissions).
// - Under it, smaller, the mission tracked in the Diário de Missões (client/MissionScreen) with its arrow.
// - Walking into a structure: its name comes down big with the mission under it (instead of the vanilla title).
// - A mission done: MISSÃO CONCLUÍDA with what it gave, and the rewards flying down to the hotbar.
// Drawn on the preview's pixel grid (client/ui/Px).
public final class QuestHud {
    private static String id = "";
    private static int count, goal, x, z;
    private static boolean way;
    // the corner panel sliding in when the quest changes
    private static String shownId = "";
    private static long shownAt;
    // the arrival title (a structure walked into) and the mission done banner
    private static String arriveMission = "";
    private static long arriveAt = -1;
    private static int arriveColor;
    private static String doneMission = "", doneQuest = "";
    private static long doneAt = -1;
    private static List<String> doneRewards = List.of();
    private static boolean doneSeen, arriveSeen;

    private QuestHud() {
    }

    static void set(String id, int count, int goal, int x, int z, boolean way) {
        if (!id.equals(QuestHud.id)) {
            shownAt = System.currentTimeMillis();
        }
        QuestHud.id = id;
        QuestHud.count = count;
        QuestHud.goal = goal;
        QuestHud.x = x;
        QuestHud.z = z;
        QuestHud.way = way;
    }

    public static void clear() {
        id = "";
    }

    // From the server (ModPayloads.MissionEvent): a structure walked into, or a mission done.
    public static void event(String mission, String quest, int color, boolean done, List<String> rewards) {
        if (done) {
            doneMission = mission;
            doneQuest = quest;
            doneRewards = List.copyOf(rewards);
            doneAt = System.currentTimeMillis();
            doneSeen = false;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f));
        } else {
            arriveMission = mission;
            arriveColor = color;
            arriveAt = System.currentTimeMillis();
            arriveSeen = false;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1f, 1f));
        }
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("hud.battlegroundsmod.missao." + key, args).getString();
    }

    // The mission of a quest id (est_<place>[_state] = a structure's; the activities of universe/DbActivities run in a
    // structure too: kame_*, gravidade, <waves>_luta). Without it the panel fell back to Bulma's face (Stark 2026-11-19:
    // the Kame's milk run showed Bulma).
    private static Journal.Mission missionOf(String quest) {
        if (quest == null) {
            return null;
        }
        String activity = activityPlace(quest);
        if (activity != null) {
            return Journal.mission(activity);
        }
        if (!quest.startsWith("est_")) {
            return null;
        }
        String rest = quest.substring(4);
        for (List<Journal.Mission> list : Journal.catalog().values()) {
            for (Journal.Mission m : list) {
                if (rest.equals(m.id()) || rest.startsWith(m.id() + "_")) {
                    return m;
                }
            }
        }
        return rest.equals("pico") ? Journal.mission("myoboku") : null;
    }

    // The structure an activity of universe/DbActivities belongs to (its Hud ids), or null.
    private static String activityPlace(String quest) {
        if (quest.startsWith("kame_")) {
            return "kame";
        }
        return switch (quest) {
            case "gravidade" -> "nave";
            case "cell_luta" -> "cell";
            case "babidi_luta" -> "babidi";
            case "namek_luta" -> "vila";
            default -> null;
        };
    }

    // The face of a Naruto structure with no mission in the journal (est_covil, est_deserto): who lives there.
    private static String narutoFace(String quest) {
        if (quest.startsWith("est_covil")) {
            return "ninja_som";            // the 64x64 skin: ninja_som_0 is the 128x128 blocky model texture (drew a garbled head)
        }
        if (quest.startsWith("est_deserto")) {
            return "gaara";                // the hideout where Gaara was taken; marionetista_0 is a blocky model texture (garbled head)
        }
        return null;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || CutsceneHud.hidden() || minecraft.screen != null || IntroHud.active() || IntroHud.running()) {
            // held at their start while something covers the HUD (the talk scene, a menu): seen when it closes
            // (only until it starts: one already on screen runs out, it does not play again after the next talk)
            long now = System.currentTimeMillis();
            if (doneAt >= 0 && !doneSeen) {
                doneAt = now;
            }
            if (arriveAt >= 0 && !arriveSeen) {
                arriveAt = now;
            }
            return;
        }
        if (doneAt >= 0) {
            doneSeen = true;
        }
        if (arriveAt >= 0) {
            arriveSeen = true;
        }
        Px.begin(graphics, -1, -1);
        try {
            float y = corner(minecraft);
            tracked(minecraft, y);
            arrival();
            done();
        } finally {
            Px.end();
        }
    }

    // The corner panel; returns where the next one goes.
    private static float corner(Minecraft minecraft) {
        if (!cornerShown()) {
            return 8;
        }
        if (!id.equals(shownId)) {
            shownId = id;
        }
        float pin = Px.easeOut((System.currentTimeMillis() - shownAt) / 400f);
        Journal.Mission m = missionOf(id);
        String own = "quest.battlegroundsmod." + id + ".titulo";
        String head = m != null ? m.lugar() : I18n.exists(own) ? Component.translatable(own).getString() : Component.translatable("message.battlegroundsmod.missao").getString();
        String text = Component.translatable("quest.battlegroundsmod." + id, count, goal).getString();
        String skin = m != null ? m.skin() : "npc_bulma";
        int uc = m != null && m.uni().equals("naruto") ? 0xF28C28 : 0xFF9A2E;
        String naruto = m == null ? narutoFace(id) : null;
        if (naruto != null) {
            skin = naruto;
            uc = 0xF28C28;
        }
        if (m == null && id.startsWith("cam_")) {
            // the Caminho (universe/GuidedPath): who sends you there, in the path's color
            String face = "quest.battlegroundsmod." + id + ".rosto";
            skin = I18n.exists(face) ? I18n.get(face) : skin;
            uc = pathColor(id);
        }
        int[] dir = null;
        if (way && minecraft.player != null) {
            double dx = x + 0.5 - minecraft.player.getX(), dz = z + 0.5 - minecraft.player.getZ();
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float rel = Mth.wrapDegrees(target - minecraft.player.getYRot());
            dir = new int[] {dist, Math.floorMod(Math.round(rel / 45f), 8)};
        }
        float slide = (1 - pin) * 180;
        String uni = m != null ? m.uni() : naruto != null ? "naruto" : id.startsWith("cam_nr") ? "naruto" : id.startsWith("cam_jj") ? "jujutsu" : "dragonball";
        return switch (Art.theme(uni)) {
            case SCROLL -> cornerScroll(skin, head, text, dir, 2 - slide, 8);
            case DEVICE -> cornerDevice(skin, head, text, dir, -8 - slide, 8);
            default -> cornerNote(skin, head, text, dir, 2 - slide, 8);
        };
    }

    // Naruto: the toad scroll with the face on its roller.
    private static float cornerScroll(String skin, String head, String text, int[] dir, float x, float y) {
        int w = 168;
        List<String> lines = Px.wrap(text, w - 26, 8);
        int n = Math.min(2, lines.size());
        boolean bar = goal > 1;
        int h = 14 + 10 + n * 9 + (bar || dir != null ? 10 : 0);
        float[] in = Art.toadScroll(x, y, w, h, 3);
        Art.toadFace(skin, x, y, h);
        float ix = in[0], iy = in[1], iw = in[2];
        T(Px.fit(head, iw - 6, 7), ix + 3, iy + 3, Art.N_RED, 7, true, -1, false, 1);
        float ty = iy + 11;
        for (int i = 0; i < n; i++) {
            T(lines.get(i), ix + 3, ty, Art.N_INK, 8, false, -1, false, 1);
            ty += 9;
        }
        if (bar) {
            int pw = 72;
            float prog = clamp(count / (float) goal, 0, 1);
            for (int k = 0; k < pw; k++) {
                if (com.starkinhon.battlegroundsmod.client.ui.Canvas.hash(k, 0, 91) > 0.55f) {
                    P(ix + 4 + k, ty + 4, Art.N_GREY, 0.4f);
                }
            }
            if (prog > 0) {
                Art.brush(ix + 4, ty + 4, Math.max(2, Math.round(pw * prog)), 3, Art.N_INK, 92, 0.95f, 1);
            }
            T(count + "/" + goal, ix + pw + 8, ty + 1, Art.N_INK, 7, false, -1, false, 1);
        }
        if (dir != null) {
            float ax = ix + iw - 38;
            Px.arrow(ax, ty, dir[1], Art.N_INK, 1);
            T(dir[0] + " m", ax + 11, ty + 1, Art.N_INK, 7, true, -1, false, 1);
        }
        return y + h + 8;
    }

    // Dragon Ball: a little Capsule Corp gadget, the face beside the screen.
    private static float cornerDevice(String skin, String head, String text, int[] dir, float x, float y) {
        int w = 158, sx = 30;
        List<String> lines = Px.wrap(text, w - sx - 14, 8);
        int n = Math.min(2, lines.size());
        boolean bar = goal > 1;
        int h = 22 + n * 8 + (bar || dir != null ? 9 : 0);
        Art.miniDevice(x, y, w, h, sx);
        SkinDraw.head(skin, x + 17, y + h / 2f, 1.6f, 0xFFFFFFFF);
        float tx = x + sx + 4;
        Art.scan(x + sx, y + 5, w - sx - 8, h - 11, Px.time);
        T(Px.fit(head, w - sx - 14, 7), tx, y + 8, Art.D_AMB, 7, true, -1, false, 1);
        float ty = y + 16;
        for (int i = 0; i < n; i++) {
            T(lines.get(i), tx, ty, Art.D_TXT, 8, false, -1, false, 1);
            ty += 8;
        }
        if (bar) {
            int segs = 10;
            Art.segBar(tx + 1, ty + 3, segs, Math.round(segs * clamp(count / (float) goal, 0, 1)), 5);
            T(count + "/" + goal, x + w - 12, ty + 1, Art.D_DIM, 7, false, 1, false, 1);
        } else if (dir != null) {
            T(dir[0] + " m", tx, ty + 1, Art.D_TXT, 7, true, -1, false, 1);
            Px.arrow(tx + tw(dir[0] + " m", 7, true) + 4, ty, dir[1], Art.D_AMB, 1);
        }
        return y + h + 8;
    }

    // Jujutsu and the rest: a lined index card with a clip.
    private static float cornerNote(String skin, String head, String text, int[] dir, float x, float y) {
        int w = 150;
        List<String> lines = Px.wrap(text, w - 22, 8);
        int n = Math.min(2, lines.size());
        boolean bar = goal > 1;
        int h = 16 + n * 9 + (bar || dir != null ? 11 : 2);
        R(x + 2, y + 2, w, h, 0, 0.3f);
        Art.note(x, y, w, h);
        R(x, y + 5, w, 1, rgb(220, 90, 90), 1);
        for (int k = 1; k * 9 + 5 < h; k++) {
            R(x, y + 5 + k * 9, w, 1, rgb(170, 190, 230), 0.5f);
        }
        Art.clip(x + 6, y - 3, 14);
        T(Px.fit(head, w - 22, 7), x + 16, y + 7, Art.J_STAMP, 7, true, -1, false, 1);
        float ty = y + 16;
        for (int i = 0; i < n; i++) {
            T(lines.get(i), x + 16, ty, Art.J_TYPE, 8, false, -1, false, 1);
            ty += 9;
        }
        if (bar) {
            float prog = clamp(count / (float) goal, 0, 1);
            int pw = 92;
            for (int k = 0; k < pw; k++) {
                if (k < pw * prog) {
                    P(x + 16 + k, ty + 4 + Math.round((com.starkinhon.battlegroundsmod.client.ui.Canvas.vnoise(k * 0.2f, 1, 4) - 0.5f) * 1.4f), Art.J_PEN, 1);
                } else if (k % 3 == 0) {
                    P(x + 16 + k, ty + 4, Art.J_GREY, 1);
                }
            }
            T(count + "/" + goal, x + 16 + pw + 4, ty + 1, Art.J_TYPE, 7, false, -1, false, 1);
        } else if (dir != null) {
            Px.arrow(x + 16, ty, dir[1], Art.J_PEN, 1);
            T(dir[0] + " m", x + 28, ty + 1, Art.J_TYPE, 7, false, -1, false, 1);
        }
        SkinDraw.head(skin, x + w - 12, y + 12, 1, 0xFFFFFFFF);
        return y + h + 8;
    }

    // "Rastrear missões sozinho" off (Experiência do jogador): the structure's mission and the Caminho's next step no
    // longer come up in the corner by themselves; an activity the player started (the Kame's training, a fight) still
    // shows its count, and the mission tracked in the journal shows as always.
    private static boolean cornerShown() {
        return !id.isEmpty() && (com.starkinhon.battlegroundsmod.client.fx.FxConfig.autoTrack() || activityPlace(id) != null);
    }

    // The mission tracked in the journal, small, under the corner panel (unless it is the one already shown).
    private static void tracked(Minecraft minecraft, float y) {
        String t = Journal.tracked;
        if (t == null || t.isEmpty()) {
            return;
        }
        Journal.Mission m = Journal.mission(t);
        String id = cornerShown() ? QuestHud.id : "";
        Journal.Mission here = id.isEmpty() ? null : missionOf(id);
        // the corner panel already runs this mission (the Kame's milk: "kame_casco_leite"), with its own arrow to the
        // spot: a second line pointing at the structure sent Stark the wrong way (2026-11-13)
        if (m == null || m == here || !id.isEmpty() && (id.equals(t) || id.startsWith(t + "_"))) {
            return;
        }
        // done (for good or for today): gone at once, the server drops the tracking on its next sync
        String state = Journal.entry(t).state();
        if (state.equals("concluida") || state.equals("feita")) {
            return;
        }
        int[] w = Journal.way(t);
        String where = m.lugar() + (w != null ? " · " + w[0] + " m" : " · " + tr("outro_mundo"));
        String step = Journal.currentStep(m);
        switch (Art.theme(m.uni())) {
            case SCROLL -> {
                int pw = 124, ph = 30;
                float[] in = Art.toadScroll(2, y, pw, ph, 8);
                Art.toadFace(m.skin(), 2, y, ph);
                T(Px.fit(where, in[2] - 16, 7), in[0] + 3, in[1] + 2, Art.N_RED, 7, true, -1, false, 1);
                T(Px.fit(step, in[2] - 6, 7), in[0] + 3, in[1] + 9, Art.N_INK, 7, false, -1, false, 1);
                if (w != null) {
                    Px.arrow(in[0] + in[2] - 11, in[1] + 1, w[1], Art.N_INK, 1);
                }
            }
            case DEVICE -> {
                int pw = 124, ph = 26;
                Art.miniDevice(-8, y, pw, ph, 30);
                SkinDraw.head(m.skin(), 9, y + 13, 1.3f, 0xFFFFFFFF);
                T(Px.fit(where, pw - 52, 7), 25, y + 6, Art.D_TXT, 7, false, -1, false, 1);
                T(Px.fit(step, pw - 44, 7), 25, y + 14, Art.D_DIM, 7, false, -1, false, 1);
                if (w != null) {
                    Px.arrow(pw - 24, y + 5, w[1], Art.D_AMB, 1);
                }
            }
            default -> {
                int pw = 140, ph = 24;
                R(4, y + 2, pw, ph, 0, 0.3f);
                Art.note(2, y, pw, ph);
                R(2, y + 4, pw, 1, rgb(220, 90, 90), 1);
                Art.clip(8, y - 3, 10);
                T(Px.fit(where, pw - 34, 7), 18, y + 6, Art.J_STAMP, 7, true, -1, false, 1);
                T(Px.fit(step, pw - 22, 7), 18, y + 14, Art.J_TYPE, 7, false, -1, false, 1);
                if (w != null) {
                    Px.arrow(pw - 12, y + 4, w[1], Art.J_PEN, 1);
                }
            }
        }
    }

    // Walking into a structure: the place's name comes down, a gold line, the mission and what it gives.
    private static void arrival() {
        if (arriveAt < 0) {
            return;
        }
        float t = (System.currentTimeMillis() - arriveAt) / 1000f;
        if (t > 3.6f) {
            arriveAt = -1;
            return;
        }
        if (arriveMission.startsWith("cam_")) {
            newPath(t);
            return;
        }
        Journal.Mission m = Journal.mission(arriveMission);
        if (m == null) {
            return;
        }
        float a = t < 0.4f ? Px.easeOut(t / 0.4f) : t > 3 ? 1 - (t - 3) / 0.6f : 1;
        float cy = GH * 0.28f;
        String nm = m.lugar().toUpperCase();
        int nw = tw(nm, 20, true);
        T(nm, GW / 2f, cy - (1 - a) * 10, rgb(255, 246, 230), 20, true, 0, true, a);
        stroke(GW / 2f - nw / 2f - 10, cy + 25, nw + 20, inkOf(m.uni()), a);
        String line = m.nome();
        T(line, GW / 2f, cy + 31, WHITE, 9, false, 0, true, a);
        String kind = com.starkinhon.battlegroundsmod.client.ui.Journal.dailyFirst(m) ? tr("diaria") : com.starkinhon.battlegroundsmod.client.ui.Journal.onceFirst(m) ? tr("unica") : tr("missao");
        String reward = m.rec().isEmpty() ? "" : rewardName(m.rec().get(0));
        T(reward.isEmpty() ? kind : tr("recompensa", kind, reward), GW / 2f, cy + 44, rgb(200, 190, 220), 7, false, 0, true, a);
    }

    // The universe's color for the strokes over the world.
    private static int inkOf(String uni) {
        return switch (Art.theme(uni)) {
            case SCROLL -> rgb(200, 60, 44);
            case DEVICE -> rgb(255, 160, 60);
            default -> rgb(150, 120, 255);
        };
    }

    // A brush stroke under a title, with a dark one under it so it reads over any sky.
    private static void stroke(float x, float y, int w, int col, float a) {
        Art.brush(x + 1, y + 1, w, 3, 0, 31, 0.45f * a, 0.8f);
        Art.brush(x, y, w, 3, col, 31, a, 0.8f);
    }

    static int pathColor(String quest) {
        if (quest.startsWith("cam_nr")) {
            return 0xF28C28;
        }
        if (quest.startsWith("cam_jj")) {
            return 0x7A5CFF;
        }
        return quest.startsWith("cam_fim") ? 0xD8352A : 0xFF9A2E;
    }

    // NOVA MISSÃO of the Caminho: the title comes down, a brush stroke in the path's color, the mission and where.
    private static void newPath(float t) {
        float a = t < 0.4f ? Px.easeOut(t / 0.4f) : t > 3 ? 1 - (t - 3) / 0.6f : 1;
        float cy = GH * 0.26f;
        String top = tr("nova");
        T(top, GW / 2f, cy - 12 - (1 - a) * 6, mix(pathColor(arriveMission), WHITE, 0.3f), 7, true, 0, true, a);
        String nm = Component.translatable("quest.battlegroundsmod." + arriveMission + ".titulo").getString().toUpperCase();
        int nw = tw(nm, 16, true);
        T(nm, GW / 2f, cy - (1 - a) * 10, rgb(255, 246, 230), 16, true, 0, true, a);
        stroke(GW / 2f - nw / 2f - 8, cy + 20, nw + 16, pathColor(arriveMission), a);
        String line = Component.translatable("quest.battlegroundsmod." + arriveMission).getString();
        T(line, GW / 2f, cy + 26, WHITE, 8, false, 0, true, a);
    }

    private static String rewardName(Journal.Reward r) {
        if (!r.name().isEmpty()) {
            return r.name();
        }
        if (r.id().equals(UiIcons.PODER)) {
            return tr("poder");
        }
        ItemStack s = r.stack();
        return s.isEmpty() ? "" : s.getHoverName().getString();
    }

    // MISSÃO CONCLUÍDA: the gold frame, the mission, what it gave, and the rewards flying down to the hotbar.
    private static void done() {
        if (doneAt < 0) {
            return;
        }
        float ct = (System.currentTimeMillis() - doneAt) / 1000f;
        if (ct > 4) {
            doneAt = -1;
            return;
        }
        Journal.Mission m = Journal.mission(doneMission);
        float a = ct < 0.3f ? Px.easeOut(ct / 0.3f) : ct > 3.4f ? 1 - (ct - 3.4f) / 0.6f : 1;
        int bw = 200;
        float bx = GW / 2f - bw / 2f, by = GH * 0.22f - (1 - a) * 8;
        Art.Theme th = Art.theme(m != null ? m.uni() : doneQuest.startsWith("cam_nr") ? "naruto" : doneQuest.startsWith("cam_jj") ? "jujutsu" : "dragonball");
        int title, body, good;
        if (th == Art.Theme.SCROLL) {
            Art.scroll(bx, by - 5, bw, 56);
            title = Art.N_RED;
            body = Art.N_INK;
            good = rgb(40, 120, 40);
        } else if (th == Art.Theme.DEVICE) {
            Art.miniDevice(bx, by - 2, bw, 50, 8);
            Art.scan(bx + 8, by + 3, bw - 16, 39, Px.time);
            title = Art.D_AMB;
            body = Art.D_TXT;
            good = rgb(150, 255, 120);
        } else {
            R(bx + 2, by + 2, bw, 46, 0, 0.3f);
            Art.note(bx, by, bw, 46);
            Art.clip(bx + 10, by - 4, 14);
            title = Art.J_STAMP;
            body = Art.J_TYPE;
            good = rgb(40, 120, 40);
        }
        T(tr("concluida"), GW / 2f, by + 7, title, 12, true, 0, false, a);
        String sub = m != null ? m.nome() + " · " + m.lugar() : Component.translatable("quest.battlegroundsmod." + doneQuest + ".titulo").getString();
        if (m == null && doneQuest.startsWith("cam_")) {
            sub = Component.translatable("quest.battlegroundsmod." + doneQuest + ".titulo").getString() + " · "
                    + Component.translatable("hud.battlegroundsmod.missao.caminho").getString();
        }
        T(Px.fit(sub, bw - 20, 7), GW / 2f, by + 22, body, 7, false, 0, false, a);
        List<String> got = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (String r : doneRewards) {
            if (r.equals(UiIcons.PODER)) {
                got.add("+ " + tr("poder"));
                continue;
            }
            String[] p = r.split(" ");
            ItemStack s = Journal.stack(p[0], p.length > 1 ? Integer.parseInt(p[1]) : 1);
            if (!s.isEmpty()) {
                stacks.add(s);
                got.add(s.getHoverName().getString() + (s.getCount() > 1 ? " x" + s.getCount() : ""));
            }
        }
        T(Px.fit(String.join("  ·  ", got), bw - 16, 8), GW / 2f, by + 32, good, 8, true, 0, false, a);
        // the rewards fly down to the hotbar
        for (int i = 0; i < 14; i++) {
            float[] r = rand(i + 11);
            float p = clamp((ct - 0.2f - r[0] * 0.5f) / 1.2f, 0, 1);
            if (p > 0 && p < 1) {
                float x0 = GW / 2f + (r[1] - 0.5f) * 90, y0 = by + 40, x1 = GW / 2f, y1 = GH - 30;
                P(lerp(x0, x1, Px.easeIn(p)), lerp(y0, y1, Px.easeIn(p)) - (float) Math.sin(p * Math.PI) * 20, mix(rgb(128, 255, 32), WHITE, r[2] * 0.5f), 1);
            }
        }
        for (int i = 0; i < stacks.size(); i++) {
            float p = clamp((ct - 0.6f - i * 0.15f) / 1f, 0, 1);
            if (p > 0 && p < 1) {
                float x0 = GW / 2f - stacks.size() * 10 + i * 20, y0 = by + 48, x1 = GW / 2f - 80 + i * 18, y1 = GH - 20;
                UiIcons.item(stacks.get(i), lerp(x0, x1, Px.easeIn(p)), lerp(y0, y1, Px.easeIn(p)) - (float) Math.sin(p * Math.PI) * 20, 1);
            }
        }
    }

    private static float[] rand(int seed) {
        int s = seed;
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            s ^= s << 13;
            s ^= s >>> 17;
            s ^= s << 5;
            out[i] = (Integer.toUnsignedLong(s) % 100000) / 100000f;
        }
        return out;
    }
}
