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

// The missions on screen, in the approved style (Stark 2026-11-13, "aplicar em todas as HUDs"): no box, a veil born at the
// screen's edge fading to the right with a gold line on top.
// - The corner panel: the face of who gives the mission, the place, what to do, the progress bar with its diamond and,
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

    // The mission of a quest id (est_<place>[_state] = a structure's).
    private static Journal.Mission missionOf(String quest) {
        if (quest == null || !quest.startsWith("est_")) {
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

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || CutsceneHud.hidden() || minecraft.screen != null) {
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
        if (id.isEmpty()) {
            return 4;
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
        int pw = 148;
        List<String> lines = Px.wrap(text, pw - 36, 7);
        if (lines.size() > 2) {
            lines = lines.subList(0, 2);
        }
        boolean bar = goal > 1;
        int[] dir = null;
        if (way && minecraft.player != null) {
            double dx = x + 0.5 - minecraft.player.getX(), dz = z + 0.5 - minecraft.player.getZ();
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float rel = Mth.wrapDegrees(target - minecraft.player.getYRot());
            dir = new int[] {dist, Math.floorMod(Math.round(rel / 45f), 8)};
        }
        float textEnd = 14 + lines.size() * 9;
        float ph = Math.max(30, textEnd + (bar ? 10 : 0) + (bar || dir != null ? 9 : 0) + 2);
        float px = 4 - (1 - pin) * 160, py = 4;
        Px.hudVeil(px, py, pw, ph, uc, 0.85f, false);
        Px.glow(px + 14.5f, py + 14.5f, 12, uc, 0.45f);
        SkinDraw.head(skin, px + 14.5f, py + 14.5f, 1.2f, 0xFFFFFFFF);
        T(Px.fit(head, pw - 36, 7), px + 28, py + 5, rgb(255, 236, 180), 7, true);
        float ty = py + 14;
        for (String l : lines) {
            T(l, px + 28, ty, TEXT, 7);
            ty += 9;
        }
        float ly = ty + 2;
        if (bar) {
            float bw = pw - 36, prog = clamp(count / (float) goal, 0, 1);
            R(px + 28, ly, bw, 2, WHITE, 0.1f);
            R(px + 28, ly, Math.round(bw * prog), 2, rgb(0x9c, 0xf0, 0x60), 1);
            Px.diamond(px + 28 + Math.round(bw * prog), ly + 1, 2, GOLD, 1);
            T(count + "/" + goal, px + pw - 8, ly + 5, MUTED, 6, false, 1, true, 1);
            ly += 5;
        }
        if (dir != null) {
            Px.arrow(px + 28, ly, dir[1], rgb(143, 211, 255), 1);
            T(dir[0] + " m", px + 40, ly + 1, rgb(143, 211, 255), 6);
        }
        return py + ph + 3;
    }

    // The mission tracked in the journal, small, under the corner panel (unless it is the one already shown).
    private static void tracked(Minecraft minecraft, float y) {
        String t = Journal.tracked;
        if (t == null || t.isEmpty()) {
            return;
        }
        Journal.Mission m = Journal.mission(t);
        Journal.Mission here = missionOf(id);
        // the corner panel already runs this mission (the Kame's milk: "kame_casco_leite"), with its own arrow to the
        // spot: a second line pointing at the structure sent Stark the wrong way (2026-11-13)
        if (m == null || m == here || !id.isEmpty() && (id.equals(t) || id.startsWith(t + "_"))) {
            return;
        }
        int pw = 148;
        Px.hudVeil(4, y, pw, 22, rgb(90, 80, 110), 0.6f, true);
        SkinDraw.head(m.skin(), 14, y + 11, 1, 0xFFFFFFFF);
        int[] w = Journal.way(t);
        float lx = 24;
        String place = m.lugar() + " · ";
        lx += T(place, lx, y + 3, rgb(200, 190, 220), 6);
        Px.sight(lx, y + 3, rgb(255, 236, 140), 1);
        lx += 9;
        if (w != null) {
            lx += T(w[0] + " m", lx, y + 3, rgb(200, 190, 220), 6) + 3;
            Px.arrow(lx, y + 2, w[1], rgb(143, 211, 255), 1);
        } else {
            T(tr("outro_mundo"), lx, y + 3, MUTED, 6);
        }
        T(Px.fit(Journal.currentStep(m), pw - 26, 6), 24, y + 12, MUTED, 6);
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
        Journal.Mission m = Journal.mission(arriveMission);
        if (m == null) {
            return;
        }
        float a = t < 0.4f ? Px.easeOut(t / 0.4f) : t > 3 ? 1 - (t - 3) / 0.6f : 1;
        float cy = GH * 0.28f;
        String nm = m.lugar().toUpperCase();
        int nw = tw(nm, 20, true);
        for (int i = 0; i < 60; i++) {
            float k = i / 60f;
            R(GW / 2f - nw / 2f - 60 + k * (nw + 120), cy + 26, (nw + 120) / 60f + 1, 1, GOLD, (float) Math.sin(k * Math.PI) * a);
        }
        T(nm, GW / 2f, cy - (1 - a) * 10, rgb(255, 236, 180), 20, true, 0, true, a);
        String line = m.nome();
        int lw = tw(line, 9);
        Px.diamond(GW / 2f - lw / 2f - 8, cy + 35, 2, GOLD, a);
        Px.diamond(GW / 2f + lw / 2f + 8, cy + 35, 2, GOLD, a);
        T(line, GW / 2f, cy + 31, WHITE, 9, false, 0, true, a);
        String kind = com.starkinhon.battlegroundsmod.client.ui.Journal.dailyFirst(m) ? tr("diaria") : com.starkinhon.battlegroundsmod.client.ui.Journal.onceFirst(m) ? tr("unica") : tr("missao");
        String reward = m.rec().isEmpty() ? "" : rewardName(m.rec().get(0));
        T(reward.isEmpty() ? kind : tr("recompensa", kind, reward), GW / 2f, cy + 44, rgb(200, 190, 220), 7, false, 0, true, a);
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
        float bx = GW / 2f - bw / 2f, by = GH * 0.22f;
        Px.frame(bx, by, bw, 46, GOLD, 0.92f * a, true, true, null);
        T(tr("concluida"), GW / 2f, by + 7, rgb(255, 236, 140), 12, true, 0, true, a);
        String sub = m != null ? m.nome() + " · " + m.lugar() : Component.translatable("quest.battlegroundsmod." + doneQuest + ".titulo").getString();
        T(sub, GW / 2f, by + 22, TEXT, 7, false, 0, true, a);
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
        T(String.join("  ·  ", got), GW / 2f, by + 32, rgb(128, 255, 32), 8, true, 0, true, a);
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
